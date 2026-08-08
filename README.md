# hu-ai-code-mother

基于大语言模型（LLM）的 AI 代码生成平台，支持用户通过自然语言描述自动生成前端项目代码，并提供应用部署、代码预览、截图展示与运维监控等全链路能力。

## 核心特性

- **多模态代码生成**：支持 HTML 单页、多文件工程、Vue 项目等多种输出类型，通过 AI 路由自动选择最佳生成策略。
- **流式实时响应**：基于 SSE（Server-Sent Events）实现 AI 对话的流式输出，用户可实时查看代码生成进度。
- **分布式限流与鉴权**：通过 Redisson 实现用户级 / IP 级 / 接口级分布式限流；基于自定义注解 `@AuthCheck` 实现角色权限校验。
- **可观测性**：集成 Prometheus + Actuator 暴露应用指标，支持自定义 AI 模型调用监控与告警。

## 技术栈

| 层级       | 技术选型                                          |
| ---------- | ------------------------------------------------- |
| 语言       | Java 21, TypeScript                               |
| 后端框架   | Spring Boot 3.4.5, Spring AOP, Spring Session     |
| AI 编排     | LangChain4j 1.1.0, LangGraph4j 1.6.0-rc2          |
| 数据访问   | MyBatis-Flex 1.11.0, MySQL, HikariCP             |
| 缓存与会话 | Redis, Redisson 3.50.0, Caffeine                  |
| 对象存储   | 腾讯云 COS                                        |
| 前端框架   | Vue 3, Vite 7, Ant Design Vue 4, Pinia            |
| 工具库     | Selenium 4.33.0, Hutool 5.8.38, Knife4j 4.4.0     |
| 监控       | Micrometer, Prometheus, Spring Boot Actuator       |
| AI 模型    | DeepSeek (v4-flash / v4-pro), 阿里云 DashScope (文生图) |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- Node.js 18+（前端）
- MySQL 8.0+
- Redis 6.0+

### 后端启动

```bash
# 1. 克隆仓库
git clone https://github.com/your-username/hu-ai-code-mother.git
cd hu-ai-code-mother

# 2. 初始化数据库
# 执行 sql/create_table.sql 创建数据库及表结构
mysql -u root -p < sql/create_table.sql

# 3. 配置密钥与连接信息
# 编辑 src/main/resources/application.yml，将占位符替换为真实值：
#   - MySQL 连接信息（datasource.url / username / password）
#   - Redis 连接信息（spring.data.redis.host / port）
#   - DeepSeek API Key（langchain4j.open-ai.chat-model.api-key 等四项）
#   - 腾讯云 COS 配置（cos.client.*）
#   - 阿里云 DashScope API Key（dashscope.api-key）
#   - Pexels 图片搜索 API Key（pexels.api-key）
#
# 生产环境部署时，可参考 application-prod-sample.yml 模板创建 application-prod.yml

# 4. 编译并启动
mvn clean compile
mvn spring-boot:run

# 服务启动后访问：
#   - API 服务：http://localhost:8123/api
#   - Knife4j 文档：http://localhost:8123/api/doc.html
```

### 前端启动

```bash
cd hu-ai-code-mother-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

### Prometheus 监控（可选）

```bash
# 使用项目根目录的 prometheus.yml 启动 Prometheus
prometheus --config.file=prometheus.yml
```

## 使用示例

### 基于注解的权限校验

项目通过自定义注解 `@AuthCheck` 结合 Spring AOP 实现声明式权限控制，无需在业务代码中编写重复的鉴权逻辑：

```java
@RestController
@RequestMapping("/app")
public class AppController {

    /**
     * 管理员删除应用 —— 仅管理员可访问
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 管理员分页查询应用列表
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(
            @RequestBody AppQueryRequest appQueryRequest) {
        // 分页查询逻辑
        // ...
    }
}
```

切面实现 `AuthInterceptor` 通过 `@Around("@annotation(authCheck)")` 拦截标注了 `@AuthCheck` 的方法，从 `RequestContextHolder` 中获取当前登录用户并校验角色，校验失败抛出 `BusinessException`。

### 基于注解的分布式限流

```java
@GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60,
           message = "AI 对话请求过于频繁，请稍后再试")
public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                   @RequestParam String message,
                                                   HttpServletRequest request) {
    // 流式 AI 对话生成代码
    // ...
}
```

`RateLimitAspect` 基于 Redisson `RRateLimiter` 实现分布式限流，支持 `USER` / `IP` / `API` 三种粒度。

## 项目结构

```
hu-ai-code-mother/
├── hu-ai-code-mother-frontend/      # 前端项目 (Vue 3 + Vite)
│   └── src/
│       ├── api/                     # API 接口封装
│       ├── components/              # 通用组件
│       ├── layouts/                 # 布局组件
│       ├── pages/                   # 页面视图
│       │   ├── admin/               # 管理后台
│       │   ├── app/                 # 应用详情 / 对话
│       │   └── user/                # 用户中心
│       ├── router/                  # 路由配置
│       ├── stores/                  # Pinia 状态管理
│       └── utils/                   # 工具函数
├── sql/                             # 数据库初始化脚本
├── src/main/java/com/huzi/huaicodemother/
│   ├── ai/                          # AI 服务层
│   │   ├── guardrail/               # 安全护栏 (输入 / 输出校验)
│   │   ├── model/                   # AI 返回结果模型
│   │   └── tools/                   # AI Agent 工具集 (文件读写等)
│   ├── annotation/                  # 自定义注解 (@AuthCheck)
│   ├── aop/                         # AOP 切面 (鉴权拦截器)
│   ├── config/                      # Spring 配置类
│   ├── controller/                  # REST 控制器
│   ├── core/                        # 代码生成核心
│   │   ├── builder/                 # 项目构建器 (Vue 工程)
│   │   ├── handler/                 # 流式消息处理器
│   │   ├── parser/                  # AI 输出解析器
│   │   └── saver/                   # 代码文件落盘
│   ├── langgraph4j/                 # LangGraph4j 工作流
│   │   ├── ai/                      # 工作流 AI 服务
│   │   ├── node/                    # 工作流节点
│   │   │   └── concurrent/          # 并行节点 (图片收集等)
│   │   ├── state/                   # 工作流状态上下文
│   │   └── tools/                   # 工作流工具
│   ├── manager/                     # 基础设施管理器 (COS)
│   ├── mapper/                      # MyBatis 数据访问层
│   ├── model/                       # 数据模型
│   │   ├── dto/                     # 请求 DTO
│   │   ├── entity/                  # 数据库实体
│   │   ├── enums/                   # 枚举类
│   │   └── vo/                      # 视图对象
│   ├── monitor/                     # AI 模型调用监控
│   ├── ratelimter/                  # 分布式限流模块
│   │   ├── annotation/              # @RateLimit 注解
│   │   ├── aspect/                  # 限流切面
│   │   └── config/                  # Redisson 配置
│   ├── service/                     # 业务服务层
│   └── utils/                       # 工具类
├── src/main/resources/
│   ├── application.yml              # 主配置文件（含占位符，可直接修改）
│   └── application-prod-sample.yml  # 生产环境配置模板
├── prometheus.yml                   # Prometheus 采集配置
└── pom.xml                          # Maven 项目配置