#!/usr/bin/env python3
"""
Gerador do Relatório de Auditoria de Segurança (PDF) — MarAberto.

Usa apenas biblioteca padrão do Python para gerar HTML/CSS + gráficos SVG
vetoriais e renderiza para A4 via Chromium headless (sem instalações globais).

Uso:
    python3 docs/security-audit/generate_report.py
"""

from __future__ import annotations

import html
import math
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent
OUTPUT_PDF = ROOT_DIR / "relatorio-auditoria-seguranca.pdf"

PALETTE = {
    "critica": "#B91C1C",
    "alta": "#EA580C",
    "media": "#D97706",
    "baixa": "#2563EB",
    "forte": "#059669",
    "slate900": "#0F172A",
    "slate700": "#334155",
    "slate500": "#64748B",
    "slate200": "#E2E8F0",
    "slate100": "#F1F5F9",
    "bg": "#FFFFFF",
}

SEVERITY_COUNTS = [
    ("Crítica", 0, PALETTE["critica"]),
    ("Alta", 2, PALETTE["alta"]),
    ("Média", 4, PALETTE["media"]),
    ("Baixa", 2, PALETTE["baixa"]),
]

CATEGORY_COUNTS = [
    ("1. Banco sem Tranca", 2, PALETTE["alta"]),
    ("2. Permissão no Navegador", 0, PALETTE["forte"]),
    ("3. IDOR", 2, PALETTE["media"]),
    ("4. Chaves Expostas", 3, PALETTE["alta"]),
    ("5. Inputs / XSS", 1, PALETTE["baixa"]),
]

FINDINGS = [
    {
        "id": "F-01",
        "severity": "Alta",
        "sev_key": "alta",
        "category": "4. Chaves Expostas",
        "location": "deploy/garage/garage.toml:10,19-20\ndocker-compose.prod.yml:91-102",
        "title": "Segredos estáticos do Garage montados diretamente em produção",
        "description": (
            "O arquivo deploy/garage/garage.toml contém rpc_secret, admin_token "
            "(\"maraberto-dev-admin-token-local\") e metrics_token "
            "(\"maraberto-dev-metrics-token-local\") fixos no repositório, e o "
            "docker-compose.prod.yml monta esse mesmo arquivo em /etc/garage.toml:ro "
            "no serviço garage de produção."
        ),
        "snippet": (
            'rpc_secret = "0d15b0e21f66d4c0d3aa4a4e62f9d1ff710d38604126a9e5173e8fd0eab1a6b4"\n'
            'admin_token = "maraberto-dev-admin-token-local"\n'
            'metrics_token = "maraberto-dev-metrics-token-local"'
        ),
        "exploitability": (
            "Qualquer serviço ou atacante que alcance a rede interna do container "
            "Garage (porta 3903/3901) pode autenticar-se na API administrativa com o "
            "token público do repositório, criar/listar chaves S3 e ler ou apagar "
            "fotos operacionais e arquivos KML."
        ),
    },
    {
        "id": "F-02",
        "severity": "Alta",
        "sev_key": "alta",
        "category": "1. Banco sem Tranca",
        "location": "backend/.../occurrences/infra/OccurrenceRepository.java:440-501\nbackend/.../occurrences/application/OccurrenceCommandService.java:403-417",
        "title": "Vazamento cross-escopo de ocorrências e chamados SOS em findNearbyDuplicates",
        "description": (
            "Ao registrar uma ocorrência manual (POST /api/v1/occurrences), "
            "OccurrenceCommandService.findDuplicates() chama "
            "OccurrenceRepository.findNearbyDuplicates() sem aplicar SCOPE_SQL "
            "(responder_id para LIFEGUARD e user_beach_scopes para COORDINATOR)."
        ),
        "snippet": (
            "FROM occurrences o, ref\n"
            "WHERE o.id <> :excludeId AND o.location IS NOT NULL\n"
            "  AND o.occurred_at BETWEEN :from AND :to\n"
            "  AND ST_DWithin(o.location, ref.geog, :radiusMeters)\n"
            "UNION ALL\n"
            "SELECT 'EMERGENCY_CALL', c.id, ... FROM emergency_calls c, ref ..."
        ),
        "exploitability": (
            "Qualquer LIFEGUARD autenticado ou COORDINATOR regional pode enviar "
            "coordenadas arbitrárias em POST /api/v1/occurrences e enumerar até 10 "
            "ocorrências de outros operadores e chamados SOS não atribuídos a ele "
            "(ID, número, status, horário e distância exata), violando RN15/RN17."
        ),
    },
    {
        "id": "F-03",
        "severity": "Média",
        "sev_key": "media",
        "category": "3. IDOR",
        "location": "backend/.../operations/api/OperationStationController.java:117-122,143-156,176-200\nbackend/.../operations/application/StationService.java:84-86,112-173,281-341",
        "title": "COORDINATOR regional altera/reposiciona qualquer posto fora de user_beach_scopes",
        "description": (
            "OperationStationController.isPrivileged() retorna true para qualquer "
            "ROLE_COORDINATOR, ignorando user_beach_scopes. Em StationService, "
            "fieldStations(), station(), moveStation() e setActiveInField() não "
            "validam se a praia do posto pertence ao escopo territorial do coordenador."
        ),
        "snippet": (
            "private static boolean isPrivileged(Authentication authentication) {\n"
            "    return authentication.getAuthorities().stream()\n"
            "        .map(authority -> authority.getAuthority())\n"
            "        .anyMatch(a -> a.equals(\"ROLE_COORDINATOR\") || a.equals(\"ROLE_ADMIN\"));\n"
            "}"
        ),
        "exploitability": (
            "Um COORDINATOR restrito à praia A pode enviar PATCH "
            "/api/v1/operations/stations/FIXED/{stationId} ou /position com o UUID de "
            "um posto da praia B, desativando-o ou movendo-o no mapa público."
        ),
    },
    {
        "id": "F-04",
        "severity": "Média",
        "sev_key": "media",
        "category": "3. IDOR",
        "location": "backend/.../operations/api/FlagController.java:63-67,86-102\nbackend/.../operations/application/FlagService.java:86-133,148-168",
        "title": "Lançamento e encerramento de bandeiras sem validação de escopo territorial/plantão",
        "description": (
            "Em POST /api/v1/operations/flags, FlagService.launch() não verifica "
            "user_beach_scopes (COORDINATOR regional) nem plantão ativo na praia "
            "(LIFEGUARD), executando flags.closeScope() sobre a bandeira vigente. "
            "Em POST /api/v1/operations/flags/{flagId}/close, isPrivileged() ignora "
            "user_beach_scopes."
        ),
        "snippet": (
            "Scope scope = resolved.get();\n"
            "flags.lockScope(scope.beachId(), scope.sectorId());\n"
            "flags.closeScope(scope.beachId(), scope.sectorId(), userId, now);"
        ),
        "exploitability": (
            "Qualquer LIFEGUARD (mesmo sem plantão aberto) pode publicar/sobrescrever "
            "a bandeira de qualquer praia/setor do estado informando beachId, e um "
            "COORDINATOR regional pode encerrar bandeiras fora da sua jurisdição."
        ),
    },
    {
        "id": "F-05",
        "severity": "Média",
        "sev_key": "media",
        "category": "1. Banco sem Tranca",
        "location": "backend/.../occurrences/application/OccurrenceCommandService.java:76-174,369-401\nbackend/.../occurrences/infra/OccurrenceRepository.java:105-124",
        "title": "Criação manual de ocorrência ignora user_beach_scopes do coordenador regional",
        "description": (
            "validateManualCreate() valida apenas existsBeach(request.beachId()), "
            "sem checar user_beach_scopes para COORDINATOR regional. Como insertManual() "
            "grava responder_id = actorId e SCOPE_SQL libera o.responder_id = :userId, "
            "o coordenador regional passa a ler e editar a ficha fora da sua área."
        ),
        "snippet": (
            "if (request.beachId() != null && !occurrenceRepository.existsBeach(request.beachId())) {\n"
            "    throw new IllegalArgumentException(\"Área de banho informada não foi encontrada.\");\n"
            "}"
        ),
        "exploitability": (
            "Um COORDINATOR restrito a determinadas praias pode criar ocorrências "
            "manuais (POST /api/v1/occurrences) vinculadas a praias de outra regional "
            "e manter acesso de leitura/edição sobre elas."
        ),
    },
    {
        "id": "F-06",
        "severity": "Média",
        "sev_key": "media",
        "category": "4. Chaves Expostas",
        "location": "docker-compose.prod.yml:19,79-89\nbackend/src/main/resources/application.yml:44\nbackend/.../SecuritySettingsConfiguration.java:48-69",
        "title": "Redis em produção sem senha obrigatória (blacklist JWT e rate limits desprotegidos)",
        "description": (
            "docker-compose.prod.yml define REDIS_PASSWORD: ${REDIS_PASSWORD:-} (vazio) "
            "e sobe redis:7 sem --requirepass. SecuritySettingsConfiguration não rejeita "
            "spring.data.redis.password vazio no perfil prod."
        ),
        "snippet": (
            "REDIS_PASSWORD: ${REDIS_PASSWORD:-}\n"
            "...\n"
            "redis:\n"
            "  image: redis:7"
        ),
        "exploitability": (
            "Com acesso à rede Docker interna, um atacante pode executar FLUSHALL no "
            "Redis, apagando a blacklist de JWTs revogados (logout/desativação) e "
            "zerando os contadores de rate limit de login e SOS."
        ),
    },
    {
        "id": "F-07",
        "severity": "Baixa",
        "sev_key": "baixa",
        "category": "5. Inputs / XSS",
        "location": "backend/.../shared/security/sanitization/TextSanitizer.java:98-104,133-155",
        "title": "Bypass de remoção de HTML em TextSanitizer.normalizePlain via entidades HTML",
        "description": (
            "normalizePlain() executa decodeEntities(PLAIN_HTML.sanitize(value)). "
            "Entidades como &lt;script&gt; atravessam o OWASP HTML Sanitizer sem "
            "alteração e são decodificadas de volta para <script>, persistindo tags "
            "HTML brutas em campos PLAIN_TEXT."
        ),
        "snippet": (
            "private static String normalizePlain(String value) {\n"
            "    String sanitized = decodeEntities(PLAIN_HTML.sanitize(value));\n"
            "    return Normalizer.normalize(sanitized, Normalizer.Form.NFKC)..."
        ),
        "exploitability": (
            "Condição de explorabilidade: não resulta em XSS direto no frontend React "
            "atual (escape automático + CSP script-src 'self' em web/nginx.conf:48), "
            "mas quebra a defesa em profundidade e gera risco de Stored XSS caso o "
            "dado seja renderizado em e-mails, relatórios HTML ou clientes futuros."
        ),
    },
    {
        "id": "F-08",
        "severity": "Baixa",
        "sev_key": "baixa",
        "category": "4. Chaves Expostas",
        "location": "backend/src/main/resources/application.yml:12,57-58,160,168,186\ndocker-compose.tunnel.yml:17-18",
        "title": "Defaults públicos de segredos ativos fora do perfil prod e contas demo no túnel",
        "description": (
            "application.yml e application-rabbit.yml embutem defaults conhecidos "
            "(JWT_SECRET, CRYPTO_MASTER_KEY, DB_PASSWORD, STORAGE_S3_*, admin/admin) "
            "rejeitados apenas quando SPRING_PROFILES_ACTIVE=prod. Além disso, "
            "docker-compose.tunnel.yml ativa IDENTITY_DEV_SEED=true por padrão em "
            "túnel público da Cloudflare."
        ),
        "snippet": (
            "CRYPTO_MASTER_KEY:ZGV2LW9ubHktbWFyYWJlcnRvLWtleS0zMmJ5dGVzISE=\n"
            "JWT_SECRET:dev-only-maraberto-jwt-secret-32bytes!!\n"
            "IDENTITY_DEV_SEED: ${IDENTITY_DEV_SEED:-true}"
        ),
        "exploitability": (
            "Condição de explorabilidade: qualquer ambiente compartilhado (staging/QA) "
            "ou túnel público iniciado sem SPRING_PROFILES_ACTIVE=prod opera com "
            "segredos públicos conhecidos, permitindo forjar JWTs de ADMIN, decifrar "
            "PII ou autenticar com contas demo (demo1234)."
        ),
    },
]

