# Parte 2.1 — Conexão com Claude

Implementado o primeiro passo do plano: SDK oficial Anthropic Java 3.4.0, teste de texto simples e tratamento de erros. As etapas 2.2 a 2.4 (fotos, dossiê e histórico) ainda estão pendentes da validação da comunicação real.

## Configurar e executar

No PowerShell, na raiz do projeto, configure a chave apenas no ambiente do processo:

```powershell
$chaveClaude = Read-Host 'Chave Anthropic' -AsSecureString
$env:ANTHROPIC_API_KEY = [System.Net.NetworkCredential]::new('', $chaveClaude).Password
$env:CLAUDE_MODEL = 'claude-haiku-4-5'
$env:CLAUDE_TIMEOUT_SECONDS = '30'
java -jar target/criativo-ia-api-1.0.0.jar
```

Use Java 21. Se uma instância antiga estiver rodando em 8080, encerre-a antes de iniciar o novo JAR. No IntelliJ, configure as mesmas variáveis na execução e reinicie. Não coloque a chave no JSON, no Postman ou no código. A aplicação não carrega arquivos `.env` automaticamente.

O modelo pode ser alterado por `CLAUDE_MODEL`. Timeout permitido: 1 a 120 segundos, padrão 30. Não há retries automáticos. Sem chave, o cadastro e as consultas locais continuam funcionando.

## Testar no Postman

Importe novamente `postman/criativo-ia-api.postman_collection.json` e execute **5 - Testar conexão Claude (texto)**:

```http
POST http://127.0.0.1:8080/api/ia/claude/testar
```

Sem body e sem Authorization no Postman. A chave fica no servidor. Cada chamada envia um texto fixo à Anthropic e consome tokens; não envia produtos ou imagens e não grava análise.

Sucesso: 200 com `mensagemId`, `modelo`, `resposta`, `tokensEntrada`, `tokensSaida` e `dataTeste`. Somente resposta com texto e término `end_turn` é aceita.

| HTTP | Significado |
|---|---|
| 503 | Chave ausente no servidor ou limite da Anthropic |
| 502 | Conexão, autenticação, créditos, modelo ou resposta do provedor inválida |
| 504 | Tempo limite excedido |

Erros usam mensagens locais sem repassar o corpo, headers ou credenciais do provedor.

## Validação

Testes locais exercitam o SDK contra um servidor HTTP simulado: payload, autenticação, resposta, consumo, erros 400/401/403/404/429/500/529, resposta incompleta, timeout e endpoint sem chave. Os testes de cadastro da Parte 1 continuam na suíte.

A chamada real não foi executada nesta entrega: `ANTHROPIC_API_KEY` não estava definida no ambiente. O próximo passo é executar a chamada acima com uma chave válida e créditos. Ainda não existem os endpoints `/analisar` ou `/analises`.

Referência: [documentação oficial do SDK Java](https://platform.claude.com/docs/en/cli-sdks-libraries/sdks/java).
