# MarAberto — frontend (`web/`)

App único em **React 19 + TypeScript + Vite + Tailwind 4**: o mapa público do banhista, o
console do operador, o painel da coordenação e a área da TI. O mesmo build roda em três
lugares, sem fork de código:

| Destino | Como roda | Observação |
| --- | --- | --- |
| Navegador | `npm run dev` / Nginx | mesma origem, proxy `/api` e `/ws` |
| PWA instalável | build servido pelo Nginx | service worker manual (`public/sw.js`) |
| App Android/iOS | build `--mode capacitor` + Capacitor 8 | WebView, plugins nativos (ADR-019) |

A arquitetura do frontend é **feature-based** (ADR-015): `app/` compõe, `shared/` é
reutilizável e `features/` tem um domínio por pasta. As fronteiras são verificadas pelo
ESLint (`eslint.config.js`), então não importe `features/x` de dentro de `features/y` fora
das exceções documentadas.

## Estrutura do código (`src/`)

```text
web/
├── Dockerfile                 # Build estático + Nginx não-root (nginx-unprivileged)
├── nginx.conf                 # CSP restritiva, rate limiting, HSTS condicional e proxy /api + /ws
├── capacitor.config.ts        # Empacotamento WebView Android/iOS (ADR-019)
├── eslint.config.js           # Lint + regras de fronteira entre features
├── ARCHITECTURE.md            # Detalhes de arquitetura, estado, mapa e build
├── public/                    # sw.js (service worker manual), manifest.webmanifest, offline.html
└── src/
    ├── app/                   # Casca da aplicação: composição de rotas, layouts e páginas raiz
    │   ├── routes.tsx         # Definição de rotas públicas e guardas RequireRole (UX)
    │   ├── layout/            # RootLayout, OperatorLayout, ManagementLayout, AdminLayout, ProfileLayout
    │   ├── pages/             # HomePage (mapa público), AboutPage, NotFoundPage
    │   └── store/             # Preferências não sensíveis de interface
    ├── shared/                # Código reutilizável sem dependência de features
    │   ├── components/        # Card, Badges, PhotoGallery, DemoNotice, NotFoundCard
    │   ├── lib/               # api-client, sanitize (DOMPurify), geo, media, push, native, overlay
    │   └── types/             # Contratos TypeScript compartilhados (API, geo, flags, content)
    └── features/              # Módulos de domínio isolados (importação externa apenas via index.ts)
        ├── auth/              # Login único, sessão em memória, refresh, troca de senha, perfil e MFA
        ├── map/               # MapLibre GL, PMTiles, camadas e marcadores acessíveis
        ├── beaches/           # Mapa/lista de áreas de banho, ficha da praia, zonas de risco e ocupação
        ├── signals/           # Bandeiras vigentes, fotos e alertas em área não mapeada
        ├── stations/          # Postos fixos e bases móveis (público, campo e administração)
        ├── sos/               # Acionamento de SOS pelo banhista (idempotência + validação)
        ├── operator/          # Console do guarda-vidas (/operador): plantão, fila SOS, STOMP e alarme
        ├── occurrences/       # Listagem paginada por cursor, filtros, ficha e encerramento
        ├── management/        # Painel da Coordenação (/gestao): mapa de situação, equipe, KML, CMS e triagem
        ├── admin/             # Painel da TI (/admin): usuários, postos e trilha de auditoria
        ├── learn/             # Central Aprender (/aprender) com facade youtube-nocookie.com
        └── tides/             # Inativo na v1 (rota /mares redireciona para /)
```

### Anatomia padrão de uma feature

Dentro de `src/features/<nome>/`:
- `index.ts` — **barril público** (único ponto de entrada permitido para importações externas).
- `api/` — chamadas tipadas usando `shared/lib/api-client.ts`.
- `hooks/` — estado assíncrono, polling e sincronização.
- `components/` — componentes visuais e fichas (`*Sheet.tsx`).
- `pages/` — telas roteadas por `app/routes.tsx`.
- `lib/` — funções puras e testes unitários (`*.test.ts`).
- `types.ts` — tipos específicos da feature.

### Guia rápido para contribuir

1. **Nova tela ou fluxo:** adicione a página na feature correspondente, exporte em `features/<nome>/index.ts` e registre a rota em `src/app/routes.tsx` com `lazy(...)` e `RequireRole` adequado.
2. **Importações cross-feature:** sempre importe do barril (`@/features/<nome>`), nunca de caminhos internos de outra feature. Rode `npm run lint` para validar as fronteiras.
3. **Marcadores MapLibre:** nunca aplique `transform`, `scale` ou `transition` no elemento raiz do marcador — use o filho interno (`.mld-marker-inner` em `features/map/lib/marker-element.ts`).
4. **Overlays acessíveis:** use `useOverlay` (`shared/lib/overlay.ts`) com `<dialog open>` / `<output>`.
5. **Verificação antes do PR:** `npm run lint && npm run build` devem passar sem erros ou warnings bloqueantes.

