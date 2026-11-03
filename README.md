# criativo-ia-api — Parte 1

API local Java 21 / Spring Boot. Cadastro, listagem, consulta e recuperação de fotografias originais, com teste de conexão ao Claude (Parte 2.1). Sem banco de dados ou armazenamento em nuvem.

## Executar

Para configurar e testar a integração com Claude da Parte 2.1, consulte [PARTE-2.md](PARTE-2.md). A coleção em `postman/criativo-ia-api.postman_collection.json` inclui a chamada de teste de texto.

Requisitos: **JDK 21** e **Maven 3.9 ou superior**, disponíveis no PATH. Execute a partir desta pasta:

```powershell
mvn clean verify
java -jar target/criativo-ia-api-1.0.0.jar
```

## Padronização do projeto

O projeto segue baseline de mercado para Java/Spring:

- **EditorConfig** para padronização de encoding, indentação e fim de linha.
- **Spotless (google-java-format)** para formatação e imports.
- **Checkstyle** para regras estáticas obrigatórias.
- **JaCoCo** com cobertura mínima de linhas em 60% no bundle.
- **Maven Enforcer** exigindo Java 21 e Maven 3.9+.
- **GitHub Actions (CI)** executando `mvn verify` em push e pull request.
- **Dependabot** semanal para atualização de dependências Maven.

Comandos úteis:

```powershell
mvn spotless:apply
mvn verify
```

O servidor escuta apenas em `http://127.0.0.1:8080`. Encerre com Ctrl+C. O JAR também é fornecido pronto; para executá-lo basta Java 21. Após a compilação, a execução não depende de internet. O primeiro build precisa baixar as dependências.

Os dados ficam em `./dados/produtos`, relativo à pasta de execução. Para manter um diretório fixo mesmo ao mudar de pasta:

```powershell
$env:CRIATIVOS_DATA_DIR = 'C:\CriativosIA\dados\produtos'
java -jar target/criativo-ia-api-1.0.0.jar
```

## Cadastro pelo curl ou Postman

Cada envio contém uma parte **produto** de tipo `application/json` e de 1 a 10 partes **imagens**. No Postman, use Body → form-data, `produto` como arquivo JSON (selecione `exemplos/produto.json`, Content-Type `application/json`) e `imagens` como File, repetido por foto. Não configure manualmente o cabeçalho multipart/boundary.

No PowerShell, substitua os caminhos das três fotos:

```powershell
curl.exe -i http://127.0.0.1:8080/api/produtos `
  -F 'produto=@exemplos/produto.json;type=application/json' `
  -F 'imagens=@C:/Fotos/frente.jpg' `
  -F 'imagens=@C:/Fotos/costas.png' `
  -F 'imagens=@C:/Fotos/detalhe.webp'
```

Sucesso: **201**, cabeçalho `Location` e JSON com UUID, campos, nomes das imagens e `dataCadastro` automática com offset. `nome` (200 caracteres) e `descricao` (10.000) são obrigatórios e não podem ser brancos. Opcionais: `categoria` (200), `linkAnuncio` (2.048, HTTP/HTTPS com host), `plataforma` (`TIKTOK_SHOP`, `SHOPEE`, `OUTRA`) e `observacoes` (10.000). Opcionais podem ser omitidos ou `null`; link vazio é aceito. Campos JSON desconhecidos são rejeitados.

```powershell
curl.exe http://127.0.0.1:8080/api/produtos
curl.exe http://127.0.0.1:8080/api/produtos/UUID
curl.exe http://127.0.0.1:8080/api/produtos/UUID/imagens/foto-01.jpg -o original.jpg
```

Listagem retorna um array completo, do mais recente ao mais antigo (UUID desempata). Produto ou foto ausente: **404**. Campos, UUID ou imagem inválidos: **400**. Tipo de parte incorreto: **415**. Upload acima do limite: **413**. Falha de disco: **500**, sem expor caminhos no corpo; detalhes no log.

## Persistência e segurança

```text
dados/produtos/
  <uuid>/
    produto.json
    imagens/
      foto-01.jpg
      foto-02.png
      foto-03.webp
```

São aceitos JPEG, PNG e WebP, até **10 MiB por arquivo**, **101 MiB por requisição**, **10 arquivos** e **20 milhões de pixels por imagem**. O conteúdo é identificado e decodificado com ImageIO, incluindo leitor WebP TwelveMonkeys; extensão e MIME enviados pelo cliente não definem o formato. As imagens nunca são recodificadas: os bytes recebidos são os bytes gravados e devolvidos. Os nomes originais são ignorados para impedir caminhos maliciosos e colisões.

Controller trata HTTP; service valida regras e imagens; storage grava e consulta arquivos. O cadastro é escrito em `.tmp-*` no mesmo diretório e publicado com `ATOMIC_MOVE`, sem alternativa não atômica. Falhas normais limpam a pasta temporária; encerramento abrupto pode deixá-la no disco, mas ela nunca aparece nas consultas. Com o servidor parado, pastas `.tmp-*` podem ser removidas manualmente. Não há limpeza automática para evitar apagar uploads de outra instância.

A recuperação exige UUID, nome gerado permitido e pertencimento à lista do produto. Caminhos são confinados à raiz e links simbólicos são rejeitados. O diretório deve ser privado do usuário do processo, sem modificações simultâneas por outros programas. Esta versão é local e sem autenticação. Atomicidade de publicação não equivale a garantia contra perda por falha elétrica; mantenha backups. Sistemas de arquivos sem movimentação atômica retornam erro no cadastro. JSON corrompido gera erro explícito em vez de ocultar silenciosamente produtos.

## Conferência de aceitação

1. Cadastre três fotos; anote o UUID e os nomes retornados.
2. Confira `produto.json` e as três fotos em disco.
3. Encerre completamente o servidor com Ctrl+C.
4. Execute novamente o mesmo JAR com o mesmo diretório de dados.
5. Consulte o UUID e baixe as três fotos.
6. Compare os hashes dos originais e downloads com `Get-FileHash caminho -Algorithm SHA256`.

`mvn test` executa testes JUnit para API, campos inválidos, imagens falsas/truncadas/vazias, formatos não permitidos, JPEG/PNG/WebP, limites, nova instância de armazenamento, bytes originais, pastas interrompidas, falha de publicação e acesso indevido. O teste de links simbólicos é ignorado quando o Windows não concede o privilégio necessário. Veja `VALIDACAO.md` para resultados da entrega.

Para backup, pare o servidor e copie toda a pasta `dados`. Não há edição ou exclusão nesta etapa.
