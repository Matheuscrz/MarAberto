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
