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
- `android:allowBackup="false"` no `AndroidManifest.xml` (o backup levaria o storage do
  WebView), permissões mínimas declaradas e sem cleartext em release.

### Ainda pendente no app

O projeto nativo já está gerado, sincroniza e gera **APK debug**
(`android/app/build/outputs/apk/debug/app-debug.apk`). O que falta para distribuir:

1. **Sessão no WebView** — **implementada**: todas as chamadas usam `CapacitorHttp` e o
   cookie de refresh é tratado pelo jar do processo (`CapacitorCookies` +
   `CookieManager` do WebView), sem afrouxar `SameSite=Strict` e sem mudar o backend.
   Falta **validar em aparelho real** (login, reload com refresh e logout).
2. **Plugins restantes** — já integrados por `shared/lib/native.ts`: `geolocation`
   (posição pontual), `local-notifications` (SOS) e `@capacitor-community/keep-awake`.
   Faltam `camera` (captura de foto) e `push-notifications` (app fechado, ADR-018 item 6);
   `browser` não é necessário porque o `Bridge` já abre `http(s)`/`tel:` no app externo.
3. **Ícone, splash, versão e keystore** de release (`versionCode`/`versionName`).
4. **iOS** — `npx cap add ios` em macOS; avaliar App Store x app não listado (diretriz 4.2).
5. **Teste em aparelho real** — login, refresh, GPS, notificação, sirene e keep-awake no
   Android físico.

Detalhes e checklist completo em `docs/Implementation.md` (Etapa 8B) e
`docs/adr/019-app-mobile-com-capacitor.md`.

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
