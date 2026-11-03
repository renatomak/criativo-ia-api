package br.com.criativosia.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record Produto(UUID id, String nome, String descricao, String categoria, String linkAnuncio,
                      Plataforma plataforma, String observacoes, List<String> imagens, OffsetDateTime dataCadastro) {
    public enum Plataforma { TIKTOK_SHOP, SHOPEE, OUTRA }
}
