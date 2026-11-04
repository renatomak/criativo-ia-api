package br.com.criativosia.controller;

import br.com.criativosia.service.AnaliseService;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnaliseController {
  private final AnaliseService service;

  public AnaliseController(AnaliseService service) {
    this.service = service;
  }

  @PostMapping("/api/produtos/{id}/analisar")
  public AnaliseService.AnaliseResponse analisar(@PathVariable UUID id) throws IOException {
    return service.analisar(id);
  }
}
