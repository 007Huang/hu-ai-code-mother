package com.huzi.huaicodemother.controller;

import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import com.huzi.huaicodemother.model.entity.User;
import com.huzi.huaicodemother.model.sse.AppStreamEvent;
import com.huzi.huaicodemother.service.AppService;
import com.huzi.huaicodemother.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppControllerTest {

    private AppController controller;
    private AppService appService;
    private UserService userService;
    private HttpServletRequest request;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        controller = new AppController();
        appService = mock(AppService.class);
        userService = mock(UserService.class);
        request = mock(HttpServletRequest.class);
        user = new User();
        user.setId(1L);
        setField(controller, "appService", appService);
        setField(controller, "userService", userService);
        when(userService.getLoginUser(request)).thenReturn(user);
    }

    @Test
    void emitsContentPhaseAndDoneOnSuccess() {
        when(appService.chatToGenCode(1L, "message", user)).thenReturn(Flux.just(
                AppStreamEvent.content("hello"),
                AppStreamEvent.phase("building", "代码已生成，正在构建预览...")
        ));

        List<ServerSentEvent<String>> events = controller
                .chatToGenCode(1L, "message", request)
                .collectList()
                .block();

        assertEquals(3, events.size());
        assertNull(events.get(0).event());
        assertEquals("phase", events.get(1).event());
        assertEquals("done", events.get(2).event());
    }

    @Test
    void emitsBusinessErrorWithoutDoneOnFailure() {
        when(appService.chatToGenCode(1L, "message", user)).thenReturn(Flux.concat(
                Flux.just(AppStreamEvent.content("partial")),
                Flux.error(new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败"))
        ));

        List<ServerSentEvent<String>> events = controller
                .chatToGenCode(1L, "message", request)
                .collectList()
                .block();

        assertEquals(2, events.size());
        assertNull(events.get(0).event());
        assertEquals("business-error", events.get(1).event());
        assertFalse(events.stream().anyMatch(event -> "done".equals(event.event())));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
