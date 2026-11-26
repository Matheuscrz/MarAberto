# Arquitetura do Backend (`backend/`) — MarAberto

Este documento descreve em detalhes a arquitetura, os módulos, o modelo de segurança, a persistência e os pontos de customização do backend do **MarAberto**, pensado para ser adotado e adaptado (via *fork* ou configuração) por **Corpos de Bombeiros Militares (CBMs)** de qualquer estado brasileiro.

> **Princípio fundamental:** zero dado regional fixo no código. Toda geografia (praias, rios, barragens, lagoas e setores) é importada via KML/QGIS pela Coordenação na própria interface web.

---

## 1. Stack Técnica

| Camada | Tecnologia | Observações |
| :--- | :--- | :--- |
| Linguagem & Runtime | **Java 21** + **Virtual Threads** (`spring.threads.virtual.enabled=true`) | Evitar `synchronized` em trechos de I/O; usar `ReentrantLock`. |
| Framework | **Spring Boot 3.3.4** + **Spring Modulith 1.2.13** | Fronteiras modulares verificadas em tempo de teste (`ModularityTests`). |
| Banco de Dados | **PostgreSQL 16 + PostGIS 3.4** | Distâncias sempre em metros via `geography`; paginação por cursor; `FOR UPDATE SKIP LOCKED` em filas/jobs. |
| Migrações | **Flyway** (`ddl-auto: validate`) | `V1` a `V28` em `src/main/resources/db/migration/`. |
| Cache & Rate Limit | **Redis 7** (opcional no boot) | Nunca fonte da verdade. Fallback degradado em memória para blacklist JWT e rate limit quando Redis não está disponível. |
| Mensageria | **RabbitMQ 3** (perfil opcional `rabbit`) | Publisher confirms, retry com TTL e DLQ para entrega resiliente de alertas. |
| Armazenamento de Objetos | **S3-compatível (Garage v2)** | Armazena fotos de sinalizações/postos e arquivos KML originais importados (chave por SHA-256). |
| Notificações Push | **Web Push (VAPID)** + **FCM HTTP v1** | Acorda operadores com o PWA/app fechado em chamados de SOS. |
| Observabilidade | **Micrometer / SLF4J MDC (`traceId`)** + **Spring Actuator** | `/actuator/health` e `/actuator/info` públicos; demais `/actuator/**` restritos a `ADMIN`. |

---

## 2. Organização Modular (Spring Modulith)

Todo o código reside em `com.maraberto.<modulo>`. Módulos só podem importar tipos dos pacotes `api/` públicos de outros módulos (ADR-011).