STRENGTHS = [
    (
        "Autorização RBAC 100% espelhada no servidor (Categoria 2 sem falhas)",
        "Todos os gates RequireRole em web/src/app/routes.tsx (/operador, /gestao, "
        "/admin, /perfil, /trocar-senha) possuem correspondência estrita em "
        "SecurityConfig.java:52-112 e @PreAuthorize em todos os controllers de "
        "/admin/** e /management/**. JwtAuthenticationFilter.java:113-138 revalida "
        "blacklist, active e role no banco a cada requisição e bloqueia "
        "mustChangePassword=true no servidor.",
    ),
    (
        "Isolamento sólido nos fluxos críticos de SOS, plantão, sessões e mídia",
        "DispatchService.java filtra openCalls, assignedCalls e acknowledge por "
        "recipient_user_id = :userId; ShiftController.java opera exclusivamente "
        "sobre o usuário autenticado; AuthSessionRepository.java:42-63 restringe "
        "revogação por userId + familyId; MediaCatalog.findOwned() impede anexar "
        "mídias de outros operadores; ManagementTeamService.java:99-107 e "
        "JdbcUnmappedSignalRepository.java:129-139 aplicam user_beach_scopes.",
    ),
    (
        "Segurança STOMP/WebSocket com allowlist estrita",
        "StompAuthenticationInterceptor.java:61-114 exige JWT válido no frame "
        "CONNECT, restringe SUBSCRIBE apenas aos destinos autorizados por papel "
        "e bloqueia SEND direto para /topic, /queue e /user.",
    ),
    (
        "Validações de startup no perfil prod e higiene de Git",
        "JwtService.java:46-51, CryptoConfiguration.java:46-54, "
        "S3StorageConfiguration.java:48-57, SecuritySettingsConfiguration.java:35-88 "
        "e os 3 dev-seeders bloqueiam o boot em prod com segredos de dev, cookie "
        "inseguro ou origens HTTP. .env não é rastreado no Git e não há chaves "
        "privadas vazadas no histórico.",
    ),
    (
        "Defesa anti-XSS e anti-XXE consistente",
        "Zero uso de dangerouslySetInnerHTML, innerHTML, eval ou new Function em "
        "web/src/. DOMPurify e safeExternalUrl() (rejeitando javascript:/data:) "
        "estão ativos em web/src/shared/lib/sanitize.ts; CSP rigorosa "
        "(script-src 'self', frame-ancestors 'none') em web/nginx.conf:48; "
        "KmlFeatureReader.java:52-62 desabilita DTD e entidades externas (XXE).",
    ),
]

