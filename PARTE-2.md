# Parte 2.1 — Conexão com Claude

Implementado o primeiro passo do plano: SDK oficial Anthropic Java 3.4.0, teste de texto simples e tratamento de erros. As etapas 2.2 a 2.4 (fotos, dossiê e histórico) ainda estão pendentes da validação da comunicação real.

## Configurar e executar

No PowerShell, na raiz do projeto, configure a chave apenas no ambiente do processo:

```powershell
$chaveClaude = Read-Host 'Chave Anthropic' -AsSecureString
$env:ANTHROPIC_AUTH_TOKEN = [System.Net.NetworkCredential]::new('', $chaveClaude).Password
$env:ANTHROPIC_API_KEY = $null
$env:ANTHROPIC_BASE_URL = 'https://api.mwapi.dev'
$env:CLAUDE_MODEL = 'claude-haiku-4-5'
$env:CLAUDE_TIMEOUT_SECONDS = '30'
java -jar target/criativo-ia-api-1.0.0.jar
```

Use Java 21. Se uma instância antiga estiver rodando em 8080, encerre-a antes de iniciar o novo JAR. No IntelliJ, configure as mesmas variáveis na execução e reinicie. Não coloque a chave no JSON, no Postman ou no código. A aplicação não carrega arquivos `.env` automaticamente.

O servidor aceita `ANTHROPIC_API_KEY` ou `ANTHROPIC_AUTH_TOKEN`, exclusivamente; definir ambos impede a inicialização. `ANTHROPIC_BASE_URL` é opcional (padrão oficial Anthropic). O modelo pode ser alterado por `CLAUDE_MODEL`. Timeout permitido: 1 a 120 segundos, padrão 30. Não há retries automáticos. Sem chave, o cadastro e as consultas locais continuam funcionando.

## Testar no Postman

Importe novamente `postman/criativo-ia-api.postman_collection.json` e execute **5 - Testar conexão Claude (texto)**:

```http
POST http://127.0.0.1:8080/api/ia/claude/testar
```

Sem body e sem Authorization no Postman. A chave fica no servidor. Cada chamada envia um texto fixo à Anthropic (ou ao `ANTHROPIC_BASE_URL` configurado) e consome tokens; não envia produtos ou imagens e não grava análise.

Sucesso: 200 com `mensagemId`, `modelo`, `resposta`, `tokensEntrada`, `tokensSaida` e `dataTeste`. Somente resposta com texto e término `end_turn` é aceita.

| HTTP | Significado |
|---|---|
| 503 | Chave ausente no servidor ou limite da Anthropic |
| 502 | Conexão, autenticação, créditos, modelo ou resposta do provedor inválida |
| 504 | Tempo limite excedido |

Erros usam mensagens locais sem repassar o corpo, headers ou credenciais do provedor.

## Validação

Testes locais exercitam o SDK contra um servidor HTTP simulado: payload, autenticação, resposta, consumo, erros 400/401/403/404/429/500/529, resposta incompleta, timeout e endpoint sem chave. Os testes de cadastro da Parte 1 continuam na suíte.

A chamada real não foi executada nesta entrega: `ANTHROPIC_API_KEY`/`ANTHROPIC_AUTH_TOKEN` não estavam definidos no ambiente. O próximo passo é executar a chamada acima com uma chave válida e créditos. Ainda não existem os endpoints `/analisar` ou `/analises`.

