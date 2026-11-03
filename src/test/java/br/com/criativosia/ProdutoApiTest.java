package br.com.criativosia;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.criativosia.dto.ProdutoRequest;
import br.com.criativosia.model.Produto;
import br.com.criativosia.service.ProdutoService;
import br.com.criativosia.storage.ProdutoStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProdutoApiTest {
  @TempDir static Path root;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("app.storage", () -> root.toString());
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @TempDir Path isolated;

  static byte[] image(String format) throws IOException {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), format, out);
    return out.toByteArray();
  }

  MockMultipartFile data(String json) {
    return new MockMultipartFile(
        "produto", "", "application/json", json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  MockMultipartFile photo(byte[] bytes) {
    return new MockMultipartFile(
        "imagens", "../../original.exe", "application/octet-stream", bytes);
  }

  @Test
  void registrationAndOriginalRetrieval() throws Exception {
    byte[] png = image("png"), jpg = image("jpg");
    var response =
        mvc.perform(
                multipart("/api/produtos")
                    .file(
                        data(
                            "{\"nome\":\"Produto\",\"descricao\":\"Descrição\",\"plataforma\":\"SHOPEE\"}"))
                    .file(photo(png))
                    .file(photo(jpg))
                    .file(photo(png)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();
    Produto p = mapper.readValue(response.getResponse().getContentAsByteArray(), Produto.class);
    assertEquals(3, p.imagens().size());
    assertTrue(Files.exists(root.resolve(p.id().toString()).resolve("produto.json")));
    mvc.perform(get("/api/produtos/" + p.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nome").value("Produto"));
    mvc.perform(get("/api/produtos")).andExpect(status().isOk());
    mvc.perform(get("/api/produtos/" + p.id() + "/imagens/" + p.imagens().getFirst()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(content().bytes(png));
    // A fresh storage instance has no in-memory index to recover.
    var reopened = new ProdutoStorage(root.toString(), mapper);
    assertEquals(p, reopened.buscar(p.id()));
    assertArrayEquals(jpg, reopened.imagem(p.id(), p.imagens().get(1)));
    mvc.perform(get("/api/produtos/" + p.id() + "/imagens/produto.json"))
        .andExpect(status().isNotFound());
  }

  @Test
  void invalidFieldsAndParts() throws Exception {
    for (String json :
        List.of(
            "{\"nome\":\" \",\"descricao\":\"x\"}",
            "{\"nome\":\"x\",\"descricao\":\"x\",\"linkAnuncio\":\"file:///secret\"}",
            "{\"nome\":\"x\",\"descricao\":\"x\",\"plataforma\":\"INVALIDA\"}"))
      mvc.perform(multipart("/api/produtos").file(data(json)).file(photo(image("png"))))
          .andExpect(status().isBadRequest());
    mvc.perform(multipart("/api/produtos").file(data("{\"nome\":\"x\",\"descricao\":\"x\"}")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/produtos/not-a-uuid")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/produtos/" + UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void rejectFakeTruncatedEmptyAndDisallowedImages() throws Exception {
    byte[] png = image("png");
    for (byte[] bytes :
        List.of(new byte[0], "fake png".getBytes(), Arrays.copyOf(png, 25), image("gif")))
      mvc.perform(
              multipart("/api/produtos")
                  .file(data("{\"nome\":\"x\",\"descricao\":\"x\"}"))
                  .file(photo(bytes)))
          .andExpect(status().isBadRequest());
  }

  ProdutoRequest request() {
    return new ProdutoRequest("x", "y", null, null, null, null);
  }

  @Test
  void webpOriginalIsPreserved() throws Exception {
    byte[] bytes =
        Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
    var storage = new ProdutoStorage(isolated.toString(), mapper);
    Produto p = new ProdutoService(storage).cadastrar(request(), List.of(photo(bytes)));
    assertEquals("foto-01.webp", p.imagens().getFirst());
    assertArrayEquals(bytes, storage.imagem(p.id(), "foto-01.webp"));
  }

  @Test
  void limitsAndUnpublishedFolders() throws Exception {
    var storage = new ProdutoStorage(isolated.toString(), mapper);
    var service = new ProdutoService(storage);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> service.cadastrar(request(), Collections.nCopies(11, photo(image("png")))));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () ->
            service.cadastrar(
                request(), List.of(photo(new byte[(int) ProdutoService.MAX_BYTES + 1]))));
    Files.createDirectory(isolated.resolve(".tmp-interrupted"));
    assertTrue(storage.listar().isEmpty());
  }

  @Test
  void failedPublicationCleansTemporaryFolder() throws Exception {
    var storage =
        new ProdutoStorage(isolated.toString(), mapper) {
          @Override
          protected void publicar(Path a, Path b) throws IOException {
            throw new IOException("injected failure");
          }
        };
    assertThrows(
        IOException.class,
        () -> new ProdutoService(storage).cadastrar(request(), List.of(photo(image("png")))));
    try (var children = Files.list(isolated)) {
      assertEquals(0, children.count());
    }
  }

  @Test
  void traversalAndSymlinkAreRejected() throws Exception {
    var storage = new ProdutoStorage(isolated.toString(), mapper);
    Produto p = new ProdutoService(storage).cadastrar(request(), List.of(photo(image("png"))));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> storage.imagem(p.id(), "../../produto.json"));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> storage.imagem(p.id(), "foto-99.png"));
  }

  @Test
  void symlinkIsRejected() throws Exception {
    var storage = new ProdutoStorage(isolated.toString(), mapper);
    Produto p = new ProdutoService(storage).cadastrar(request(), List.of(photo(image("png"))));
    Path file = isolated.resolve(p.id().toString()).resolve("imagens/foto-01.png");
    Path outside = Files.createTempFile("criativos-test", ".png");
    try {
      try {
        Files.createSymbolicLink(file.resolveSibling("link-probe"), outside);
      } catch (IOException | UnsupportedOperationException e) {
        Assumptions.abort("SO não permite criar symlinks");
      }
      Files.delete(file);
      Files.createSymbolicLink(file, outside);
      assertThrows(
          org.springframework.web.server.ResponseStatusException.class,
          () -> storage.imagem(p.id(), "foto-01.png"));
    } finally {
      Files.deleteIfExists(outside);
    }
  }

  @Test
  void interruptedImageWritePublishesNothing() throws Exception {
    var storage =
        new ProdutoStorage(isolated.toString(), mapper) {
          int count;

          @Override
          protected void gravarImagem(Path path, byte[] bytes) throws IOException {
            if (++count == 2) {
              Files.write(path, new byte[] {1});
              throw new IOException("disk failure");
            }
            super.gravarImagem(path, bytes);
          }
        };
    assertThrows(
        IOException.class,
        () ->
            new ProdutoService(storage)
                .cadastrar(request(), List.of(photo(image("png")), photo(image("png")))));
    assertTrue(storage.listar().isEmpty());
    try (var children = Files.list(isolated)) {
      assertEquals(0, children.count());
    }
  }
}