RECOMMENDATIONS = [
    (
        "P1 — Imediato",
        "Remover segredos fixos de deploy/garage/garage.toml da stack de produção (F-01)",
        "Gerar garage.toml via template/entrypoint ou secrets do orquestrador a partir "
        "de variáveis obrigatórias (GARAGE_RPC_SECRET, GARAGE_ADMIN_TOKEN, "
        "GARAGE_METRICS_TOKEN) em docker-compose.prod.yml, sem defaults públicos.",
    ),
    (
        "P1 — Imediato",
        "Aplicar SCOPE_SQL em OccurrenceRepository.findNearbyDuplicates (F-02)",
        "Propagar userId, allScopes e bypassTerritorialScope de "
        "OccurrenceCommandService.findDuplicates() até findNearbyDuplicates(), "
        "filtrando occurrences por SCOPE_SQL e emergency_calls apenas pelos "
        "despachos atribuídos ao operador (ou dentro de user_beach_scopes).",
    ),
    (
        "P2 — Curto Prazo",
        "Enforçar user_beach_scopes em OperationStationController, FlagService e criação de ocorrências (F-03, F-04, F-05)",
        "Substituir o booleano genérico isPrivileged() por verificação explícita de "
        "isAdmin vs. escopo territorial em StationService e FlagService; exigir "
        "plantão ativo na praia (ou escopo territorial) em FlagService.launch() e "
        "validar user_beach_scopes em OccurrenceCommandService.validateManualCreate().",
    ),
    (
        "P2 — Curto Prazo",
        "Exigir autenticação do Redis em produção (F-06)",
        "Configurar --requirepass ${REDIS_PASSWORD:?...} no serviço redis em "
        "docker-compose.prod.yml e adicionar validação de startup em "
        "SecuritySettingsConfiguration.rejectDefaultCredentials() para recusar "
        "spring.data.redis.password vazio no perfil prod.",
    ),
    (
        "P3 — Endurecimento",
        "Corrigir ordem de sanitização em TextSanitizer e endurecer perfil de túnel/staging (F-07, F-08)",
        "Em TextSanitizer.normalizePlain(), evitar reintroduzir tags HTML após "
        "PLAIN_HTML.sanitize() (aplicar sanitize após decode ou rejeitar markup "
        "remanescente). Em docker-compose.tunnel.yml, definir IDENTITY_DEV_SEED=false "
        "por padrão e adotar allowlist de perfil 'dev'/'test' para aceitar defaults.",
    ),
]