## Requisitos

- **Node.js ≥ 22** (o CLI do Capacitor 8 exige; o projeto foi validado com Node 24)
- Backend rodando em `http://localhost:8080` para o login real e o console do operador
- Para gerar o APK: **JDK 21** e **Android SDK** com `platforms;android-36` + build-tools
  (`ANDROID_HOME`/`ANDROID_SDK_ROOT` ou `sdk.dir` em `web/android/local.properties`). iOS
  exige macOS + Xcode. Ícones/splash de marca ainda não existem — os atuais são os padrões
  do Capacitor.

## Desenvolvimento

```bash
cp .env.example .env     # ajuste o que precisar
npm install
npm run dev              # http://localhost:3000
```

O Vite faz proxy de `/api` e `/ws` (WebSocket do STOMP) para `VITE_PROXY_TARGET`
(padrão `http://localhost:8080`). Se aparecer `504 Outdated Optimize Dep`, limpe o cache:

```bash
rm -rf node_modules/.vite && npm run dev -- --force
```

Contas de desenvolvimento (com `IDENTITY_DEV_SEED=true` no backend), senha `demo1234`:

| Perfil | Login | Área |
| --- | --- | --- |
| Guarda-vidas (`LIFEGUARD`) | `operador@maraberto.dev` | `/operador` |
| Coordenação (`COORDINATOR`) | `coordenador@maraberto.dev` | `/gestao` |
| TI (`ADMIN`) | `admin@maraberto.dev` | `/admin` |

## Variáveis de ambiente

Todas são lidas em tempo de build (`VITE_*`) — mudar o `.env` exige reiniciar/recompilar.

| Variável | Padrão | Para que serve |
| --- | --- | --- |
| `VITE_API_BASE_URL` | vazio (mesma origem) | vazio no navegador/PWA; **obrigatório, absoluto e HTTPS** no app |
| `VITE_MAP_DEFAULT_CENTER` | `[-51.9253,-14.2350]` | centro inicial do mapa (Brasil; nada regional no código) |
| `VITE_MAP_DEFAULT_ZOOM` | `4` | zoom inicial |
| `VITE_MAP_BOUNDS` | — | limite opcional de navegação `[[oeste,sul],[leste,norte]]` |
| `VITE_PMTILES_URL` | — | tiles vetoriais próprios (ADR-005) |
| `VITE_DEMO_TILE_URL` | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | basemap raster de demonstração; a origem entra na CSP do Nginx e do app |
| `VITE_APP_NAME` / `VITE_ORGANIZATION_NAME` | `MarAberto` / `Instituto Mar Aberto` | textos de marca |
| `VITE_DEMO_AUTH` | `false` | atalho de login fictício **só** em desenvolvimento |

## Build e verificação

```bash
npm run build    # tsc + vite build → dist/
npm run lint     # ESLint, incluindo as fronteiras entre features
npm run preview  # serve dist/ localmente
```

O `dist/` do build web é o que o Nginx publica (headers e CSP em `nginx.conf`). O service
worker **nunca** cacheia `/api/**`, `/tiles/**`, WebSocket, requisições não-GET nem as
rotas autenticadas `/operador`, `/gestao` e `/admin`.

## App Android/iOS (Capacitor, ADR-019)

O projeto nativo fica **aqui dentro** (`android/`; `ios/` é gerado em macOS). A configuração
está em `capacitor.config.ts` (`appId org.maraberto.app`, `webDir dist`) e o Capacitor
copia o `dist` no `cap sync`.

```bash
cp .env.capacitor.example .env.capacitor   # edite VITE_API_BASE_URL (HTTPS)
npm run cap:sync                           # build --mode capacitor + cap sync android
npm run cap:open                           # abre o Android Studio
```

Ou direto pelo Gradle, sem IDE:

```bash
# local.properties (ignorado pelo git) aponta o SDK; o Android Studio cria,
# ou crie à mão: sdk.dir=/caminho/para/Android/Sdk
cd android
JAVA_HOME=/caminho/para/jdk-21 ./gradlew assembleDebug     # app/build/outputs/apk/debug/
JAVA_HOME=/caminho/para/jdk-21 ./gradlew assembleRelease   # exige keystore configurada
```

