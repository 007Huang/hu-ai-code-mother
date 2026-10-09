package com.huzi.huaicodemother.service;

import com.huzi.huaicodemother.support.TestTransactions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeaturedAppCacheInvalidatorTest {

    private FeaturedAppCacheInvalidator invalidator;
    private CacheManager cacheManager;
    private Cache cache;
    private TransactionTemplate transaction;
    private TestTransactions.RecordingManager manager;

    @BeforeEach
    void setUp() {
        cacheManager = mock(CacheManager.class);
        cache = mock(Cache.class);
        when(cacheManager.getCache("good_app_page")).thenReturn(cache);
        invalidator = new FeaturedAppCacheInvalidator(cacheManager);
        manager = new TestTransactions.RecordingManager();
        transaction = new TransactionTemplate(manager);
    }

    @Test
    void clearsOnlyAfterSuccessfulCommit() {
        transaction.executeWithoutResult(status -> {
            invalidator.invalidateAfterCommit();
            verifyNoInteractions(cacheManager, cache);
        });
        assertEquals(1, manager.getCommits());
        verify(cache).clear();
        verify(cacheManager).getCache("good_app_page");
    }

    @Test
    void registeredCallbackDoesNotRunOnExplicitRollback() {
        transaction.executeWithoutResult(status -> {
            invalidator.invalidateAfterCommit();
            status.setRollbackOnly();
        });
        assertEquals(1, manager.getRollbacks());
        verifyNoInteractions(cacheManager, cache);
    }

    @Test
    void registeredCallbackDoesNotRunOnExceptionRollback() {
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            invalidator.invalidateAfterCommit();
            throw new IllegalStateException("database failed");
        }));
        assertEquals(1, manager.getRollbacks());
        verifyNoInteractions(cacheManager, cache);
    }

    @Test
    void callbackFailureCannotTurnCommitIntoFailure() {
        doThrow(new IllegalStateException("redis unavailable")).when(cache).clear();
        assertDoesNotThrow(() -> transaction.executeWithoutResult(status -> invalidator.invalidateAfterCommit()));
        assertEquals(1, manager.getCommits());
    }

    @Test
    void noTransactionDoesNotClearCache() {
        invalidator.invalidateAfterCommit();
        verifyNoInteractions(cacheManager, cache);
    }
}