ISSUES = [
    {
        "num": 1,
        "title": "[Segurança] Remover segredos estáticos do Garage (rpc_secret, admin_token, metrics_token) da stack de produção",
        "labels": "security, severity:high",
        "body": """### Descrição do problema
O arquivo `deploy/garage/garage.toml` contém segredos fixos no repositório (`rpc_secret`, `admin_token` e `metrics_token`) e é montado diretamente em produção pelo `docker-compose.prod.yml` (`/etc/garage.toml:ro`).

**Por que é explorável:** Qualquer processo ou ator que alcance a rede interna do container Garage (portas `3901`/`3903`) pode autenticar na API Admin do Garage utilizando `Authorization: Bearer maraberto-dev-admin-token-local`, obtendo controle total para criar chaves S3, listar/excluir buckets e ler ou sobrescrever fotos operacionais e KMLs.

### Evidência
- `deploy/garage/garage.toml:10, 19-20`
- `docker-compose.prod.yml:91-102`

```toml
rpc_secret = "0d15b0e21f66d4c0d3aa4a4e62f9d1ff710d38604126a9e5173e8fd0eab1a6b4"
...
[admin]
api_bind_addr = "[::]:3903"
admin_token = "maraberto-dev-admin-token-local"
metrics_token = "maraberto-dev-metrics-token-local"
```

### Impacto
Comprometimento integral do armazenamento de objetos S3 (fotos de sinalizações, postos e arquivos KML arquivados) e potencial movimentação lateral via API administrativa do Garage em produção.

### Sugestão de correção
1. Separar a configuração de dev (`deploy/garage/garage.dev.toml`) da configuração de produção.
2. Em `docker-compose.prod.yml`, exigir `GARAGE_RPC_SECRET`, `GARAGE_ADMIN_TOKEN` e `GARAGE_METRICS_TOKEN` via `${VAR:?...}` (suportados nativamente pelo Garage via variáveis de ambiente ou gerados por entrypoint/Docker secret) sem valores padrão no repositório.
3. Restringir `admin` API do Garage para `127.0.0.1:3903` ou desabilitar se não for necessária em runtime.

### Critérios de aceite
- [ ] `docker-compose.prod.yml` não monta `deploy/garage/garage.toml` com tokens fixos.
- [ ] Subir a stack de produção falha imediatamente se `GARAGE_RPC_SECRET` ou `GARAGE_ADMIN_TOKEN` estiverem ausentes.
- [ ] Requisição à API Admin do Garage com `maraberto-dev-admin-token-local` retorna `401/403` em produção.""",
    },
    {
        "num": 2,
        "title": "[Segurança] Aplicar isolamento de escopo em OccurrenceRepository.findNearbyDuplicates para evitar vazamento cross-escopo",
        "labels": "security, severity:high",
        "body": """### Descrição do problema
Ao registrar uma ocorrência manual (`POST /api/v1/occurrences`), `OccurrenceCommandService.findDuplicates()` invoca `OccurrenceRepository.findNearbyDuplicates()` sem aplicar a cláusula `SCOPE_SQL` (`responder_id` para `LIFEGUARD` e `user_beach_scopes` para `COORDINATOR` regional).

**Por que é explorável:** Um guarda-vidas autenticado (ou coordenador regional) pode enviar requisições `POST /api/v1/occurrences` com coordenadas de interesse e receber na resposta até 10 registros de ocorrências de outros operadores e chamados SOS ainda não assumidos (`emergency_calls`), expondo `id`, `number`, `origin`, `status`, `occurredAt` e `distanceMeters` fora do seu escopo (violando RN15 e RN17).

### Evidência
- `backend/src/main/java/com/maraberto/occurrences/application/OccurrenceCommandService.java:403-417`
- `backend/src/main/java/com/maraberto/occurrences/infra/OccurrenceRepository.java:440-501`

```java
SELECT 'OCCURRENCE' AS kind, o.id, o.number, o.origin, o.status, o.occurred_at,
       ST_Distance(o.location, ref.geog) AS distance_meters
FROM occurrences o, ref
WHERE o.id <> :excludeId
  AND o.location IS NOT NULL
  AND o.occurred_at BETWEEN :from AND :to
  AND ST_DWithin(o.location, ref.geog, :radiusMeters)
```

### Impacto
Vazamento de informações operacionais sensíveis (existência, horário e proximidade de chamados SOS e ocorrências de outros operadores/regionais), quebrando o isolamento por operador e por escopo territorial.

### Sugestão de correção
1. Passar `userId`, `allScopes` e `bypassTerritorialScope` para `findDuplicates()` e `findNearbyDuplicates()`.
2. Aplicar `SCOPE_SQL` na subquery de `occurrences`.
3. Na subquery de `emergency_calls`, restringir: para `LIFEGUARD`, apenas chamados com despacho atribuído ao próprio `userId`; para `COORDINATOR` regional, apenas chamados cuja área resolvida pertença a `user_beach_scopes`.

### Critérios de aceite
- [ ] `LIFEGUARD` não recebe em `duplicates` ocorrências de outros guarda-vidas nem chamados SOS não despachados para ele.
- [ ] `COORDINATOR` com `user_beach_scopes` recebe apenas duplicatas das praias em seu escopo.
- [ ] Testes de integração cobrem ambos os cenários negativos e positivos.""",
    },
    {
        "num": 3,
        "title": "[Segurança] Validar escopo territorial (user_beach_scopes) em OperationStationController e StationService",
        "labels": "security, severity:medium",
        "body": """### Descrição do problema
Em `OperationStationController`, o método `isPrivileged()` retorna `true` para qualquer usuário com `ROLE_COORDINATOR`, ignorando `user_beach_scopes`. Como consequência, `StationService.fieldStations()`, `station()`, `moveStation()` e `setActiveInField()` permitem que um coordenador regional visualize, reposicione e ative/desative qualquer posto fixo do estado.

**Por que é explorável:** Um `COORDINATOR` restrito à praia A pode enviar `PATCH /api/v1/operations/stations/FIXED/{stationId}` ou `/position` informando o UUID de um posto da praia B (obtido via `GET /api/v1/stations` público) e desativar ou alterar a localização do posto no mapa público.

### Evidência
- `backend/src/main/java/com/maraberto/operations/api/OperationStationController.java:117-122, 143-156, 176-200`
- `backend/src/main/java/com/maraberto/operations/application/StationService.java:84-86, 112-173, 281-341`

```java
private static boolean isPrivileged(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(authority -> authority.getAuthority())
        .anyMatch(authority -> authority.equals("ROLE_COORDINATOR")
                || authority.equals("ROLE_ADMIN"));
}
```

### Impacto
Alteração indevida de estruturas operacionais (IDOR horizontal entre regionais), podendo remover postos ativos do mapa público ou distorcer o despacho espacial de SOS.

### Sugestão de correção
1. Distinguir `isAdmin` (`ROLE_ADMIN`) de `isCoordinator` (`ROLE_COORDINATOR`).
2. Para `COORDINATOR`, aplicar filtro por `user_beach_scopes` em `fieldStations()`, `station()`, `createStation()` (para `FIXED`), `moveStation()` e `setActiveInField()`.
3. Retornar `403` ou `404` quando a estrutura pertencer a uma praia fora do escopo do coordenador.

### Critérios de aceite
- [ ] `COORDINATOR` com `user_beach_scopes` recebe `403/404` ao tentar mover ou desativar posto fixo de outra praia.
- [ ] `GET /api/v1/operations/stations` filtra postos fixos pelo escopo territorial do coordenador.
- [ ] `ADMIN` e `COORDINATOR` sem restrições mantêm acesso completo.""",
    },
    {
        "num": 4,
        "title": "[Segurança] Restringir lançamento e encerramento de bandeiras por escopo territorial e plantão ativo",
        "labels": "security, severity:medium",
        "body": """### Descrição do problema
Em `FlagController` e `FlagService`:
1. `POST /api/v1/operations/flags/{flagId}/close` usa `isPrivileged()` (`COORDINATOR || ADMIN`), ignorando `user_beach_scopes` para coordenadores regionais.
2. `POST /api/v1/operations/flags` (`launch`) não valida `user_beach_scopes` para coordenadores regionais nem verifica se o `LIFEGUARD` possui plantão aberto na área/praia alvo — e executa `flags.closeScope()` automaticamente sobre a bandeira vigente.

**Por que é explorável:** Qualquer `LIFEGUARD` (mesmo sem plantão ativo) pode publicar uma bandeira verde/vermelha em qualquer `beachId`/`sectorId` do estado, encerrando automaticamente a sinalização vigente daquela praia no mapa público. Um `COORDINATOR` regional pode encerrar bandeiras fora da sua jurisdição.

### Evidência
- `backend/src/main/java/com/maraberto/operations/api/FlagController.java:63-67, 86-102`
- `backend/src/main/java/com/maraberto/operations/application/FlagService.java:86-133, 148-168`

```java
Scope scope = resolved.get();
flags.lockScope(scope.beachId(), scope.sectorId());
flags.closeScope(scope.beachId(), scope.sectorId(), userId, now);
```

### Impacto
Adulteração ou remoção não autorizada de bandeiras de risco no mapa público de praias fora do escopo do operador, com impacto direto na segurança dos banhistas.

### Sugestão de correção
1. Passar `isAdmin` e `isCoordinator` para `FlagService.launch()` e `FlagService.close()`.
2. Para `COORDINATOR`, validar se `scope.beachId()` (ou `current.beachId()`) está permitido em `user_beach_scopes`.
3. Para `LIFEGUARD` em `launch()`, exigir plantão aberto na praia alvo (ou posição GPS validada dentro da área no plantão ativo), reutilizando a lógica de `canCloseFlag()`.

### Critérios de aceite
- [ ] `COORDINATOR` regional recebe `403` ao lançar ou encerrar bandeira fora de `user_beach_scopes`.
- [ ] `LIFEGUARD` sem plantão ativo na praia alvo não consegue substituir bandeira de outra praia.
- [ ] Testes automatizados validam todos os papéis (`LIFEGUARD`, `COORDINATOR` regional/global, `ADMIN`).""",
    },
    {
        "num": 5,
        "title": "[Segurança] Validar user_beach_scopes na criação manual de ocorrências (POST /api/v1/occurrences)",
        "labels": "security, severity:medium",
        "body": """### Descrição do problema
Em `OccurrenceCommandService.create()`, o método `validateManualCreate()` verifica apenas se a praia existe (`existsBeach`), sem validar se a praia informada (ou resolvida por PostGIS a partir das coordenadas) pertence ao `user_beach_scopes` do `COORDINATOR` regional. Como `insertManual()` grava `responder_id = actorId`, a regra `OR o.responder_id = :userId` em `SCOPE_SQL` concede acesso permanente à ficha criada fora da jurisdição.

**Por que é explorável:** Um `COORDINATOR` restrito à praia A pode registrar ocorrências manuais vinculadas à praia B (`beachId` de outra regional) e continuar consultando e editando registros fora do seu escopo territorial.

### Evidência
- `backend/src/main/java/com/maraberto/occurrences/application/OccurrenceCommandService.java:76-174, 369-401`
- `backend/src/main/java/com/maraberto/occurrences/infra/OccurrenceRepository.java:105-124`

```java
if (request.beachId() != null && !occurrenceRepository.existsBeach(request.beachId())) {
    throw new IllegalArgumentException("Área de banho informada não foi encontrada.");
}
```

### Impacto
Quebra do isolamento territorial (`user_beach_scopes`) na escrita de ocorrências operacionais e poluição de indicadores/estatísticas de outras regionais.

### Sugestão de correção
1. Antes de inserir, resolver a `beachId` efetiva (explícita ou via PostGIS `ST_Contains`).
2. Quando `!bypassTerritorialScope` e o usuário possuir linhas em `user_beach_scopes`, rejeitar (`403 Forbidden`) qualquer criação cuja `beachId` efetiva não esteja no escopo do coordenador.

### Critérios de aceite
- [ ] `COORDINATOR` com `user_beach_scopes` recebe `403` ao criar ocorrência manual com `beachId` ou coordenadas de praia fora do seu escopo.
- [ ] `COORDINATOR` sem restrições e `ADMIN` continuam podendo registrar em qualquer praia.""",
    },
    {
        "num": 6,
        "title": "[Segurança] Exigir autenticação do Redis em produção e endurecer defaults de segredos/contas demo",
        "labels": "security, severity:medium",
        "body": """### Descrição do problema
1. Em `docker-compose.prod.yml`, `REDIS_PASSWORD` tem default vazio (`${REDIS_PASSWORD:-}`) e o container `redis:7` não configura `--requirepass`. `SecuritySettingsConfiguration` também não valida `spring.data.redis.password` no perfil `prod`.
2. `application.yml` e `application-rabbit.yml` embutem segredos padrão (`JWT_SECRET`, `CRYPTO_MASTER_KEY`, `DB_PASSWORD`, `STORAGE_S3_*`, `admin`/`admin`) cuja rejeição só ocorre se `SPRING_PROFILES_ACTIVE=prod` estiver explícito.
3. `docker-compose.tunnel.yml` ativa `IDENTITY_DEV_SEED=true` por padrão, expondo contas demo (`demo1234`) em túnel público da Cloudflare.

**Por que é explorável:** Sem senha no Redis em produção, um ator na rede Docker pode executar `FLUSHALL`, apagando a blacklist de JWTs revogados e os rate limits de login/SOS. Em ambientes compartilhados iniciados sem `-Dspring.profiles.active=prod`, os segredos públicos permitem forjar JWTs de `ADMIN` e decifrar PII.

### Evidência
- `docker-compose.prod.yml:19, 79-89`
- `backend/src/main/resources/application.yml:12, 44, 57-58, 160, 168, 186`
- `backend/src/main/java/com/maraberto/identity/infra/security/SecuritySettingsConfiguration.java:48-69`
- `docker-compose.tunnel.yml:17-18`

### Impacto
Bypass de revogação de JWTs e rate limiting via Redis sem senha; risco de comprometimento total de autenticação e confidencialidade de PII em deploys mal configurados.

### Sugestão de correção
1. Em `docker-compose.prod.yml`, exigir `REDIS_PASSWORD: ${REDIS_PASSWORD:?defina REDIS_PASSWORD no .env}` e iniciar o Redis com `command: ["redis-server", "--requirepass", "${REDIS_PASSWORD:?defina REDIS_PASSWORD no .env}"]`.
2. Em `SecuritySettingsConfiguration.rejectDefaultCredentials()`, rejeitar `spring.data.redis.password` vazio/em branco no perfil `prod`.
3. Inverter a lógica de validação de defaults (aceitar defaults apenas nos perfis `dev`/`test`/`local`, rejeitando em qualquer outro perfil).
4. Alterar `IDENTITY_DEV_SEED` e `OPERATIONS_DEV_SEED` em `docker-compose.tunnel.yml` para `false` por padrão.

### Critérios de aceite
- [ ] `redis` em `docker-compose.prod.yml` recusa comandos sem `AUTH`.
- [ ] Backend falha no boot em `prod` se `REDIS_PASSWORD` estiver vazio.
- [ ] `docker-compose.tunnel.yml` não cria contas `demo1234` por padrão.""",
    },
    {
        "num": 7,
        "title": "[Segurança] Impedir reintrodução de tags HTML em TextSanitizer.normalizePlain (duplo-encoding)",
        "labels": "security, severity:low",
        "body": """### Descrição do problema
Em `TextSanitizer.normalizePlain()`, o fluxo executa `decodeEntities(PLAIN_HTML.sanitize(value))`. Quando a entrada contém entidades HTML como `&lt;script&gt;alert(1)&lt;/script&gt;`, o `PLAIN_HTML.sanitize` preserva o texto (pois não há tags literais) e, na etapa seguinte, `decodeEntities()` converte `&lt;`/`&gt;` em `<script>alert(1)</script>`, armazenando markup HTML bruto no banco de dados.

**Por que é explorável:** No frontend React atual não há XSS imediato (o React escapa texto por padrão e `web/nginx.conf:48` aplica CSP `script-src 'self'`), mas a garantia de defesa em profundidade do backend (`@Sanitized(PLAIN_TEXT)`) é contornada. Qualquer exportação futura em HTML, e-mail ou cliente não-React que renderize esse texto armazenado ficará exposto a Stored XSS.

### Evidência
- `backend/src/main/java/com/maraberto/shared/security/sanitization/TextSanitizer.java:98-104, 133-155`

```java
private static String normalizePlain(String value) {
    String sanitized = decodeEntities(PLAIN_HTML.sanitize(value));
    return Normalizer.normalize(sanitized, Normalizer.Form.NFKC)
            .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
            .replaceAll("\\s+", " ")
            .trim();
}
```

### Impacto
Contorno da política de sanitização `PLAIN_TEXT` do backend, permitindo persistência de payloads `<script>` / `<img onerror=...>` em campos de texto puro.

### Sugestão de correção
Aplicar `PLAIN_HTML.sanitize()` iterativamente após `decodeEntities()` até estabilizar (ponto fixo, com limite de 2–3 iterações) ou remover `<` e `>` residuais em texto puro após a sanitização, preservando apenas texto seguro sem tags.

### Critérios de aceite
- [ ] `TextSanitizer.sanitize("&lt;script&gt;alert(1)&lt;/script&gt;", PLAIN_TEXT)` não retorna `<script>`.
- [ ] Testes unitários em `TextSanitizerTest` cobrem entradas com entidades numéricas (`&#60;`, `&#x3c;`) e nomeadas (`&lt;`) simples e duplas.""",
    },
]


