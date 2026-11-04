# Validação da entrega — 03/11/2026

## Parte 2.1

- SDK oficial Anthropic Java 3.4.0; endpoint `POST /api/ia/claude/testar`.
- `mvn verify`: 20 testes, 0 falhas, 0 erros, 1 ignorado (privilégio de links simbólicos da Parte 1).
- Onze casos novos com SDK e HTTP local simulado: texto, consumo, autenticação, sanitização de erros, timeout, resposta incompleta e endpoint sem chave.
- Chamada real pendente: `ANTHROPIC_API_KEY`/`ANTHROPIC_AUTH_TOKEN` ausentes no ambiente. Nenhum produto ou foto enviado à Anthropic nesta validação.
- As etapas 2.2 a 2.4 não foram implementadas nesta entrega, conforme sequência do plano.

## Parte 1

- Java Microsoft OpenJDK 21.0.13; Maven 3.9.11; Spring Boot 3.5.7.
- Build `mvn verify`: **BUILD SUCCESS**.
- JUnit: **9 testes, 0 falhas, 0 erros, 1 ignorado** (8 aprovados).
- O teste ignorado exige criação de links simbólicos, privilégio indisponível neste Windows. As verificações de traversal e pertencimento ao cadastro passaram em teste separado.
- JPEG, PNG e WebP originais preservados; formatos inválidos, bytes truncados, campos inválidos e limites testados.
- Falha simulada na segunda gravação e falha na publicação: nenhum produto visível e nenhuma pasta temporária residual.
- Teste HTTP com JAR em processo independente: cadastro de três fotografias, encerramento completo do processo, nova inicialização, consulta e recuperação das três imagens com SHA-256 idêntico.
- SHA-256 do arquivo original usado na aceitação: `DF0C5ABC6370B121CB044DCCFF1B6AB562ADDE1F0FEB8FFC584B1301A3CC8349`.

O teste de reinício usou três envios de uma pequena imagem PNG. A suíte JUnit cobre separadamente JPEG e WebP. O teste HTTP foi executado na porta 18080 com dados isolados; a configuração entregue usa 8080 e não contém os produtos de teste.

Documentação de compatibilidade do Spring Boot: [requisitos oficiais](https://docs.spring.io/spring-boot/3.5/system-requirements.html).

## Correção de autenticação do gateway

Configuração separada de API key e auth token, conflito explícito entre credenciais e teste HTTP do token Bearer no endpoint configurado. mvn verify aprovado: 21 testes, 0 falhas, 1 ignorado; Checkstyle, Spotless e cobertura aprovados. Teste real do gateway não executado: inicialização com credencial local bloqueada pela revisão automática de execução.

