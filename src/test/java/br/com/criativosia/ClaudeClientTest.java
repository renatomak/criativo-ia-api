package br.com.criativosia;

import br.com.criativosia.config.ClaudeConfig;
import br.com.criativosia.config.ClaudeConfig.ClaudeConnection;
import br.com.criativosia.controller.ClaudeController;
import br.com.criativosia.integration.ClaudeClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClaudeClientTest {
    private HttpServer server;
    private ClaudeConnection connection;
    private final AtomicReference<String> request = new AtomicReference<>();
    private final AtomicReference<String> key = new AtomicReference<>();
    private static final String RESPONSE = """
        {"id":"msg_test","type":"message","role":"assistant","model":"claude-haiku-4-5",
         "content":[{"type":"text","text":"Conexão com Claude funcionando."}],
         "stop_reason":"end_turn","stop_sequence":null,"usage":{"input_tokens":12,"output_tokens":8}}
        """;

    private ClaudeClient client(int status, String body, long delayMillis, long timeoutMillis) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            try {
                request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                key.set(exchange.getRequestHeaders().getFirst("Authorization"));
                if (delayMillis > 0) Thread.sleep(delayMillis);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        connection = new ClaudeConnection(AnthropicOkHttpClient.builder().apiKey("secret-test-only")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .maxRetries(0).timeout(Duration.ofMillis(timeoutMillis)).build(), "claude-haiku-4-5");
        return new ClaudeClient(connection);
    }

    @AfterEach void close() {
        if (connection != null) connection.close();
        if (server != null) server.stop(0);
    }

    @Test void sdkSendsTextAndReadsUsage() throws Exception {
        var response = client(200, RESPONSE, 0, 3000).testarConexao();
        assertEquals("msg_test", response.mensagemId());
        assertEquals("Conexão com Claude funcionando.", response.resposta());
        assertEquals(12, response.tokensEntrada());
        assertEquals(8, response.tokensSaida());
        assertNotNull(response.dataTeste());
        var json = new ObjectMapper().readTree(request.get());
        assertEquals("claude-haiku-4-5", json.get("model").asText());
        assertEquals(64, json.get("max_tokens").asInt());
        assertTrue(json.get("messages").toString().contains("Conexão com Claude funcionando."));
        assertEquals("Bearer secret-test-only", key.get());
        assertFalse(response.toString().contains("secret-test-only"));
    }

    @ParameterizedTest
    @CsvSource({"400,502", "401,502", "403,502", "404,502", "429,503", "500,502", "529,502"})
    void providerErrorsAreSanitized(int providerStatus, int expectedStatus) throws Exception {
        var client = client(providerStatus,
                "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"secret-test-only\"}}", 0, 3000);
        var ex = assertThrows(ResponseStatusException.class, client::testarConexao);
        assertEquals(expectedStatus, ex.getStatusCode().value());
        assertFalse(ex.getReason().contains("secret-test-only"));
        assertNull(ex.getCause());
    }

    @Test void timeoutIsControlled() throws Exception {
        var client = client(200, RESPONSE, 600, 100);
        var ex = assertThrows(ResponseStatusException.class, client::testarConexao);
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatusCode());
    }

    @Test void invalidOrIncompleteResponseIsRejected() throws Exception {
        var client = client(200, RESPONSE.replace("end_turn", "max_tokens"), 0, 3000);
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(ResponseStatusException.class, client::testarConexao).getStatusCode());
    }

    @Test void missingKeyDoesNotPreventApplicationUseAndEndpointReturns503() throws Exception {
        var configuration = new ClaudeConfig();
        try (var missing = configuration.claudeConnection("", "claude-haiku-4-5", 30)) {
            var mvc = MockMvcBuilders.standaloneSetup(new ClaudeController(new ClaudeClient(missing))).build();
            mvc.perform(post("/api/ia/claude/testar")).andExpect(status().isServiceUnavailable());
        }
        assertThrows(IllegalArgumentException.class,
                () -> configuration.claudeConnection("", "model", 0));
    }
}
