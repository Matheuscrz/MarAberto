# ADR-030 — Assinatura de Access Token JWT: HS256 na v1 vs. Assimétrico com `kid`

- **Status:** Aceita
- **Data:** 2026-11-25
- **Contexto:** `docs/Architecture.md` mencionava assinatura assimétrica com `kid` para access tokens JWT, enquanto a implementação do módulo `identity` (`JwtService`) utiliza HMAC-SHA256 (`HS256`) com segredo simétrico mínimo de 32 bytes (`JWT_SECRET`).

## Contexto e Análise

O MarAberto v1 é um **monólito modular** (Spring Modulith, ADR-011) com implantação single-tenant por instalação estadual (ADR-003). Existe apenas **um único serviço** (`maraberto-backend`) que simultaneamente **emite** e **verifica** os access tokens JWT. Não há API Gateway verificador de JWKS nem microsserviços independentes consumindo tokens sem acesso ao módulo `identity`.

As propriedades reais de segurança na v1 são garantidas por:
1. Segredo `JWT_SECRET` com mínimo obrigatório de 32 bytes (recusado o segredo de desenvolvimento no perfil `prod`).
2. TTL curto do access token (`PT15M`), mantido exclusivamente em memória no cliente.
3. Refresh token opaco com rotação e detecção de reuso em cookie `HttpOnly; Secure; SameSite=Strict`.
4. Revogação imediata por blacklist de `jti` e corte por usuário (`TokenRevocationService`, Redis com fallback em memória) + revalidação de `active` e `role` no `JwtAuthenticationFilter`.

Exigir par de chaves assimétricas (RSA/ECDSA/Ed25519) + rotação PEM + endpoint JWKS na v1 adicionaria complexidade operacional desnecessária para equipes de TI de Corpos de Bombeiros Militares estaduais que fazem deploy via Docker Compose.

## Decisão

1. **Na v1, mantemos `HS256` (`HMAC-SHA256`)** com validação estrita de comprimento mínimo (≥ 32 bytes) e recusa do valor padrão no perfil `prod`.
2. **Gatilho de migração para assimétrico (`EdDSA` ou `RS256` com `kid` e `/.well-known/jwks.json`):** somente quando o projeto introduzir um segundo serviço verificador independente (ex.: gateway de mídia/tiles dedicado ou integração externa que precise validar tokens sem compartilhar o segredo de assinatura).

## Consequências

- **Positivas:** deploy simples em qualquer CBM estadual (`openssl rand -base64 48` em `JWT_SECRET`), zero gestão de arquivos PEM ou JWKS na v1, mesma resistência criptográfica contra forja para emissor/verificador único.
- **Negativas:** todos os nós de `maraberto-backend` compartilham o mesmo `JWT_SECRET`; rotação do segredo invalida access tokens ativos (mitigado pela renovação silenciosa via refresh token).