| Módulo | Responsabilidade |
| :--- | :--- |
| `identity` | Autenticação (e-mail, CPF ou matrícula), JWT HS256 (`JwtService`), refresh token opaco com rotação e detecção de reuso (`auth_sessions`), bloqueio progressivo contra força bruta (`V21`), revogação imediata de JWT (`TokenRevocationService`), MFA opcional via TOTP RFC 6238 + códigos de recuperação (`V28`), CRUD administrativo de usuários com senha temporária (`AdminUserService`) e bootstrap do `admin` inicial (`AdminBootstrapRunner`). |
| `region` | Metadados regionais configuráveis da instalação (UF, instituição, contatos de emergência). |
| `beaches` | Áreas de banho (`BEACH`, `RIVER`, `DAM`, `LAGOON`), setores (`beach_sectors`), importação assistida de KML/QGIS (ADR-016, ADR-021) e busca tolerante a erros (`pg_trgm`, ADR-014). |
| `operations` | Postos fixos e bases móveis autônomas (`lifeguard_stations`, ADR-028), plantões com lease renovável (`operator_shifts`, expiração preguiçosa + sweep), sinalizações/bandeiras vigentes com validade configurável (ADR-022/023), alertas de sinalização fora do mapeamento (`unmapped_signals`, ADR-029) e visão gerencial da equipe (`GET /api/v1/management/team`). |
| `emergency` | Caminho crítico do SOS anônimo (ADR-001, ADR-017, ADR-018, ADR-020): criação idempotente, geoindexação, seleção de destinatários por raio/escalonamento em anéis, **Transactional Outbox** (`emergency_outbox`) com `FOR UPDATE SKIP LOCKED` e encerramento pelo operador. |
| `occurrences` | Registro e ciclo de vida de ocorrências operacionais (geradas automaticamente por SOS ou registro manual 193/rádio/presencial), paginação por cursor (`occurred_at DESC, number DESC`), filtros no servidor, dados cifrados de vítima (`V20`) e reconciliação de segurança. |
| `safety` | Zonas de risco temporais associadas às áreas de banho (`V2`, ADR-010). |
| `content` | CMS educativo da Coordenação e API pública `/api/v1/content` para a aba Aprender (vídeos YouTube e materiais de divulgação, `V26`). |
| `occupancy` | Coleta anônima consentida de amostras de ocupação das praias (`V14`), agregação com k-anonimato (`k >= 5`) e expurgo automático sem histórico individual. |
| `media` | Upload validado (magic bytes, tamanho máximo, sanitização) e leitura pública controlada de imagens no storage S3/Garage. |
| `realtime` | WebSocket/STOMP autorizado por papel no frame `SUBSCRIBE`, publicação de eventos de SOS, atualização dinâmica do mapa público (bandeiras/postos), perfil `rabbit` e envio Web Push (VAPID) / FCM (`V27`). |
| `audit` | Trilha de auditoria append-only (`audit_log`, `V25`) com encadeamento criptográfico para verificação de integridade, consultável por `traceId`, ator, ação e período. |
| `analytics` | Agregações operacionais sem PII. |
| `tides` | Módulo reservado para R2 (inativo na v1). |
| `shared` | Criptografia (`FieldEncryptor`, `BlindIndexer`, `EncryptedStringConverter`, `SensitiveDataProtector`), sanitização (`@Sanitized`), validação de CPF, RFC 9457 (`ApiProblem`, `ApiProblemWriter`) e headers de segurança. |

---

## 3. Segurança, LGPD e Criptografia (ADR-009, ADR-012, ADR-027, ADR-030)

1. **Cifragem autenticada em repouso (AES-256-GCM):**
   - `FieldEncryptor` usa IV aleatório de 12 bytes, tag de 128 bits e formato `enc:v1:<keyId>:<base64url>`.
   - `OperationalUserFieldConverters` aplica **AAD específico por coluna** (`maraberto:operational-users:name:v1`, `email:v1`, `cpf:v1`, `totp-secret:v1`), impedindo troca silenciosa de ciphertexts entre colunas no banco, mantendo compatibilidade de leitura com registros legados.
   - Campos pesquisáveis cifrados (e-mail, CPF) usam `BlindIndexer` (HMAC-SHA256 com chave derivada) — nunca `LIKE` em PII.
2. **Sanitização de entrada:** todo campo texto em DTOs de request recebe `@Sanitized`.
3. **Autenticação e Sessão:**
   - **Access token:** JWT `HS256` (`PT15M`, ADR-030), armazenado apenas em memória no cliente.
   - **Refresh token:** opaco, rotacionado a cada uso com janela de tolerância de 10 s e revogação da família inteira em caso de reuso; transportado em cookie `HttpOnly; Secure; SameSite=Strict` (`Path=/api/v1/auth`).
   - **Revogação imediata e revalidação:** `JwtAuthenticationFilter` consulta `TokenRevocationService` (blacklist de `jti` no logout + corte `revokeAllForUser` ao desativar, trocar papel ou redefinir senha) **e** revalida `active` + `role` no banco.
   - **MFA opcional (TOTP RFC 6238):** `COORDINATOR` e `ADMIN` podem ativar ou desativar voluntariamente em `/api/v1/auth/mfa/{setup,confirm,disable}`, com 8 códigos de recuperação de uso único (armazenados apenas como hash SHA-256).
   - **Bootstrap do Administrador Inicial:** `AdminBootstrapRunner` cria `admin` (senha padrão `admin`, configurável via `ADMIN_BOOTSTRAP_*`) com `must_change_password = true` apenas se não existir nenhum `ADMIN` ativo. Até trocar a senha, todas as rotas de negócio respondem `403 password-change-required`.
