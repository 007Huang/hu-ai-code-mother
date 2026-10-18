package com.huzi.huaicodemother.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class QuotaDataAccessExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void lockBusyUsesStableCodeWithoutLeakingSql() {
        var response = handler.dataAccessExceptionHandler(new CannotAcquireLockException("SELECT password private SQL"));
        assertEquals(ErrorCode.LOCK_BUSY_ERROR.getCode(), response.getCode());
        assertEquals(ErrorCode.LOCK_BUSY_ERROR.getMessage(), response.getMessage());
        assertFalse(response.getMessage().contains("SQL"));
    }

    @Test
    void timeoutUsesStableBusyCode() {
        var response = handler.dataAccessExceptionHandler(new QueryTimeoutException("private table and SQL"));
        assertEquals(ErrorCode.LOCK_BUSY_ERROR.getCode(), response.getCode());
    }

    @Test
    void otherDataFailuresUseGenericOperationError() {
        var response = handler.dataAccessExceptionHandler(new DataAccessResourceFailureException("private host and SQL"));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), response.getCode());
        assertEquals(ErrorCode.OPERATION_ERROR.getMessage(), response.getMessage());
    }
}
