# Contribuindo

## Requisitos

- Java 21
- Maven 3.9+

## Fluxo de desenvolvimento

1. Crie uma branch a partir da principal.
2. Faça commits pequenos e objetivos.
3. Rode a validação local antes de abrir PR:

```powershell
mvn clean verify
```

4. Abra PR com contexto do problema, solução e impacto.

## Padrão de commits

Use Conventional Commits:

- `feat:`
- `fix:`
- `refactor:`
- `test:`
- `docs:`
- `chore:`
- `ci:`

Exemplo:

```text
feat: adicionar validação de formato para imagens webp
```

## Qualidade obrigatória no PR

- Build e testes passando
- Checkstyle sem violações
- Spotless sem divergência de formatação
- Cobertura mínima de linhas no bundle: 60%
