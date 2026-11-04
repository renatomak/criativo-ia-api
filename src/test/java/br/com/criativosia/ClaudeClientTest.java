package br.com.criativosia;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.criativosia.config.ClaudeConfig;
import br.com.criativosia.config.ClaudeConfig.ClaudeConnection;
import br.com.criativosia.controller.ClaudeController;
import br.com.criativosia.integration.ClaudeClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ClaudeClientTest {
  private HttpServer server;
  private ClaudeConnection connection;
  private final AtomicReference<String> request = new AtomicReference<>();
  private final AtomicReference<String> key = new AtomicReference<>();
  private static final String RESPONSE =
      """
        {"id":"msg_test","type":"message","role":"assistant","model":"claude-haiku-4-5",
         "content":[{"type":"text","text":"Conexão com Claude funcionando."}],
         "stop_reason":"end_turn","stop_sequence":null,"usage":{"input_tokens":12,"output_tokens":8}}
        """;

  private ClaudeClient client(int status, String body, long delayMillis, long timeoutMillis)
      throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/messages",
        exchange -> {
          try {
            request.set(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if (delayMillis > 0) Thread.sleep(delayMillis);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
    connection =
        new ClaudeConnection(
            AnthropicOkHttpClient.builder()
                .apiKey("secret-test-only")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .maxRetries(0)
                .timeout(Duration.ofMillis(timeoutMillis))
                .build(),
            "claude-haiku-4-5");
    return new ClaudeClient(connection);
  }

  @AfterEach
  void close() {
    if (connection != null) connection.close();
    if (server != null) server.stop(0);
  }

  @Test
  void gatewayConfigurationSendsBearerToken() throws Exception {
    client(200, RESPONSE, 0, 3000);
    connection.close();
    connection =
        new ClaudeConfig()
            .claudeConnection(
                "",
                "gateway-test-token",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "claude-haiku-4-5",
                3);
    assertEquals("msg_test", new ClaudeClient(connection).testarConexao().mensagemId());
    assertEquals("Bearer gateway-test-token", key.get());
    assertThrows(
        IllegalArgumentException.class,
        () -> new ClaudeConfig().claudeConnection("old-key", "token", "", "model", 30));
  }

  @Test
  void newEndpointReturnsStatusAndKeepsLegacyRoute() throws Exception {
    var client = client(200, RESPONSE, 0, 3000);
    var mvc = MockMvcBuilders.standaloneSetup(new ClaudeController(client)).build();
    for (String route :
        java.util.List.of("/api/integracoes/claude/testar", "/api/ia/claude/testar")) {
      mvc.perform(post(route))
          .andExpect(status().isOk())
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status")
                  .value("OK"))
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                      "$.resposta")
                  .isNotEmpty());
    }
    assertEquals(
        "disabled",
        new ObjectMapper().readTree(request.get()).path("thinking").path("type").asText());
  }

  @Test
  void productAnalysisSendsThreeOriginalImagesAndDoesNotPersist(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
    var gateway = client(200, RESPONSE, 0, 3000);
    var mapper =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(
                com.fasterxml.jackson.databind.DeserializationFeature
                    .ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    var storage = new br.com.criativosia.storage.ProdutoStorage(root.toString(), mapper);
    var id = java.util.UUID.randomUUID();
    byte[] png = ProdutoApiTest.image("png");
    byte[] jpg = ProdutoApiTest.image("jpg");
    var names = java.util.List.of("foto-01.png", "foto-02.jpg", "foto-03.png");
    var produto =
        new br.com.criativosia.model.Produto(
            id,
            "Kit Lupo",
            "Modelo slip; cinco peças",
            "Moda",
            null,
            null,
            "Cores podem repetir",
            names,
            java.time.OffsetDateTime.now());
    storage.salvar(
        produto,
        java.util.List.of(
            new br.com.criativosia.service.ProdutoService.Foto(png, "png"),
            new br.com.criativosia.service.ProdutoService.Foto(jpg, "jpg"),
            new br.com.criativosia.service.ProdutoService.Foto(png, "png")));
    byte[] originalJson =
        java.nio.file.Files.readAllBytes(root.resolve(id.toString()).resolve("produto.json"));
    var service = new br.com.criativosia.service.AnaliseService(storage, gateway, mapper);
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new br.com.criativosia.controller.AnaliseController(service))
            .build();
    mvc.perform(post("/api/produtos/" + id + "/analisar"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status")
                .value("AGUARDANDO_REVISAO"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.imagensEnviadas.length()")
                .value(3));
    var payload = mapper.readTree(request.get());
    var blocks = payload.path("messages").get(0).path("content");
    assertEquals(7, blocks.size());
    assertTrue(blocks.get(0).path("text").asText().contains("Modelo slip"));
    for (int i = 0; i < 3; i++) {
      assertTrue(blocks.get(1 + i * 2).path("text").asText().contains(names.get(i)));
      var source = blocks.get(2 + i * 2).path("source");
      assertEquals("base64", source.path("type").asText());
      assertEquals(i == 1 ? "image/jpeg" : "image/png", source.path("media_type").asText());
      org.junit.jupiter.api.Assertions.assertArrayEquals(
          i == 1 ? jpg : png, java.util.Base64.getDecoder().decode(source.path("data").asText()));
    }
    assertFalse(java.nio.file.Files.exists(root.resolve(id.toString()).resolve("analises")));
    org.junit.jupiter.api.Assertions.assertArrayEquals(
        originalJson,
        java.nio.file.Files.readAllBytes(root.resolve(id.toString()).resolve("produto.json")));
    request.set(null);
    mvc.perform(post("/api/produtos/" + java.util.UUID.randomUUID() + "/analisar"))
        .andExpect(status().isNotFound());
    assertNull(request.get());
    var photo = root.resolve(id.toString()).resolve("imagens/foto-01.png");
    java.nio.file.Files.writeString(photo, "corrompida");
    mvc.perform(post("/api/produtos/" + id + "/analisar"))
        .andExpect(status().isUnprocessableEntity());
    assertNull(request.get());
    java.nio.file.Files.write(photo, new byte[3 * 1024 * 1024 + 1]);
    mvc.perform(post("/api/produtos/" + id + "/analisar")).andExpect(status().isPayloadTooLarge());
    assertNull(request.get());
  }

  @Test
  void sdkSendsTextAndReadsUsage() throws Exception {
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
    var client =
        client(
            providerStatus,
            "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"secret-test-only\"}}",
            0,
            3000);
    var ex = assertThrows(ResponseStatusException.class, client::testarConexao);
    assertEquals(expectedStatus, ex.getStatusCode().value());
    assertFalse(ex.getReason().contains("secret-test-only"));
    assertNull(ex.getCause());
  }

  @Test
  void timeoutIsControlled() throws Exception {
    var client = client(200, RESPONSE, 600, 100);
    var ex = assertThrows(ResponseStatusException.class, client::testarConexao);
    assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatusCode());
  }

  @Test
  void invalidOrIncompleteResponseIsRejected() throws Exception {
    var client = client(200, RESPONSE.replace("end_turn", "max_tokens"), 0, 3000);
    assertEquals(
        HttpStatus.BAD_GATEWAY,
        assertThrows(ResponseStatusException.class, client::testarConexao).getStatusCode());
  }

  @Test
  void missingKeyDoesNotPreventApplicationUseAndEndpointReturns503() throws Exception {
    var configuration = new ClaudeConfig();
    try (var missing = configuration.claudeConnection("", "", "", "claude-haiku-4-5", 30)) {
      var mvc =
          MockMvcBuilders.standaloneSetup(new ClaudeController(new ClaudeClient(missing))).build();
      mvc.perform(post("/api/ia/claude/testar")).andExpect(status().isServiceUnavailable());
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> configuration.claudeConnection("", "", "", "model", 0));
  }
}
