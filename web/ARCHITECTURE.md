# Arquitetura do Frontend (`web/`) — MarAberto

Este documento detalha a arquitetura, as convenções, a segurança no cliente e o guia de customização do frontend do **MarAberto**, preparado para uso como **aplicação web**, **PWA instalável** e **app mobile (Capacitor 8)** por Corpos de Bombeiros Militares (CBMs) de qualquer estado.

---

## 1. Stack Técnica

| Camada | Tecnologia | Observações |
| :--- | :--- | :--- |
| Core | **React 19** + **TypeScript 6** + **Vite 8** | Build único para navegador, PWA e Capacitor. |
| Estilo | **Tailwind CSS 4** | Tokens de cor institucionais (`ocean-*`, `coral-*`) definidos em `src/index.css`. |
| Mapa | **MapLibre GL** + **PMTiles** | Suporta tiles vetoriais auto-hospedados (PMTiles) ou tiles raster configuráveis. |
| Tempo Real | **STOMP over WebSocket** (`@stomp/stompjs`) | Alertas de SOS para operadores e atualização dinâmica do mapa público sem reload. |
| Mobile Nativo | **Capacitor 8** (`web/android`, `capacitor.config.ts`) | Geolocalização, HTTP nativo (cookie jar para refresh token), Splash e FCM (ADR-019). |
| Container de Produção | **Nginx não-root** (`nginxinc/nginx-unprivileged:1.29-alpine`) | Porta `8080`, CSP restritiva, `limit_req` na borda, HSTS automático sob HTTPS e `HEALTHCHECK`. |

---

## 2. Arquitetura Feature-Based (ADR-015)

```text
web/src/
├── app/          # Composição da aplicação: router, providers, AreaShell, OfflineBanner
├── shared/       # Utilitários puros e componentes reutilizáveis (api-client, overlay, sanitize, pwa, format)
└── features/     # Domínios isolados — importação cross-feature apenas via index.ts!
    ├── auth/         # Login único, sessão em memória, MFA TOTP opcional, guards de rota
    ├── beaches/      # Áreas de banho, setores, busca, BeachSheet, página de detalhes
    ├── map/          # MapView (MapLibre), marcadores, camadas, controle de ocupação anônima
    ├── sos/          # SosFab, acionamento público de SOS com confirmação e geolocalização
    ├── stations/     # Postos fixos e bases móveis (público, campo e administração)
    ├── occurrences/  # Listagem paginada por cursor, filtros, ficha e registro manual
    ├── operator/     # Console do guarda-vidas (/operador): plantão, fila SOS, sinalização, postos
    ├── management/   # Painel da Coordenação (/gestao): mapa de situação, equipe, importar KML, CMS Aprender, triagem
    ├── admin/        # Painel de TI (/admin): CRUD de usuários, senha temporária, auditoria, MFA opcional
    ├── learn/        # Página pública /aprender (facade youtube-nocookie.com + materiais da coordenação)
    └── tides/        # Reservado para R2 (inativo na v1; /mares redireciona para /)
```

As fronteiras entre features são verificadas automaticamente pelo ESLint (`web/eslint.config.js` via `no-restricted-imports`).

---

## 3. Segurança no Cliente e LGPD

1. **Zero segredos em `localStorage`/`sessionStorage`:**
   - O access token JWT vive **exclusivamente em memória** (`features/auth/session.ts`).
   - O refresh token opaco reside no cookie `HttpOnly` gerenciado pelo servidor; o JavaScript nunca lê seu valor.
   - Renovação *single-flight* serializada entre abas via `navigator.locks` (`refreshSingleFlight`).
   - Preferências de camadas do mapa de situação da coordenação ficam apenas em memória (`useState`).
2. **Troca obrigatória de senha e MFA opcional:**
   - Se `user.mustChangePassword === true`, todas as rotas redirecionam para `/trocar-senha` (espelhando o bloqueio `403` do backend).
   - Se o usuário (`ADMIN`/`COORDINATOR`) ativar MFA opcionalmente em `MfaSettingsCard`, o `LoginPage` apresenta automaticamente a etapa de código TOTP / código de recuperação.
3. **Sanitização e CSP restritiva:**
   - Textos dinâmicos passam por `sanitizeText` (`shared/lib/sanitize.ts`) antes de renderização ou tooltips do MapLibre.
   - A CSP em `web/nginx.conf` bloqueia `script-src` inline/eval e `object-src 'none'`; vídeos do YouTube usam facade `youtube-nocookie.com` (nenhum iframe carregado antes do clique do usuário).
4. **Ocupação anônima (`occupancy`):**
   - Nenhuma coordenada é enviada sem consentimento explícito do banhista na UI; no máximo 1 amostra por janela de 15 min mantida em memória.

---

## 4. Convenções de UI, MapLibre e Acessibilidade (RNF18)

- **Overlays semânticos:** modais e fichas usam `<dialog open>` gerenciados pelo hook `useOverlay` (`shared/lib/overlay.ts`, que cuida de foco inicial, `Escape`, trap de `Tab` e retorno de foco). Nunca usar `div role="dialog"`.
- **Mensagens de status:** banners e confirmações dinâmicas usam `<output>` (nunca `div role="status"`).
- **Marcadores MapLibre:** nunca aplicar `transform`, `scale` ou `transition` no elemento raiz entregue ao `maplibregl.Marker` (o MapLibre controla o `transform` da raiz para posicionar o pin); animações ficam sempre em um elemento filho interno (`features/map/lib/marker-element.ts`).

---

## 5. Customização para Forks de Outros CBMs

1. **Identidade visual e marca:**
   - Substituir ícones e logomarcas em `web/public/brand/` e `web/public/icons/`.
   - Ajustar nome do aplicativo em `web/index.html`, `web/public/manifest.webmanifest` e `web/capacitor.config.ts`.
   - Ajustar paleta de cores em `web/src/index.css`.
2. **Mapa base (PMTiles próprio vs. OSM):**
   - Em produção institucional, recomenda-se hospedar o arquivo `.pmtiles` do estado no próprio domínio e configurar `VITE_PMTILES_URL` (removendo `https://tile.openstreetmap.org` da CSP em `web/nginx.conf`).
3. **Build e validação:**
   ```bash
   cd web
   npm ci
   npm run lint
   npm run build
   ```
4. **App Android (Capacitor):**
   ```bash
   cd web
   npm run build -- --mode capacitor
   npx cap sync android
   ```
