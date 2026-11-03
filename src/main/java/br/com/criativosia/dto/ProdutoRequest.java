package br.com.criativosia.dto;

import br.com.criativosia.model.Produto.Plataforma;
import jakarta.validation.constraints.*;

public record ProdutoRequest(
        @NotBlank @Size(max=200) String nome,
        @NotBlank @Size(max=10000) String descricao,
        @Size(max=200) String categoria,
        @Size(max=2048) String linkAnuncio,
        Plataforma plataforma,
        @Size(max=10000) String observacoes) {}
