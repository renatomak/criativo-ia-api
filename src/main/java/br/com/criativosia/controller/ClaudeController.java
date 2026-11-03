package br.com.criativosia.controller;

import br.com.criativosia.integration.ClaudeClient;
import br.com.criativosia.integration.ClaudeClient.TesteResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ia/claude")
public class ClaudeController {
  private final ClaudeClient client;

  public ClaudeController(ClaudeClient client) {
    this.client = client;
  }

  @PostMapping("/testar")
  public TesteResponse testar() {
    return client.testarConexao();
  }
}
