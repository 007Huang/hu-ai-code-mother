package com.huzi.huaicodemother.service;

import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import com.huzi.huaicodemother.mapper.AppMapper;
import com.huzi.huaicodemother.mapper.UserMapper;
import com.huzi.huaicodemother.model.entity.App;
import com.huzi.huaicodemother.model.entity.User;
import com.huzi.huaicodemother.support.TestTransactions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppCreationServiceTest {

    private UserMapper userMapper;
    private AppMapper appMapper;
    private AppQuotaService quotaService;
    private AppCreationService service;
    private TestTransactions.RecordingManager manager;
    private User lockedUser;
    private App app;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        appMapper = mock(AppMapper.class);
        quotaService = mock(AppQuotaService.class);
        manager = new TestTransactions.RecordingManager();
        service = TestTransactions.proxy(new AppCreationService(userMapper, appMapper, quotaService), manager);
        lockedUser = User.builder().id(7L).appLimit(1).build();
        app = App.builder().userId(7L).codeGenType("html").build();
        when(userMapper.selectActiveUserForUpdate(7L)).thenReturn(lockedUser);
    }

    @Test
    void locksThenChecksThenInsertsAndReturnsBackfilledId() {
        when(appMapper.insert(app, true)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            app.setId(123L);
            return 1;
        });
        assertEquals(123L, service.createApp(app));
        var order = inOrder(userMapper, quotaService, appMapper);
        order.verify(userMapper).selectActiveUserForUpdate(7L);
        order.verify(quotaService).assertCanCreate(lockedUser);
        order.verify(appMapper).insert(app, true);
        assertEquals(1, manager.getCommits());
        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, manager.getLastDefinition().getIsolationLevel());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, manager.getLastDefinition().getPropagationBehavior());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void finalQuotaCheckRejectsConcurrentCapChangeWithoutInsert() {
        doThrow(new BusinessException(ErrorCode.APP_QUOTA_EXCEEDED)).when(quotaService).assertCanCreate(lockedUser);
        assertEquals(ErrorCode.APP_QUOTA_EXCEEDED.getCode(),
                assertThrows(BusinessException.class, () -> service.createApp(app)).getCode());
        verifyNoInteractions(appMapper);
        assertEquals(1, manager.getRollbacks());
    }

    @Test
    void lockFailureDoesNotCheckOrInsertAndRollsBack() {
        when(userMapper.selectActiveUserForUpdate(7L)).thenThrow(new CannotAcquireLockException("lock SQL"));
        assertThrows(CannotAcquireLockException.class, () -> service.createApp(app));
        verifyNoInteractions(quotaService, appMapper);
        assertEquals(1, manager.getRollbacks());
    }

    @Test
    void deletedUserCannotCreate() {
        when(userMapper.selectActiveUserForUpdate(7L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.createApp(app));
        verifyNoInteractions(quotaService, appMapper);
        assertEquals(1, manager.getRollbacks());
    }

    @Test
    void failedInsertRowCountRollsBack() {
        assertThrows(BusinessException.class, () -> service.createApp(app));
        assertEquals(1, manager.getRollbacks());
        assertEquals(0, manager.getCommits());
    }

    @Test
    void databaseInsertFailureIsNotSwallowedOrRetried() {
        when(appMapper.insert(app, true)).thenThrow(new DataIntegrityViolationException("private SQL"));
        assertThrows(DataIntegrityViolationException.class, () -> service.createApp(app));
        verify(appMapper, times(1)).insert(app, true);
        assertEquals(1, manager.getRollbacks());
    }

    @Test
    void successfulRowCountWithoutIdIsStillFailure() {
        when(appMapper.insert(app, true)).thenReturn(1);
        assertThrows(BusinessException.class, () -> service.createApp(app));
        assertEquals(1, manager.getRollbacks());
    }
}
