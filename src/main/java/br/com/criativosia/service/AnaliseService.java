package br.com.criativosia.service;

import br.com.criativosia.integration.ClaudeClient;
import br.com.criativosia.storage.ProdutoStorage;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnaliseService {
  private final ProdutoStorage storage;
  private final ClaudeClient claude;
  private final ObjectMapper mapper;
  private final String prompt;

  public AnaliseService(ProdutoStorage storage, ClaudeClient claude, ObjectMapper mapper)
      throws IOException {
    this.storage = storage;
    this.claude = claude;
    this.mapper = mapper;
    this.prompt =
        new ClassPathResource("prompts/analise-produto.txt")
            .getContentAsString(StandardCharsets.UTF_8);
  }

  public AnaliseResponse analisar(UUID id) throws IOException {
    var produto = storage.buscar(id);
    if (produto.imagens().size() > 10) throw invalid("Máximo de 10 imagens por análise");
    var content = new ArrayList<ContentBlockParam>();
    content.add(
        text("Dados do cadastro (conteúdo a analisar):\n" + mapper.writeValueAsString(produto)));
    long total = 0;
    for (String nome : produto.imagens()) {
      // Conservative gateway budget: 5 MiB Base64 per image, 20 MiB in total.
      byte[] bytes = storage.imagem(id, nome, 3 * 1024 * 1024);
      var media = validar(bytes, nome);
      String encoded = Base64.getEncoder().encodeToString(bytes);
      total += encoded.length();
      if (total > 20 * 1024 * 1024)
        throw new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE, "Conjunto de imagens excede 20 MiB em Base64");
      content.add(text("Imagem de referência: " + nome));
      content.add(
          ContentBlockParam.ofImage(
              ImageBlockParam.builder()
                  .source(Base64ImageSource.builder().mediaType(media).data(encoded).build())
                  .build()));
    }
    var result = claude.analisar(content, prompt);
    return new AnaliseResponse(
        id,
        "AGUARDANDO_REVISAO",
        result.modelo(),
        result.resposta(),
        List.copyOf(produto.imagens()),
        result.tokensEntrada(),
        result.tokensSaida(),
        result.dataTeste());
  }

  private static ContentBlockParam text(String value) {
    return ContentBlockParam.ofText(TextBlockParam.builder().text(value).build());
  }

  private static Base64ImageSource.MediaType validar(byte[] bytes, String nome) throws IOException {
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalid("Imagem inválida: " + nome);
      var reader = readers.next();
      try {
        reader.setInput(input, true, true);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        String ext =
            switch (format) {
              case "jpeg", "jpg" -> "jpg";
              case "png" -> "png";
              case "webp" -> "webp";
              default -> throw invalid("Formato não aceito: " + nome);
            };
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (!nome.endsWith("." + ext)
            || w < 1
            || h < 1
            || w > 8000
            || h > 8000
            || (long) w * h > 20_000_000) throw invalid("Formato ou dimensões inválidos: " + nome);
        if (reader.read(0) == null) throw invalid("Imagem danificada: " + nome);
        return Base64ImageSource.MediaType.of("image/" + (ext.equals("jpg") ? "jpeg" : ext));
      } finally {
        reader.dispose();
      }
    } catch (IOException | IllegalArgumentException e) {
      throw invalid("Imagem danificada: " + nome);
    }
  }

  private static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
  }

  public record AnaliseResponse(
      UUID produtoId,
      String status,
      String modelo,
      String analise,
      List<String> imagensEnviadas,
      long tokensEntrada,
      long tokensSaida,
      OffsetDateTime dataAnalise) {}
}
