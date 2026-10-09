package com.huzi.huaicodemother.support;

import com.huzi.huaicodemother.config.AppQuotaProperties;
import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import com.huzi.huaicodemother.mapper.AppMapper;
import com.huzi.huaicodemother.mapper.UserMapper;
import com.huzi.huaicodemother.model.entity.App;
import com.huzi.huaicodemother.model.vo.AppQuotaVO;
import com.huzi.huaicodemother.service.AppCreationService;
import com.huzi.huaicodemother.service.AppQuotaService;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.spring.FlexSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 必须显式提供独立测试库；默认跳过，不读取生产数据库配置。 */
@EnabledIfEnvironmentVariable(named = "PERSONAL_CENTER_TEST_DB_URL", matches = ".+")
class AppQuotaMySqlIntegrationTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private String tablePrefix;
    private AppCreationService creation;
    private AppQuotaService quotas;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("PERSONAL_CENTER_TEST_DB_URL");
        assertTrue(url.startsWith("jdbc:mysql://"), "只允许 MySQL 测试连接");
        String schema = URI.create(url.substring(5)).getPath();
        assertTrue(schema != null && schema.matches("/[A-Za-z0-9_]+_quota_test"),
                "测试库名必须以 _quota_test 结尾，禁止连接业务库");
        dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("PERSONAL_CENTER_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("PERSONAL_CENTER_TEST_DB_PASSWORD", ""));
        jdbc = new JdbcTemplate(dataSource);
        tablePrefix = "pc_" + UUID.randomUUID().toString().replace("-", "") + "_";
        jdbc.execute("CREATE TABLE " + tablePrefix + "user (id BIGINT PRIMARY KEY, appLimit INT NULL, "
                + "editTime DATETIME DEFAULT CURRENT_TIMESTAMP, isDelete INT NOT NULL DEFAULT 0) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE " + tablePrefix + "app (id BIGINT PRIMARY KEY, appName VARCHAR(256), "
                + "userId BIGINT NOT NULL, initPrompt TEXT, codeGenType VARCHAR(64), priority INT DEFAULT 0, "
                + "cover VARCHAR(512), deployKey VARCHAR(64), deployedTime DATETIME, "
                + "editTime DATETIME DEFAULT CURRENT_TIMESTAMP, createTime DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "updateTime DATETIME DEFAULT CURRENT_TIMESTAMP, isDelete INT DEFAULT 0, "
                + "INDEX idx_owner_active(userId, isDelete)) ENGINE=InnoDB");
        jdbc.update("INSERT INTO " + tablePrefix + "user (id, appLimit) VALUES (1, 10)");
        for (long id = 1; id <= 9; id++) {
            jdbc.update("INSERT INTO " + tablePrefix + "app (id, userId) VALUES (?, 1)", id);
        }

        // 使用独立物理库内的随机表，真实 mapper SQL 通过插件改写表名，避免清理其他测试数据。
        FlexConfiguration configuration = new FlexConfiguration();
        configuration.addInterceptor(new TestTableInterceptor(tablePrefix));
        configuration.addMapper(UserMapper.class);
        configuration.addMapper(AppMapper.class);
        FlexSqlSessionFactoryBean factory = new FlexSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        SqlSessionFactory sqlSessionFactory = factory.getObject();
        SqlSessionTemplate session = new SqlSessionTemplate(sqlSessionFactory);
        AppQuotaProperties properties = new AppQuotaProperties();
        AppQuotaService targetQuotas = new AppQuotaService(session.getMapper(UserMapper.class),
                session.getMapper(AppMapper.class), properties);
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        quotas = transactional(targetQuotas, transactions);
        creation = transactional(new AppCreationService(session.getMapper(UserMapper.class),
                session.getMapper(AppMapper.class), quotas), transactions);
    }

    @Test
    void twentyConcurrentCreatesOnlyUseTheLastSlotAndDeletionReleasesIt() throws Exception {
        try (var executor = Executors.newFixedThreadPool(20)) {
            CountDownLatch ready = new CountDownLatch(20);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    try {
                        App app = new App();
                        app.setUserId(1L);
                        app.setAppName("并发测试");
                        app.setCodeGenType("html");
                        Long id = creation.createApp(app);
                        assertTrue(id > 0, "插入必须回填雪花 ID");
                        return true;
                    } catch (BusinessException e) {
                        assertEquals(ErrorCode.APP_QUOTA_EXCEEDED.getCode(), e.getCode());
                        return false;
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int successes = 0;
            for (Future<Boolean> result : futures) {
                if (result.get(30, TimeUnit.SECONDS)) successes++;
            }
            assertEquals(1, successes);
        }
        assertEquals(10, quotas.getQuota(1L).getUsedCount());
        jdbc.update("UPDATE " + tablePrefix + "app SET isDelete = 1 WHERE id = 1");
        App app = new App();
        app.setUserId(1L);
        app.setAppName("释放后创建");
        creation.createApp(app);
        assertEquals(10, quotas.getQuota(1L).getUsedCount());
    }

    @Test
    void zeroAndResetToNullAndLegacyOverLimitKeepExistingRecords() {
        AppQuotaVO zero = quotas.updateQuota(1L, 0);
        assertEquals(9, zero.getUsedCount());
        assertEquals(0, zero.getRemainingCount());
        assertTrue(zero.getOverLimit());
        AppQuotaVO restored = quotas.updateQuota(1L, null);
        assertTrue(restored.getUsesDefault());
        assertEquals(10, restored.getLimitCount());
    }

    @AfterEach
    void cleanUpOwnTables() {
        if (jdbc != null && tablePrefix != null) {
            jdbc.execute("DROP TABLE IF EXISTS " + tablePrefix + "app");
            jdbc.execute("DROP TABLE IF EXISTS " + tablePrefix + "user");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T transactional(T target, DataSourceTransactionManager manager) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(
            type = org.apache.ibatis.executor.statement.StatementHandler.class,
            method = "prepare", args = { java.sql.Connection.class, Integer.class }))
    static class TestTableInterceptor implements org.apache.ibatis.plugin.Interceptor {
        private final String prefix;
        TestTableInterceptor(String prefix) { this.prefix = prefix; }
        @Override
        public Object intercept(org.apache.ibatis.plugin.Invocation invocation) throws Throwable {
            var handler = (org.apache.ibatis.executor.statement.StatementHandler) invocation.getTarget();
            var sql = handler.getBoundSql();
            String replaced = sql.getSql()
                    .replaceAll("(?i)(?<![A-Za-z0-9_])`?user`?(?![A-Za-z0-9_])", prefix + "user")
                    .replaceAll("(?i)(?<![A-Za-z0-9_])`?app`?(?![A-Za-z0-9_])", prefix + "app");
            org.apache.ibatis.reflection.SystemMetaObject.forObject(sql).setValue("sql", replaced);
            return invocation.proceed();
        }
    }
}
