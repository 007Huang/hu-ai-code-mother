package com.huzi.huaicodemother.config;

import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class PersonalCenterOriginInterceptorTest {

    private PersonalCenterOriginInterceptor interceptor;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        interceptor = new PersonalCenterOriginInterceptor(new AllowedOrigins(AllowedOrigins.LOCAL_DEFAULTS));
        request = new MockHttpServletRequest("POST", "/api/user/update/my");
        request.setContextPath("/api");
        request.setScheme("https");
        request.setServerName("app.example.com");
        request.setServerPort(443);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:4173", "http://127.0.0.1:4173"})
    void explicitlyConfiguredLocalOriginPasses(String origin) {
        request.addHeader("Origin", origin);
        assertTrue(check());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://app.example.com", "https://APP.EXAMPLE.COM:443"})
    void actualSameOriginPassesWithNormalizedHostAndDefaultPort(String origin) {
        request.addHeader("Origin", origin);
        assertTrue(check());
    }

    @Test
    void sameOriginWithNonDefaultPortUsesActualPort() {
        request.setServerPort(8443);
        request.addHeader("Origin", "https://app.example.com:8443");
        assertTrue(check());
    }

    @Test
    void validRefererPassesOnlyWhenOriginAbsent() {
        request.addHeader("Referer", "https://app.example.com/personal-center?tab=profile");
        assertTrue(check());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://app.example.com.evil.test", "https://app.example.com@evil.test",
            "http://localhost:5173.evil.test", "https://app.example.com:8443", "http://app.example.com",
            "null", "", "https://app.example.com/path", "https://app.example.com?x=1",
            "https://app.example.com https://evil.test", "https://user@app.example.com"})
    void rejectsInvalidOriginsAndMaliciousHostSuffixesEvenWithGoodReferer(String origin) {
        request.addHeader("Origin", origin);
        request.addHeader("Referer", "https://app.example.com/personal-center");
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), assertThrows(BusinessException.class, this::check).getCode());
    }

    @Test
    void missingBothHeadersRejected() {
        assertThrows(BusinessException.class, this::check);
    }

    @Test
    void forgedForwardedHeadersDoNotDefineSameOrigin() {
        request.addHeader("Origin", "https://evil.test");
        request.addHeader("X-Forwarded-Host", "evil.test");
        request.addHeader("X-Forwarded-Proto", "https");
        assertThrows(BusinessException.class, this::check);
    }

    @Test
    void optionsAlwaysPasses() {
        request.setMethod("OPTIONS");
        assertTrue(check());
    }

    @Test
    void unrelatedExistingEndpointsRemainUnchanged() {
        request.setRequestURI("/api/user/login");
        assertTrue(check());
    }

    @Test
    void quotaUpdateIsProtectedWithoutContextPathAsWell() {
        request.setContextPath("");
        request.setRequestURI("/user/quota/update");
        assertThrows(BusinessException.class, this::check);
        request.addHeader("Origin", "https://app.example.com");
        assertTrue(check());
    }

    @Test
    void wildcardAndPathConfigurationRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new AllowedOrigins("*"));
        assertThrows(IllegalArgumentException.class, () -> new AllowedOrigins("https://example.com/path"));
        assertThrows(IllegalArgumentException.class, () -> new AllowedOrigins("null"));
    }

    @Test
    void productionConfiguredOriginIsNormalizedAndAcceptedExactly() {
        interceptor = new PersonalCenterOriginInterceptor(new AllowedOrigins(" https://FRONT.EXAMPLE.COM:443 "));
        request.addHeader("Origin", "https://front.example.com");
        assertTrue(check());
    }

    private boolean check() {
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }
}