4. **Travas do perfil `prod` (`SPRING_PROFILES_ACTIVE=prod`):**
   - Recusa `CRYPTO_MASTER_KEY` padrão, `JWT_SECRET` padrão, `STORAGE_PROVIDER=stub`, `SECURITY_COOKIE_SECURE=false`, origens HTTP em `SECURITY_ALLOWED_ORIGINS`, credenciais padrão de PostgreSQL/Garage/RabbitMQ/Admin (`maraberto`, `maraberto-secret-key`, `admin`) e qualquer `*_DEV_SEED=true`.
   - Desliga Swagger/OpenAPI automaticamente (`application-prod.yml`).

---

## 4. Consistência, Concorrência e Performance

- **Zero Dual Write:** eventos críticos de SOS são persistidos na tabela `emergency_outbox` na mesma transação do chamado; a publicação ocorre em `AFTER_COMMIT` com sweeper de recuperação (`FOR UPDATE SKIP LOCKED`, `claim_lease=PT30S`).
- **Idempotência:** garantida por constraints `UNIQUE` no PostgreSQL (`client_request_id`, `emergency_call_id`, índices cegos únicos).
- **Virtual Threads:** habilitadas para alta concorrência de I/O (HTTP, JDBC, S3, Web Push).
- **Consultas espaciais:** índices GiST em colunas `geography` e `VIEW`s operacionais para manter latência previsível conforme o volume histórico cresce.

---

## 5. Como Fazer Fork e Implantar em Outro CBM Estadual

1. **Clonar/forkar** o repositório e copiar `.env.example` para `.env`.
2. **Gerar segredos únicos** para a instalação:
   ```bash
   openssl rand -base64 32  # CRYPTO_MASTER_KEY
   openssl rand -base64 48  # JWT_SECRET
   ```
3. **Configurar variáveis de ambiente** no `.env`:
   - `SPRING_PROFILES_ACTIVE=prod` (em produção sob HTTPS)
   - `SECURITY_COOKIE_SECURE=true` e `SECURITY_ALLOWED_ORIGINS=https://seu-dominio.cbm.uf.gov.br,https://localhost,capacitor://localhost`
   - `REALTIME_ALLOWED_ORIGINS=https://seu-dominio.cbm.uf.gov.br`
   - Credenciais próprias de PostgreSQL, Garage (S3) e RabbitMQ (opcional, com `SPRING_PROFILES_ACTIVE=prod,rabbit`).
   - Chaves VAPID (`PUSH_VAPID_*`) para Web Push no PWA.
4. **Subir a stack:** em desenvolvimento use `docker compose up -d --build`; em produção use `docker compose -f docker-compose.prod.yml up -d --build` (sem defaults de segredos e com `SPRING_PROFILES_ACTIVE=prod`).
5. **Primeiro acesso:** entrar em `/login` com `admin` / `admin`, definir a senha definitiva, importar o KML das áreas de banho do estado em `/gestao/importar` e cadastrar a equipe em `/admin/usuarios`.

---

## 6. Testes e Qualidade

```bash
cd backend
./gradlew clean check
```

A suíte executa:
- Testes unitários de domínio, criptografia, TOTP e revogação.
- `ModularityTests` (verificação automática das fronteiras do Spring Modulith).
- Testes de integração reais contra PostgreSQL/PostGIS e RabbitMQ via **Testcontainers**.
