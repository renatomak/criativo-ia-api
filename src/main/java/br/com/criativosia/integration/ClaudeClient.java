package br.com.criativosia.integration;

import br.com.criativosia.config.ClaudeConfig.ClaudeConnection;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import java.io.InterruptedIOException;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ClaudeClient {
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ClaudeClient.class);
  private final ClaudeConnection connection;

  public ClaudeClient(ClaudeConnection connection) {
    this.connection = connection;
  }

  public TesteResponse testarConexao() {
    return enviar(
        MessageCreateParams.builder()
            .model(connection.model())
            .maxTokens(64)
            .thinking(com.anthropic.models.messages.ThinkingConfigDisabled.builder().build())
            .addUserMessage("Responda apenas: Conexão com Claude funcionando.")
            .build(),
        "testeConexao");
  }

  public TesteResponse analisar(
      java.util.List<com.anthropic.models.messages.ContentBlockParam> content, String prompt) {
    return enviar(
        MessageCreateParams.builder()
            .model(connection.model())
            .maxTokens(2048)
            .thinking(com.anthropic.models.messages.ThinkingConfigDisabled.builder().build())
            .system(prompt)
            .addUserMessageOfContentBlockParams(content)
            .build(),
        "analiseProduto");
  }

  private TesteResponse enviar(MessageCreateParams params, String operacao) {
    long start = System.nanoTime();
    String id = org.slf4j.MDC.get("requestId");
    if (id == null) id = java.util.UUID.randomUUID().toString();
    LOG.info(
        "CLAUDE ENVIO | id={} | operacao={} | modelo={}",
        id,
        operacao,
        connection.model().replaceAll("[^a-zA-Z0-9._:-]", "_"));
    try {
      TesteResponse result = executar(params);
      LOG.info(
          "CLAUDE RETORNO | id={} | resultado=SUCESSO | duracaoMs={} | tokensEntrada={} | tokensSaida={}",
          id,
          java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
          result.tokensEntrada(),
          result.tokensSaida());
      return result;
    } catch (RuntimeException ex) {
      int status =
          ex instanceof ResponseStatusException response ? response.getStatusCode().value() : 500;
      LOG.warn(
          "CLAUDE RETORNO | id={} | resultado=ERRO | statusLocal={} | duracaoMs={}",
          id,
          status,
          java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
      throw ex;
    }
  }

  private TesteResponse executar(MessageCreateParams params) {
    var client =
        connection
            .client()
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Configure ANTHROPIC_API_KEY ou ANTHROPIC_AUTH_TOKEN no ambiente do servidor e reinicie a aplicação."));
    try {
      var message = client.messages().create(params);
      String text =
          message.content().stream()
              .flatMap(block -> block.text().stream())
              .map(block -> block.text())
              .collect(Collectors.joining("\n"));
      if (text.isBlank()
          || message.stopReason().map(reason -> !reason.toString().equals("end_turn")).orElse(true))
        throw error(HttpStatus.BAD_GATEWAY, "Claude retornou resposta vazia ou incompleta.");
      return new TesteResponse(
          "OK",
          message.id(),
          message.model().toString(),
          text,
          message.usage().inputTokens(),
          message.usage().outputTokens(),
          OffsetDateTime.now());
    } catch (AnthropicServiceException ex) {
      LOG.warn(
          "CLAUDE PROVEDOR | id={} | statusProvedor={}",
          org.slf4j.MDC.get("requestId"),
          ex.statusCode());
      // Não propagar corpo, headers ou mensagem do provedor, que podem conter dados sensíveis.
      switch (ex.statusCode()) {
        case 401 ->
            throw error(
                HttpStatus.BAD_GATEWAY,
                "O gateway retornou HTTP 401: credencial recusada. Confira o token e ANTHROPIC_BASE_URL; verifique se o token foi revogado ou expirou.");
        case 403 ->
            throw error(
                HttpStatus.BAD_GATEWAY,
                "O gateway retornou HTTP 403: acesso negado. Verifique no provedor as permissões do token, grupo e modelo configurado em claude.model.");
        case 400, 402 ->
            throw error(
                HttpStatus.BAD_GATEWAY,
                "Anthropic recusou a solicitação. Verifique créditos, modelo e configuração da conta.");
        case 404 ->
            throw error(
                HttpStatus.BAD_GATEWAY,
                "O provedor retornou HTTP 404. Verifique ANTHROPIC_BASE_URL e se o modelo em claude.model está disponível na sua conta.");
        case 429 ->
            throw error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Limite da Anthropic atingido. Tente novamente mais tarde.");
        case 408, 504 ->
            throw error(HttpStatus.GATEWAY_TIMEOUT, "Tempo limite na comunicação com Claude.");
        default ->
            throw error(
                HttpStatus.BAD_GATEWAY, "Falha na API da Anthropic. Tente novamente mais tarde.");
      }
    } catch (AnthropicIoException ex) {
      for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
        if (cause instanceof InterruptedIOException)
          throw error(HttpStatus.GATEWAY_TIMEOUT, "Tempo limite na comunicação com Claude.");
      }
      throw error(
          HttpStatus.BAD_GATEWAY,
          "Não foi possível conectar à Anthropic. Verifique a conexão do servidor.");
    } catch (AnthropicException ex) {
      throw error(HttpStatus.BAD_GATEWAY, "Resposta inválida recebida da Anthropic.");
    }
  }

  private static ResponseStatusException error(HttpStatus status, String detail) {
    return new ResponseStatusException(status, detail);
  }

  public record TesteResponse(
      String status,
      String mensagemId,
      String modelo,
      String resposta,
      long tokensEntrada,
      long tokensSaida,
      OffsetDateTime dataTeste) {}
}
