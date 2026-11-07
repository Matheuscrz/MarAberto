# AGENTS.md — Memória de contexto do projeto

Este arquivo serve como memória para agentes de IA (assistentes de codificação) que
trabalham neste repositório. Mantenha-o atualizado com o estado do projeto.

## Descrição do projeto

**MarAberto** (repositório: Praias-Seguras) — plataforma open source de segurança balnear:
mapa de zonas de risco, bandeiramento em tempo real, tábua de marés, reportes comunitários
com quórum, estimativa anônima de ocupação e **SOS cidadão** para guarda-vidas.
Ferramenta de *apoio*; não substitui o acionamento oficial (ligar 193).

## Panorama geral (2026-11-07)

- **Semana de início do projeto; 3 issues concluídas** (esqueleto do backend, ambiente local, CI)
- **Backend esqueleto funcional:** Spring Boot 3.3.4 (docs visam Boot 4.1.x — atualizar em issue futura), Java 21, Gradle 9.8.2, Virtual Threads, H2 em dev, Swagger, `/actuator/health` UP
- **Domínios em `com.maraberto`** (padrão `api/ application/ domain/ infra/` por módulo): `beaches`, `safety`, `emergency`, `oceanography`, `education`, `shared` — ainda vazios (só `package-info.java`); lógica de negócio zero até agora
- **Módulos potenciais do Architecture.md ainda não criados:** `identity`, `region`, `operations`, `tides` (≈ `oceanography`), `content` (≈ `education`), `occupancy`, `media`, `realtime`, `audit`, `analytics`
- **Stack de infra local (docker compose) validada:** Postgres 16 + PostGIS, Redis 7, RabbitMQ 3, Garage S3 (bucket `media-public`) — aplicação ainda não conecta a nenhum deles
- **CI no GitHub Actions** (`.github/workflows/ci.yml`: JDK 21 Temurin + cache Gradle, compilação e testes unitários); **sem frontend**; sem Flyway, sem Spring Modulith, sem Spring Security

## Documentação principal (ler antes de trabalhar)

- `docs/Architecture.md` — arquitetura (monólito modular, DDD, Spring Modulith, Postgres+PostGIS, Redis, S3-compatível)
- `docs/Requirements.md` — requisitos funcionais e não funcionais (SOS: p95 < 3 s)
- `docs/Implementation.md` — stack e etapas de implementação
- `docs/adr/` — decisões arquiteturais (ADRs 001–009). ADRs mais relevantes: 001 (monólito), 003 (sem broker obrigatório), 006 (Garage, não MinIO)

## Estado do backend

- `backend/src/main/java/com/maraberto/MarAbertoApplication.java` — classe raiz (component scan cobre todos os módulos)
- `application.yml` único em `backend/src/main/resources` (properties removido); datasource H2 jdbc:h2:mem:maraberto
- Teste de contexto: `MarAbertoApplicationTests.contextLoads`

## CI (GitHub Actions)

- `.github/workflows/ci.yml`, job `build` em `ubuntu-latest`: checkout, JDK 21 (Temurin), `gradle/actions/setup-gradle@v6` com `cache-provider: basic`, `./gradlew compileJava compileTestJava`, `./gradlew test` e upload dos relatórios de teste em caso de falha
- Gatilhos: `pull_request` e `push` para `main`; `defaults.run.working-directory: backend`
- Status check a exigir no ruleset de proteção da `main`: **`CI / build`**
- Ainda não cobre: Testcontainers (PostGIS/Redis/Garage), SAST/SCA, cobertura, lint

## Ambiente local (docker compose)

- `docker-compose.yml` na raiz; `cp .env.example .env` para credenciais (padrão de usuários/senhas: `maraberto`)
- **Garage no lugar de MinIO** (ADR-006): imagem MinIO não é mais pública; S3 em `localhost:9000`, admin em `localhost:9001`; config em `deploy/garage/garage.toml`
- Volumes nomeados: `maraberto_postgres_data`, `maraberto_redis_data`, `maraberto_garage_data`, `maraberto_rabbitmq_data`
- Swagger: `backend` em `/swagger-ui/index.html` (com a app rodando)

## Convenções

- Commits em português (pt-BR) com Conventional Commits (`feat:`, `chore:`, `fix:`, `docs:`).
- Não comentar código desnecessariamente; seguir estilo do código existente.
- Idioma do código/commits: pt-BR quando for texto exibido ao usuário; nomes de classes em inglês.

## Roadmap conhecido (próximas entregas plausíveis)

- Spring Modulith + teste `ApplicationModules.verify()`
- Flyway + conectar backend ao Postgres do Compose (matar H2)
- `identity`: autenticação Jwt + refresh rotation + TOTP (seção 13.1 do Architecture.md)
- `emergency` (SOS): Outbox, ack com compare-and-set, escalonamento
- Frontends: PWA banhista + console operacional (pnpm workspace em `frontend/`)
- Pipeline CI: base pronta (build + testes unitários); evoluir com Testcontainers (PostGIS/Redis/Garage), SAST/SCA e cobertura

---
Última atualização: 2026-11-07, após execução das 3 primeiras issues.
