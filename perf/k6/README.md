# Testes de Carga (k6) — MarAberto

Suíte de testes de carga para validar capacidade, latência (`p95 < 500 ms` nas leituras públicas do mapa) e limites de borda (`limit_req` do Nginx e rate limits do backend) antes de releases.

## Pré-requisitos

- Stack rodando (`docker compose up -d --build`)
- [k6](https://k6.io/) instalado localmente ou via Docker (`grafana/k6`)

## Execução

### 1. Teste padrão contra a borda (Nginx em `localhost:3000`)

```bash
k6 run perf/k6/load-test.js
```

### 2. Teste direto no backend (sem `limit_req` do Nginx, para medir capacidade máxima da JVM/PostgreSQL)

```bash
BASE_URL=http://localhost:8080 k6 run perf/k6/load-test.js
```

### 3. Via Docker (sem instalar k6 no host)

```bash
docker run --rm -i --network=host -e BASE_URL=http://localhost:8080 grafana/k6 run - < perf/k6/load-test.js
```

## Métricas e SLA esperados

- `http_req_duration p(95) < 500 ms` e `p(99) < 1200 ms` nas consultas públicas do mapa (`/api/v1/beaches`, `/api/v1/flags`, `/api/v1/stations`, `/api/v1/unmapped-signals`, `/api/v1/content`).
- `http_req_failed < 1%`.
- Ao testar contra o Nginx (`localhost:3000`), rajadas acima de `30 req/s` (burst 60) por IP retornam `429 Too Many Requests` conforme configurado em `web/nginx.conf`.

## Teste misto de capacidade (`mixed-capacity.js`)

Simula o uso real: leitura do mapa público, leituras autenticadas (equipe,
chamados, ocorrências), heartbeat/posição de plantão, SOS anônimo, ocorrência
manual + encerramento e amostra de ocupação. É o teste indicado para medir
capacidade e deixar pronto para produção.

```bash
# Capacidade bruta (backend direto, sem limit_req do Nginx)
docker run --rm -i --network=host \
  -e BASE_URL=http://localhost:8080 \
  -e K6_USER=admin@maraberto.dev -e K6_PASS=demo1234 \
  grafana/k6 run - < perf/k6/mixed-capacity.js

# Borda completa (Nginx + rate limit por IP)
docker run --rm -i --network=host \
  -e BASE_URL=http://localhost \
  -e K6_USER=admin@maraberto.dev -e K6_PASS=demo1234 \
  grafana/k6 run - < perf/k6/mixed-capacity.js
```

### Variáveis

| Variável | Padrão | Efeito |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Alvo (backend direto ou URL pública) |
| `K6_USER` / `K6_PASS` | `admin@maraberto.dev` / `demo1234` | Conta de teste (precisa existir e não ter `mustChangePassword`) |
| `READ_PEAK` | `100` | Pico de VUs do mapa público |
| `AUTH_VUS` | `20` | VUs de leitura autenticada |
| `WRITE_VUS` | `5` | VUs de heartbeat/posição |
| `SOS_RATE` | `6` | SOS por minuto |
| `OCC_RATE` | `4` | Ocorrências por minuto |
| `DURATION` | `2m` | Platô de cada cenário |
| `P95` / `P99` | `500` / `1200` | Limites de latência (ms) |

### Cuidados ao rodar contra produção

- **Rate limits por IP:** todos os VUs saem do mesmo IP. O login aceita
  `30/5min` e o SOS `3/10min`; para medir capacidade (e não o limitador),
  rode com `LOGIN_IP_LIMIT_ENABLED=false` e `SOS_RATE_LIMIT_ENABLED=false`
  em uma janela de QA, ou libere o IP do gerador. O script tolera `429` no
  SOS, mas o volume real de escrita fica subestimado.
- **Dados:** o cenário de ocorrências cria e encerra fichas reais, e o SOS
  cria chamados reais. Use um ambiente de QA ou uma conta/beach descartável.
- **Conta:** use uma conta dedicada de carga, sem `mustChangePassword` e com
  escopo administrativo (o script cria ocorrência e abre plantão).
- **Nginx:** acima de `30 req/s` por IP o `limit_req` responde `429`. Para
  capacidade da JVM/PostgreSQL, meça no `:8080`; para a borda, meça no `:80`.

