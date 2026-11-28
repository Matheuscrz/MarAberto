# 🌊 MarAberto

> Plataforma aberta e colaborativa de segurança balnear, monitoramento oceanográfico, prevenção de afogamentos e resposta rápida a emergências para praias e áreas litorâneas.

[![Licença MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%2B%20%7C%20PostGIS%203.4-blue.svg)](https://postgis.net/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-blue.svg)](https://www.docker.com/)
[![Contribuições](https://img.shields.io/badge/contribui%C3%A7%C3%B5es-bem--vindas-brightgreen.svg)](Contributing.md)

> ⚠️ **Aviso importante:** o MarAberto é uma ferramenta de **apoio**. Ele **não substitui** o acionamento oficial do Corpo de Bombeiros. Em caso de emergência, **ligue 193**.

---

## 📌 1. Visão Geral do Projeto

O **MarAberto** é uma iniciativa _open source_ desenvolvida para aproximar a população dos serviços de salvamento aquático (Corpo de Bombeiros e guarda-vidas). Diferente de plataformas informativas estáticas, o MarAberto atua em três frentes complementares:

1. **Prevenção Ativa & Conscientização:** Mapeamento visual de zonas de risco permanente (ex.: desembocaduras perigosas de rios, fossas submarinas e rebojos como na Praia dos Artistas), fichas completas das praias e vídeos educativos curados (YouTube) sobre como escapar de correntes de retorno (_rip currents_).
2. **Colaboração Cidadã Confiável:** Estimativa passiva e anônima de ocupação da praia (estilo Waze), envio de reportes comunitários de perigo com validação por quórum geográfico e canal de **SOS Cidadão** para socorro rápido, sem exigir cadastro ou conta prévia do banhista.
3. **Console Tático Operacional:** Aplicação de campo para guarda-vidas atualizarem bandeiramento em tempo real, registrarem postos móveis com fotos (inclusive usando a localização atual), receberem despachos de emergência por proximidade e **registrarem ocorrências** — as geradas por SOS e as que chegaram pelo 193 ou fora do sistema — com uma ficha pronta para transcrever no CIOSP. Um único `/login` leva cada perfil à sua área: operador de campo, gestão do Corpo de Bombeiros e TI.

---

## 🏗️ 2. Arquitetura e Engenharia

O projeto é estruturado como um **Monólito** em **Java 21 com Spring Boot**, dividindo os contextos de domínio de acordo com os princípios do _Domain-Driven Design_ (DDD). Essa abordagem garante alta coesão e baixo acoplamento sem a sobrecarga de infraestrutura de microsserviços.

```
                           +--------------------------------------------+
                           |            CLIENTES (WEB / PWA)            |
                           |   [ Banhista / Cidadão ]   [ Guarda-Vidas ]|
                           +---------------------+----------------------+
                                                 |
                                     HTTPS / WSS (WebSockets)
                                                 v
                           +--------------------------------------------+
                           |            NGINX REVERSE PROXY             |
                           +---------------------+----------------------+
                                                 |
                                                 v
+-------------------------------------------------------------------------------------------------+
|                                SPRING BOOT 3 (MONÓLITO MODULAR)                                 |
|                                                                                                 |
|  [ beaches ]              [ safety ]                  [ emergency ]           [ media ]         |
|  - Polígonos de praia     - Postos Fixos e Móveis     - Despacho SOS          - URLs assinadas  |
|  - Riscos Permanentes     - Bandeiramento do Dia      - STOMP WebSockets      - Sanitiza EXIF   |
|  - Estimativa Lotação     - Upload Fotos (Presigned)  - Quórum Comunitário    - Miniaturas      |
|                                                                                                 |
|  [ occurrences ]          [ search ]                  [ identity / audit ]                      |
|  - Ocorrências (SOS e 193) - Busca tolerante a erros  - Login, papéis, trilha imutável          |
|  - Ficha para o CIOSP     - pg_trgm (sem serviço extra)                                         |
|                                                                                                 |
|  [ content ]              [ shared / kernel ]                                                   |
|  - Vídeos YouTube         - Auth JWT, CPF operacional, Operações Geoespaciais                   |
+------------------------------------------------+------------------------------------------------+
                                                 |
         +---------------------------------------+---------------------------------------+
         |                                       |                                       |
         v                                       v                                       v
+------------------+                   +--------------------+                  +------------------+
|    PostgreSQL    |                   |       Redis        |                  |    Garage/S3     |
|    + PostGIS     |                   | (Cache de Mapas e  |                  | (Object Storage  |
| (SRID 4326/GiST) |                   |  Limiter)          |                  |   Fotos/Mídias)  |
+------------------+                   +--------------------+                  +------------------+
```

### Principais Decisões Tecnológicas:

- **PostgreSQL 16 + PostGIS:** Permite cálculos esféricos precisos via tipo `geography(Point, 4326)` e geofencing com `geometry(Polygon, 4326)` indexados com GiST para consultas espaciais de alta performance (`ST_Contains`, `ST_DWithin`).
- **MapLibre GL JS + OpenStreetMap:** Camada de visualização de mapas sem custos de faturamento por visualização de tiles, substituindo APIs pagas do Google Maps.
- **Upload via Presigned URLs (S3-compatível):** O backend não atua como intermediário no tráfego de imagens pesadas; ele gera links temporários pré-assinados e o cliente envia a foto diretamente para o storage. O código fala a API S3, então o armazenamento pode ser trocado sem alterar o domínio.
- **STOMP sobre WebSockets:** Notificações em tempo real para os postos de guarda-vidas com latência sub-segundo em ocorrências de SOS.
- **Autenticação embutida:** registro e login são do próprio sistema (JWT de vida curta + refresh com rotação, MFA para perfis operacionais), sem dependência de provedor de identidade externo.
- **Eventos com Outbox transacional:** o alerta de SOS só é notificado depois de persistido, e eventos pendentes são reenviados após uma reinicialização.

> Os detalhes e a justificativa de cada decisão estão em [`docs/Architecture.md`](docs/Architecture.md).

---

## 🎯 3. Matriz de Funcionalidades

| Domínio          | Módulo                          | Descrição Detalhada                                                                                                                                                                                                |
| :--------------- | :------------------------------ | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Banhista**     | Mapa Dinâmico Multicamadas      | Visualização de postos ativos, bandeiras do dia, áreas de banho seguras e alertas comunitários.                                                                                                                    |
| **Banhista**     | Zonas Críticas Permanentes      | Demarcação explícita de áreas de alto perigo estático (ex.: desembocadura do rio com o mar, pedrais submersos, rebojos profundos).                                                                                 |
| **Banhista**     | Tábua de Marés (R2)             | Roadmap: preamar/baixa-mar do dia, altura, tendência e destaque de sizígia. Fora da v1 até existir provider oficial validado — a aba `/mares` não é publicada nem aparece na navegação. |
| **Banhista**     | Hub Educativo (YouTube)         | Vídeos informativos embutidos (_lazy-loaded_) com instruções de auto-resgate, fuga de correntes de retorno e primeiros socorros.                                                                                   |
| **Banhista**     | SOS Cidadão (Botão de Pânico)   | Disparo georreferenciado de socorro imediato para guarda-vidas da circunscrição, sem cadastro ou CPF prévio.                                                                                                       |
| **Banhista**     | Nível de Lotação "Waze"         | Estimativa anônima de ocupação da praia através de geocheck no dispositivo sem persistência de rastro do usuário.                                                                                                  |
| **Guarda-Vidas** | Gestão de Postos e Bases Móveis | Cadastro, reposição e ativação do posto/base na posição atual, com foto; o console lista todas as estruturas com distância, idade e precisão da posição. |
| **Guarda-Vidas** | Bandeiramento & Correntes       | Atualização da sinalização diária (verde, amarela, vermelha) com fotos comprobatórias das bandeiras fincadas.                                                                                                      |
| **Guarda-Vidas** | Moderação e Quórum              | Homologação de reportes da população ou promoção automática caso $\ge 3$ banhistas distintos relatem o mesmo problema em 100 m.                                                                                    |
| **Guarda-Vidas** | Central Receptora de SOS        | Alertas visuais e sonoros com cálculo métrico de distância e rota até o banhista solicitante.                                                                                                                      |
| **Guarda-Vidas** | Ocorrências e Ficha CIOSP       | Todo SOS **assumido** gera uma ocorrência; o guarda-vidas encerra com relato e desfecho e a coordenação completa depois o protocolo do CIOSP e os dados da vítima. Ocorrências do 193 ou de fora do sistema entram pelo registro manual. |
| **Gestão**       | Acompanhamento do Campo         | Área `/gestao`: plantões, SOS em andamento, ocorrências e pendências de registro no CIOSP por posto.                                                                                                               |
| **Gestão**       | Painel Tático e Estatísticas    | Mapa de calor (_heatmap_) de ocorrências e inteligência geoespacial para distribuição preventiva de efetivo.                                                                                                       |
| **TI**           | Administração da Instalação     | Área `/admin`: cadastro e convite de usuários, papéis e configuração regional.                                                                                                                                     |
| **Todos**        | Busca tolerante a erros         | Encontra praias, postos e pontos mesmo com erro de digitação (PostgreSQL `pg_trgm`).                                                                                                                               |

---

## 📚 4. Documentação

| Documento                                                                                                                | Conteúdo                                                                                                               |
| :----------------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------- |
| [`docs/Requirements.md`](docs/Requirements.md)                                                                           | Visão, personas, requisitos funcionais e não funcionais, regras de negócio, escala e decisões registradas.             |
| [`docs/Architecture.md`](docs/Architecture.md)                                                                           | Arquitetura, módulos, modelo de dados, fluxos críticos (SOS), cache, segurança, observabilidade, implantação e riscos. |
| [`backend/ARCHITECTURE.md`](backend/ARCHITECTURE.md)                                                                     | Guia técnico detalhado do backend (Spring Modulith, PostGIS, segurança, Outbox, RabbitMQ, Web Push) para forks estaduais. |
| [`web/ARCHITECTURE.md`](web/ARCHITECTURE.md)                                                                             | Guia técnico detalhado do frontend (Feature-Based, MapLibre, PWA, Capacitor 8, RNF18) para forks estaduais.            |
| [`perf/k6/README.md`](perf/k6/README.md)                                                                                 | Suíte de testes de carga k6 para validação de capacidade e rate-limits.                                                |
| [`docs/plan/fatias-10-13-operacao-gestao-admin-ocupacao.md`](docs/plan/fatias-10-13-operacao-gestao-admin-ocupacao.md)   | Backlog detalhado das próximas fatias: console de campo, mapa da coordenação, administração de identidade/auditoria e ocupação pública. |
| [`Contributing.md`](Contributing.md)                                                                                     | Como contribuir, padrões de código e checklist de Pull Request.                                                        |
| [`Security.md`](Security.md)                                                                                             | Como reportar vulnerabilidades, modelo de sanitização/criptografia e recomendações de implantação.                     |
| [`docs/adr/012-protecao-de-dados-sensiveis-e-sanitizacao.md`](docs/adr/012-protecao-de-dados-sensiveis-e-sanitizacao.md) | Decisão sobre sanitização de entrada e criptografia de dados sensíveis em repouso.                                     |
| [`docs/adr/013-ocorrencias-operacionais.md`](docs/adr/013-ocorrencias-operacionais.md)                                   | Ocorrência como entidade própria, criada no aceite do SOS, encerrada pelo responsável e editável pela coordenação (CIOSP e vítima). |
| [`docs/adr/014-busca-tolerante-a-erros.md`](docs/adr/014-busca-tolerante-a-erros.md)                                     | Busca com `pg_trgm` antes de OpenSearch e critérios para reavaliar.                                                    |
| [`docs/adr/015-frontend-feature-based.md`](docs/adr/015-frontend-feature-based.md)                                       | Estrutura `app/ shared/ features/` do frontend e regras de fronteira.                                                  |
| [`docs/adr/017-geoindexacao-do-sos.md`](docs/adr/017-geoindexacao-do-sos.md)                                             | Coluna geográfica gerada + GiST, `ST_DWithin`/KNN para postos e geofence que não bloqueia o SOS.                        |
| [`docs/adr/018-selecao-de-destinatarios-e-entrega-do-sos.md`](docs/adr/018-selecao-de-destinatarios-e-entrega-do-sos.md) | O banco decide quem notificar e o transporte só entrega; sem filtro geográfico no broker.                              |
| [`AGENTS.md`](AGENTS.md)                                                                                                 | Memória de contexto para agentes de IA e convenções do repositório.                                                    |
| [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md)                                                                               | Regras de convivência da comunidade.                                                                                   |

---

## 🚀 5. Como Hospedar e Rodar em seu Servidor (Release v1)

O MarAberto foi projetado para ser auto-hospedado por qualquer Corpo de Bombeiros Militar (CBM) ou equipe técnica via **Docker Compose**, sem nenhum dado regional fixo no código.

### Pré-requisitos

- **Docker** e **Docker Compose** (v2+) no servidor
- *(Apenas para desenvolvimento fora do Docker)*: **Java 21 LTS** e **Node.js 22+** (validado com Node 24)

---

### Passo 1 — Clonar e configurar o ambiente (`.env`)

```bash
git clone https://github.com/Matheuscrz/MarAberto.git
cd MarAberto
cp .env.example .env
```

Para **ambientes de homologação/produção**, gere chaves criptográficas exclusivas e substitua no `.env` (nunca use os valores de exemplo em produção):

```bash
# Chave mestra AES-256-GCM (32 bytes em base64)
openssl rand -base64 32   # -> CRYPTO_MASTER_KEY

# Segredo de assinatura JWT HS256 (mínimo 32 bytes)
openssl rand -base64 48   # -> JWT_SECRET
```

| Variável | QA / Avaliação local (HTTP) | Produção (HTTPS) |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | *(vazio / padrão)* | `prod` *(ou `prod,rabbit` com RabbitMQ)* |
| `CRYPTO_MASTER_KEY` | Valor do `.env.example` | **Obrigatório gerar** (`openssl rand -base64 32`) |
| `JWT_SECRET` | Valor do `.env.example` | **Obrigatório gerar** (`openssl rand -base64 48`) |
| `POSTGRES_PASSWORD` / `GARAGE_SECRET_KEY` / `RABBITMQ_PASSWORD` | Valor do `.env.example` | **Trocar por senhas fortes** |
| `SECURITY_COOKIE_SECURE` | `false` *(funciona em HTTP/LAN)* | `true` *(exigido pelo perfil `prod`)* |
| `SECURITY_ALLOWED_ORIGINS` | `http://localhost,http://127.0.0.1` | `https://seu-dominio.gov.br,https://localhost,capacitor://localhost` |
| `REALTIME_ALLOWED_ORIGINS` | `http://localhost,http://127.0.0.1` | `https://seu-dominio.gov.br` |
| `ADMIN_BOOTSTRAP_ENABLED` | `true` *(cria `admin` se não houver ADMIN ativo)* | `true` na 1ª subida (pode desligar após trocar a senha) |
| `ADMIN_BOOTSTRAP_PASSWORD` | `admin` | `admin` ou senha inicial customizada |

> 🔒 **Travas automáticas do perfil `prod`:** em produção, utilize `docker-compose.prod.yml` (`docker compose -f docker-compose.prod.yml up -d --build`), que exige todos os segredos via `.env` sem defaults e ativa `SPRING_PROFILES_ACTIVE=prod`. Com `prod` ativo, o backend **recusa subir** se detectar `CRYPTO_MASTER_KEY` padrão, `JWT_SECRET` padrão, `SECURITY_COOKIE_SECURE=false`, origens HTTP sem TLS, `STORAGE_PROVIDER=stub`, credenciais padrão de PostgreSQL/Garage/RabbitMQ/Admin (`maraberto`, `maraberto-secret-key`, `admin`) ou `*_DEV_SEED=true`, e desliga automaticamente o Swagger/OpenAPI.

---

### Passo 2 — Subir toda a stack com Docker Compose

#### Opção A — Sem WAF (padrão leve / atrás de Cloudflare ou proxy corporativo)

- **QA / Desenvolvimento local:**
  ```bash
  docker compose up -d --build
  ```
- **Produção (`prod`):**
  ```bash
  docker compose -f docker-compose.prod.yml up -d --build
  ```

#### Opção B — Com WAF perimetral (OWASP ModSecurity CRS, para deploy *on-premises* sem WAF externo)

Adiciona o serviço `waf` (`owasp/modsecurity-crs:nginx`, `PARANOIA=1`, `ANOMALY_INBOUND=5`) na frente do Nginx, mantendo o `web` isolado na rede interna Docker e aplicando exclusões cirúrgicas em [`web/modsecurity/REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf`](web/modsecurity/REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf) para importação de KML, upload de mídia e handshake WebSocket (`/ws`):

- **QA / Desenvolvimento local com WAF:**
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.waf.yml up -d --build
  ```
- **Produção (`prod`) com WAF:**
  ```bash
  docker compose -f docker-compose.prod.yml -f docker-compose.waf.yml up -d --build
  ```

Aguarde os healthchecks ficarem saudáveis (`docker compose ps`):

- **Aplicação Web / PWA (Nginx não-root):** `http://localhost` (ou `http://<IP-DO-SERVIDOR>`)
- **API Backend:** `http://localhost:8080` (exposta pelo proxy do Nginx em `/api` e `/ws`)
- **Healthcheck:** `http://localhost:8080/actuator/health`
- **Swagger/OpenAPI (desligado por padrão; habilite com `SPRINGDOC_API_DOCS_ENABLED=true SPRINGDOC_SWAGGER_UI_ENABLED=true` ou perfil `dev`/`local`):** `http://localhost:8080/swagger-ui/index.html`

Para parar os containers preservando o banco e as mídias:

```bash
docker compose down
```

---

### Passo 3 — Primeiro Acesso e Configuração Inicial

Na primeira subida com banco vazio, o `AdminBootstrapRunner` cria automaticamente a conta inicial de administração:

- **URL de login:** `http://localhost/login`
- **Usuário:** `admin` *(ou `admin@maraberto.local`)*
- **Senha inicial:** `admin` *(ou o valor definido em `ADMIN_BOOTSTRAP_PASSWORD`)*

**O que acontece no primeiro login:**
1. O sistema redireciona obrigatoriamente para `/trocar-senha` e bloqueia todas as demais rotas (`403`) até que você defina uma **senha definitiva** (mínimo 12 caracteres, com maiúsculas, minúsculas e números).
2. Após definir a senha definitiva, entre novamente em `/login` com `admin` e a nova senha.
3. **(Opcional) Ative o MFA (TOTP):** em `/admin/usuarios`, administradores e coordenadores podem opcionalmente ativar o segundo fator (Google Authenticator / FreeOTP) e salvar os 8 códigos de recuperação de uso único.
4. **Cadastre a equipe:** em `/admin/usuarios`, crie as contas de `COORDINATOR` (Coordenação) e `LIFEGUARD` (Guarda-vidas). O sistema gera uma senha temporária exibida **uma única vez**, com troca obrigatória no primeiro acesso de cada usuário.
5. **Importe o mapa do seu estado:** com uma conta `COORDINATOR` (ou `ADMIN`), acesse `/gestao/importar` e envie o arquivo `.kml` exportado do QGIS com as áreas de banho e setores. O sistema faz *dry-run* com prévia antes de aplicar.
6. **Configure a aba Aprender (opcional):** em `/gestao/aprender`, cadastre os vídeos educativos do YouTube e materiais de divulgação exibidos em `/aprender`.

---

### Serviços da Stack Docker Compose

| Serviço                  | Porta do Host    | Credenciais Padrão (Local) / Notas    |
| :----------------------- | :--------------- | :------------------------------------ |
| **Web (Nginx unprivileged)** | `80`         | Proxy `/api` e `/ws`, CSP, `limit_req`, HSTS sob HTTPS |
| **Backend (Spring Boot)** | `8080`          | Java 21 + Virtual Threads + PostGIS   |
| **PostgreSQL + PostGIS** | `5432`           | user: `maraberto` / pass: `maraberto` |
| **Redis**                | `6379`           | Cache, rate limit e blacklist JWT     |
| **Garage S3 API**        | `9000`           | S3-compatível; bucket `media-public`  |
| **Garage Admin**         | `9001`           | API administrativa                    |
| **RabbitMQ**             | `5672` / `15672` | Opcional (perfil `rabbit`, DLQ/retry) |

> ℹ️ O armazenamento usa **Garage** no lugar do MinIO (ADR-006), porque a imagem CE do MinIO deixou de ser pública. A configuração fica em `deploy/garage/garage.toml`.
>
> 🗺️ O backend fala com o Garage por padrão (`STORAGE_PROVIDER=s3`): fotos de sinalizações/postos e o KML aplicado na importação são armazenados em `media-public`.

### Desenvolvimento Local (fora do Docker)

#### Rodar o Backend Spring Boot

```bash
cd backend
./gradlew bootRun
```

- API REST disponível em: `http://localhost:8080/api/v1`
- Documentação interativa Swagger/OpenAPI: `http://localhost:8080/swagger-ui/index.html`
- Praias para o banhista: `GET /api/v1/beaches?lat=&lon=&radiusM=` (raio máximo de 30 km; sem posição não há lista) e `GET /api/v1/beaches/{id}` (ficha com setores). Ocupação anônima: `POST /api/v1/occupancy/samples` e `GET /api/v1/occupancy/beaches/{id}`.

#### Contas de desenvolvimento (login real)

O login único já usa o backend (`POST /api/v1/auth/login`) e o papel decide a área. Para criar as contas locais, suba o backend com o seed de desenvolvimento ligado:

```bash
cd backend
IDENTITY_DEV_SEED=true ./gradlew bootRun
```

| Perfil        | Área        | Nome       | E-mail                    | Matrícula | CPF           |
| :------------ | :---------- | :--------- | :------------------------ | :-------- | :------------ |
| `LIFEGUARD`   | `/operador` | Sgt. Silva | `operador@maraberto.dev`  | `BM-10492` | `11144477735` |
| `COORDINATOR` | `/gestao`   | Ten. Rocha | `coordenador@maraberto.dev` | `CBM-2041` | `52998224725` |
| `ADMIN` (TI)  | `/admin`    | Ana (TI)   | `admin@maraberto.dev`     | —         | `39053344705` |

- **Senha de todas as contas:** `demo1234`.
- O identificador aceita **e-mail, CPF ou matrícula** (a conta de TI não tem matrícula).
- O seed é idempotente por e-mail: reiniciar o backend não duplica usuários. As contas permanecem no banco mesmo sem a variável; o seed só as recria se o banco for zerado.
- ⚠️ O seed é **exclusivo de desenvolvimento**: o backend **recusa subir** com `IDENTITY_DEV_SEED=true` nos perfis `prod`/`production`. Nunca use essas contas em ambiente compartilhado.
- O seed também pode ser ligado pelo Compose (`IDENTITY_DEV_SEED` no `.env`, padrão `false`).

#### Plantão e postos de demonstração

Para testar o ciclo completo no console do operador (abrir plantão → receber SOS → assumir), ligue também o seed de estrutura operacional:

```bash
cd backend
IDENTITY_DEV_SEED=true OPERATIONS_DEV_SEED=true ./gradlew bootRun
```

- Cria **um posto fixo** (`Posto Pajuçara 01`, com praia) e **uma base móvel** (`Base Móvel Orla`) em Maceió/AL — apenas se ainda não existir nenhum posto/base no banco.
- O operador escolhe a estrutura, captura a posição do aparelho e abre o plantão em `/operador`. Sem plantão aberto ele **não** é escolhido pelo PostGIS.
- ⚠️ Mesma regra das contas: recusado nos perfis `prod`/`production`. Em um ambiente real, postos e bases vêm do cadastro/importação de mapeamento.

### 3. Rodar o Frontend PWA

```bash
cd web
npm install
npm run dev
```

- Aplicação Web acessível em: `http://localhost:3000`
- O Vite encaminha `/api` para `http://localhost:8080` (`VITE_PROXY_TARGET` para sobrescrever).
- **Configuração regional do mapa** (sem dados fixos no código): `VITE_MAP_DEFAULT_CENTER` (JSON `[lon, lat]`, padrão `[-51.9253, -14.2350]`, centro do Brasil), `VITE_MAP_DEFAULT_ZOOM` e `VITE_MAP_BOUNDS` (opcional). Qualquer Corpo de Bombeiros pode implantar a própria instância definindo esses valores.
- `npm run build` gera o PWA; `npm run lint` verifica o código; `npm run icons` regenera os ícones PNG.

### 4. Rodar os testes

```bash
cd backend
./gradlew clean check
cd ../web && npm run lint && npm run build
```

### 5. Criptografia de dados sensíveis

O backend cifra dados sensíveis em repouso com AES-256-GCM e exige uma chave mestra de 32 bytes:

```bash
# Gere uma chave e exporte no ambiente (nunca versione o valor)
openssl rand -base64 32
export CRYPTO_MASTER_KEY="<valor-gerado>"
export CRYPTO_KEY_ID="k1"
```

Sem `CRYPTO_MASTER_KEY`, o ambiente local usa a chave de desenvolvimento — que é **recusada no perfil `prod`**. Detalhes em [`docs/adr/012-protecao-de-dados-sensiveis-e-sanitizacao.md`](docs/adr/012-protecao-de-dados-sensiveis-e-sanitizacao.md).

---

## 🤝 6. Como Contribuir

O projeto é aberto a toda a comunidade técnica, membros de corporações de bombeiros e especialistas em salvamento. Leia o [`Contributing.md`](Contributing.md) antes de começar. Em resumo:

1. Faça um **Fork** do repositório.
2. Crie uma branch para sua funcionalidade (`git checkout -b feature/sua-feature`).
3. Adicione testes de unidade e integração para novos fluxos de domínio.
4. Garanta que o linter e o build passem sem avisos (`./gradlew clean check`).
5. Abra um **Pull Request** detalhando as alterações e motivações técnicas.

Ao participar, você concorda em seguir o [Código de Conduta](CODE_OF_CONDUCT.md).

### 🔐 Encontrou uma vulnerabilidade?

**Não abra uma issue pública.** Siga as instruções de [`Security.md`](Security.md) para um relato responsável. Isto é especialmente importante aqui: o sistema lida com localização e com pedidos de socorro.

---

## 📄 7. Licença e Aspectos Legais

Este projeto é software livre licenciado sob os termos da [Licença MIT](LICENSE).

> **Aviso de Independência:** O MarAberto é um projeto de iniciativa comunitária e autônoma com finalidade educacional e social.
>
> **Limitação de responsabilidade:** o software é fornecido "como está". Ele é uma ferramenta de apoio e **não substitui o acionamento oficial dos serviços de emergência (193)** nem a decisão dos órgãos responsáveis pela segurança balnear. Quem implanta uma instância é responsável por sua operação e pelo tratamento de dados pessoais conforme a LGPD.