def build_donut_svg() -> str:
    total = sum(count for _, count, _ in SEVERITY_COUNTS)
    cx, cy, r = 95, 95, 66
    stroke_w = 28
    circumference = 2 * math.pi * r

    segments = []
    offset = 0.0
    for label, count, color in SEVERITY_COUNTS:
        if count <= 0:
            continue
        frac = count / total
        dash = frac * circumference
        segments.append(
            f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{color}" '
            f'stroke-width="{stroke_w}" stroke-dasharray="{dash:.2f} {circumference - dash:.2f}" '
            f'stroke-dashoffset="{-offset:.2f}" transform="rotate(-90 {cx} {cy})" />'
        )
        offset += dash

    legend_rows = []
    for idx, (label, count, color) in enumerate(SEVERITY_COUNTS):
        y = 42 + idx * 32
        pct = (count / total * 100) if total else 0
        legend_rows.append(
            f'<rect x="196" y="{y - 11}" width="12" height="12" rx="3" fill="{color}" />'
            f'<text x="215" y="{y}" font-size="11.5" font-weight="600" fill="{PALETTE["slate900"]}">{html.escape(label)}</text>'
            f'<text x="342" y="{y}" font-size="11.5" font-weight="700" fill="{PALETTE["slate700"]}" text-anchor="end">{count} ({pct:.0f}%)</text>'
        )

    return f"""
    <svg viewBox="0 0 352 190" width="100%" height="185" xmlns="http://www.w3.org/2000/svg">
      <circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{PALETTE['slate100']}" stroke-width="{stroke_w}" />
      {''.join(segments)}
      <text x="{cx}" y="{cy - 4}" text-anchor="middle" font-size="26" font-weight="800" fill="{PALETTE['slate900']}">{total}</text>
      <text x="{cx}" y="{cy + 15}" text-anchor="middle" font-size="10.5" font-weight="600" fill="{PALETTE['slate500']}">ACHADOS</text>
      {''.join(legend_rows)}
    </svg>
    """


def build_bar_svg() -> str:
    max_val = max(c for _, c, _ in CATEGORY_COUNTS) or 1
    bar_x = 178
    bar_max_w = 134
    rows = []
    for idx, (label, count, color) in enumerate(CATEGORY_COUNTS):
        y = 22 + idx * 33
        w = (count / max_val) * bar_max_w if count > 0 else 5
        fill = color if count > 0 else PALETTE["forte"]
        status_txt = f"{count}" if count > 0 else "0 (OK)"
        rows.append(
            f'<text x="0" y="{y + 12}" font-size="10.2" font-weight="600" fill="{PALETTE["slate900"]}">{html.escape(label)}</text>'
            f'<rect x="{bar_x}" y="{y}" width="{bar_max_w}" height="16" rx="4" fill="{PALETTE["slate100"]}" />'
            f'<rect x="{bar_x}" y="{y}" width="{w:.1f}" height="16" rx="4" fill="{fill}" />'
            f'<text x="{bar_x + bar_max_w + 8}" y="{y + 12}" font-size="10.2" font-weight="700" fill="{fill}">{status_txt}</text>'
        )

    return f"""
    <svg viewBox="0 0 368 190" width="100%" height="185" xmlns="http://www.w3.org/2000/svg">
      {''.join(rows)}
    </svg>
    """


