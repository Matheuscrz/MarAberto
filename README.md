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

1. **Prevenção Ativa & Conscientização:** Mapeamento visual de zonas de risco permanente (ex.: desembocaduras perigosas de rios, fossas submarinas e rebojos como na Praia dos Artistas), sincronização automática da tábua de marés oficial e vídeos educativos curados (YouTube) sobre como escapar de correntes de retorno (_rip currents_).
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
|  [ beaches ]              [ safety ]                  [ emergency ]           [ tides ]         |
|  - Polígonos de praia     - Postos Fixos e Móveis     - Despacho SOS          - Tábua de Marés  |
|  - Riscos Permanentes     - Bandeiramento do Dia      - STOMP WebSockets      - Ingestão DHN    |
|  - Estimativa Lotação     - Upload Fotos (Presigned)  - Quórum Comunitário    - Marés de Sizígia|
|                                                                                                 |
|  [ occurrences ]          [ search ]                  [ identity / audit ]                      |
|  - Ocorrências (SOS e 193) - Busca tolerante a erros  - Login, papéis, trilha imutável          |
|  - Ficha para o CIOSP     - pg_trgm (sem serviço extra)                                         |
|                                                                                                 |
|  [ content ]              [ shared / kernel ]                                                   |
|  - Vídeos YouTube         - Auth JWT, CPF operacional, Operações Geoespaciais                  |
+------------------------------------------------+------------------------------------------------+
                                                 |
         +---------------------------------------+---------------------------------------+
         |                                       |                                       |
         v                                       v                                       v
+------------------+                   +--------------------+                  +------------------+
|    PostgreSQL    |                   |       Redis        |                  |    Garage/S3     |
|    + PostGIS     |                   | (Cache de Mapas,   |                  | (Object Storage  |
| (SRID 4326/GiST) |                   |  Marés e Limiter)  |                  |   Fotos/Mídias)  |
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
| **Banhista**     | Tábua de Marés em Tempo Real    | Indicador visual de preamar/baixa-mar, coeficientes de maré, horários de pico e avisos de maré de sizígia (maré viva).                                                                                             |
| **Banhista**     | Hub Educativo (YouTube)         | Vídeos informativos embutidos (_lazy-loaded_) com instruções de auto-resgate, fuga de correntes de retorno e primeiros socorros.                                                                                   |
| **Banhista**     | SOS Cidadão (Botão de Pânico)   | Disparo georreferenciado de socorro imediato para guarda-vidas da circunscrição, sem cadastro ou CPF prévio.                                                                                                       |
| **Banhista**     | Nível de Lotação "Waze"         | Estimativa anônima de ocupação da praia através de geocheck no dispositivo sem persistência de rastro do usuário.                                                                                                  |
| **Guarda-Vidas** | Gestão de Postos e Bases Móveis | Abertura de ponto de atendimento em campo com foto da viatura/posto e coordenadas em tempo real.                                                                                                                   |
| **Guarda-Vidas** | Bandeiramento & Correntes       | Atualização da sinalização diária (verde, amarela, vermelha) com fotos comprobatórias das bandeiras fincadas.                                                                                                      |
| **Guarda-Vidas** | Moderação e Quórum              | Homologação de reportes da população ou promoção automática caso $\ge 3$ banhistas distintos relatem o mesmo problema em 100 m.                                                                                    |
| **Guarda-Vidas** | Central Receptora de SOS        | Alertas visuais e sonoros com cálculo métrico de distância e rota até o banhista solicitante.                                                                                                                      |
| **Guarda-Vidas** | Ocorrências e Ficha CIOSP       | Todo SOS gera uma ocorrência; ocorrências do 193 ou de fora do sistema são registradas manualmente. Fila de pendentes e ficha com os campos na ordem do CIOSP para transcrição. O sistema não se integra ao CIOSP. |
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
| [`Contributing.md`](Contributing.md)                                                                                     | Como contribuir, padrões de código e checklist de Pull Request.                                                        |
| [`Security.md`](Security.md)                                                                                             | Como reportar vulnerabilidades, modelo de sanitização/criptografia e recomendações de implantação.                     |
| [`docs/adr/012-protecao-de-dados-sensiveis-e-sanitizacao.md`](docs/adr/012-protecao-de-dados-sensiveis-e-sanitizacao.md) | Decisão sobre sanitização de entrada e criptografia de dados sensíveis em repouso.                                     |
| [`docs/adr/013-ocorrencias-operacionais.md`](docs/adr/013-ocorrencias-operacionais.md)                                   | Ocorrência como entidade própria, criada por evento a partir do SOS e por registro manual.                             |
| [`docs/adr/014-busca-tolerante-a-erros.md`](docs/adr/014-busca-tolerante-a-erros.md)                                     | Busca com `pg_trgm` antes de OpenSearch e critérios para reavaliar.                                                    |
| [`docs/adr/015-frontend-feature-based.md`](docs/adr/015-frontend-feature-based.md)                                       | Estrutura `app/ shared/ features/` do frontend e regras de fronteira.                                                  |
| [`docs/adr/017-geoindexacao-do-sos.md`](docs/adr/017-geoindexacao-do-sos.md)                                             | Coluna geográfica gerada + GiST, `ST_DWithin`/KNN para postos e geofence que não bloqueia o SOS.                        |
| [`docs/adr/018-selecao-de-destinatarios-e-entrega-do-sos.md`](docs/adr/018-selecao-de-destinatarios-e-entrega-do-sos.md) | O banco decide quem notificar e o transporte só entrega; sem filtro geográfico no broker.                              |
| [`AGENTS.md`](AGENTS.md)                                                                                                 | Memória de contexto para agentes de IA e convenções do repositório.                                                    |
| [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md)                                                                               | Regras de convivência da comunidade.                                                                                   |

---

## 🚀 5. Guia de Execução Rápida

### Pré-requisitos

- **Java 21 LTS** ou superior
- **Node.js 20+**
- **Docker & Docker Compose**

### 1. Iniciar Infraestrutura de Apoio

Suba os serviços de banco de dados PostGIS, cache Redis e armazenamento de arquivos S3-compatível via Docker Compose:

```bash
docker compose up -d
```

O Compose também pode construir e subir o backend e o frontend localmente:

```bash
docker compose up -d --build
```

- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui/index.html

Para parar os serviços sem remover os dados persistidos:

```bash
docker compose down
```

| Serviço                  | Porta do Host    | Credenciais / Notas                   |
| :----------------------- | :--------------- | :------------------------------------ |
| **PostgreSQL + PostGIS** | `5432`           | user: `maraberto` / pass: `maraberto` |
| **Redis**                | `6379`           | Cache de dados e rate limiting        |
| **Garage S3 API**        | `9000`           | S3-compatível; bucket `media-public`  |
| **Garage Admin**         | `9001`           | API administrativa                    |
| **RabbitMQ**             | `5672` / `15672` | AMQP / console de gestão              |

> 🔒 **Estas credenciais são exclusivamente para desenvolvimento local.** Nunca as utilize em produção. Veja as recomendações de implantação em [`Security.md`](Security.md).
>
> ℹ️ O armazenamento usa **Garage** no lugar do MinIO (ADR-006), porque a imagem CE do MinIO deixou de ser pública. A configuração fica em `deploy/garage/garage.toml`.

### 2. Rodar o Backend Spring Boot

```bash
cd backend
./gradlew bootRun
```

- API REST disponível em: `http://localhost:8080/api/v1`
- Documentação interativa Swagger/OpenAPI: `http://localhost:8080/swagger-ui/index.html`

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
