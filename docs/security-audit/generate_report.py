#!/usr/bin/env python3
"""
Gerador do Relatório de Auditoria de Segurança em PDF — MarAberto (Praias-Seguras).

Usa apenas a biblioteca padrão do Python para gerar HTML + gráficos SVG vetoriais
e invoca o Chromium headless local para renderizar o PDF A4 com margens de 2 cm,
cabeçalho e rodapé numerado.

Uso:
    python3 docs/security-audit/generate_report.py
"""

from __future__ import annotations

import html
import math
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile

OUT_DIR = pathlib.Path(__file__).resolve().parent
HTML_PATH = OUT_DIR / "relatorio-auditoria-seguranca.html"
PDF_PATH = OUT_DIR / "relatorio-auditoria-seguranca.pdf"

PALETTE = {
    "CRITICA": "#B91C1C",
    "ALTA": "#EA580C",
    "MEDIA": "#D97706",
    "BAIXA": "#2563EB",
    "INFORMATIVA": "#475569",
    "FORTE": "#059669",
}

SEVERITY_LABELS = {
    "CRITICA": "Crítica",
    "ALTA": "Alta",
    "MEDIA": "Média",
    "BAIXA": "Baixa",
    "INFORMATIVA": "Informativa",
}

CAT_ISOLAMENTO = "1. Banco sem Tranca (Isolamento)"
CAT_PERMISSAO = "2. Permissão no Navegador / Auth"
CAT_IDOR = "3. IDOR"
CAT_CHAVES = "4. Chaves Expostas (Hardcode)"
CAT_INPUTS = "5. Inputs sem Tratamento (XSS/SSRF)"

