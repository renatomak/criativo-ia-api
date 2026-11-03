package br.com.criativosia.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
import java.util.Optional;

@Configuration
public class ClaudeConfig {
    @Bean
    public ClaudeConnection claudeConnection(
            @Value("${ANTHROPIC_API_KEY:}") String key,
            @Value("${claude.model}") String model,
            @Value("${claude.timeout-seconds}") int timeoutSeconds) {
        if (model.isBlank() || timeoutSeconds < 1 || timeoutSeconds > 120)
            throw new IllegalArgumentException("Configure modelo e timeout Claude entre 1 e 120 segundos");
        // Não lê base URLs externas nem registra credenciais. Sem chave, a Parte 1 continua disponível.
        AnthropicClient client = key.isBlank() ? null : AnthropicOkHttpClient.builder()
                .apiKey(key.trim()).timeout(Duration.ofSeconds(timeoutSeconds)).maxRetries(0).build();
        return new ClaudeConnection(client, model);
    }

    public static final class ClaudeConnection implements AutoCloseable {
        private final AnthropicClient client;
        private final String model;
        public ClaudeConnection(AnthropicClient client, String model) { this.client = client; this.model = model; }
        public Optional<AnthropicClient> client() { return Optional.ofNullable(client); }
        public String model() { return model; }
        @Override public void close() { if (client != null) client.close(); }
    }
}
