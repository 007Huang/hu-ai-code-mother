package com.huzi.huaicodemother.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huzi.huaicodemother.aop.AuthInterceptor;
import com.huzi.huaicodemother.config.AllowedOrigins;
import com.huzi.huaicodemother.config.PersonalCenterOriginInterceptor;
import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import com.huzi.huaicodemother.exception.GlobalExceptionHandler;
import com.huzi.huaicodemother.model.dto.user.UserQuotaUpdateRequest;
import com.huzi.huaicodemother.model.dto.user.UserUpdateMyRequest;
import com.huzi.huaicodemother.model.entity.User;
import com.huzi.huaicodemother.model.vo.AppQuotaVO;
import com.huzi.huaicodemother.model.vo.LoginUserVO;
import com.huzi.huaicodemother.service.AppQuotaService;
import com.huzi.huaicodemother.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** standalone MVC + 实际 ADMIN 切面；不启动根应用、自动配置或外部服务。 */
class PersonalCenterControllerTest {

    private MockMvc mvc;
    private UserService userService;
    private AppQuotaService quotaService;
    private User sessionUser;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        quotaService = mock(AppQuotaService.class);
        sessionUser = User.builder().id(7L).userRole("user").build();
        when(userService.getLoginUser(any())).thenAnswer(invocation -> sessionUser);
        UserController userController = new UserController();
        ReflectionTestUtils.setField(userController, "userService", userService);
        ReflectionTestUtils.setField(userController, "appQuotaService", quotaService);
        AppController appController = new AppController();
        ReflectionTestUtils.setField(appController, "userService", userService);
        ReflectionTestUtils.setField(appController, "appQuotaService", quotaService);
        AuthInterceptor auth = new AuthInterceptor();
        ReflectionTestUtils.setField(auth, "userService", userService);
        AspectJProxyFactory factory = new AspectJProxyFactory(userController);
        factory.setProxyTargetClass(true);
        factory.addAspect(auth);
        json = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mvc = MockMvcBuilders.standaloneSetup(factory.getProxy(), appController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .addInterceptors(new PersonalCenterOriginInterceptor(new AllowedOrigins(AllowedOrigins.LOCAL_DEFAULTS)))
                .build();
    }

    @Test
    void nonAdminCannotGetAnotherUsersQuotaThroughAuthProxy() throws Exception {
        mvc.perform(get("/user/quota/get").param("id", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
        verifyNoInteractions(quotaService);
    }

    @Test
    void nonAdminCannotUpdateAnotherUsersQuotaThroughAuthProxy() throws Exception {
        mvc.perform(post("/user/quota/update").header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"99\",\"appLimit\":0}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
        verifyNoInteractions(quotaService);
    }

    @Test
    void unauthenticatedQuotaRequestRejectedThroughAuthProxy() throws Exception {
        when(userService.getLoginUser(any())).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
        mvc.perform(get("/user/quota/get").param("id", "99"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN_ERROR.getCode()));
        verifyNoInteractions(quotaService);
    }

    @Test
    void adminCanGetAndSetQuotaIncludingExplicitReset() throws Exception {
        sessionUser.setUserRole("admin");
        AppQuotaVO quota = quota();
        when(quotaService.getQuota(99L)).thenReturn(quota);
        when(quotaService.updateQuota(99L, null)).thenReturn(quota);
        mvc.perform(get("/user/quota/get").param("id", "99"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.usedCount").value(2))
                .andExpect(jsonPath("$.data.usedCount").isNumber());
        mvc.perform(post("/user/quota/update").header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"99\",\"appLimit\":null}"))
                .andExpect(jsonPath("$.code").value(0));
        verify(quotaService).updateQuota(99L, null);
    }

    @Test
    void missingOrMisspelledLimitCannotSilentlyResetQuotaOrForgePresenceFlag() throws Exception {
        sessionUser.setUserRole("admin");
        for (String body : new String[]{"{\"id\":\"99\"}", "{\"id\":\"99\",\"appLmit\":4}",
                "{\"id\":\"99\",\"appLimitPresent\":true}"}) {
            mvc.perform(post("/user/quota/update").header("Origin", "http://localhost:5173")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAMS_ERROR.getCode()));
        }
        verifyNoInteractions(quotaService);
    }

    @Test
    void jsonSetterTracksMissingVersusExplicitNullAndFlagIsNotSerialized() throws Exception {
        UserQuotaUpdateRequest absent = json.readValue("{\"id\":\"7\"}", UserQuotaUpdateRequest.class);
        UserQuotaUpdateRequest reset = json.readValue("{\"id\":\"7\",\"appLimit\":null}", UserQuotaUpdateRequest.class);
        assertFalse(absent.isAppLimitPresent());
        assertTrue(reset.isAppLimitPresent());
        assertNull(reset.getAppLimit());
        assertFalse(json.valueToTree(reset).has("appLimitPresent"));
    }

    @Test
    void myQuotaAlwaysUsesCurrentUserId() throws Exception {
        when(quotaService.getQuota(7L)).thenReturn(quota());
        mvc.perform(get("/app/my/quota").param("userId", "99"))
                .andExpect(jsonPath("$.code").value(0));
        verify(quotaService).getQuota(7L);
        verify(quotaService, never()).getQuota(99L);
    }

    @Test
    void profileUpdateTargetsSessionUserAndOnlyAcceptsWhitelistedDto() throws Exception {
        LoginUserVO result = new LoginUserVO();
        result.setUserName("新昵称");
        when(userService.updateMyUser(any(), eq(sessionUser))).thenReturn(result);
        mvc.perform(post("/api/user/update/my").contextPath("/api").header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"99\",\"userRole\":\"admin\",\"appLimit\":10000,\"userName\":\"新昵称\"}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.userName").value("新昵称"))
                .andExpect(jsonPath("$.data.appLimit").doesNotExist());
        verify(userService).updateMyUser(argThat(dto -> "新昵称".equals(dto.getUserName())), eq(sessionUser));
    }

    @Test
    void missingSourceHeaderProducesCommonJsonNoAuthBeforeMutation() throws Exception {
        mvc.perform(post("/user/update/my").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
        verify(userService, never()).updateMyUser(any(UserUpdateMyRequest.class), any());
    }

    @Test
    void evilOriginOverridesValidRefererInActualMvcChain() throws Exception {
        sessionUser.setUserRole("admin");
        mvc.perform(post("/user/quota/update").header("Origin", "https://evil.test")
                        .header("Referer", "http://localhost:5173/personal-center")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":7,\"appLimit\":1}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
        verifyNoInteractions(quotaService);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"0.9", "-0.9", "3.5", "10.0", "1e2", "\"10\"", "true", "2147483648"})
    void nonIntegerQuotaIsRejectedBeforeMutation(String value) throws Exception {
        sessionUser.setUserRole("admin");
        mvc.perform(post("/user/quota/update").header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"7\",\"appLimit\":" + value + "}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAMS_ERROR.getCode()));
        verifyNoInteractions(quotaService);
    }

    @Test
    void matrixParametersDoNotBypassProfileSourceCheck() throws Exception {
        mvc.perform(post(java.net.URI.create("/user/update/my;view=%2Fignored"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
        verify(userService, never()).updateMyUser(any(UserUpdateMyRequest.class), any());
    }

    private AppQuotaVO quota() {
        AppQuotaVO quota = new AppQuotaVO();
        quota.setUsedCount(2);
        quota.setLimitCount(10);
        quota.setRemainingCount(8);
        quota.setDefaultLimit(10);
        quota.setUsesDefault(true);
        quota.setOverLimit(false);
        quota.setCanCreate(true);
        return quota;
    }
}
