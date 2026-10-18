package com.huzi.huaicodemother.support;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** 仅用于单元测试事务代理和同步回调，不创建数据源或读取应用配置。 */
public final class TestTransactions {

    private TestTransactions() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T proxy(T target, RecordingManager manager) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    public static class RecordingManager extends AbstractPlatformTransactionManager {

        private int commits;
        private int rollbacks;
        private TransactionDefinition lastDefinition;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            lastDefinition = definition;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }

        public int getCommits() {
            return commits;
        }

        public int getRollbacks() {
            return rollbacks;
        }

        public TransactionDefinition getLastDefinition() {
            return lastDefinition;
        }
    }
}
