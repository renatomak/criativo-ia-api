package br.com.criativosia.integration;

import br.com.criativosia.config.ClaudeConfig.ClaudeConnection;
import com.anthropic.errors.*;
import com.anthropic.models.messages.MessageCreateParams;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.io.InterruptedIOException;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@Component
public class ClaudeClient {
    private final ClaudeConnection connection;
    public ClaudeClient(ClaudeConnection connection) { this.connection = connection; }

    public TesteResponse testarConexao() {
        var client = connection.client().orElseThrow(() -> error(HttpStatus.SERVICE_UNAVAILABLE,
                "Configure ANTHROPIC_API_KEY no ambiente do servidor e reinicie a aplicação."));
        try {
            var message = client.messages().create(MessageCreateParams.builder()
                    .model(connection.model()).maxTokens(64)
                    .addUserMessage("Responda apenas: Conexão com Claude funcionando.").build());
            String text = message.content().stream().flatMap(block -> block.text().stream())
                    .map(block -> block.text()).collect(Collectors.joining("\n"));
            if (text.isBlank() || message.stopReason().map(reason -> !reason.toString().equals("end_turn")).orElse(true))
                throw error(HttpStatus.BAD_GATEWAY, "Claude retornou resposta vazia ou incompleta.");
            return new TesteResponse(message.id(), message.model().toString(), text,
                    message.usage().inputTokens(), message.usage().outputTokens(), OffsetDateTime.now());
        } catch (AnthropicServiceException ex) {
            // Não propagar corpo, headers ou mensagem do provedor, que podem conter dados sensíveis.
            switch (ex.statusCode()) {
                case 401, 403 -> throw error(HttpStatus.BAD_GATEWAY, "Autenticação ou permissão recusada pela Anthropic. Revise a chave no servidor.");
                case 400, 402 -> throw error(HttpStatus.BAD_GATEWAY, "Anthropic recusou a solicitação. Verifique créditos, modelo e configuração da conta.");
                case 404 -> throw error(HttpStatus.BAD_GATEWAY, "Modelo Claude indisponível. Revise CLAUDE_MODEL.");
                case 429 -> throw error(HttpStatus.SERVICE_UNAVAILABLE, "Limite da Anthropic atingido. Tente novamente mais tarde.");
                case 408, 504 -> throw error(HttpStatus.GATEWAY_TIMEOUT, "Tempo limite na comunicação com Claude.");
                default -> throw error(HttpStatus.BAD_GATEWAY, "Falha na API da Anthropic. Tente novamente mais tarde.");
            }
        } catch (AnthropicIoException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof InterruptedIOException)
                    throw error(HttpStatus.GATEWAY_TIMEOUT, "Tempo limite na comunicação com Claude.");
            }
            throw error(HttpStatus.BAD_GATEWAY, "Não foi possível conectar à Anthropic. Verifique a conexão do servidor.");
        } catch (AnthropicException ex) {
            throw error(HttpStatus.BAD_GATEWAY, "Resposta inválida recebida da Anthropic.");
        }
    }

    private static ResponseStatusException error(HttpStatus status, String detail) {
        return new ResponseStatusException(status, detail);
    }

    public record TesteResponse(String mensagemId, String modelo, String resposta,
                                long tokensEntrada, long tokensSaida, OffsetDateTime dataTeste) {}
}