FINDINGS = [
    {
        "id": "F-01",
        "category": CAT_IDOR,
        "severity": "ALTA",
        "location": "backend/src/main/java/com/maraberto/operations/api/OperationStationController.java:158-179\nbackend/src/main/java/com/maraberto/operations/application/StationService.java:279-305",
        "title": "Reposicionamento de qualquer posto ou base móvel sem verificação de posse/plantão",
        "description": (
            "O endpoint PATCH /api/v1/operations/stations/{kind}/{stationId}/position está liberado para "
            "LIFEGUARD, COORDINATOR e ADMIN em SecurityConfig.java:94-97. Diferente de updateStatus() "
            "(linhas 143-156), que valida ownedByOpenShift(), o método move() nem sequer recebe "
            "Authentication e delega diretamente para stationService.moveStation(kind, stationId, request)."
        ),
        "snippet": (
            "@PatchMapping(\"/{kind}/{stationId}/position\")\n"
            "public StationAdminView move(\n"
            "        @PathVariable StationKind kind,\n"
            "        @PathVariable UUID stationId,\n"
            "        @Valid @RequestBody StationPositionRequest request) {\n"
            "    return stationService.moveStation(kind, stationId, request);\n"
            "}"
        ),
        "exploitability": (
            "Qualquer guarda-vidas autenticado (LIFEGUARD) pode alterar as coordenadas geográficas de "
            "qualquer posto fixo ou base móvel da instalação — mesmo sem plantão aberto. Como a posição "
            "da estrutura define a seleção espacial do despacho de SOS, a falha permite desviar ou sabotar "
            "alertas de emergência em outras praias."
        ),
    },
    {
        "id": "F-02",
        "category": CAT_PERMISSAO,
        "severity": "ALTA",
        "location": "backend/src/main/java/com/maraberto/identity/infra/security/JwtAccessTokenVerifier.java:25-39\nbackend/src/main/java/com/maraberto/realtime/infra/StompAuthenticationInterceptor.java:72-81",
        "title": "WebSocket STOMP ignora blacklist JWT, status active e troca obrigatória de senha",
        "description": (
            "O filtro HTTP (JwtAuthenticationFilter) valida revogação imediata (TokenRevocationService), "
            "revalida active/role no banco e bloqueia tokens com mustChangePassword=true. Porém, "
            "JwtAccessTokenVerifier (usado pelo StompAuthenticationInterceptor no frame CONNECT de /ws) "
            "verifica apenas a assinatura e expiração do JWT."
        ),
        "snippet": (
            "Claims claims = jwtService.validateAndExtractClaims(token);\n"
            "UUID userId = UUID.fromString(claims.getSubject());\n"
            "Role role = Role.valueOf(claims.get(\"role\", String.class));\n"
            "boolean mustChangePassword = Boolean.TRUE.equals(\n"
            "        claims.get(\"mustChangePassword\", Boolean.class));\n"
            "return Optional.of(new AccessTokenClaims(userId, role.name(), mustChangePassword));"
        ),
        "exploitability": (
            "Um operador desativado, deslogado ou com senha temporária pendente (que o frontend bloqueia "
            "em /trocar-senha) ainda consegue abrir conexão WebSocket em /ws com o access token vigente "
            "por até 15 minutos e assinar /user/queue/sos ou /topic/operational/emergency-calls."
        ),
    },
    {
        "id": "F-03",
        "category": CAT_CHAVES,
        "severity": "ALTA",
        "location": "docker-compose.yml:14,21-22,28,30,39,75,111-112,126-127\ndocker-compose.tunnel.yml:17-18",
        "title": "Defaults públicos de segredos no Docker Compose e ausência de trava de startup para DB/Rabbit/Admin",
        "description": (
            "docker-compose.yml embute valores padrão públicos via ${VAR:-default} para CRYPTO_MASTER_KEY, "
            "JWT_SECRET, POSTGRES_PASSWORD (maraberto), GARAGE_SECRET_KEY (maraberto-secret-key), "
            "RABBITMQ_DEFAULT_PASS (maraberto) e ADMIN_BOOTSTRAP_PASSWORD (admin), sem definir "
            "SPRING_PROFILES_ACTIVE=prod. Além disso, mesmo com prod ativo, não há validação de startup "
            "que rejeite POSTGRES_PASSWORD=maraberto, RABBITMQ_PASSWORD=maraberto ou ADMIN_BOOTSTRAP_PASSWORD=admin."
        ),
        "snippet": (
            "CRYPTO_MASTER_KEY: ${CRYPTO_MASTER_KEY:-ZGV2LW9ubHktbWFyYWJlcnRvLWtleS0zMmJ5dGVzISE=}\n"
            "JWT_SECRET: ${JWT_SECRET:-dev-only-maraberto-jwt-secret-32bytes!!}\n"
            "ADMIN_BOOTSTRAP_PASSWORD: ${ADMIN_BOOTSTRAP_PASSWORD:-admin}\n"
            "POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-maraberto}"
        ),
        "exploitability": (
            "Condição: subir o docker-compose.yml diretamente em ambiente compartilhado/produção sem "
            "ativar SPRING_PROFILES_ACTIVE=prod e sem sobrescrever .env. Nesse cenário, qualquer pessoa "
            "que conheça o repositório público pode forjar JWTs de ADMIN, decifrar PII/CPF e acessar "
            "PostgreSQL/Garage/RabbitMQ com credenciais triviais."
        ),
    },
    {
        "id": "F-04",
        "category": CAT_IDOR,
        "severity": "MEDIA",
        "location": "backend/src/main/java/com/maraberto/operations/api/FlagController.java:69-88\nbackend/src/main/java/com/maraberto/operations/application/FlagService.java:136-153",
        "title": "Encerramento de qualquer sinalização (bandeira) por ID sem validação de escopo",
        "description": (
            "POST /api/v1/operations/flags/{flagId}/close permite LIFEGUARD, COORDINATOR e ADMIN "
            "(SecurityConfig.java:94). Em FlagService.close(userId, flagId), o serviço verifica apenas se "
            "a bandeira existe e está aberta, encerrando-a sem conferir se o guarda-vidas lançou o registro "
            "ou está em plantão naquela área de banho."
        ),
        "snippet": (
            "public FlagResponse close(UUID userId, UUID flagId) {\n"
            "    FlagResponse current = flags.findById(flagId)\n"
            "            .filter(flag -> flag.closedAt() == null)\n"
            "            .orElseThrow(FlagNotFoundException::new);\n"
            "    flags.close(flagId, userId, now);\n"
            "    ..."
        ),
        "exploitability": (
            "Qualquer guarda-vidas autenticado pode consultar GET /api/v1/flags (público), obter o UUID "
            "de bandeiras de perigo (vermelha/roxa) de outras praias e encerrá-las prematuramente, "
            "removendo avisos críticos do mapa dos banhistas."
        ),
    },
    {
        "id": "F-05",
        "category": CAT_INPUTS,
        "severity": "MEDIA",
        "location": "backend/src/main/java/com/maraberto/realtime/api/PushSubscriptionController.java:62-75\nbackend/src/main/java/com/maraberto/realtime/infra/WebPushSender.java:44-49",
        "title": "Endpoint Web Push aceito sem validação de esquema HTTPS ou bloqueio de rede interna (SSRF)",
        "description": (
            "POST /api/v1/push/subscriptions valida apenas que request.endpoint() não está em branco "
            "e o armazena diretamente. Quando VAPID está configurado e ocorre um SOS, WebPushSender.send() "
            "dispara uma requisição HTTP de saída diretamente para subscription.endpoint()."
        ),
        "snippet": (
            "if (isBlank(request.endpoint()) || isBlank(request.p256dh()) || isBlank(request.auth())) {\n"
            "    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ...);\n"
            "}\n"
            "repository.upsert(userId, PushChannel.WEB_PUSH, request.endpoint().trim(), ...);"
        ),
        "exploitability": (
            "Condição: VAPID configurado em produção. Um operador autenticado pode registrar um endpoint "
            "interno (ex.: http://127.0.0.1:8080/actuator/..., http://garage:3900, http://169.254.169.254) "
            "e induzir o servidor a enviar requisições HTTP POST para a rede interna (SSRF cego)."
        ),
    },
    {
        "id": "F-06",
        "category": CAT_ISOLAMENTO,
        "severity": "MEDIA",
        "location": "backend/src/main/java/com/maraberto/occurrences/api/OccurrenceController.java:262-267\nbackend/src/main/java/com/maraberto/operations/api/ManagementTeamController.java:56-61\nbackend/src/main/java/com/maraberto/operations/api/ManagementUnmappedSignalController.java:56-87",
        "title": "Ausência de escopo territorial (praia/batalhão) para o papel COORDINATOR",
        "description": (
            "O isolamento da v1 é single-tenant por instalação estadual (ADR-003) com segregação por "
            "responder_id/user_id para LIFEGUARD. Para COORDINATOR, seesAllScopes() libera acesso total "
            "a todas as ocorrências (inclusive POST /api/v1/occurrences/{id}/victim/reveal com PII), "
            "posições de toda a equipe em plantão e toda a fila de triagem estadual."
        ),
        "snippet": (
            "private static boolean seesAllScopes(Authentication authentication) {\n"
            "    return authentication.getAuthorities().stream()\n"
            "            .map(GrantedAuthority::getAuthority)\n"
            "            .anyMatch(authority -> \"ROLE_COORDINATOR\".equals(authority)\n"
            "                    || \"ROLE_ADMIN\".equals(authority));\n"
            "}"
        ),
        "exploitability": (
            "Em CBMs com múltiplos batalhões/litorais na mesma instalação, um coordenador regional tem "
            "acesso desnecessário à localização em tempo real e dados pessoais de vítimas de outras "
            "regiões (princípio do menor privilégio / LGPD)."
        ),
    },
    {
        "id": "F-07",
        "category": CAT_CHAVES,
        "severity": "BAIXA",
        "location": "web/src/features/auth/demo.ts:19-40\nweb/dist/assets/demo-*.js:1",
        "title": "Credenciais e lógica de login demo embutidas no bundle de produção do frontend",
        "description": (
            "O arquivo demo.ts define a senha fixa demo1234 e contas fictícias (operador, coordenador, "
            "admin) que acabam incluídas no chunk de produção (web/dist/assets/demo-*.js). A execução só "
            "ocorre quando VITE_DEMO_AUTH=true no build."
        ),
        "snippet": (
            "const DEMO_PASSWORD = \"demo1234\";\n"
            "export const DEMO_ACCOUNTS = [ ... ];\n"
            "// Presente em web/dist/assets/demo-*.js mesmo sem VITE_DEMO_AUTH=true"
        ),
        "exploitability": (
            "Baixa em produção normal (DEMO_AUTH_ENABLED=false), mas um build acidental com "
            "VITE_DEMO_AUTH=true permitiria acesso à UI administrativa/operacional sem backend e expõe "
            "artefatos de desenvolvimento no bundle público."
        ),
    },
    {
        "id": "F-08",
        "category": CAT_PERMISSAO,
        "severity": "BAIXA",
        "location": "backend/src/main/java/com/maraberto/identity/infra/security/SecurityConfig.java:80,87-107",
        "title": "Rota inativa /api/v1/tides/** em permitAll() e ausência de @PreAuthorize nos controllers",
        "description": (
            "SecurityConfig.java:80 mantém /api/v1/tides/** liberado em permitAll() mesmo com o módulo "
            "tides desativado na v1. Além disso, apesar de @EnableMethodSecurity estar ativo (linha 27), "
            "nenhum controller usa @PreAuthorize como segunda camada de defesa caso um path mude."
        ),
        "snippet": (
            "@EnableMethodSecurity\n"
            "...\n"
            ".requestMatchers(HttpMethod.GET, \"/api/v1/tides/**\").permitAll()\n"
            ".requestMatchers(\"/api/v1/admin/**\").hasRole(\"ADMIN\")"
        ),
        "exploitability": (
            "Baixa isoladamente (SecurityConfig hoje cobre todos os 24 controllers corretamente), mas "
            "reduz a defesa em profundidade durante futuras refatorações ou forks estaduais."
        ),
    },
    {
        "id": "F-09",
        "category": CAT_INPUTS,
        "severity": "INFORMATIVA",
        "location": "web/src/features/management/pages/ManagementLearnPage.tsx:623\nbackend/src/main/java/com/maraberto/realtime/api/PushSubscriptionController.java:112-123",
        "title": "Pequenas inconsistências de sanitização/tamanho sem impacto de XSS direto",
        "description": (
            "Em ManagementLearnPage.tsx:623, item.externalLinkLabel é exibido em JSX sem sanitizeText() "
            "(ao contrário de LearnPage.tsx:273); o React já faz escape de texto, portanto não há XSS. "
            "Em PushSubscriptionController.java:112-123, os campos @Sanitized não trazem @Size(max=...) explícito."
        ),
        "snippet": (
            "// ManagementLearnPage.tsx:623\n"
            "{item.externalLinkLabel ?? \"Link externo\"}\n"
            "// PushSubscriptionController.java:114\n"
            "@Sanitized String endpoint, @Sanitized String p256dh, @Sanitized String auth"
        ),
        "exploitability": (
            "Não explorável para XSS (escape nativo do React + CSP estrita + @Sanitized no backend). "
            "Recomendada padronização para higiene e prevenção de payloads grandes."
        ),
    },
]

