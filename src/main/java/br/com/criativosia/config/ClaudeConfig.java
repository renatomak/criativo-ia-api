package br.com.criativosia.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClaudeConfig {
  @Bean
  public ClaudeConnection claudeConnection(
      @Value("${ANTHROPIC_API_KEY:}") String apiKey,
      @Value("${ANTHROPIC_AUTH_TOKEN:}") String authToken,
      @Value("${ANTHROPIC_BASE_URL:}") String baseUrl,
      @Value("${claude.model}") String model,
      @Value("${claude.timeout-seconds}") int timeoutSeconds) {
    if (model.isBlank() || timeoutSeconds < 1 || timeoutSeconds > 120)
      throw new IllegalArgumentException(
          "Configure modelo e timeout Claude entre 1 e 120 segundos");
    if (!apiKey.isBlank() && !authToken.isBlank())
      throw new IllegalArgumentException(
          "Configure somente ANTHROPIC_API_KEY ou ANTHROPIC_AUTH_TOKEN, nunca ambos.");
    AnthropicClient client =
        apiKey.isBlank() && authToken.isBlank()
            ? null
            : buildClient(apiKey.trim(), authToken.trim(), baseUrl.trim(), timeoutSeconds);
    return new ClaudeConnection(client, model);
  }

  private static AnthropicClient buildClient(
      String apiKey, String authToken, String baseUrl, int timeoutSeconds) {
    var builder =
        AnthropicOkHttpClient.builder().timeout(Duration.ofSeconds(timeoutSeconds)).maxRetries(0);
    if (!authToken.isBlank()) builder.authToken(authToken);
    else builder.apiKey(apiKey);
    if (!baseUrl.isBlank()) builder.baseUrl(baseUrl);
    return builder.build();
  }

  public static final class ClaudeConnection implements AutoCloseable {
    private final AnthropicClient client;
    private final String model;

    public ClaudeConnection(AnthropicClient client, String model) {
      this.client = client;
      this.model = model;
    }

    public Optional<AnthropicClient> client() {
      return Optional.ofNullable(client);
    }

    public String model() {
      return model;
    }

    @Override
    public void close() {
      if (client != null) client.close();
    }
  }
}
