package com.huzi.huaicodemother.config;

import com.huzi.huaicodemother.ai.AiCodeGenTypeRoutingService;
import com.huzi.huaicodemother.model.enums.CodeGenTypeEnum;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingAiModelConfigTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void requestsIdentityEncodingAndParsesCompleteResponse(boolean chunked) throws Exception {
        AtomicReference<String> acceptEncoding = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            String encoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
            acceptEncoding.set(encoding);
            exchange.getRequestBody().readAllBytes();
            String body = "identity".equals(encoding) ? """
                    {
                      "id": "routing-test",
                      "object": "chat.completion",
                      "model": "routing-test-model",
                      "choices": [{
                        "index": 0,
                        "message": {"role": "assistant", "content": "HTML"},
                        "finish_reason": "stop"
                      }]
                    }
                    """ : "{";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, chunked ? 0 : bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes, 0, 1);
                output.flush();
                output.write(bytes, 1, bytes.length - 1);
            }
        });
        server.start();
        try {
            RoutingAiModelConfig config = new RoutingAiModelConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            config.setApiKey("test-key");
            config.setModelName("routing-test-model");
            config.setMaxTokens(300);
            AiCodeGenTypeRoutingService service = AiServices.builder(AiCodeGenTypeRoutingService.class)
                    .chatModel(config.routingChatModelPrototype())
                    .build();

            assertEquals(CodeGenTypeEnum.HTML, service.routeCodeGenType("做一个简单的个人备忘录页面"));
            assertEquals("identity", acceptEncoding.get());
        } finally {
            server.stop(0);
        }
    }
}
