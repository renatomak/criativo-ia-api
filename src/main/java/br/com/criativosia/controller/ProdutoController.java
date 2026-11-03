package br.com.criativosia.controller;

import br.com.criativosia.dto.ProdutoRequest;
import br.com.criativosia.model.Produto;
import br.com.criativosia.service.ProdutoService;
import br.com.criativosia.storage.ProdutoStorage;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {
    private final ProdutoService service;
    private final ProdutoStorage storage;
    public ProdutoController(ProdutoService service, ProdutoStorage storage) { this.service=service; this.storage=storage; }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Produto> cadastrar(@Valid @RequestPart("produto") ProdutoRequest request,
            @RequestPart("imagens") List<MultipartFile> imagens) throws IOException {
        Produto p = service.cadastrar(request, imagens);
        return ResponseEntity.created(URI.create("/api/produtos/"+p.id())).body(p);
    }
    @GetMapping public List<Produto> listar() throws IOException { return storage.listar(); }
    @GetMapping("/{id}") public Produto buscar(@PathVariable UUID id) throws IOException { return storage.buscar(id); }
    @GetMapping("/{id}/imagens/{nome}")
    public ResponseEntity<byte[]> imagem(@PathVariable UUID id, @PathVariable String nome) throws IOException {
        byte[] bytes = storage.imagem(id, nome);
        String type = nome.endsWith(".jpg") ? "image/jpeg" : nome.endsWith(".png") ? "image/png" : "image/webp";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).contentLength(bytes.length)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\""+nome+"\"").body(bytes);
    }
}
