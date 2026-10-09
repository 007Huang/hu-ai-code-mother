package com.huzi.huaicodemother.service.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.mapper.UserMapper;
import com.huzi.huaicodemother.model.dto.user.UserUpdateMyRequest;
import com.huzi.huaicodemother.model.entity.User;
import com.huzi.huaicodemother.model.vo.LoginUserVO;
import com.huzi.huaicodemother.service.FeaturedAppCacheInvalidator;
import com.huzi.huaicodemother.support.TestTransactions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserProfileServiceTest {

    private UserMapper mapper;
    private UserServiceImpl service;
    private User currentUser;
    private Cache cache;
    private TestTransactions.RecordingManager transactions;

    @BeforeEach
    void setUp() {
        mapper = mock(UserMapper.class);
        CacheManager cacheManager = mock(CacheManager.class);
        cache = mock(Cache.class);
        when(cacheManager.getCache("good_app_page")).thenReturn(cache);
        transactions = new TestTransactions.RecordingManager();
        UserServiceImpl target = new UserServiceImpl();
        ReflectionTestUtils.setField(target, "mapper", mapper);
        ReflectionTestUtils.setField(target, "featuredAppCacheInvalidator", new FeaturedAppCacheInvalidator(cacheManager));
        service = TestTransactions.proxy(target, transactions);
        currentUser = User.builder().id(7L).userName("旧昵称").userAvatar("https://example.com/old.png")
                .userProfile("旧简介").userRole("user").userPassword("unchanged").appLimit(15).build();
        when(mapper.selectActiveUserForUpdate(7L)).thenReturn(currentUser);
        when(mapper.selectActiveUser(7L)).thenReturn(currentUser);
    }

    @Test
    void trimsNicknameAndUpdatesOnlyWhitelistedFieldsAndEvictsAfterCommit() {
        UserUpdateMyRequest request = new UserUpdateMyRequest();
        request.setUserName("  新昵称  ");
        request.setUserProfile("新的简介");
        request.setUserAvatar("https://example.com/avatar.png");
        when(mapper.update(any(User.class), eq(true))).thenAnswer(invocation -> {
            User update = invocation.getArgument(0);
            verify(cache, never()).clear();
            currentUser.setUserName(update.getUserName());
            currentUser.setUserProfile(update.getUserProfile());
            currentUser.setUserAvatar(update.getUserAvatar());
            return 1;
        });
        doAnswer(invocation -> {
            assertEquals(1, transactions.getCommits());
            return null;
        }).when(cache).clear();

        LoginUserVO result = service.updateMyUser(request, currentUser);
        assertEquals("新昵称", result.getUserName());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(mapper).update(captor.capture(), eq(true));
        User update = captor.getValue();
        assertEquals(7L, update.getId());
        assertNotNull(update.getEditTime());
        assertNull(update.getUserRole());
        assertNull(update.getUserPassword());
        assertNull(update.getUserAccount());
        assertNull(update.getAppLimit());
        assertNull(update.getIsDelete());
        verify(cache).clear();
    }

    @Test
    void nullAndMissingFieldsStayNullInSelectiveUpdate() {
        when(mapper.update(any(User.class), eq(true))).thenReturn(1);
        LoginUserVO result = service.updateMyUser(new UserUpdateMyRequest(), currentUser);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(mapper).update(captor.capture(), eq(true));
        assertNull(captor.getValue().getUserName());
        assertNull(captor.getValue().getUserProfile());
        assertNull(captor.getValue().getUserAvatar());
        assertEquals("旧昵称", result.getUserName());
        assertEquals("旧简介", result.getUserProfile());
    }

    @Test
    void emptyAvatarAndBioExplicitlyClearWhileNullNicknameStaysUntouched() {
        UserUpdateMyRequest request = new UserUpdateMyRequest();
        request.setUserAvatar("");
        request.setUserProfile("");
        when(mapper.update(any(User.class), eq(true))).thenReturn(1);
        service.updateMyUser(request, currentUser);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(mapper).update(captor.capture(), eq(true));
        assertEquals("", captor.getValue().getUserAvatar());
        assertEquals("", captor.getValue().getUserProfile());
        assertNull(captor.getValue().getUserName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a", "123456789012345678901"})
    void rejectsInvalidNicknameBeforeDatabase(String nickname) {
        UserUpdateMyRequest request = new UserUpdateMyRequest();
        request.setUserName(nickname);
        assertThrows(BusinessException.class, () -> service.updateMyUser(request, currentUser));
        verify(mapper, never()).update(any(), anyBoolean());
        verify(mapper, never()).selectActiveUserForUpdate(any());
        verify(cache, never()).clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/a.png", "javascript:alert(1)", "https:///avatar.png",
            "https://user:pass@example.com/a.png", "https://example.com:70000/a.png", "https://bad host/a.png"})
    void rejectsUnsafeOrMalformedAvatarBeforeDatabase(String avatar) {
        UserUpdateMyRequest request = new UserUpdateMyRequest();
        request.setUserAvatar(avatar);
        assertThrows(BusinessException.class, () -> service.updateMyUser(request, currentUser));
        verify(mapper, never()).update(any(), anyBoolean());
        verify(cache, never()).clear();
    }

    @Test
    void rejectsOversizeBioAndAvatar() {
        UserUpdateMyRequest bio = new UserUpdateMyRequest();
        bio.setUserProfile("x".repeat(201));
        assertThrows(BusinessException.class, () -> service.updateMyUser(bio, currentUser));
        UserUpdateMyRequest avatar = new UserUpdateMyRequest();
        avatar.setUserAvatar("https://example.com/" + "a".repeat(1024));
        assertThrows(BusinessException.class, () -> service.updateMyUser(avatar, currentUser));
        verify(mapper, never()).update(any(), anyBoolean());
    }

    @Test
    void maxLengthFieldsAreAccepted() {
        UserUpdateMyRequest request = new UserUpdateMyRequest();
        request.setUserName("x".repeat(20));
        request.setUserProfile("x".repeat(200));
        request.setUserAvatar("https://example.com/" + "x".repeat(1004));
        when(mapper.update(any(User.class), eq(true))).thenReturn(1);
        assertDoesNotThrow(() -> service.updateMyUser(request, currentUser));
    }

    @Test
    void cannotOverpostIdRolePasswordOrQuota() throws Exception {
        ObjectMapper json = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        UserUpdateMyRequest request = json.readValue("""
                {"id":"99","userRole":"admin","userPassword":"pwned","appLimit":10000,"userName":"新昵称"}
                """, UserUpdateMyRequest.class);
        when(mapper.update(any(User.class), eq(true))).thenReturn(1);
        service.updateMyUser(request, currentUser);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(mapper).update(captor.capture(), eq(true));
        assertEquals(7L, captor.getValue().getId());
        assertNull(captor.getValue().getUserRole());
        assertNull(captor.getValue().getUserPassword());
        assertNull(captor.getValue().getAppLimit());
        assertFalse(json.valueToTree(service.getLoginUserVO(currentUser)).has("appLimit"));
        assertFalse(json.valueToTree(service.getUserVO(currentUser)).has("appLimit"));
    }

    @Test
    void databaseFailureRollsBackAndDoesNotEvict() {
        when(mapper.update(any(User.class), eq(true))).thenThrow(new DataAccessResourceFailureException("private SQL"));
        assertThrows(DataAccessResourceFailureException.class,
                () -> service.updateMyUser(new UserUpdateMyRequest(), currentUser));
        assertEquals(1, transactions.getRollbacks());
        verify(cache, never()).clear();
    }

    @Test
    void zeroUpdatedRowsRollsBackAndDoesNotEvict() {
        assertThrows(BusinessException.class,
                () -> service.updateMyUser(new UserUpdateMyRequest(), currentUser));
        assertEquals(1, transactions.getRollbacks());
        verify(cache, never()).clear();
    }

    @Test
    void cacheFailureAfterCommitDoesNotFailSuccessfulProfileUpdate() {
        when(mapper.update(any(User.class), eq(true))).thenReturn(1);
        doThrow(new IllegalStateException("cache unavailable")).when(cache).clear();
        assertDoesNotThrow(() -> service.updateMyUser(new UserUpdateMyRequest(), currentUser));
        assertEquals(1, transactions.getCommits());
        assertEquals(0, transactions.getRollbacks());
    }
}