Referência: [documentação oficial do SDK Java](https://platform.claude.com/docs/en/cli-sdks-libraries/sdks/java).

## Gateway MWAPI no Windows

Execute ./iniciar-gateway.ps1 no PowerShell com Java 21. O script pede o token com entrada oculta, usa https://api.mwapi.dev e limpa a chave concorrente apenas durante a execução. Encerre a instância antiga antes. Use -Modelo para selecionar um modelo habilitado na sua conta.

O JSON de configuração do Claude Code e comandos export do Bash não configuram a execução Java no IntelliJ. No IntelliJ, defina ANTHROPIC_BASE_URL e ANTHROPIC_AUTH_TOKEN em Run > Edit Configurations > Environment variables, remova ANTHROPIC_API_KEY e reinicie. CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC pertence ao Claude Code e não é utilizado nesta API.

O script também carrega o token de gateway.local.json, quando presente. Esse arquivo está excluído do Git. A leitura é feita pelo script; iniciar diretamente pelo IntelliJ ainda exige configurar as variáveis da execução.


## Execução pelo IntelliJ

O Spring Boot importa `gateway.local.properties` da pasta de execução. Esse arquivo local, excluído do Git, contém `ANTHROPIC_BASE_URL=https://api.mwapi.dev`, o token e a API key vazia. A credencial não fica em `src/main/resources` nem é incluída no JAR.

No IntelliJ, use a raiz do projeto como Working directory. Remova das Environment variables da configuração de execução quaisquer valores antigos de `ANTHROPIC_API_KEY`, `ANTHROPIC_AUTH_TOKEN` e `ANTHROPIC_BASE_URL`, pois variáveis de ambiente têm prioridade sobre o arquivo local. Pare e execute novamente a aplicação para carregar as alterações.

## Configuração atual — application.properties (04/11/2026)

Por solicitação do usuário, endereço e token foram transferidos para `src/main/resources/application.properties`. A importação de `gateway.local.properties` foi removida. Para esta configuração, execute pelo IntelliJ ou diretamente com Java 21; o script de gateway não é necessário. Remova overrides ANTHROPIC_* e CLAUDE_MODEL da configuração de execução caso existam.

`claude.model=claude-haiku-4-5` continua sendo o modelo configurado. Um HTTP 404 do gateway não confirma sozinho se a causa é modelo sem acesso ou rota inexistente. O nome exato do modelo deve ser conferido no painel do gateway. `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC` configura o Claude Code e não é utilizado pelo SDK Java.

A credencial agora está em um arquivo versionado e será incluída no JAR. Não publique esse arquivo/JAR com o token.

Modelo atualizado para claude-sonnet-4-6 conforme configuração fornecida pelo usuário. O SDK Anthropic usa a base https://api.mwapi.dev e a rota /v1/messages. A configuração wire_api=responses fornecida pertence ao cliente Codex; a disponibilidade desse modelo no protocolo Anthropic ainda depende do gateway.


## Contrato atualizado da etapa 2.1

Endpoint principal: `POST http://localhost:8080/api/integracoes/claude/testar`, sem corpo. A rota `/api/ia/claude/testar` permanece como alias. A resposta contém `status: OK`, `modelo`, `resposta`, ID da mensagem, tokens e data. O SDK continua na versão 3.4.0.

O modelo configurado diretamente no application.properties agora é `claude-haiku-5-5`, conforme solicitado. Thinking está desabilitado para o teste curto de texto. O endereço e a credencial do gateway foram preservados. A disponibilidade do Haiku 5.5 na MWAPI não foi confirmada; o teste real anterior aprovado usava Sonnet 4.6.

Reinicie a execução no IntelliJ e importe novamente a coleção Postman. Não há Maven Wrapper neste projeto: pelo terminal use `mvn spring-boot:run` com Java 21 e Maven instalados, ou execute o JAR compilado. O teste não persiste resposta nem envia imagens; os logs existentes aparecem no console.

Modelo documentado oficialmente: https://platform.claude.com/docs/en/models/haiku-5-5/overview

## Etapa 2.2 — Envio de produto e imagens

`POST /api/produtos/{id}/analisar`, sem body, envia os dados do cadastro e todas as imagens ao gateway configurado. A coleção Postman contém a chamada 6 com o produto Lupo. Reinicie a aplicação após compilar.

A resposta contém produtoId, status AGUARDANDO_REVISAO, modelo, analise (texto preliminar), imagensEnviadas, tokensEntrada, tokensSaida e dataAnalise. Ainda não existe esquema de dossiê validado nem persistência/histórico. Cada POST faz nova chamada paga; imagens e informações do cadastro saem do computador para o gateway.

Limites locais conservadores para esta etapa: até 10 fotos, 3 MiB de bytes por foto, 20 MiB totais em Base64, dimensões até 8000 por lado e 20 milhões de pixels. Arquivos aceitos no cadastro podem exceder os limites menores de análise; nesse caso retorna 413, sem enviar parte do produto. Não há redimensionamento ou alteração dos originais. Formato e decodificação são conferidos novamente antes do envio; imagem inválida retorna 422, produto ou arquivo ausente retorna 404.

Os logs globais incluem a chamada e o consumo, sem conteúdo ou Base64. A análise usa o mesmo modelo e timeout configurados no application.properties. Um 504 pode exigir aumentar claude.timeout-seconds (máximo 120). A etapa foi validada com HTTP simulado; interpretação real das fotos pelo gateway ainda requer teste no Postman.

Referência de formatos e limites da API: https://platform.claude.com/docs/en/build-with-claude/vision

## Diagnóstico de autenticação — 05/11/2026

Restaurado `claude-sonnet-4-6`, modelo anteriormente validado pelo usuário na MWAPI. O token e a URL configurados foram preservados. A disponibilidade do Haiku 5.5 no gateway não foi confirmada.

O log CLAUDE ENVIO agora mostra o modelo; CLAUDE PROVEDOR mostra o status HTTP original sem corpo ou credenciais. O JSON diferencia HTTP 401 (credencial recusada) de HTTP 403 (acesso negado: verificar permissões, grupo e modelo). Ambos continuam retornando 502 na API local porque são falhas do provedor externo. Reinicie a aplicação e repita o teste; esta alteração não comprova a validade atual do token.
