package com.huzi.huaicodemother.service.impl;

import com.huzi.huaicodemother.ai.AiCodeGenTypeRoutingService;
import com.huzi.huaicodemother.ai.AiCodeGenTypeRoutingServiceFactory;
import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import com.huzi.huaicodemother.mapper.AppMapper;
import com.huzi.huaicodemother.mapper.UserMapper;
import com.huzi.huaicodemother.model.dto.app.AppAddRequest;
import com.huzi.huaicodemother.model.entity.App;
import com.huzi.huaicodemother.model.entity.User;
import com.huzi.huaicodemother.model.enums.CodeGenTypeEnum;
import com.huzi.huaicodemother.model.vo.AppQuotaVO;
import com.huzi.huaicodemother.service.AppCreationService;
import com.huzi.huaicodemother.service.AppQuotaService;
import com.huzi.huaicodemother.service.ChatHistoryService;
import com.huzi.huaicodemother.service.FeaturedAppCacheInvalidator;
import com.huzi.huaicodemother.support.TestTransactions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppQuotaLifecycleTest {

    private AppServiceImpl service;
    private AppQuotaService quotaService;
    private AppCreationService creationService;
    private AiCodeGenTypeRoutingServiceFactory routingFactory;
    private AppMapper mapper;
    private UserMapper userMapper;
    private ChatHistoryService historyService;
    private Cache cache;
    private TestTransactions.RecordingManager transactions;
    private User user;

    @BeforeEach
    void setUp() {
        AppServiceImpl target = new AppServiceImpl();
        quotaService = mock(AppQuotaService.class);
        creationService = mock(AppCreationService.class);
        routingFactory = mock(AiCodeGenTypeRoutingServiceFactory.class);
        mapper = mock(AppMapper.class);
        userMapper = mock(UserMapper.class);
        historyService = mock(ChatHistoryService.class);
        CacheManager cacheManager = mock(CacheManager.class);
        cache = mock(Cache.class);
        when(cacheManager.getCache("good_app_page")).thenReturn(cache);
        ReflectionTestUtils.setField(target, "appQuotaService", quotaService);
        ReflectionTestUtils.setField(target, "appCreationService", creationService);
        ReflectionTestUtils.setField(target, "aiCodeGenTypeRoutingServiceFactory", routingFactory);
        ReflectionTestUtils.setField(target, "mapper", mapper);
        ReflectionTestUtils.setField(target, "userMapper", userMapper);
        ReflectionTestUtils.setField(target, "chatHistoryService", historyService);
        ReflectionTestUtils.setField(target, "featuredAppCacheInvalidator", new FeaturedAppCacheInvalidator(cacheManager));
        transactions = new TestTransactions.RecordingManager();
        service = TestTransactions.proxy(target, transactions);
        user = User.builder().id(7L).build();
    }

    @Test
    void capPrecheckSkipsRoutingFactoryAndCreation() {
        AppQuotaVO quota = new AppQuotaVO();
        quota.setCanCreate(false);
        when(quotaService.getQuota(7L)).thenReturn(quota);
        AppAddRequest request = new AppAddRequest();
        request.setInitPrompt("make a page");
        assertEquals(ErrorCode.APP_QUOTA_EXCEEDED.getCode(),
                assertThrows(BusinessException.class, () -> service.createApp(request, user)).getCode());
        verifyNoInteractions(routingFactory, creationService, mapper);
    }

    @ParameterizedTest
    @EnumSource(CodeGenTypeEnum.class)
    void preservesEveryRoutingBranchAndDelegatesAfterPrecheck(CodeGenTypeEnum type) {
        AppQuotaVO quota = new AppQuotaVO();
        quota.setCanCreate(true);
        when(quotaService.getQuota(7L)).thenReturn(quota);
        AiCodeGenTypeRoutingService routing = mock(AiCodeGenTypeRoutingService.class);
        when(routingFactory.createAiCodeGenTypeRoutingService()).thenReturn(routing);
        when(routing.routeCodeGenType("create a useful page")).thenReturn(type);
        when(creationService.createApp(any())).thenReturn(9L);
        AppAddRequest request = new AppAddRequest();
        request.setInitPrompt("create a useful page");
        assertEquals(9L, service.createApp(request, user));
        var order = inOrder(quotaService, routingFactory, routing, creationService);
        order.verify(quotaService).getQuota(7L);
        order.verify(routingFactory).createAiCodeGenTypeRoutingService();
        order.verify(routing).routeCodeGenType("create a useful page");
        ArgumentCaptor<App> app = ArgumentCaptor.forClass(App.class);
        order.verify(creationService).createApp(app.capture());
        assertEquals(type.getValue(), app.getValue().getCodeGenType());
        assertEquals(7L, app.getValue().getUserId());
        assertEquals("create a use", app.getValue().getAppName());
        verifyNoInteractions(mapper);
        assertEquals(0, transactions.getCommits());
    }

    @Test
    void removalWithoutHistoryIsSuccessfulAndClearsCacheOnlyAfterCommit() {
        prepareRemoval();
        when(historyService.deleteByAppId(9L)).thenReturn(false);
        when(mapper.deleteById(9L)).thenAnswer(invocation -> {
            verify(cache, never()).clear();
            return 1;
        });
        doAnswer(invocation -> {
            assertEquals(1, transactions.getCommits());
            return null;
        }).when(cache).clear();
        assertTrue(service.removeById(9L));
        var order = inOrder(userMapper, historyService, mapper);
        order.verify(mapper).selectOneById(9L);
        order.verify(userMapper).selectActiveUserForUpdate(7L);
        order.verify(historyService).deleteByAppId(9L);
        order.verify(mapper).deleteById(9L);
        verify(cache).clear();
    }

    @Test
    void historyFailureAbortsRemovalAndRollsBackWithoutEviction() {
        prepareRemoval();
        when(historyService.deleteByAppId(9L)).thenThrow(new DataAccessResourceFailureException("private SQL"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.removeById(9L));
        verify(mapper, never()).deleteById(any());
        verify(cache, never()).clear();
        assertEquals(1, transactions.getRollbacks());
    }

    @Test
    void appDeleteFailureRollsBackHistoryAndDoesNotEvict() {
        prepareRemoval();
        when(historyService.deleteByAppId(9L)).thenReturn(true);
        when(mapper.deleteById(9L)).thenThrow(new DataAccessResourceFailureException("private SQL"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.removeById(9L));
        assertEquals(1, transactions.getRollbacks());
        verify(historyService).deleteByAppId(9L);
        verify(cache, never()).clear();
    }

    @Test
    void zeroAppDeleteRowsRollsBackHistoryAndDoesNotEvict() {
        prepareRemoval();
        when(historyService.deleteByAppId(9L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.removeById(9L));
        assertEquals(1, transactions.getRollbacks());
        verify(cache, never()).clear();
    }

    private void prepareRemoval() {
        when(mapper.selectOneById(9L)).thenReturn(App.builder().id(9L).userId(7L).build());
        when(userMapper.selectActiveUserForUpdate(7L)).thenReturn(user);
    }
}