STRENGTHS = [
    (
        "Isolamento por dono (LIFEGUARD) em Ocorrências, SOS, Plantões e Mídia",
        "OccurrenceController (list/get/close/update/revealVictim) filtra por responder_id = userId e retorna 404 uniforme fora do escopo. DispatchController valida atribuição em emergency_dispatches (403/409). ShiftController usa exclusivamente o userId do JWT. FlagService e StationService validam posse das fotos via mediaCatalog.findOwned().",
    ),
    (
        "RBAC do Servidor alinhado aos gates de UI do Frontend",
        "Cruzamento completo de todos os 24 controllers do backend com web/src/app/routes.tsx (RequireRole): /admin -> ROLE_ADMIN, /gestao -> COORDINATOR/ADMIN, /operador -> LIFEGUARD/COORDINATOR/ADMIN, /actuator/** restrito a ADMIN (exceto health/info) e Swagger desligado em prod.",
    ),
    (
        "Proteção sólida contra XSS no Frontend e Backend",
        "Zero usos de dangerouslySetInnerHTML, innerHTML, outerHTML, eval, new Function, setHTML do MapLibre ou HTML gerado no backend. Links externos validados com safeExternalUrl() (bloqueia javascript: e data:), facade youtube-nocookie.com, CSP restritiva em web/nginx.conf e 68 anotações @Sanitized nos DTOs.",
    ),
    (
        "Criptografia autenticada, revogação HTTP e Git limpo",
        "AES-256-GCM com AAD por coluna (OperationalUserFieldConverters), Blind Index HMAC-SHA256, Argon2id, refresh opaco rotacionado em cookie HttpOnly/SameSite=Strict, blacklist JWT + revalidação de active/role no filtro HTTP, MFA TOTP opcional, travas de startup no perfil prod (CryptoConfiguration, JwtService, SecuritySettingsConfiguration) e histórico Git sem .env ou chaves privadas.",
    ),
]

