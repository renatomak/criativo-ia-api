# Testar no Postman

Importe `criativo-ia-api.postman_collection.json` em **Postman > Import > Files**.
A coleção contém a URL local, o ID do kit Lupo já cadastrado e os caminhos das três imagens.

Execute as chamadas 1, 2 e 3 para listar, consultar o produto e ver a primeira foto.
A chamada 4 cadastra outro produto: cada POST gera um novo UUID. Após sucesso, a coleção atualiza o ID usado nas consultas.

Base: `http://127.0.0.1:8080`

| Método | Caminho | Função |
|---|---|---|
| POST | /api/produtos | Cadastrar com fotos |
| GET | /api/produtos | Listar |
| GET | /api/produtos/{id} | Consultar |
| GET | /api/produtos/{id}/imagens/{nome} | Buscar foto |

O POST usa Body > form-data: arquivo JSON na chave `produto`, com Content-Type `application/json`, e três arquivos na chave `imagens`. Deixe o Postman gerar o cabeçalho multipart automaticamente. Se solicitar acesso aos arquivos, selecione `exemplos/produto-lupo.json` e as imagens na pasta original.

Use o JSON adaptado: o JSON original da pasta possui campos que esta API não aceita. A descrição e os detalhes foram preservados no cadastro.

Se o servidor estiver parado, execute na raiz do projeto, com Java 21:

```powershell
java -jar target/criativo-ia-api-1.0.0.jar
```

Execute **5 - Testar conexão Claude (texto)** para testar `POST /api/ia/claude/testar`, sem body. Configure `ANTHROPIC_API_KEY` no servidor e reinicie com o novo JAR antes. Consulte `../PARTE-2.md`. A chamada consome tokens e não envia fotos.

Ainda não existe endpoint para gerar criativos ou analisar produtos. O teste de conexão corresponde à Parte 2.1.