`local.properties` e `build/` não vão para o repositório. O wrapper usa Gradle 8.14.3 e
precisa de **JDK 21**; se o Gradle já baixou um toolchain 21 antes, ele fica em
`~/.gradle/jdks/` e pode ser usado como `JAVA_HOME`. O Android Studio também traz um JBR 21
em `<studio>/jbr`.

Os ícones e o splash atuais são os **padrões do Capacitor**: ainda não há arte de marca no
repositório. Quando existir, gere os assets e rode `npx @capacitor/assets generate` (ou
substitua `android/app/src/main/res/mipmap-*` e `drawable*/splash.png`).

O que muda no build nativo e **não pode ser esquecido**:

- `VITE_API_BASE_URL` absoluto e HTTPS — no WebView a origem é `https://localhost`
  (Android) / `capacitor://localhost` (iOS), então `/api` relativo apontaria para o app.
- A CSP vai por `<meta>` no `index.html` (injetada pelo `vite.config.ts`), porque no app
  não existe Nginx.
- O **service worker não é registrado** no nativo (`shared/lib/pwa.ts`): no WKWebView ele
  não existe e o bundle já é local. Por isso `offline.html`, prompt de instalação e
  notificação via SW não valem no app — o alerta do SOS usa notificação nativa.
- Nenhuma feature importa `@capacitor/*`: a detecção de plataforma e os adaptadores
  dos plugins ficam em `shared/lib/platform.ts` e `shared/lib/native.ts`, sempre com o
  comportamento web como padrão (`shared/lib/geolocation.ts`, `shared/lib/pwa.ts`).
- **Sessão pelo cookie nativo** — como o backend não abre CORS e o cookie é
  `SameSite=Strict`, todas as chamadas do app saem por `CapacitorHttp` (`executeNative` em
  `shared/lib/api-client.ts`). O plugin core `CapacitorCookies` registra o `CookieHandler`
  do processo com o `CookieManager` do WebView, então o refresh (`Path=/api/v1/auth`) é
  enviado no refresh/logout e o `Set-Cookie` do login é guardado **automaticamente**,
  inclusive entre reinícios — o JavaScript nunca vê o valor. O access token continua só em
  memória; nada de token em `localStorage`/`sessionStorage`/Preferences. O backend **não
  muda**: sem CORS, sem `SameSite=None`.
- **STOMP no app** — o handshake leva `Origin: https://localhost` (Android) /
  `capacitor://localhost` (iOS); o backend precisa listar essas origens em
  `REALTIME_ALLOWED_ORIGINS` (allow-list do WebSocket, não CORS).
- **Push (Firebase)** — o `google-services.json` do projeto fica em
  `android/app/google-services.json` e **não é versionado** (a API key do app é pública,
  mas o arquivo é por projeto/ambiente). Copie do console do Firebase antes de gerar o
  APK: o `app/build.gradle` aplica o plugin `com.google.gms.google-services` só quando o
  arquivo existe, e sem ele o app compila normalmente, porém sem push.
- `android:allowBackup="false"` no `AndroidManifest.xml` (o backup levaria o storage do
  WebView), permissões mínimas declaradas e sem cleartext em release.

### Push no app (registro e recebimento)

`@capacitor/push-notifications` já está instalado e o `shared/lib/push.ts` é o único ponto
que fala com ele. O registro acontece dentro do gesto de **armar o alerta do SOS**
(`armSosAlerts`, em `features/operator/lib/sos-alarm.ts`): pede a permissão, registra o
aparelho no FCM e, com o app aberto, transforma a mensagem recebida em notificação local no
canal do SOS (o Android não exibe sozinho a mensagem entregue ao processo). O toque na
notificação abre `data.url` quando for caminho interno do app.

Para conferir que o `google-services.json` está correto, sem backend envolvido:

```bash
# token FCM no logcat (aparece só com VITE_PUSH_DEBUG=true ou em dev)
adb logcat -s Capacitor/Console | grep '\[push\] token'
```

Com o token em mãos, use **Firebase Console → Messaging → Nova campanha → Mensagem de
teste** (ou `POST https://fcm.googleapis.com/v1/projects/<projeto>/messages:send` com um
token OAuth de service account).

**O que ainda não existe:** o **envio pelo servidor**. O `google-services.json` é
configuração do *cliente* e não autoriza envio; falta no backend a tabela de tokens, o
endpoint autenticado de registro e o envio FCM HTTP v1 com a chave de service account
(ADR-018, item 6). Até lá, push não substitui o polling de reconciliação do console.