ISSUES = [
    {
        "num": 1,
        "title": "[Segurança] Validar posse/plantão no reposicionamento de postos e bases móveis (IDOR)",
        "labels": "security, high, idor",
        "description": (
            "O endpoint `PATCH /api/v1/operations/stations/{kind}/{stationId}/position` está acessível para o papel `LIFEGUARD` "
            "em `SecurityConfig.java:94-97`, mas o método `OperationStationController.move()` não recebe `Authentication` nem "
            "valida se a estrutura pertence ao plantão aberto do operador — diferentemente de `updateStatus()` no mesmo controller."
        ),
        "evidence": (
            "- `backend/src/main/java/com/maraberto/operations/api/OperationStationController.java:158-179`\n"
            "- `backend/src/main/java/com/maraberto/operations/application/StationService.java:279-305`\n\n"
            "```java\n"
            "@PatchMapping(\"/{kind}/{stationId}/position\")\n"
            "public StationAdminView move(\n"
            "        @PathVariable StationKind kind,\n"
            "        @PathVariable UUID stationId,\n"
            "        @Valid @RequestBody StationPositionRequest request) {\n"
            "    return stationService.moveStation(kind, stationId, request);\n"
            "}\n"
            "```"
        ),
        "impact": (
            "Qualquer guarda-vidas autenticado pode mover as coordenadas de qualquer posto fixo ou base móvel da instalação, "
            "corrompendo o mapa público e o cálculo espacial de despacho de chamados de SOS."
        ),
        "fix": (
            "Injetar `Authentication` em `OperationStationController.move()` e validar em `StationService.moveStation()` "
            "que operadores não privilegiados (`!isPrivileged`) só podem reposicionar estruturas vinculadas ao próprio plantão "
            "aberto (`ownedByOpenShift(userId, kind, id)`), lançando `StationOutsideShiftException` (403) caso contrário, e "
            "registrar o reposicionamento na trilha de auditoria."
        ),
        "criteria": [
            "`LIFEGUARD` sem plantão ou em plantão de outro posto recebe `403 Forbidden` ao chamar `PATCH /api/v1/operations/stations/{kind}/{id}/position`.",
            "`LIFEGUARD` em plantão ativo na estrutura (ou `COORDINATOR`/`ADMIN`) consegue reposicionar normalmente.",
            "Teste de integração cobrindo o cenário negativo (outro guarda-vidas) e positivo adicionado e passando em `./gradlew clean check`.",
        ],
    },
    {
        "num": 2,
        "title": "[Segurança] Aplicar blacklist JWT, status active e bloqueio mustChangePassword no WebSocket STOMP",
        "labels": "security, high, auth",
        "description": (
            "`JwtAccessTokenVerifier` (utilizado por `StompAuthenticationInterceptor` no frame `CONNECT` de `/ws`) valida apenas "
            "a assinatura e expiração do JWT. Ele não consulta `TokenRevocationService`, não revalida `active`/`role` no banco "
            "e não bloqueia sessões com `mustChangePassword = true`."
        ),
        "evidence": (
            "- `backend/src/main/java/com/maraberto/identity/infra/security/JwtAccessTokenVerifier.java:25-39`\n"
            "- `backend/src/main/java/com/maraberto/realtime/infra/StompAuthenticationInterceptor.java:72-81`\n\n"
            "```java\n"
            "Claims claims = jwtService.validateAndExtractClaims(token);\n"
            "UUID userId = UUID.fromString(claims.getSubject());\n"
            "Role role = Role.valueOf(claims.get(\"role\", String.class));\n"
            "boolean mustChangePassword = Boolean.TRUE.equals(claims.get(\"mustChangePassword\", Boolean.class));\n"
            "return Optional.of(new AccessTokenClaims(userId, role.name(), mustChangePassword));\n"
            "```"
        ),
        "impact": (
            "Contas desativadas pela TI, sessões encerradas via logout ou usuários com senha temporária pendente continuam "
            "podendo conectar ao `/ws` por até 15 minutos e assinar `/user/queue/sos` ou `/topic/operational/emergency-calls`."
        ),
        "fix": (
            "Injetar `TokenRevocationService` e `OperationalUserRepository` em `JwtAccessTokenVerifier`, rejeitando tokens "
            "revogados, tokens com `purpose=mfa_challenge`, usuários inativos ou com papel divergente, e rejeitar `mustChangePassword=true` "
            "em `StompAuthenticationInterceptor.authenticate()`."
        ),
        "criteria": [
            "Token revogado no logout ou pertencente a usuário desativado é rejeitado no frame `CONNECT` STOMP.",
            "Token com `mustChangePassword=true` é rejeitado no frame `CONNECT` STOMP.",
            "Testes unitários/integração do `StompAuthenticationInterceptor` e `JwtAccessTokenVerifier` validam os três cenários.",
        ],
    },
    {
        "num": 3,
        "title": "[Segurança] Endurecer defaults de segredos no Docker Compose e validar credenciais padrão no perfil prod",
        "labels": "security, high, secrets, devops",
        "description": (
            "`docker-compose.yml` contém defaults públicos para `CRYPTO_MASTER_KEY`, `JWT_SECRET`, `POSTGRES_PASSWORD`, "
            "`GARAGE_SECRET_KEY`, `RABBITMQ_DEFAULT_PASS` e `ADMIN_BOOTSTRAP_PASSWORD` sem ativar `SPRING_PROFILES_ACTIVE=prod`. "
            "Além disso, mesmo no perfil `prod`, o boot não recusa `POSTGRES_PASSWORD=maraberto`, `RABBITMQ_PASSWORD=maraberto` "
            "ou `ADMIN_BOOTSTRAP_PASSWORD=admin`."
        ),
        "evidence": (
            "- `docker-compose.yml:14,21-22,28,30,39,75,111-112,126-127`\n"
            "- `docker-compose.tunnel.yml:17-18`\n\n"
            "```yaml\n"
            "CRYPTO_MASTER_KEY: ${CRYPTO_MASTER_KEY:-ZGV2LW9ubHktbWFyYWJlcnRvLWtleS0zMmJ5dGVzISE=}\n"
            "JWT_SECRET: ${JWT_SECRET:-dev-only-maraberto-jwt-secret-32bytes!!}\n"
            "ADMIN_BOOTSTRAP_PASSWORD: ${ADMIN_BOOTSTRAP_PASSWORD:-admin}\n"
            "```"
        ),
        "impact": (
            "Implantação em produção usando o `docker-compose.yml` sem sobrescrever variáveis ou sem `SPRING_PROFILES_ACTIVE=prod` "
            "expõe a instalação à forja de JWTs, decifragem de PII e acesso administrativo trivial."
        ),
        "fix": (
            "Criar `docker-compose.prod.yml` usando sintaxe obrigatória (`${VAR:?defina no .env}` sem defaults) com "
            "`SPRING_PROFILES_ACTIVE=prod` e `SECURITY_COOKIE_SECURE=true`. No backend, adicionar checagem de startup no "
            "perfil `prod` recusando `ADMIN_BOOTSTRAP_PASSWORD=admin` (ou exigindo senha gerada/configurada) e credenciais "
            "padrão de banco/RabbitMQ."
        ),
        "criteria": [
            "`docker-compose.prod.yml` falha ao subir se qualquer segredo obrigatório não estiver definido no `.env`.",
            "Boot com `SPRING_PROFILES_ACTIVE=prod` recusa credenciais padrão conhecidas (`maraberto`, `admin`, `maraberto-secret-key`).",
            "Documentação de deploy em `README.md` e `backend/ARCHITECTURE.md` referencia `docker-compose.prod.yml` para produção.",
        ],
    },
    {
        "num": 4,
        "title": "[Segurança] Restringir encerramento de bandeiras (flags) ao escopo do guarda-vidas ou coordenação",
        "labels": "security, medium, idor",
        "description": (
            "`POST /api/v1/operations/flags/{flagId}/close` permite que qualquer `LIFEGUARD` encerre qualquer sinalização ativa "
            "da instalação apenas conhecendo o `flagId` (exposto publicamente em `GET /api/v1/flags`)."
        ),
        "evidence": (
            "- `backend/src/main/java/com/maraberto/operations/api/FlagController.java:69-88`\n"
            "- `backend/src/main/java/com/maraberto/operations/application/FlagService.java:136-153`\n\n"
            "```java\n"
            "public FlagResponse close(UUID userId, UUID flagId) {\n"
            "    FlagResponse current = flags.findById(flagId)\n"
            "            .filter(flag -> flag.closedAt() == null)\n"
            "            .orElseThrow(FlagNotFoundException::new);\n"
            "    flags.close(flagId, userId, now);\n"
            "```"
        ),
        "impact": (
            "Um guarda-vidas pode remover prematuramente bandeiras de perigo (vermelha/roxa) de praias fora do seu plantão."
        ),
        "fix": (
            "Passar `isPrivileged(authentication)` para `FlagService.close()` e exigir para `LIFEGUARD` que o usuário seja "
            "o autor do lançamento (`created_by`) ou esteja em plantão aberto na mesma área de banho (`beach_id`)."
        ),
        "criteria": [
            "`LIFEGUARD` recebe `403 Forbidden` ao tentar encerrar bandeira de outra praia/autor fora do seu plantão.",
            "`COORDINATOR` e `ADMIN` continuam podendo encerrar qualquer bandeira.",
            "Teste de integração validando o bloqueio adicionado.",
        ],
    },
    {
        "num": 5,
        "title": "[Segurança] Validar URL de endpoint Web Push contra SSRF antes de persistir e enviar",
        "labels": "security, medium, ssrf",
        "description": (
            "`POST /api/v1/push/subscriptions` aceita qualquer string não vazia no campo `endpoint` e `WebPushSender.send()` "
            "efetua uma requisição HTTP POST diretamente para essa URL quando VAPID está configurado."
        ),
        "evidence": (
            "- `backend/src/main/java/com/maraberto/realtime/api/PushSubscriptionController.java:62-75`\n"
            "- `backend/src/main/java/com/maraberto/realtime/infra/WebPushSender.java:44-49`\n\n"
            "```java\n"
            "Notification notification = new Notification(\n"
            "        subscription.endpoint(), subscription.p256dh(), subscription.auth(), ...);\n"
            "HttpResponse response = service.send(notification);\n"
            "```"
        ),
        "impact": (
            "Um operador autenticado pode cadastrar um endpoint apontando para serviços internos da rede Docker/cloud "
            "(ex.: `http://garage:3900`, `http://169.254.169.254`) e disparar SSRF cego via envio de Web Push."
        ),
        "fix": (
            "Validar em `PushSubscriptionController` (e defensivamente em `WebPushSender`) que `endpoint` usa obrigatoriamente "
            "esquema `https`, porta padrão 443, comprimento máximo (ex.: 2048 chars), não resolve para endereços loopback, "
            "link-local ou faixas privadas RFC 1918/4193, e opcionalmente restringir à allowlist de serviços de push "
            "(`fcm.googleapis.com`, `updates.push.services.mozilla.com`, `*.notify.windows.com`, `*.push.apple.com`)."
        ),
        "criteria": [
            "Endpoints `http://`, `localhost`, IPs privados ou hosts fora da política retornam `400 Bad Request`.",
            "Endpoints legítimos de Chrome/Firefox/Edge/Safari Web Push continuam funcionando normalmente.",
            "Testes unitários cobrindo tentativas de SSRF (IP literal, loopback, HTTP, metadata cloud).",
        ],
    },
    {
        "num": 6,
        "title": "[Segurança] Implementar escopo territorial (praia/região) para o papel COORDINATOR",
        "labels": "security, medium, authorization, lgpd",
        "description": (
            "Na v1, `COORDINATOR` tem acesso irrestrito (`seesAllScopes() == true`) a todas as ocorrências, revelação de "
            "dados de vítimas, equipe em plantão e triagem de toda a instalação estadual, sem filtro por área/batalhão."
        ),
        "evidence": (
            "- `backend/src/main/java/com/maraberto/occurrences/api/OccurrenceController.java:262-267`\n"
            "- `backend/src/main/java/com/maraberto/operations/api/ManagementTeamController.java:56-61`\n"
            "- `backend/src/main/java/com/maraberto/operations/api/ManagementUnmappedSignalController.java:56-87`"
        ),
        "impact": (
            "Em instalações estaduais com múltiplos comandos regionais, coordenadores acessam PII de vítimas e posições de "
            "guarda-vidas fora da sua área de responsabilidade."
        ),
        "fix": (
            "Introduzir vínculo opcional `user_beach_scopes` (ou unidade/batalhão) gerenciado pela TI, aplicando filtro "
            "automático em `OccurrenceQueryService`, `OccurrenceCommandService`, `ManagementTeamService` e `UnmappedSignalService` "
            "quando o coordenador possuir escopo restrito."
        ),
        "criteria": [
            "Coordenador com escopo restrito vê apenas ocorrências, equipe e triagem das praias vinculadas.",
            "Tentativa de revelar vítima de ocorrência fora do escopo retorna `404 Not Found`.",
            "Testes de integração validando isolamento entre dois coordenadores de regiões distintas.",
        ],
    },
    {
        "num": 7,
        "title": "[Segurança] Excluir modo demo do bundle de produção, remover rota inativa /tides e padronizar @PreAuthorize/@Size",
        "labels": "security, low, hardening",
        "description": (
            "Agrupa três melhorias de endurecimento de baixo risco: (1) `web/src/features/auth/demo.ts` é empacotado em "
            "`web/dist/assets/demo-*.js` mesmo sem `VITE_DEMO_AUTH=true`; (2) `SecurityConfig.java:80` mantém `/api/v1/tides/**` "
            "em `permitAll()` apesar do módulo estar inativo; (3) ausência de `@PreAuthorize` nos controllers e de `@Size` nos "
            "campos de `PushSubscriptionController` (e `sanitizeText` em `ManagementLearnPage.tsx:623`)."
        ),
        "evidence": (
            "- `web/src/features/auth/demo.ts:19-40` e `web/dist/assets/demo-*.js:1`\n"
            "- `backend/src/main/java/com/maraberto/identity/infra/security/SecurityConfig.java:80`\n"
            "- `backend/src/main/java/com/maraberto/realtime/api/PushSubscriptionController.java:112-123`\n"
            "- `web/src/features/management/pages/ManagementLearnPage.tsx:623`"
        ),
        "impact": (
            "Superfície residual desnecessária no bundle público e menor defesa em profundidade em futuras manutenções."
        ),
        "fix": (
            "Garantir tree-shaking do modo demo via `import.meta.env.DEV` (ou alias vazio no `vite.config.ts` em prod), "
            "remover `/api/v1/tides/**` do `SecurityConfig`, adicionar `@PreAuthorize` nos controllers administrativos/gestão "
            "e incluir `@Size` explícito nos DTOs de push e `sanitizeText()` em `ManagementLearnPage.tsx:623`."
        ),
        "criteria": [
            "`npm run build` não contém `demo1234` nem `demoLogin` em `web/dist/`.",
            "`/api/v1/tides/**` removido de `SecurityConfig.java`.",
            "`@PreAuthorize` presente nos controllers `/api/v1/admin/**` e `/api/v1/management/**` e `@Size` nos DTOs de push.",
        ],
    },
]


