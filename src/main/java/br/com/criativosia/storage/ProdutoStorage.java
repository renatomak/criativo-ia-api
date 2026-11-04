package br.com.criativosia.storage;

import br.com.criativosia.model.Produto;
import br.com.criativosia.service.ProdutoService.Foto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProdutoStorage {
  private final Path root;
  private final ObjectMapper mapper;

  public ProdutoStorage(@Value("${app.storage}") String directory, ObjectMapper mapper)
      throws IOException {
    Path configured = Path.of(directory).toAbsolutePath().normalize();
    Files.createDirectories(configured);
    this.root = configured.toRealPath();
    this.mapper = mapper;
  }

  public void salvar(Produto produto, List<Foto> fotos) throws IOException {
    Path temp = Files.createTempDirectory(root, ".tmp-");
    try {
      Path images = Files.createDirectory(temp.resolve("imagens"));
      for (int i = 0; i < fotos.size(); i++)
        gravarImagem(images.resolve(produto.imagens().get(i)), fotos.get(i).bytes());
      mapper
          .writerWithDefaultPrettyPrinter()
          .writeValue(temp.resolve("produto.json").toFile(), produto);
      publicar(temp, root.resolve(produto.id().toString()));
    } finally {
      if (Files.exists(temp, LinkOption.NOFOLLOW_LINKS)) {
        try (var paths = Files.walk(temp)) {
          for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
      }
    }
  }

  // No non-atomic fallback: unsupported filesystems must fail rather than expose partial data.
  protected void publicar(Path temp, Path target) throws IOException {
    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
  }

  protected void gravarImagem(Path path, byte[] bytes) throws IOException {
    Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
  }

  public List<Produto> listar() throws IOException {
    List<Produto> result = new ArrayList<>();
    try (var entries = Files.list(root)) {
      for (Path path : entries.toList()) {
        UUID id;
        try {
          id = UUID.fromString(path.getFileName().toString());
        } catch (IllegalArgumentException e) {
          continue;
        }
        result.add(buscar(id));
      }
    }
    result.sort(Comparator.comparing(Produto::dataCadastro).reversed().thenComparing(Produto::id));
    return result;
  }

  public Produto buscar(UUID id) throws IOException {
    Path dir = seguro(root.resolve(id.toString()));
    Path json = seguro(dir.resolve("produto.json"));
    Produto produto = mapper.readValue(json.toFile(), Produto.class);
    if (!id.equals(produto.id())
        || produto.imagens() == null
        || produto.imagens().isEmpty()
        || produto.dataCadastro() == null) throw new IOException("Metadados inconsistentes");
    for (String nome : produto.imagens()) {
      if (!nomeValido(nome)) throw new IOException("Nome de imagem inválido no cadastro");
      seguro(dir.resolve("imagens").resolve(nome));
    }
    return produto;
  }

  public byte[] imagem(UUID id, String nome) throws IOException {
    return imagem(id, nome, 10 * 1024 * 1024);
  }

  public byte[] imagem(UUID id, String nome, int limite) throws IOException {
    if (!nomeValido(nome)) throw ausente();
    Produto produto = buscar(id);
    if (!produto.imagens().contains(nome)) throw ausente();
    Path path = seguro(root.resolve(id.toString()).resolve("imagens").resolve(nome));
    try (var in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
      byte[] bytes = in.readNBytes(limite + 1);
      if (bytes.length > limite)
        throw new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE, "Imagem excede o limite para esta operação");
      return bytes;
    }
  }

  private boolean nomeValido(String name) {
    return name != null && name.matches("foto-[0-9]{2}\\.(jpg|png|webp)");
  }

  private Path seguro(Path path) throws IOException {
    Path normalized = path.toAbsolutePath().normalize();
    if (!normalized.startsWith(root)) throw ausente();
    Path current = root;
    for (Path part : root.relativize(normalized)) {
      current = current.resolve(part);
      if (Files.isSymbolicLink(current) || !Files.exists(current, LinkOption.NOFOLLOW_LINKS))
        throw ausente();
    }
    if (!normalized.toRealPath().startsWith(root)) throw ausente();
    return normalized;
  }

  private ResponseStatusException ausente() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto ou imagem não encontrado");
  }
}
