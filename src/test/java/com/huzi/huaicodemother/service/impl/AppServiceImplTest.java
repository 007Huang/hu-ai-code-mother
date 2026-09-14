package com.huzi.huaicodemother.service.impl;

import com.huzi.huaicodemother.core.AiCodeGeneratorFacade;
import com.huzi.huaicodemother.core.builder.VueProjectBuilder;
import com.huzi.huaicodemother.core.handler.StreamHandlerExecutor;
import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.model.entity.App;
import com.huzi.huaicodemother.model.entity.User;
import com.huzi.huaicodemother.model.enums.CodeGenTypeEnum;
import com.huzi.huaicodemother.model.sse.AppStreamEvent;
import com.huzi.huaicodemother.service.ChatHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Signal;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppServiceImplTest {

    private AppServiceImpl appService;
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    private ChatHistoryService chatHistoryService;
    private StreamHandlerExecutor streamHandlerExecutor;
    private VueProjectBuilder vueProjectBuilder;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        appService = spy(new AppServiceImpl());
        aiCodeGeneratorFacade = mock(AiCodeGeneratorFacade.class);
        chatHistoryService = mock(ChatHistoryService.class);
        streamHandlerExecutor = mock(StreamHandlerExecutor.class);
        vueProjectBuilder = mock(VueProjectBuilder.class);
        user = new User();
        user.setId(2L);

        setField(appService, "aiCodeGeneratorFacade", aiCodeGeneratorFacade);
        setField(appService, "chatHistoryService", chatHistoryService);
        setField(appService, "streamHandlerExecutor", streamHandlerExecutor);
        setField(appService, "vueProjectBuilder", vueProjectBuilder);
        setField(appService, "vueProjectBuildTaskExecutor", (TaskExecutor) Runnable::run);
    }

    @Test
    void appendsBuildPhaseAndCompletesAfterSuccessfulVueBuild() {
        prepareApp(CodeGenTypeEnum.VUE_PROJECT);
        when(vueProjectBuilder.buildProject(any())).thenReturn(true);

        List<Signal<AppStreamEvent>> signals = appService
                .chatToGenCode(1L, "message", user)
                .materialize()
                .collectList()
                .block();

        assertEquals(3, signals.size());
        assertEquals(AppStreamEvent.Type.CONTENT, signals.get(0).get().type());
        assertEquals("processed", signals.get(0).get().data());
        assertEquals(AppStreamEvent.Type.PHASE, signals.get(1).get().type());
        assertEquals("building", signals.get(1).get().phase());
        assertTrue(signals.get(2).isOnComplete());
        verify(vueProjectBuilder).buildProject(any());
    }

    @Test
    void emitsErrorAfterBuildPhaseWhenVueBuildFails() {
        prepareApp(CodeGenTypeEnum.VUE_PROJECT);
        when(vueProjectBuilder.buildProject(any())).thenReturn(false);

        List<Signal<AppStreamEvent>> signals = appService
                .chatToGenCode(1L, "message", user)
                .materialize()
                .collectList()
                .block();

        assertEquals(3, signals.size());
        assertEquals(AppStreamEvent.Type.CONTENT, signals.get(0).get().type());
        assertEquals(AppStreamEvent.Type.PHASE, signals.get(1).get().type());
        assertInstanceOf(BusinessException.class, signals.get(2).getThrowable());
        assertEquals("Vue 项目构建失败，请检查生成代码和依赖",
                signals.get(2).getThrowable().getMessage());
    }

    @Test
    void reportsBusyWhenBuildExecutorRejectsTask() throws Exception {
        prepareApp(CodeGenTypeEnum.VUE_PROJECT);
        setField(appService, "vueProjectBuildTaskExecutor", (TaskExecutor) task -> {
            throw new IllegalStateException("rejected");
        });

        List<Signal<AppStreamEvent>> signals = appService
                .chatToGenCode(1L, "message", user)
                .materialize()
                .collectList()
                .block();

        assertEquals(3, signals.size());
        assertEquals(AppStreamEvent.Type.PHASE, signals.get(1).get().type());
        assertInstanceOf(BusinessException.class, signals.get(2).getThrowable());
        assertEquals("当前构建任务较多，请稍后重试", signals.get(2).getThrowable().getMessage());
        verify(vueProjectBuilder, never()).buildProject(any());
    }

    @Test
    void cancelledSubscriptionSkipsQueuedBuild() throws Exception {
        prepareApp(CodeGenTypeEnum.VUE_PROJECT);
        AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        setField(appService, "vueProjectBuildTaskExecutor", (TaskExecutor) queuedTask::set);
        List<AppStreamEvent> events = new ArrayList<>();

        Disposable subscription = appService
                .chatToGenCode(1L, "message", user)
                .subscribe(events::add);
        subscription.dispose();
        queuedTask.get().run();

        assertEquals(2, events.size());
        assertEquals(AppStreamEvent.Type.PHASE, events.get(1).type());
        verify(vueProjectBuilder, never()).buildProject(any());
    }

    @Test
    void nonVueGenerationDoesNotRunBuild() {
        prepareApp(CodeGenTypeEnum.HTML);

        List<AppStreamEvent> events = appService
                .chatToGenCode(1L, "message", user)
                .collectList()
                .block();

        assertEquals(1, events.size());
        assertEquals(AppStreamEvent.Type.CONTENT, events.get(0).type());
        verify(vueProjectBuilder, never()).buildProject(any());
    }

    private void prepareApp(CodeGenTypeEnum codeGenType) {
        App app = new App();
        app.setId(1L);
        app.setUserId(user.getId());
        app.setCodeGenType(codeGenType.getValue());
        doReturn(app).when(appService).getById(1L);
        when(aiCodeGeneratorFacade.generateAndSaveCodeStream("message", codeGenType, 1L))
                .thenReturn(Flux.just("raw"));
        when(streamHandlerExecutor.doExecute(
                any(), eq(chatHistoryService), eq(1L), eq(user), eq(codeGenType)))
                .thenReturn(Flux.just("processed"));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