def build_donut_svg(counts: dict[str, int]) -> str:
    total = sum(counts.values()) or 1
    cx, cy, r_outer, r_inner = 110, 110, 88, 52
    order = ["CRITICA", "ALTA", "MEDIA", "BAIXA", "INFORMATIVA"]
    angle = -math.pi / 2
    paths = []

    for sev in order:
        val = counts.get(sev, 0)
        if val <= 0:
            continue
        sweep = (val / total) * 2 * math.pi
        end_angle = angle + sweep
        large_arc = 1 if sweep > math.pi else 0

        x1_o = cx + r_outer * math.cos(angle)
        y1_o = cy + r_outer * math.sin(angle)
        x2_o = cx + r_outer * math.cos(end_angle)
        y2_o = cy + r_outer * math.sin(end_angle)

        x1_i = cx + r_inner * math.cos(end_angle)
        y1_i = cy + r_inner * math.sin(end_angle)
        x2_i = cx + r_inner * math.cos(angle)
        y2_i = cy + r_inner * math.sin(angle)

        d = (
            f"M {x1_o:.2f} {y1_o:.2f} "
            f"A {r_outer} {r_outer} 0 {large_arc} 1 {x2_o:.2f} {y2_o:.2f} "
            f"L {x1_i:.2f} {y1_i:.2f} "
            f"A {r_inner} {r_inner} 0 {large_arc} 0 {x2_i:.2f} {y2_i:.2f} Z"
        )
        paths.append(
            f'<path d="{d}" fill="{PALETTE[sev]}" stroke="#ffffff" stroke-width="2"/>'
        )
        angle = end_angle

    legend_rows = []
    ly = 42
    for sev in order:
        val = counts.get(sev, 0)
        legend_rows.append(
            f'<rect x="230" y="{ly - 10}" width="12" height="12" rx="3" fill="{PALETTE[sev]}"/>'
            f'<text x="248" y="{ly}" font-size="11.5" fill="#1e293b" font-weight="600">'
            f'{SEVERITY_LABELS[sev]}: {val}</text>'
        )
        ly += 26

    return f"""
    <svg viewBox="0 0 380 220" width="100%" height="205" role="img" aria-label="Distribuição de achados por severidade" xmlns="http://www.w3.org/2000/svg">
      <title>Distribuição de achados por severidade</title>
      {''.join(paths)}
      <text x="{cx}" y="{cy - 4}" text-anchor="middle" font-size="26" font-weight="800" fill="#0f172a">{total}</text>
      <text x="{cx}" y="{cy + 14}" text-anchor="middle" font-size="10.5" fill="#64748b" font-weight="600">ACHADOS</text>
      {''.join(legend_rows)}
    </svg>
    """


def build_bar_svg(cat_counts: list[tuple[str, int, str]]) -> str:
    max_val = max((c for _, c, _ in cat_counts), default=1) or 1
    bars = []
    y = 22
    for label, count, color in cat_counts:
        bar_w = max(8, int((count / max_val) * 195))
        bars.append(
            f'<text x="0" y="{y + 12}" font-size="10.5" fill="#1e293b" font-weight="600">{html.escape(label)}</text>'
            f'<rect x="155" y="{y}" width="195" height="18" rx="4" fill="#f1f5f9"/>'
            f'<rect x="155" y="{y}" width="{bar_w}" height="18" rx="4" fill="{color}"/>'
            f'<text x="{155 + bar_w + 8}" y="{y + 13}" font-size="11" fill="#0f172a" font-weight="700">{count}</text>'
        )
        y += 38
    return f"""
    <svg viewBox="0 0 390 220" width="100%" height="205" role="img" aria-label="Achados por categoria de segurança" xmlns="http://www.w3.org/2000/svg">
      <title>Achados por categoria de segurança</title>
      {''.join(bars)}
    </svg>
    """


def chip(sev: str) -> str:
    color = PALETTE[sev]
    label = SEVERITY_LABELS[sev]
    return f'<span class="chip" style="background:{color};">{label}</span>'