### Ainda pendente no app

O projeto nativo já está gerado, sincroniza e gera **APK debug**
(`android/app/build/outputs/apk/debug/app-debug.apk`). O que falta para distribuir:

1. **Sessão no WebView** — **implementada e validada no emulador**: todas as chamadas usam
   `CapacitorHttp` e o cookie de refresh é tratado pelo jar do processo (`CapacitorCookies` +
   `CookieManager` do WebView), sem afrouxar `SameSite=Strict` e sem mudar o backend. O
   login real foi validado no **emulador Android 15 (API 35)** contra o backend local por
   túnel HTTPS (ver abaixo). Falta repetir em **aparelho físico** (login, reload com
   refresh e logout).
2. **Plugins restantes** — já integrados por `shared/lib/native.ts`: `geolocation`
   (posição pontual), `local-notifications` (SOS), `@capacitor-community/keep-awake` e
   `push-notifications` (registro no FCM, ver seção acima). Falta `camera` (captura de
   foto); `browser` não é necessário porque o `Bridge` já abre `http(s)`/`tel:` no app
   externo.
3. **Ícone, splash, versão e keystore** de release (`versionCode`/`versionName`).
4. **iOS** — `npx cap add ios` em macOS; avaliar App Store x app não listado (diretriz 4.2).
5. **Teste em aparelho físico** — refresh, GPS, notificação, sirene e keep-awake no
   Android real (o login já foi validado no emulador).
6. **Alerta com o app fechado** — hoje o popup/sirene/notificação só acontecem com a mesa
   em **primeiro plano**. Medido no emulador: 60 s minimizado ou tela bloqueada → nenhum
   alerta; o chamado aparece ao voltar ao app. O app já registra no FCM e recebe a
   mensagem; falta o **envio pelo backend** (tabela de tokens + endpoint + FCM HTTP v1 com
   service account) para push de prioridade alta + `fullScreenIntent` (ADR-018 item 6) ou
   um foreground service de plantão.

Detalhes e checklist completo em `docs/adr/019-app-mobile-com-capacitor.md`
e em `docs/Architecture.md` §6.1–§6.2.

### Testar o app no emulador Android

O refresh é cookie `__Secure-`, então o app **precisa de HTTPS de verdade**: apontar para
`http://10.0.2.2:8080` faz o WebView rejeitar o cookie e a sessão não sobrevive. O caminho
que funciona é expor o backend local por um túnel HTTPS:

```bash
# 1. backend local com os seeds de desenvolvimento
cd backend
IDENTITY_DEV_SEED=true OPERATIONS_DEV_SEED=true \
  REALTIME_ALLOWED_ORIGINS="http://localhost:3000,https://localhost,capacitor://localhost" \
  ./gradlew bootRun

# 2. túnel HTTPS para a API (qualquer provedor serve; o exemplo usa cloudflared)
cloudflared tunnel --url http://localhost:8080     # copie a URL https://*.trycloudflare.com

# 3. build do app apontando para o túnel, e instalação
cd web
VITE_API_BASE_URL=https://<sua-url>.trycloudflare.com npm run cap:sync
cd android && JAVA_HOME=/caminho/jdk-21 ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`adb logcat` mostra o tráfego do WebView; o túnel muda de URL a cada execução, então
rebuildar (`cap:sync`) sempre que trocar. Para simular a posição do guarda-vidas:
`adb emu geo fix -35.7088 -9.6659`.

## Segurança (regras que valem para qualquer tela nova)

- **Nunca** persistir token, coordenada, `clientRequestId`, CPF, e-mail ou telefone em
  `localStorage`/`sessionStorage`/Preferences. O access token vive só em memória; o refresh
  é cookie `HttpOnly; Secure; SameSite=Strict`.
- Popup, som e notificação do SOS só são armados em **gesto** do usuário; não há
  rastreamento contínuo da posição (RN17) — a posição do plantão só é renovada com a aba
  visível e a do aparelho só no check-in/cadastro.
- Dado não confiável não vai para `dangerouslySetInnerHTML`; a sanitização de exibição está
  em `shared/lib/sanitize.ts`.
- Erros da API chegam em RFC 9457 (`application/problem+json`) e são tratados como
  identificadores (`type`), nunca como redirecionamento.

## Documentação

- `docs/Architecture.md` — arquitetura, §6 frontend e §6.2 empacotamento mobile
- `docs/adr/` — decisões (015 frontend feature-based, 019 app com Capacitor)
- `docs/Requirements.md` — requisitos e decisões de produto
- `AGENTS.md` na raiz — estado atual do projeto e convenções
