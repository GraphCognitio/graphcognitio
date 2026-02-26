# Graphcognitio Backend MVP

MVP de backend para uma rede social estilo "Twitter", com conversas modeladas como grafo:
- `posts` = nós
- `edges` do tipo `REPLY` = arestas (`reply -> pai`)

## Stack

- Java 17
- Spring Boot 3.5.11
- Maven
- PostgreSQL (`postgres:18`)
- Spring Data JPA
- Flyway
- Spring Security + JWT (JJWT 0.13.0)
- Lombok
- MapStruct 1.6.3
- springdoc-openapi 2.8.15
- Testcontainers
- Docker Compose

## Regras de produto implementadas

- Post raiz:
  - `parent_id = null`
  - `root_id = id`
- Reply:
  - `parent_id = post respondido`
  - `root_id = root do pai` (ou `pai.id` quando o pai é raiz)
- `content` com máximo de 500 caracteres
- `reply_count` incrementado no post alvo ao criar reply
- `edges` registra aresta `REPLY` com:
  - `source_post_id = reply.id`
  - `target_post_id = parent.id`

## Modelo de dados

Migração: `src/main/resources/db/migration/V1__init_schema.sql`

Tabelas:
- `users`
- `posts`
- `edges`

Inclui constraints de integridade (FKs, check de tipo de edge, check de tamanho de conteúdo, `reply_count >= 0`) e índices para feed/lookup de replies.

## Segurança

JWT obrigatório em todos os endpoints, exceto:
- `POST /auth/register`
- `POST /auth/login`
- `/v3/api-docs/**`
- `/swagger-ui/**`
- `/swagger-ui.html`

Header esperado:
- `Authorization: Bearer <accessToken>`

## Variáveis de ambiente

No `application.yml`:

```yaml
app:
  jwt:
    secret: ${APP_JWT_SECRET}
    exp-minutes: ${APP_JWT_EXP_MINUTES:60}
```

- `APP_JWT_SECRET` é obrigatório (sem default).
- `APP_JWT_EXP_MINUTES` é opcional (default 60).

Valor oficial para desenvolvimento/teste:

```bash
APP_JWT_SECRET=graphcognitio-dev-secret-key-256bits-minimum-length
```

Importante para produção:
- usar secret aleatório real com 256 bits
- armazenar em variável de ambiente segura

## Rodando localmente

1. Subir PostgreSQL:

```bash
docker compose up -d postgres
```

`docker-compose.yml` usa:
- `POSTGRES_DB=graphcognitio`
- `POSTGRES_USER=graphcognitio`
- `POSTGRES_PASSWORD=graphcognitio_dev_password`
- porta `5432:5432`

2. Exportar secret JWT:

```bash
export APP_JWT_SECRET=graphcognitio-dev-secret-key-256bits-minimum-length
```

3. Subir aplicação:

```bash
./mvnw spring-boot:run
```

4. Swagger/OpenAPI:
- `/swagger-ui/index.html`
- `/v3/api-docs`

## Endpoints

Base URL: use o host/porta onde a aplicação estiver executando.

### Auth

#### `POST /auth/register`
Request:

```json
{
  "name": "Eduardo",
  "email": "eduardo@example.com",
  "password": "StrongPassword123"
}
```

Response `201 Created`:

```json
{
  "accessToken": "<jwt>",
  "user": {
    "id": "uuid",
    "name": "Eduardo",
    "email": "eduardo@example.com",
    "createdAt": "2026-02-26T21:10:00Z"
  }
}
```

#### `POST /auth/login`
Request:

```json
{
  "email": "eduardo@example.com",
  "password": "StrongPassword123"
}
```

Response `200 OK` com o mesmo formato de `AuthResponse`.

### Posts

#### `POST /posts`
Cria post raiz. `authorId` vem do JWT autenticado (não existe no body).

Request:

```json
{
  "content": "Meu post raiz"
}
```

Response:
- `201 Created`
- `Location: /posts/{novoId}`
- body `PostResponse`:

```json
{
  "id": "uuid",
  "authorId": "uuid",
  "authorName": "Eduardo",
  "content": "Meu post raiz",
  "createdAt": "2026-02-26T21:10:00Z",
  "parentId": null,
  "rootId": "uuid",
  "replyCount": 0
}
```

#### `POST /posts/{id}/reply`
Request:

```json
{
  "content": "Minha resposta"
}
```

Response:
- `201 Created`
- `Location: /posts/{novoId}`
- body `PostResponse`

Regras:
- pai inexistente -> `404 Not Found`
- UUID inválido -> `400 Bad Request`
- tudo em uma única transação (post reply + edge + incremento de `reply_count`)

#### `GET /posts/{id}`
Retorna detalhe de um post no formato `PostResponse`.

### Feed

#### `GET /feed?cursor=...&limit=20`

- retorna somente posts raiz (`parent_id IS NULL`)
- ordenação estável: `created_at DESC, id DESC`
- cursor:
  - formato: `Base64 URL-safe(createdAt|id)`
  - timestamp em ISO-8601 UTC (ex.: `2026-02-26T21:10:00Z`)
- limites:
  - `limit` opcional
  - default `20`
  - min `1`
  - max `50` (acima disso -> `400`)

Response:

```json
{
  "items": [
    {
      "id": "uuid",
      "authorId": "uuid",
      "authorName": "Eduardo",
      "content": "Meu post raiz",
      "createdAt": "2026-02-26T21:10:00Z",
      "parentId": null,
      "rootId": "uuid",
      "replyCount": 1
    }
  ],
  "nextCursor": "..."
}
```

Quando não houver mais resultados: `"nextCursor": null`.

### Graph

#### `GET /graphs/conversation/{rootId}?depth=2&limit=120`

- BFS por camadas
- busca filhos em lote com `WHERE parent_id IN (...)` (sem N+1)
- para quando:
  - `depth` acabar
  - ou `limit` total de nós for atingido (inclui root)
- `depth=0` retorna só o root
- `rootId` deve referenciar post raiz

Response:

```json
{
  "nodes": [
    {
      "id": "uuid",
      "authorId": "uuid",
      "authorName": "Eduardo",
      "contentPreview": "texto truncado para no máximo 140 caracteres",
      "createdAt": "2026-02-26T21:10:00Z",
      "parentId": null,
      "rootId": "uuid",
      "replyCount": 1
    }
  ],
  "edges": [
    {
      "id": "uuid",
      "sourcePostId": "uuid",
      "targetPostId": "uuid",
      "type": "REPLY",
      "createdAt": "2026-02-26T21:11:00Z"
    }
  ]
}
```

## Erros

A API retorna `ProblemDetail` (`application/problem+json`, RFC 7807).

Exemplo:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/feed",
  "errors": {
    "limit": "must be less than or equal to 50"
  }
}
```

## Testes

Executar:

```bash
./mvnw clean test
```

Inclui:
- testes unitários e WebMvc
- teste de integração com Testcontainers (`MvpFlowIntegrationTest`) cobrindo fluxo completo:
  - register
  - login
  - criar post
  - reply
  - feed
  - graph

Observação: Testcontainers requer Docker em execução.