def render_html() -> str:
    sev_counts = dict.fromkeys(["CRITICA", "ALTA", "MEDIA", "BAIXA", "INFORMATIVA"], 0)
    for f in FINDINGS:
        sev_counts[f["severity"]] += 1

    categories = [
        ("1. Banco sem Tranca", CAT_ISOLAMENTO, "#D97706"),
        ("2. Permissão / Auth", CAT_PERMISSAO, "#EA580C"),
        ("3. IDOR", CAT_IDOR, "#EA580C"),
        ("4. Chaves Expostas", CAT_CHAVES, "#EA580C"),
        ("5. Inputs (XSS/SSRF)", CAT_INPUTS, "#D97706"),
    ]
    cat_counts = [
        (short, sum(1 for f in FINDINGS if f["category"] == full), color)
        for short, full, color in categories
    ]

    donut_svg = build_donut_svg(sev_counts)
    bar_svg = build_bar_svg(cat_counts)

    strengths_html = "".join(
        f"""
        <div class="strength-card">
          <div class="strength-title"><span class="dot" style="background:{PALETTE['FORTE']};"></span>{html.escape(t)}</div>
          <p>{html.escape(d)}</p>
        </div>
        """
        for t, d in STRENGTHS
    )

    table_rows = "".join(
        f"""
        <tr>
          <td class="col-sev">{chip(f['severity'])}<div class="fid">{f['id']}</div></td>
          <td class="col-cat">{html.escape(f['category'])}</td>
          <td class="col-loc"><code>{html.escape(f['location']).replace(chr(10), '<br/>')}</code></td>
          <td class="col-desc">
            <strong>{html.escape(f['title'])}</strong><br/>
            {html.escape(f['description'])}
            <pre class="code-mini">{html.escape(f['snippet'])}</pre>
            <div class="exploit"><strong>Explorabilidade:</strong> {html.escape(f['exploitability'])}</div>
          </td>
        </tr>
        """
        for f in FINDINGS
    )

    issues_blocks = []
    for iss in ISSUES:
        checklist = "\n".join(f"- [ ] {c}" for c in iss["criteria"])
        md = (
            f"--- ISSUE {iss['num']} ---\n"
            f"# {iss['title']}\n\n"
            f"**Labels:** `{iss['labels']}`\n\n"
            f"## Descrição do problema\n{iss['description']}\n\n"
            f"## Evidência\n{iss['evidence']}\n\n"
            f"## Impacto\n{iss['impact']}\n\n"
            f"## Sugestão de correção\n{iss['fix']}\n\n"
            f"## Critérios de aceite\n{checklist}\n"
            f"--- FIM ISSUE {iss['num']} ---"
        )
        issues_blocks.append(f'<pre class="issue-block">{html.escape(md)}</pre>')

    return f"""<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8" />
<title>Relatório de Auditoria de Segurança — MarAberto</title>
<style>
  @page {{
    size: A4;
    margin: 22mm 18mm 22mm 18mm;
    @top-left {{
      content: "Relatório de Auditoria de Segurança — MarAberto (v1)";
      font-family: system-ui, -apple-system, sans-serif;
      font-size: 8.5pt;
      color: #64748b;
    }}
    @top-right {{
      content: "25/11/2026";
      font-family: system-ui, -apple-system, sans-serif;
      font-size: 8.5pt;
      color: #64748b;
    }}
    @bottom-left {{
      content: "Confidencial — Revisão de Segurança Pré-Release v1";
      font-family: system-ui, -apple-system, sans-serif;
      font-size: 8.5pt;
      color: #64748b;
    }}
    @bottom-right {{
      content: "Página " counter(page) " de " counter(pages);
      font-family: system-ui, -apple-system, sans-serif;
      font-size: 8.5pt;
      font-weight: 600;
      color: #334155;
    }}
  }}
  @page :first {{
    @top-left {{ content: none; }}
    @top-right {{ content: none; }}
    @bottom-left {{ content: none; }}
    @bottom-right {{ content: none; }}
  }}

  * {{ box-sizing: border-box; }}
  body {{
    font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
    color: #0f172a;
    font-size: 9.8pt;
    line-height: 1.45;
    margin: 0;
  }}

  /* Capa */
  .cover {{
    page-break-after: always;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    min-height: 230mm;
    padding: 0;
  }}
  .cover-banner {{
    background: linear-gradient(135deg, #0c2340 0%, #0f3b68 100%);
    color: #ffffff;
    border-radius: 14px;
    padding: 22px 26px;
  }}
  .cover-kicker {{
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 9pt;
    color: #93c5fd;
    font-weight: 700;
    margin-bottom: 10px;
  }}
  .cover h1 {{
    font-size: 24pt;
    line-height: 1.15;
    margin: 0 0 12px 0;
    font-weight: 800;
  }}
  .cover-sub {{
    font-size: 11pt;
    color: #dbeafe;
    margin: 0;
  }}
  .meta-grid {{
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
    margin-top: 20px;
  }}
  .meta-box {{
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    padding: 12px 14px;
    background: #f8fafc;
  }}
  .meta-label {{
    font-size: 8pt;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: #64748b;
    font-weight: 700;
    margin-bottom: 4px;
  }}
  .meta-val {{
    font-size: 9.5pt;
    color: #0f172a;
    font-weight: 600;
  }}
  .method-box {{
    border: 1px solid #cbd5e1;
    border-left: 4px solid #0c2340;
    border-radius: 8px;
    padding: 14px 16px;
    background: #ffffff;
    margin-top: 18px;
  }}
  .method-box h2 {{
    font-size: 11pt;
    margin: 0 0 8px 0;
    color: #0c2340;
  }}
  .method-box ul {{
    margin: 0;
    padding-left: 18px;
  }}
  .method-box li {{
    margin-bottom: 5px;
    font-size: 9.2pt;
  }}

  /* Títulos de seção */
  h2.sec-title {{
    font-size: 14pt;
    color: #0c2340;
    border-bottom: 2px solid #e2e8f0;
    padding-bottom: 5px;
    margin: 0 0 12px 0;
    page-break-after: avoid;
  }}
  .page-break {{ page-break-before: always; }}

  /* KPIs */
  .kpi-row {{
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 8px;
    margin-bottom: 16px;
  }}
  .kpi {{
    border-radius: 10px;
    padding: 10px 8px;
    text-align: center;
    color: #ffffff;
  }}
  .kpi .num {{ font-size: 20pt; font-weight: 800; line-height: 1.1; }}
  .kpi .lbl {{ font-size: 8pt; text-transform: uppercase; letter-spacing: 0.06em; font-weight: 700; margin-top: 3px; }}

  .charts-grid {{
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 14px;
    margin-bottom: 18px;
  }}
  .chart-card {{
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    padding: 12px 14px;
    background: #ffffff;
    break-inside: avoid;
  }}
  .chart-card h3 {{
    font-size: 10pt;
    margin: 0 0 8px 0;
    color: #334155;
  }}

  /* Pontos fortes e fracos */
  .strength-card {{
    border: 1px solid #d1fae5;
    background: #ecfdf5;
    border-radius: 8px;
    padding: 10px 12px;
    margin-bottom: 8px;
    break-inside: avoid;
  }}
  .strength-title {{
    font-weight: 700;
    color: #065f46;
    font-size: 9.5pt;
    margin-bottom: 4px;
  }}
  .strength-card p {{ margin: 0; font-size: 9pt; color: #064e3b; }}
  .dot {{
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    margin-right: 6px;
  }}
  .weak-box {{
    border: 1px solid #fed7aa;
    background: #fff7ed;
    border-radius: 8px;
    padding: 10px 14px;
    margin-top: 12px;
    break-inside: avoid;
  }}
  .weak-box h3 {{ margin: 0 0 6px 0; font-size: 10pt; color: #9a3412; }}
  .weak-box ul {{ margin: 0; padding-left: 18px; font-size: 9.2pt; color: #7c2d12; }}
  .weak-box li {{ margin-bottom: 4px; }}

  /* Tabela de achados */
  table.findings {{
    width: 100%;
    border-collapse: collapse;
    font-size: 8.7pt;
  }}
  table.findings th {{
    background: #0c2340;
    color: #ffffff;
    text-align: left;
    padding: 7px 8px;
    font-weight: 700;
    font-size: 8.5pt;
  }}
  table.findings td {{
    border-bottom: 1px solid #e2e8f0;
    padding: 6px 6px;
    vertical-align: top;
  }}
  table.findings tr {{ break-inside: avoid; }}
  .col-sev {{ width: 11%; text-align: center; }}
  .col-cat {{ width: 14%; font-weight: 600; color: #334155; font-size: 8.2pt; }}
  .col-loc {{ width: 23%; overflow-wrap: anywhere; font-size: 7.3pt; line-height: 1.3; }}
  .col-desc {{ width: 52%; font-size: 8.4pt; line-height: 1.38; }}
  .fid {{ font-size: 8pt; font-weight: 700; color: #475569; margin-top: 4px; }}
  .chip {{
    display: inline-block;
    color: #ffffff;
    font-size: 7.5pt;
    font-weight: 800;
    padding: 2px 7px;
    border-radius: 999px;
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }}
  code {{
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    background: #f1f5f9;
    padding: 1px 3px;
    border-radius: 3px;
    font-size: 7.8pt;
  }}
  pre.code-mini {{
    background: #0f172a;
    color: #e2e8f0;
    padding: 6px 8px;
    border-radius: 6px;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 7.4pt;
    line-height: 1.35;
    white-space: pre-wrap;
    overflow-wrap: break-word;
    margin: 6px 0;
  }}
  .exploit {{
    font-size: 8.3pt;
    color: #334155;
    background: #f8fafc;
    border-left: 3px solid #94a3b8;
    padding: 4px 7px;
    border-radius: 0 4px 4px 0;
  }}

  /* Recomendações */
  .prio-card {{
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 10px 14px;
    margin-bottom: 10px;
    break-inside: avoid;
  }}
  .prio-header {{
    font-weight: 800;
    font-size: 10pt;
    margin-bottom: 5px;
  }}
  .prio-card ul {{ margin: 0; padding-left: 18px; font-size: 9.2pt; }}
  .prio-card li {{ margin-bottom: 4px; }}

  /* Issues Markdown */
  pre.issue-block {{
    background: #f8fafc;
    border: 1px solid #cbd5e1;
    border-left: 4px solid #0c2340;
    border-radius: 6px;
    padding: 10px 12px;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 7.7pt;
    line-height: 1.38;
    white-space: pre-wrap;
    overflow-wrap: break-word;
    margin: 0 0 12px 0;
    break-inside: avoid;
  }}
</style>
</head>
<body>

<!-- CAPA -->
<section class="cover">
  <div>
    <div class="cover-banner">
      <div class="cover-kicker">Auditoria de Segurança de Código-Fonte · Release v1</div>
      <h1>Relatório de Auditoria de Segurança — MarAberto (Praias-Seguras)</h1>
      <p class="cover-sub">Avaliação defensiva exaustiva sobre isolamento de dados, autorização servidor vs. cliente, IDOR, gestão de segredos e tratamento de entradas (XSS/SSRF).</p>
    </div>

    <div class="meta-grid">
      <div class="meta-box">
        <div class="meta-label">Data da Auditoria</div>
        <div class="meta-val">25 de novembro de 2026</div>
      </div>
      <div class="meta-box">
        <div class="meta-label">Escopo Auditado</div>
        <div class="meta-val">24 controllers REST, interceptor STOMP/WS, 15 módulos Spring Modulith, PWA/React (web/), Dockerfiles, Nginx, Compose e CI</div>
      </div>
      <div class="meta-box">
        <div class="meta-label">Stack Detectada — Backend</div>
        <div class="meta-val">Java 21 (Virtual Threads), Spring Boot 3.3.4, Spring Modulith 1.2.13, Spring Security 6, PostgreSQL 16 + PostGIS, Flyway V1–V28, Redis 7, RabbitMQ 3, Garage S3</div>
      </div>
      <div class="meta-box">
        <div class="meta-label">Stack Detectada — Frontend &amp; Auth</div>
        <div class="meta-val">React 19, TypeScript 6, Vite 8, Tailwind 4, MapLibre GL, Capacitor 8, DOMPurify; JWT HS256 (memória) + Refresh HttpOnly rotacionado + MFA TOTP opcional</div>
      </div>
    </div>

    <div class="method-box">
      <h2>Nota Metodológica — Mapeamento das 5 Categorias para a Stack</h2>
      <ul>
        <li><strong>1. Banco sem Tranca (Isolamento):</strong> arquitetura single-tenant por instalação estadual (ADR-003) + filtro manual por <code>responder_id</code>/<code>user_id</code> em queries JPA/JDBC. Auditado escopo de <code>LIFEGUARD</code> e <code>COORDINATOR</code>.</li>
        <li><strong>2. Permissão Definida no Navegador:</strong> cruzamento sistemático entre os gates <code>RequireRole</code> em <code>web/src/app/routes.tsx</code> e as regras de <code>SecurityConfig.java</code> + <code>StompAuthenticationInterceptor.java</code>.</li>
        <li><strong>3. IDOR:</strong> inspeção de todos os 24 controllers REST e serviços de aplicação buscando rotas com IDs em path/query/body sem validação de posse ou plantão.</li>
        <li><strong>4. Chaves Expostas (Hardcode):</strong> varredura de <code>docker-compose*.yml</code>, <code>application*.yml</code>, <code>.env.example</code>, CI, histórico Git, bundle <code>web/dist/</code> e travas de startup no perfil <code>prod</code>.</li>
        <li><strong>5. Inputs sem Tratamento (XSS/SSRF):</strong> busca por <code>dangerouslySetInnerHTML</code>, <code>innerHTML</code>, URLs <code>javascript:</code>, DOM MapLibre, templates HTML no backend, uso de <code>DOMPurify</code>/<code>@Sanitized</code> e validação de endpoints externos.</li>
      </ul>
    </div>
  </div>

  <div style="font-size:8.5pt; color:#64748b; border-top:1px solid #e2e8f0; padding-top:8px;">
    Todos os achados deste relatório foram verificados diretamente no código-fonte real da branch <code>59-feature-revisar</code> (revisão final v1), sem especulação.
  </div>
</section>

<!-- RESUMO EXECUTIVO -->
<section>
  <h2 class="sec-title">1. Resumo Executivo</h2>
  <p>A auditoria verificou integralmente os 24 controllers REST do backend, o canal WebSocket/STOMP, as rotas e componentes do frontend React/Capacitor e os artefatos de empacotamento Docker/CI. Não foram encontradas vulnerabilidades <strong>Críticas</strong> nem vetores diretos de <strong>XSS</strong> ou <strong>SQL Injection</strong>. Foram identificados <strong>9 achados reais</strong> (3 de severidade Alta, 3 Média, 2 Baixa e 1 Informativa), todos com correção objetiva e de baixo risco antes da publicação em produção.</p>

  <div class="kpi-row">
    <div class="kpi" style="background:{PALETTE['CRITICA']};"><div class="num">{sev_counts['CRITICA']}</div><div class="lbl">Crítica</div></div>
    <div class="kpi" style="background:{PALETTE['ALTA']};"><div class="num">{sev_counts['ALTA']}</div><div class="lbl">Alta</div></div>
    <div class="kpi" style="background:{PALETTE['MEDIA']};"><div class="num">{sev_counts['MEDIA']}</div><div class="lbl">Média</div></div>
    <div class="kpi" style="background:{PALETTE['BAIXA']};"><div class="num">{sev_counts['BAIXA']}</div><div class="lbl">Baixa</div></div>
    <div class="kpi" style="background:{PALETTE['INFORMATIVA']};"><div class="num">{sev_counts['INFORMATIVA']}</div><div class="lbl">Informativa</div></div>
  </div>

  <div class="charts-grid">
    <div class="chart-card">
      <h3>Distribuição por Severidade</h3>
      {donut_svg}
    </div>
    <div class="chart-card">
      <h3>Achados por Categoria</h3>
      {bar_svg}
    </div>
  </div>

  <h2 class="sec-title">2. Pontos Fortes e Pontos Fracos</h2>
  {strengths_html}

  <div class="weak-box">
    <h3>Riscos Centrais Identificados (Pontos Fracos)</h3>
    <ul>
      <li><strong>IDOR operacional no reposicionamento de postos (F-01):</strong> <code>PATCH /api/v1/operations/stations/{{kind}}/{{id}}/position</code> não verifica se a estrutura pertence ao plantão do guarda-vidas, podendo distorcer o despacho espacial do SOS.</li>
      <li><strong>Assimetria entre HTTP e WebSocket na revogação de tokens (F-02):</strong> o handshake STOMP (<code>JwtAccessTokenVerifier</code>) não aplica a blacklist de logout/desativação nem bloqueia <code>mustChangePassword=true</code>.</li>
      <li><strong>Defaults públicos no Compose padrão (F-03):</strong> subir <code>docker-compose.yml</code> sem <code>SPRING_PROFILES_ACTIVE=prod</code> e sem sobrescrever segredos expõe chaves conhecidas do repositório.</li>
      <li><strong>SSRF cego em Web Push (F-05) e escopo amplo em bandeiras/coordenação (F-04, F-06):</strong> ausência de validação de host/esquema HTTPS em <code>endpoint</code> de push e ausência de filtro territorial para <code>COORDINATOR</code>.</li>
    </ul>
  </div>
</section>

<!-- TABELA DE ACHADOS DETALHADOS -->
<section class="page-break">
  <h2 class="sec-title">3. Achados Detalhados por Categoria</h2>
  <table class="findings">
    <thead>
      <tr>
        <th class="col-sev">Severidade</th>
        <th class="col-cat">Categoria</th>
        <th class="col-loc">Arquivo : Linha</th>
        <th class="col-desc">Descrição, Evidência e Explorabilidade</th>
      </tr>
    </thead>
    <tbody>
      {table_rows}
    </tbody>
  </table>
</section>

<!-- RECOMENDAÇÕES PRIORIZADAS -->
<section class="page-break">
  <h2 class="sec-title">4. Recomendações Priorizadas</h2>

  <div class="prio-card" style="border-left:4px solid {PALETTE['ALTA']};">
    <div class="prio-header" style="color:{PALETTE['ALTA']};">P1 — Ação Imediata (antes do deploy em produção)</div>
    <ul>
      <li><strong>Corrigir IDOR de reposicionamento (F-01):</strong> injetar <code>Authentication</code> em <code>OperationStationController.move()</code> e exigir <code>ownedByOpenShift()</code> para <code>LIFEGUARD</code>, espelhando <code>updateStatus()</code>.</li>
      <li><strong>Fechar bypass de revogação no WebSocket STOMP (F-02):</strong> integrar <code>TokenRevocationService</code>, <code>OperationalUserRepository.findActiveRoleById()</code> e recusa de <code>mustChangePassword=true</code> em <code>JwtAccessTokenVerifier</code> / <code>StompAuthenticationInterceptor</code>.</li>
      <li><strong>Separar Compose de produção sem defaults (F-03):</strong> criar <code>docker-compose.prod.yml</code> com <code>SPRING_PROFILES_ACTIVE=prod</code> e variáveis obrigatórias (<code>${{VAR:?erro}}</code>), além de validar credenciais padrão de DB/Rabbit/Admin no startup de <code>prod</code>.</li>
    </ul>
  </div>

  <div class="prio-card" style="border-left:4px solid {PALETTE['MEDIA']};">
    <div class="prio-header" style="color:{PALETTE['MEDIA']};">P2 — Curto Prazo (pós-QA / v1.1)</div>
    <ul>
      <li><strong>Validar endpoint Web Push contra SSRF (F-05):</strong> exigir esquema <code>https://</code>, porta 443 e bloquear IPs privados/loopback/link-local em <code>PushSubscriptionController</code> e <code>WebPushSender</code>.</li>
      <li><strong>Restringir encerramento de bandeiras (F-04):</strong> validar autoria ou plantão na mesma praia em <code>FlagService.close()</code> para <code>LIFEGUARD</code>.</li>
      <li><strong>Escopo territorial para Coordenação (F-06):</strong> vincular coordenadores a praias/regionais quando a instalação operar múltiplos batalhões.</li>
    </ul>
  </div>

  <div class="prio-card" style="border-left:4px solid {PALETTE['BAIXA']};">
    <div class="prio-header" style="color:{PALETTE['BAIXA']};">P3 — Melhoria Contínua e Higiene</div>
    <ul>
      <li><strong>Remover código demo do bundle de produção (F-07):</strong> isolar <code>demo.ts</code> sob <code>import.meta.env.DEV</code> para que <code>demo1234</code> não apareça em <code>web/dist/</code>.</li>
      <li><strong>Defesa em profundidade (F-08, F-09):</strong> remover <code>/api/v1/tides/**</code> de <code>SecurityConfig</code>, adicionar <code>@PreAuthorize</code> nos controllers privilegiados, <code>@Size</code> nos DTOs de push e <code>sanitizeText()</code> em <code>ManagementLearnPage.tsx:623</code>.</li>
    </ul>
  </div>
</section>

<!-- ISSUES PARA O GITHUB -->
<section class="page-break">
  <h2 class="sec-title">5. Issues para o GitHub (Markdown Pronto para Copiar)</h2>
  <p style="margin-top:0; font-size:9pt; color:#475569;">Achados correlatos de baixo risco foram agrupados na Issue 7 para evitar spam no backlog.</p>
  {''.join(issues_blocks)}
</section>

</body>
</html>
"""


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    HTML_PATH.write_text(render_html(), encoding="utf-8")

    chromium = shutil.which("chromium") or shutil.which("google-chrome")
    if not chromium:
        print("Erro: chromium/google-chrome não encontrado no PATH.", file=sys.stderr)
        return 1

    with tempfile.TemporaryDirectory(prefix="maraberto-pdf-") as tmpdir:
        env = os.environ.copy()
        env["HOME"] = tmpdir
        env["XDG_CONFIG_HOME"] = tmpdir
        env["XDG_CACHE_HOME"] = tmpdir
        cmd = [
            chromium,
            "--headless=new",
            "--no-sandbox",
            "--disable-gpu",
            "--disable-dev-shm-usage",
            "--disable-crash-reporter",
            f"--user-data-dir={tmpdir}/profile",
            f"--print-to-pdf={PDF_PATH}",
            "--no-pdf-header-footer",
            HTML_PATH.as_uri(),
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, env=env)
    if result.returncode != 0:
        print("Falha ao gerar PDF:", result.stderr, file=sys.stderr)
        return result.returncode

    print(f"PDF gerado com sucesso: {PDF_PATH} ({PDF_PATH.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
