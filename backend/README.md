# MarAberto — Backend (`backend/`)

API REST e WebSocket/STOMP da plataforma open source **MarAberto**, construída em **Java 21**, **Spring Boot 3.3.4** e **Spring Modulith 1.2.13**, com **PostgreSQL 16 + PostGIS 3.4**.

> Para detalhes profundos de decisões arquiteturais, criptografia, consistência e fork por estado, consulte [ARCHITECTURE.md](./ARCHITECTURE.md) e `../docs/adr/`.

---

## 1. Requisitos

- **JDK 21** (Temurin ou equivalente)
- **Docker** (para PostgreSQL/PostGIS, Redis, Garage S3 e Testcontainers nos testes de integração)

---

## 2. Como rodar localmente

Na raiz do repositório, suba a infraestrutura de apoio (PostgreSQL/PostGIS, Redis, Garage S3 e opcionalmente RabbitMQ):

```bash
cp .env.example .env
docker compose up -d postgres redis garage
```

Em seguida, rode o backend em modo desenvolvimento:

```bash
cd backend
./gradlew bootRun
```

Para subir com contas e postos de demonstração (apenas desenvolvimento local — recusado no perfil `prod`):

```bash
IDENTITY_DEV_SEED=true OPERATIONS_DEV_SEED=true ./gradlew bootRun
```

| Recurso | URL local |
| :--- | :--- |
| API Base | `http://localhost:8080/api/v1` |
| Swagger UI (desligado em `prod`) | `http://localhost:8080/swagger-ui.html` |
| Health Check | `http://localhost:8080/actuator/health` |
| WebSocket STOMP | `ws://localhost:8080/ws` |

---

## 3. Estrutura do Código

O projeto é um **monólito modular** organizado por domínio de negócio (`com.maraberto.<modulo>`). Cada módulo segue a mesma estrutura interna para facilitar a navegação de novos contribuidores:

```text
backend/
├── Dockerfile                         # Imagem multi-stage enxuta (usuário não-root + HEALTHCHECK)
├── build.gradle                       # Dependências, Spring Modulith, Testcontainers, Checkstyle/Test
├── ARCHITECTURE.md                    # Arquitetura detalhada, segurança e guia de fork estadual
└── src/
    ├── main/
    │   ├── java/com/maraberto/
    │   │   ├── identity/              # Login, JWT HS256, refresh opaco, MFA TOTP, usuários e user_beach_scopes
    │   │   ├── beaches/               # Áreas de banho (BEACH/RIVER/DAM/LAGOON), setores e importador KML/QGIS
    │   │   ├── operations/            # Postos fixos, bases móveis, plantões (lease), bandeiras, triagem e equipe
    │   │   ├── emergency/             # SOS anônimo idempotente, despacho espacial, escalonamento e outbox
    │   │   ├── occurrences/           # Ocorrências (SOS e manuais 193/rádio/campo), CIOSP e dados de vítima cifrados
    │   │   ├── content/               # CMS Aprender (gestão + leitura pública com cache invalidável)
    │   │   ├── occupancy/             # Amostras anônimas de ocupação com k-anonimato (k >= 5)
    │   │   ├── media/                 # Upload com re-encoding (strip EXIF) e leitura no storage S3/Garage
    │   │   ├── realtime/              # WebSocket/STOMP, perfil RabbitMQ opcional e Web Push (VAPID) / FCM
    │   │   ├── audit/                 # Trilha de auditoria append-only com verificação de cadeia de hashes
    │   │   ├── safety/                # Zonas de risco permanentes e semipermanentes (GeoJSON)
    │   │   ├── region/                # Metadados configuráveis da instalação estadual
    │   │   ├── analytics/             # Agregações operacionais sem PII
    │   │   ├── tides/                 # Reservado para R2 (inativo na v1)
    │   │   └── shared/                # Criptografia AES-256-GCM/HMAC, @Sanitized, RFC 9457, headers e traceId
    │   └── resources/
    │       ├── application.yml        # Configuração base (desenvolvimento)
    │       ├── application-prod.yml   # Endurecimento de produção (desliga Swagger, exige HTTPS/segredos)
    │       ├── application-rabbit.yml # Perfil opcional RabbitMQ (publisher confirms + DLQ)
    │       └── db/migration/          # Migrações Flyway imutáveis (V1..V29; próxima: V30)
    └── test/java/com/maraberto/
        ├── ModularityTests.java       # Valida fronteiras entre módulos Spring Modulith
        ├── security/                  # Testes de integração de isolamento, RBAC, IDOR e hardening
        ├── support/                   # Base Testcontainers (PostgisIntegrationTest) e helpers de auth
        └── <modulo>/                  # Testes unitários e de integração por módulo
```

