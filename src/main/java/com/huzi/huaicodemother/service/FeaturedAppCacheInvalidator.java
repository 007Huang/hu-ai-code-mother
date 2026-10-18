package com.huzi.huaicodemother.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 精选列表包含用户资料；仅在数据库成功提交后尽力失效缓存。 */
@Component
@Slf4j
public class FeaturedAppCacheInvalidator {

    private final CacheManager cacheManager;

    public FeaturedAppCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void invalidateAfterCommit() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("未处于事务中，跳过精选应用缓存失效");
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    Cache cache = cacheManager.getCache("good_app_page");
                    if (cache != null) {
                        cache.clear();
                    }
                } catch (RuntimeException e) {
                    log.warn("精选应用缓存失效失败", e);
                }
            }
        });
    }
}
