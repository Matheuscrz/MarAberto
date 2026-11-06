# AGENTS.md — Memória de contexto do projeto

Este arquivo serve como memória para agentes de IA (assistentes de codificação) que
trabalham neste repositório. Mantenha-o atualizado com o estado do projeto.

## Descrição do projeto

**MarAberto** (repositório: Praias-Seguras) — plataforma open source de segurança balnear:
mapa de zonas de risco, bandeiramento em tempo real, tábua de marés, reportes comunitários
com quórum, estimativa anônima de ocupação e **SOS cidadão** para guarda-vidas.
Ferramenta de *apoio*; não substitui o acionamento oficial (ligar 193).

## Documentação principal (ler antes de trabalhar)

- `docs/Architecture.md` — arquitetura (monólito modular, DDD, Spring Modulith, Postgres+PostGIS, Redis, S3-compatível)
- `docs/Requirements.md` — requisitos funcionais e não funcionais (SOS: p95 < 3 s)
- `docs/Implementation.md` — stack e etapas de implementação
- `docs/adr/` — decisões arquiteturais (ADRs 001–009)

## Estado atual (2026-11-06 — Issue: esqueleto do monólito)

- Backend em `backend/` — Gradle 9.8.2, Java 21, Spring Boot 3.3.4
- Virtual Threads ativadas: `spring.threads.virtual.enabled=true`
- Configuração em `backend/src/main/resources/application.yml` (properties removido)
- Dependências: Spring Web, Data JPA, Bean Validation, Actuator, Lombok, springdoc-openapi (Swagger em `/swagger-ui/index.html`)
- H2 em memória para desenvolvimento; PostgreSQL/PostGIS em produção
- Estrutura DDD por domínio em `com.maraberto` (classe raiz `MarAbertoApplication`):
  - `beaches`, `safety`, `emergency`, `oceanography`, `education`, `shared`
  - Cada módulo segue o padrão: `api/`, `application/`, `domain/`, `infra/`
- `/actuator/health` respondendo UP

## Convenções

- Commits em português (pt-BR) com Conventional Commits (`feat:`, `chore:`, `fix:`, `docs:`).
- Não comentar código desnecessariamente; seguir estilo do código existente.
- Idioma do código/commits: pt-BR quando for texto exibido ao usuário; nomes de classes em inglês.

## Próximos passos conhecidos

- Spring Modulith + verificação `ApplicationModules.verify()`
- Migrações com Flyway (trocar H2 por PostgreSQL/PostGIS)
- Docker Compose (Nginx, Postgres/PostGIS, Redis, Garage)
- Pipeline CI (build, testes, Testcontainers, SAST/SCA)