### Anatomia padrão de um módulo

Dentro de `com.maraberto.<modulo>`, os pacotes seguem responsabilidades claras:

| Pacote | Papel | Visibilidade entre módulos |
| :--- | :--- | :--- |
| `api/` | Controllers REST, DTOs de request/response, portas públicas e eventos de domínio publicados | **Público** (único pacote importável por outros módulos) |
| `application/` | Casos de uso (`@Service`), orquestração transacional e regras de negócio | Interno ao módulo |
| `domain/` | Entidades, enums, value objects e exceções de domínio | Interno ao módulo |
| `infra/` | Repositórios JDBC/JPA, clientes externos (S3, RabbitMQ, Push) e filtros/configs | Interno ao módulo |

---

## 4. Comandos de Verificação

Antes de abrir um Pull Request, execute a suíte completa (compilação, testes unitários, `ModularityTests` e testes de integração com Testcontainers):

```bash
cd backend
./gradlew clean check
```

Para rodar uma classe de teste específica durante o desenvolvimento:

```bash
./gradlew test --tests "com.maraberto.security.SecurityAuditFixesIntegrationTests"
```

---

## 5. Guia Rápido para Contribuir

### Onde mexer para cada tipo de tarefa
- **Novo endpoint REST:** crie/edite o controller em `<modulo>/api/`, adicione a regra de autorização em `identity/infra/security/SecurityConfig.java` (e `@PreAuthorize` quando aplicável) e valide escopo por operador (`user_id`/`responder_id`) ou territorial (`user_beach_scopes`) na camada `application/`/`infra/`.
- **Nova tabela ou coluna:** crie uma nova migration versionada em `src/main/resources/db/migration/V<N>__descricao.sql` (nunca altere migrations já aplicadas; Hibernate roda com `ddl-auto: validate`).
- **Comunicação entre módulos:** exponha apenas interfaces/DTOs/eventos no pacote `<modulo>/api/` e verifique com `ModularityTests`.

### Regras obrigatórias de engenharia e segurança
1. **Sanitização:** todo campo `String` de DTO de request deve levar `@Sanitized` (`shared.security.sanitization`).
2. **Isolamento e escopo:** toda consulta/mutação operacional deve respeitar RBAC, posse por `user_id`/`responder_id` e `user_beach_scopes` (para `COORDINATOR` regional).
3. **LGPD e PII:** nunca registre CPF, dados de vítima, tokens ou coordenadas de operador em logs, outbox ou métricas; cifre PII com `SensitiveDataProtector` / `FieldEncryptor`.
4. **Zero dual write:** eventos críticos assíncronos usam Transactional Outbox (`AFTER_COMMIT` + sweeper com `FOR UPDATE SKIP LOCKED`).
5. **Virtual Threads:** evite `synchronized` em trechos com I/O; prefira `ReentrantLock`.
6. **Erros padronizados:** todas as falhas da API devem retornar RFC 9457 (`application/problem+json`).
7. **Idioma:** classes, métodos, variáveis e comentários técnicos em inglês ou pt-BR consistente com o módulo; mensagens de erro de API e commits (Conventional Commits) em **pt-BR**.

---

## 6. Perfis e Variáveis Críticas em Produção

Com `SPRING_PROFILES_ACTIVE=prod`, o backend **recusa subir** (`IllegalStateException`) se detectar:
- `CRYPTO_MASTER_KEY` ou `JWT_SECRET` padrão de desenvolvimento;
- `DB_PASSWORD`, `RABBITMQ_PASSWORD`, `STORAGE_S3_*` ou `ADMIN_BOOTSTRAP_PASSWORD` padrão;
- `REDIS_PASSWORD` vazio;
- `SECURITY_COOKIE_SECURE=false` ou origens HTTP em `SECURITY_ALLOWED_ORIGINS`;
- qualquer `*_DEV_SEED=true`.

Em produção, use `docker-compose.prod.yml` (que injeta segredos obrigatórios inclusive para Redis e Garage via `deploy/garage/garage.prod.toml`).