def chip(severity: str, key: str) -> str:
    color = PALETTE[key]
    return (
        f'<span class="chip" style="background:{color}18;color:{color};'
        f'border:1px solid {color}55;">{html.escape(severity.upper())}</span>'
    )


def render_html() -> str:
    findings_rows = []
    for f in FINDINGS:
        loc_html = "<br/>".join(
            f'<code>{html.escape(line)}</code>' for line in f["location"].split("\n")
        )
        findings_rows.append(
            f"""
            <tr>
              <td style="width:13%;vertical-align:top;">
                <div style="font-weight:800;font-size:9.5pt;margin-bottom:4px;">{f['id']}</div>
                {chip(f['severity'], f['sev_key'])}
                <div class="cat-tag">{html.escape(f['category'])}</div>
              </td>
              <td style="width:31%;vertical-align:top;word-break:break-word;">{loc_html}</td>
              <td style="width:56%;vertical-align:top;">
                <div class="finding-title">{html.escape(f['title'])}</div>
                <p class="finding-desc">{html.escape(f['description'])}</p>
                <pre class="code-mini">{html.escape(f['snippet'])}</pre>
                <p class="finding-exp"><strong>Explorabilidade:</strong> {html.escape(f['exploitability'])}</p>
              </td>
            </tr>
            """
        )

    strengths_html = "\n".join(
        f"""
        <div class="strength-card">
          <div class="strength-title">✓ {html.escape(title)}</div>
          <p>{html.escape(desc)}</p>
        </div>
        """
        for title, desc in STRENGTHS
    )

    recs_html = "\n".join(
        f"""
        <tr>
          <td style="width:17%;font-weight:800;color:#0F172A;vertical-align:top;">{html.escape(prio)}</td>
          <td style="width:33%;font-weight:700;vertical-align:top;">{html.escape(title)}</td>
          <td style="width:50%;vertical-align:top;">{html.escape(desc)}</td>
        </tr>
        """
        for prio, title, desc in RECOMMENDATIONS
    )

    issues_html = []
    for issue in ISSUES:
        md_block = (
            f"--- ISSUE {issue['num']} ---\n"
            f"# {issue['title']}\n\n"
            f"**Labels:** `{issue['labels']}`\n\n"
            f"{issue['body']}\n"
            f"--- FIM ISSUE {issue['num']} ---"
        )
        issues_html.append(
            f"""
            <div class="issue-block">
              <div class="issue-header">Issue #{issue['num']} — {html.escape(issue['title'])}</div>
              <pre class="issue-pre">{html.escape(md_block)}</pre>
            </div>
            """
        )

    return f"""<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8" />
<title>Relatório de Auditoria de Segurança — MarAberto</title>
<style>
  @page {{
    size: A4 portrait;
    margin: 2cm 1.8cm 2cm 1.8cm;
    @top-left {{
      content: "Relatório de Auditoria de Segurança — MarAberto";
      font-family: "DejaVu Sans", Arial, sans-serif;
      font-size: 8pt;
      color: #64748B;
      border-bottom: 0.5pt solid #CBD5E1;
      padding-bottom: 4pt;
    }}
    @top-right {{
      content: "26/11/2026";
      font-family: "DejaVu Sans", Arial, sans-serif;
      font-size: 8pt;
      color: #64748B;
      border-bottom: 0.5pt solid #CBD5E1;
      padding-bottom: 4pt;
    }}
    @bottom-left {{
      content: "Confidencial — Uso interno da equipe de engenharia";
      font-family: "DejaVu Sans", Arial, sans-serif;
      font-size: 8pt;
      color: #64748B;
      border-top: 0.5pt solid #CBD5E1;
      padding-top: 4pt;
    }}
    @bottom-right {{
      content: "Página " counter(page) " de " counter(pages);
      font-family: "DejaVu Sans", Arial, sans-serif;
      font-size: 8pt;
      font-weight: 700;
      color: #334155;
      border-top: 0.5pt solid #CBD5E1;
      padding-top: 4pt;
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
    font-family: "DejaVu Sans", Arial, Helvetica, sans-serif;
    color: #0F172A;
    font-size: 9.5pt;
    line-height: 1.45;
    margin: 0;
  }}

  /* Cover */
  .cover {{
    min-height: 24.5cm;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    page-break-after: always;
  }}
  .cover-banner {{
    background: linear-gradient(135deg, #0C4A6E 0%, #0F172A 100%);
    color: #FFFFFF;
    padding: 36pt 28pt;
    border-radius: 12pt;
  }}
  .cover-kicker {{
    text-transform: uppercase;
    letter-spacing: 1.6pt;
    font-size: 8.5pt;
    font-weight: 700;
    color: #7DD3FC;
    margin-bottom: 10pt;
  }}
  .cover-title {{
    font-size: 24pt;
    font-weight: 800;
    line-height: 1.18;
    margin: 0 0 12pt 0;
  }}
  .cover-sub {{
    font-size: 11pt;
    color: #E0F2FE;
    margin: 0;
  }}
  .meta-grid {{
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10pt;
    margin-top: 18pt;
  }}
  .meta-card {{
    background: #F8FAFC;
    border: 1pt solid #E2E8F0;
    border-radius: 8pt;
    padding: 11pt 13pt;
  }}
  .meta-label {{
    font-size: 7.5pt;
    text-transform: uppercase;
    letter-spacing: 0.8pt;
    font-weight: 700;
    color: #64748B;
    margin-bottom: 4pt;
  }}
  .meta-value {{
    font-size: 9.5pt;
    font-weight: 700;
    color: #0F172A;
  }}
  .method-box {{
    background: #F8FAFC;
    border-left: 4pt solid #0284C7;
    border-radius: 6pt;
    padding: 12pt 14pt;
    margin-top: 14pt;
  }}
  .method-box h3 {{
    margin: 0 0 6pt 0;
    font-size: 10.5pt;
    color: #0C4A6E;
  }}
  .method-list {{
    margin: 0;
    padding-left: 16pt;
    font-size: 8.8pt;
  }}
  .method-list li {{ margin-bottom: 4pt; }}

  /* Headings */
  h1.sec-title {{
    font-size: 14.5pt;
    font-weight: 800;
    color: #0C4A6E;
    border-bottom: 2pt solid #0284C7;
    padding-bottom: 5pt;
    margin: 18pt 0 10pt 0;
    page-break-after: avoid;
  }}
  h2.sub-title {{
    font-size: 11pt;
    font-weight: 800;
    color: #0F172A;
    margin: 12pt 0 6pt 0;
    page-break-after: avoid;
  }}

  /* KPI row */
  .kpi-grid {{
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 8pt;
    margin-bottom: 14pt;
  }}
  .kpi {{
    border-radius: 8pt;
    padding: 10pt 8pt;
    text-align: center;
    border: 1pt solid #E2E8F0;
    background: #F8FAFC;
  }}
  .kpi-num {{
    font-size: 20pt;
    font-weight: 800;
    line-height: 1.1;
  }}
  .kpi-lbl {{
    font-size: 7.8pt;
    font-weight: 700;
    text-transform: uppercase;
    color: #475569;
    margin-top: 3pt;
  }}

  /* Charts */
  .charts-grid {{
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12pt;
    margin: 10pt 0 14pt 0;
    page-break-inside: avoid;
  }}
  .chart-card {{
    border: 1pt solid #E2E8F0;
    border-radius: 8pt;
    padding: 10pt 12pt;
    background: #FFFFFF;
  }}
  .chart-title {{
    font-size: 9.5pt;
    font-weight: 800;
    color: #0F172A;
    margin-bottom: 6pt;
  }}

  /* Strengths & Weaknesses */
  .strength-card {{
    border-left: 3.5pt solid #059669;
    background: #ECFDF5;
    border-radius: 6pt;
    padding: 8pt 11pt;
    margin-bottom: 7pt;
    page-break-inside: avoid;
  }}
  .strength-title {{
    font-weight: 800;
    color: #065F46;
    font-size: 9.2pt;
    margin-bottom: 2pt;
  }}
  .strength-card p {{
    margin: 0;
    font-size: 8.6pt;
    color: #1E293B;
  }}
  .weakness-box {{
    border-left: 3.5pt solid #EA580C;
    background: #FFF7ED;
    border-radius: 6pt;
    padding: 9pt 12pt;
    margin-top: 8pt;
    page-break-inside: avoid;
  }}
  .weakness-box ul {{
    margin: 4pt 0 0 0;
    padding-left: 16pt;
    font-size: 8.8pt;
  }}
  .weakness-box li {{ margin-bottom: 3pt; }}

  /* Tables */
  table.audit-table {{
    width: 100%;
    border-collapse: collapse;
    font-size: 8.4pt;
    margin-top: 6pt;
  }}
  table.audit-table th {{
    background: #0F172A;
    color: #FFFFFF;
    text-align: left;
    padding: 7pt 7pt;
    font-size: 8.2pt;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.4pt;
  }}
  table.audit-table td {{
    border-bottom: 0.75pt solid #CBD5E1;
    padding: 7pt 7pt;
  }}
  table.audit-table tr {{
    page-break-inside: avoid;
  }}
  table.audit-table tbody tr:nth-child(even) {{
    background: #F8FAFC;
  }}

  .chip {{
    display: inline-block;
    font-size: 7.2pt;
    font-weight: 800;
    padding: 2pt 6pt;
    border-radius: 999pt;
    letter-spacing: 0.4pt;
  }}
  .cat-tag {{
    font-size: 7.4pt;
    color: #475569;
    margin-top: 4pt;
    font-weight: 600;
  }}
  code {{
    font-family: "DejaVu Sans Mono", monospace;
    font-size: 7.4pt;
    background: #F1F5F9;
    padding: 1pt 3pt;
    border-radius: 3pt;
    color: #0F172A;
  }}
  .finding-title {{
    font-weight: 800;
    font-size: 9pt;
    color: #0F172A;
    margin-bottom: 3pt;
  }}
  .finding-desc {{
    margin: 0 0 4pt 0;
    font-size: 8.3pt;
    color: #1E293B;
  }}
  .finding-exp {{
    margin: 4pt 0 0 0;
    font-size: 8.1pt;
    color: #334155;
  }}
  .code-mini {{
    font-family: "DejaVu Sans Mono", monospace;
    font-size: 7.2pt;
    background: #0F172A;
    color: #E2E8F0;
    padding: 5pt 7pt;
    border-radius: 4pt;
    margin: 4pt 0;
    white-space: pre-wrap;
    word-break: break-word;
  }}

  /* Issues */
  .page-break {{ page-break-before: always; }}
  .issue-block {{
    border: 1pt solid #CBD5E1;
    border-radius: 7pt;
    margin-bottom: 12pt;
    overflow: hidden;
    page-break-inside: avoid;
  }}
  .issue-header {{
    background: #1E293B;
    color: #F8FAFC;
    padding: 6pt 10pt;
    font-size: 8.6pt;
    font-weight: 700;
  }}
  .issue-pre {{
    margin: 0;
    padding: 8pt 10pt;
    font-family: "DejaVu Sans Mono", monospace;
    font-size: 7.2pt;
    line-height: 1.38;
    background: #F8FAFC;
    color: #0F172A;
    white-space: pre-wrap;
    word-break: break-word;
  }}
</style>
</head>
<body>

  <!-- CAPA -->
  <section class="cover">
    <div>
      <div class="cover-banner">
        <div class="cover-kicker">Auditoria Estática de Código &amp; Infraestrutura</div>
        <h1 class="cover-title">Relatório de Auditoria de Segurança — MarAberto</h1>
        <p class="cover-sub">
          Revisão sistemática de isolamento de dados, autorização RBAC, IDOR, gestão de segredos e sanitização de entradas (XSS).
        </p>
      </div>

      <div class="meta-grid">
        <div class="meta-card">
          <div class="meta-label">Data da Auditoria</div>
          <div class="meta-value">26 de novembro de 2026</div>
        </div>
        <div class="meta-card">
          <div class="meta-label">Branch de Trabalho</div>
          <div class="meta-value">fix/security-audit-vulnerabilities</div>
        </div>
        <div class="meta-card">
          <div class="meta-label">Stack Detectada — Backend</div>
          <div class="meta-value">Java 21 · Spring Boot 3.3.4 · Spring Modulith 1.2.13 · PostgreSQL 16/PostGIS · JDBC/JPA · JWT HS256 + Cookie Refresh · STOMP/WS</div>
        </div>
        <div class="meta-card">
          <div class="meta-label">Stack Detectada — Frontend &amp; Deploy</div>
          <div class="meta-value">React 19 · Vite 8 · TypeScript 6 · Tailwind 4 · MapLibre GL · Docker Compose · Nginx unprivileged · Garage S3 · Redis · RabbitMQ · GitHub Actions</div>
        </div>
      </div>

      <div class="method-box">
        <h3>Escopo Auditado e Nota Metodológica</h3>
        <p style="margin:0 0 6pt 0;font-size:8.8pt;">
          Foram inspecionados <strong>100% dos 24 controllers REST</strong> do backend, o interceptor WebSocket/STOMP,
          os serviços de domínio/repositórios JDBC e JPA, as rotas/componentes do frontend (<code>web/src/</code>),
          os manifestos Docker/Nginx/Garage/CI e o histórico Git. Cada categoria foi mapeada para a arquitetura do MarAberto:
        </p>
        <ul class="method-list">
          <li><strong>1. Banco sem Tranca:</strong> isolamento por operador (<code>user_id</code>/<code>responder_id</code>) e escopo territorial regional (<code>user_beach_scopes</code>) em consultas JDBC/JPA e PostGIS.</li>
          <li><strong>2. Permissão no Navegador:</strong> cruzamento de todos os gates <code>RequireRole</code> (<code>web/src/app/routes.tsx</code>) com <code>SecurityConfig.java</code> e <code>@PreAuthorize</code>.</li>
          <li><strong>3. IDOR:</strong> revisão de todos os handlers com IDs em path/query/body (ocorrências, despachos, postos, bandeiras, sessões, mídia, triagem).</li>
          <li><strong>4. Chaves Expostas:</strong> segredos hardcoded em código, <code>application*.yml</code>, <code>docker-compose*.yml</code>, <code>garage.toml</code>, validações de startup <code>prod</code>, bundle Vite e histórico Git.</li>
          <li><strong>5. Inputs sem Tratamento (XSS):</strong> sinks DOM/React (<code>dangerouslySetInnerHTML</code>, <code>href</code>, <code>iframe</code>), CSP do Nginx, <code>DOMPurify</code>, <code>@Sanitized</code>/<code>TextSanitizer</code> e parser XML/KML (XXE).</li>
        </ul>
      </div>
    </div>

    <div style="font-size:8.2pt;color:#64748B;border-top:1pt solid #E2E8F0;padding-top:8pt;">
      Todos os achados reportados neste documento foram verificados diretamente no código-fonte real do repositório, com caminho de arquivo, linha exata e condição de explorabilidade.
    </div>
  </section>

  <!-- RESUMO EXECUTIVO -->
  <h1 class="sec-title">1. Resumo Executivo</h1>
  <p style="margin-top:0;">
    A auditoria identificou <strong>8 achados acionáveis</strong> (0 Crítica, 2 Alta, 4 Média e 2 Baixa) e confirmou
    <strong>5 pontos fortes</strong> estruturais — destacando-se a <strong>ausência total de falhas na Categoria 2 (Permissão no Navegador)</strong>,
    com autorização RBAC rigorosamente aplicada no servidor em todas as rotas. Os riscos centrais concentram-se na
    <strong>montagem de segredos estáticos do Garage em produção</strong>, no <strong>vazamento cross-escopo na detecção de duplicatas de ocorrências</strong>
    e em <strong>lacunas de aplicação do escopo territorial (<code>user_beach_scopes</code>)</strong> nos módulos de estruturas, bandeiras e criação manual de ocorrências.
  </p>

  <div class="kpi-grid">
    <div class="kpi" style="border-top:3pt solid #B91C1C;">
      <div class="kpi-num" style="color:#B91C1C;">0</div>
      <div class="kpi-lbl">Crítica</div>
    </div>
    <div class="kpi" style="border-top:3pt solid #EA580C;">
      <div class="kpi-num" style="color:#EA580C;">2</div>
      <div class="kpi-lbl">Alta</div>
    </div>
    <div class="kpi" style="border-top:3pt solid #D97706;">
      <div class="kpi-num" style="color:#D97706;">4</div>
      <div class="kpi-lbl">Média</div>
    </div>
    <div class="kpi" style="border-top:3pt solid #2563EB;">
      <div class="kpi-num" style="color:#2563EB;">2</div>
      <div class="kpi-lbl">Baixa</div>
    </div>
    <div class="kpi" style="border-top:3pt solid #059669;">
      <div class="kpi-num" style="color:#059669;">5</div>
      <div class="kpi-lbl">Pontos Fortes</div>
    </div>
  </div>

  <div class="charts-grid">
    <div class="chart-card">
      <div class="chart-title">Distribuição de Achados por Severidade</div>
      {build_donut_svg()}
    </div>
    <div class="chart-card">
      <div class="chart-title">Achados por Categoria Auditada</div>
      {build_bar_svg()}
    </div>
  </div>

  <!-- PONTOS FORTES E FRACOS -->
  <h1 class="sec-title">2. Pontos Fortes e Pontos Fracos</h1>
  <h2 class="sub-title" style="color:#065F46;">Pontos Fortes (Controles Verificados)</h2>
  {strengths_html}

  <div class="weakness-box">
    <div style="font-weight:800;color:#9A3412;font-size:9.5pt;">⚠ Pontos Fracos (Riscos Centrais)</div>
    <ul>
      <li><strong>Infraestrutura S3/Cache em produção:</strong> <code>garage.toml</code> com <code>admin_token</code>/<code>rpc_secret</code> fixos é montado em <code>docker-compose.prod.yml</code>, e o Redis de produção opera sem <code>--requirepass</code>.</li>
      <li><strong>Vazamento cross-escopo em ocorrências:</strong> <code>findNearbyDuplicates</code> consulta <code>occurrences</code> e <code>emergency_calls</code> globalmente sem aplicar <code>SCOPE_SQL</code>.</li>
      <li><strong>Escopo territorial incompleto:</strong> <code>user_beach_scopes</code> é aplicado em equipe, triagem e leitura de ocorrências, mas ausente em postos (<code>OperationStationController</code>), bandeiras (<code>FlagController</code>) e criação manual de ocorrências.</li>
      <li><strong>Defesa em profundidade na sanitização:</strong> <code>TextSanitizer.normalizePlain</code> decodifica entidades HTML depois de rodar o OWASP HTML Sanitizer, permitindo persistir tags via duplo-encoding.</li>
    </ul>
  </div>

  <!-- TABELA DE ACHADOS -->
  <h1 class="sec-title page-break">3. Achados Detalhados por Categoria</h1>
  <table class="audit-table">
    <thead>
      <tr>
        <th style="width:13%;">Severidade</th>
        <th style="width:31%;">Arquivo:linha</th>
        <th style="width:56%;">Descrição e Evidência</th>
      </tr>
    </thead>
    <tbody>
      {''.join(findings_rows)}
    </tbody>
  </table>

  <!-- RECOMENDAÇÕES PRIORIZADAS -->
  <h1 class="sec-title">4. Recomendações Priorizadas</h1>
  <table class="audit-table">
    <thead>
      <tr>
        <th style="width:17%;">Prioridade</th>
        <th style="width:33%;">Ação</th>
        <th style="width:50%;">Diretriz Técnica</th>
      </tr>
    </thead>
    <tbody>
      {recs_html}
    </tbody>
  </table>

  <!-- ISSUES PARA O GITHUB -->
  <h1 class="sec-title page-break">5. Issues para o GitHub</h1>
  <p style="margin-top:0;font-size:8.8pt;color:#334155;">
    Abaixo constam as <strong>7 issues acionáveis</strong> em Markdown prontas para copiar e colar no GitHub
    (F-06 e F-08 foram consolidados na Issue #6 por tratarem do mesmo tema de endurecimento de credenciais/defaults de ambiente).
  </p>
  {''.join(issues_html)}

</body>
</html>
"""


