package br.com.criativosia.service;

import br.com.criativosia.dto.ProdutoRequest;
import br.com.criativosia.model.Produto;
import br.com.criativosia.storage.ProdutoStorage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ProdutoService {
    public static final long MAX_BYTES = 10 * 1024 * 1024;
    private final ProdutoStorage storage;
    public ProdutoService(ProdutoStorage storage) { this.storage = storage; }
    public record Foto(byte[] bytes, String extensao) {}
    public Produto cadastrar(ProdutoRequest r, List<MultipartFile> arquivos) throws IOException {
        if (r.linkAnuncio() != null && !r.linkAnuncio().isBlank()) {
            try {
                URI u = URI.create(r.linkAnuncio());
                if (!("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme()))
                        || u.getHost() == null || u.getUserInfo() != null) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw invalido("linkAnuncio deve ser uma URL HTTP ou HTTPS válida"); }
        }
        if (arquivos == null || arquivos.isEmpty() || arquivos.size() > 10)
            throw invalido("Envie de 1 a 10 imagens");
        List<Foto> fotos = new ArrayList<>();
        for (MultipartFile a : arquivos) fotos.add(validar(a));
        UUID id = UUID.randomUUID();
        List<String> nomes = new ArrayList<>();
        for (int i=0; i<fotos.size(); i++) nomes.add("foto-%02d.%s".formatted(i+1, fotos.get(i).extensao()));
        Produto p = new Produto(id, r.nome().strip(), r.descricao().strip(), r.categoria(), r.linkAnuncio(),
                r.plataforma(), r.observacoes(), List.copyOf(nomes), OffsetDateTime.now());
        storage.salvar(p, fotos);
        return p;
    }
    private Foto validar(MultipartFile a) throws IOException {
        if (a.isEmpty()) throw invalido("Imagem vazia");
        if (a.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Imagem excede 10 MB");
        byte[] bytes;
        try (InputStream in = a.getInputStream()) { bytes = in.readNBytes((int) MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Imagem excede 10 MB");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalido("Arquivo não é JPG, PNG ou WebP válido");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String ext = switch(format) { case "jpeg", "jpg" -> "jpg"; case "png" -> "png"; case "webp" -> "webp"; default -> throw invalido("Formato não permitido"); };
                reader.setInput(input, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || (long)w*h > 20_000_000) throw invalido("Imagem excede 20 milhões de pixels");
                if (reader.read(0) == null) throw invalido("Imagem inválida");
                return new Foto(bytes, ext);
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException e) { throw invalido("Imagem inválida ou danificada"); }
    }
    private static ResponseStatusException invalido(String msg) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg); }
}
