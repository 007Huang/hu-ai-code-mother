package com.huzi.huaicodemother.service;

import com.huzi.huaicodemother.config.AppQuotaProperties;
import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import com.huzi.huaicodemother.mapper.AppMapper;
import com.huzi.huaicodemother.mapper.UserMapper;
import com.huzi.huaicodemother.model.entity.User;
import com.huzi.huaicodemother.model.vo.AppQuotaVO;
import com.huzi.huaicodemother.support.TestTransactions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.TransactionDefinition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppQuotaServiceTest {

    private UserMapper userMapper;
    private AppMapper appMapper;
    private AppQuotaProperties properties;
    private AppQuotaService service;
    private User user;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        appMapper = mock(AppMapper.class);
        properties = new AppQuotaProperties();
        service = new AppQuotaService(userMapper, appMapper, properties);
        user = User.builder().id(7L).build();
        when(userMapper.selectActiveUser(7L)).thenReturn(user);
    }

    @Test
    void legacyNullLimitUsesDefaultAndCountsOnlyTargetUserThroughDedicatedMapper() {
        when(appMapper.countActiveByUserId(7L)).thenReturn(3L);
        AppQuotaVO quota = service.getQuota(7L);
        assertEquals(3, quota.getUsedCount());
        assertEquals(10, quota.getLimitCount());
        assertEquals(7, quota.getRemainingCount());
        assertEquals(10, quota.getDefaultLimit());
        assertNull(quota.getAppLimit());
        assertTrue(quota.getUsesDefault());
        assertTrue(quota.getCanCreate());
        assertFalse(quota.getOverLimit());
        verify(appMapper).countActiveByUserId(7L);
        verifyNoMoreInteractions(appMapper);
    }

    @Test
    void zeroLimitDisablesCreationEvenWithoutApps() {
        user.setAppLimit(0);
        AppQuotaVO quota = service.getQuota(7L);
        assertEquals(0, quota.getLimitCount());
        assertEquals(0, quota.getRemainingCount());
        assertFalse(quota.getCanCreate());
        assertFalse(quota.getOverLimit());
        assertFalse(quota.getUsesDefault());
        assertEquals(ErrorCode.APP_QUOTA_EXCEEDED.getCode(),
                assertThrows(BusinessException.class, () -> service.assertCanCreate(user)).getCode());
    }

    @Test
    void zeroDefaultIsAlsoHonored() {
        properties.setDefaultLimit(0);
        properties.validate();
        assertFalse(service.getQuota(7L).getCanCreate());
    }

    @Test
    void loweredLimitKeepsExistingAppsAndClampsRemaining() {
        user.setAppLimit(2);
        when(appMapper.countActiveByUserId(7L)).thenReturn(12L);
        AppQuotaVO quota = service.getQuota(7L);
        assertEquals(12, quota.getUsedCount());
        assertEquals(0, quota.getRemainingCount());
        assertTrue(quota.getOverLimit());
        assertFalse(quota.getCanCreate());
        verify(appMapper, never()).deleteById(any());
    }

    @Test
    void equalLimitIsNotOverLimitButCannotCreate() {
        user.setAppLimit(10000);
        when(appMapper.countActiveByUserId(7L)).thenReturn(10000L);
        AppQuotaVO quota = service.getQuota(7L);
        assertFalse(quota.getOverLimit());
        assertFalse(quota.getCanCreate());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 2147483648L, Long.MAX_VALUE})
    void invalidOrOverflowCountRaisesBusinessException(long count) {
        when(appMapper.countActiveByUserId(7L)).thenReturn(count);
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.getQuota(7L)).getCode());
    }

    @Test
    void maximumSupportedCountIsStillAnInteger() {
        when(appMapper.countActiveByUserId(7L)).thenReturn((long) Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, service.getQuota(7L).getUsedCount());
    }

    @Test
    void resetUsesExplicitNullWriteUnderUserLockAndShortReadCommittedTransaction() {
        user.setAppLimit(20);
        when(userMapper.selectActiveUserForUpdate(7L)).thenReturn(user);
        when(userMapper.updateAppLimit(7L, null)).thenReturn(1);
        TestTransactions.RecordingManager manager = new TestTransactions.RecordingManager();
        AppQuotaService proxy = TestTransactions.proxy(service, manager);

        assertTrue(proxy.updateQuota(7L, null).getUsesDefault());
        var order = inOrder(userMapper, appMapper);
        order.verify(userMapper).selectActiveUserForUpdate(7L);
        order.verify(userMapper).updateAppLimit(7L, null);
        order.verify(appMapper).countActiveByUserId(7L);
        assertEquals(1, manager.getCommits());
        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, manager.getLastDefinition().getIsolationLevel());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, manager.getLastDefinition().getPropagationBehavior());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 10001})
    void invalidUpdateRejectedBeforeLock(int limit) {
        assertThrows(BusinessException.class, () -> service.updateQuota(7L, limit));
        verify(userMapper, never()).selectActiveUserForUpdate(any());
    }

    @Test
    void lockFailureRollsBackWithoutUpdating() {
        when(userMapper.selectActiveUserForUpdate(7L)).thenThrow(new CannotAcquireLockException("private SQL"));
        TestTransactions.RecordingManager manager = new TestTransactions.RecordingManager();
        AppQuotaService proxy = TestTransactions.proxy(service, manager);
        assertThrows(CannotAcquireLockException.class, () -> proxy.updateQuota(7L, 1));
        assertEquals(1, manager.getRollbacks());
        verify(userMapper, never()).updateAppLimit(any(), any());
    }

    @Test
    void failedUpdateRowCountRollsBack() {
        when(userMapper.selectActiveUserForUpdate(7L)).thenReturn(user);
        TestTransactions.RecordingManager manager = new TestTransactions.RecordingManager();
        AppQuotaService proxy = TestTransactions.proxy(service, manager);
        assertThrows(BusinessException.class, () -> proxy.updateQuota(7L, 10));
        assertEquals(1, manager.getRollbacks());
        verifyNoInteractions(appMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 10001})
    void startupRejectsInvalidDefault(int defaultLimit) {
        properties.setDefaultLimit(defaultLimit);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }
}