def find_chromium() -> str:
    for candidate in ("chromium", "google-chrome", "chromium-browser"):
        path = shutil.which(candidate)
        if path:
            return path
    raise RuntimeError("Chromium/Google Chrome não encontrado no PATH.")


def main() -> None:
    ROOT_DIR.mkdir(parents=True, exist_ok=True)
    html_content = render_html()
    chromium = find_chromium()

    with tempfile.TemporaryDirectory(prefix="maraberto-pdf-") as tmp_dir:
        tmp_path = Path(tmp_dir)
        tmp_html = tmp_path / "report.html"
        tmp_html.write_text(html_content, encoding="utf-8")
        profile_dir = tmp_path / "chromium-profile"
        profile_dir.mkdir(exist_ok=True)

        env = {
            **os.environ,
            "HOME": str(tmp_path),
            "XDG_CONFIG_HOME": str(tmp_path / "config"),
            "XDG_CACHE_HOME": str(tmp_path / "cache"),
        }
        cmd = [
            chromium,
            "--headless=new",
            "--disable-gpu",
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-crash-reporter",
            f"--user-data-dir={profile_dir}",
            "--no-pdf-header-footer",
            f"--print-to-pdf={OUTPUT_PDF}",
            tmp_html.as_uri(),
        ]
        result = subprocess.run(cmd, env=env, capture_output=True, text=True, check=False)
        if result.returncode != 0 or not OUTPUT_PDF.exists():
            raise RuntimeError(
                f"Falha ao gerar PDF ({result.returncode}): {result.stderr}"
            )

    print(f"PDF gerado com sucesso: {OUTPUT_PDF} ({OUTPUT_PDF.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
