import http from "k6/http";
import { randomBytes } from "k6/crypto";
import { check, group, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

/**
 * Teste de capacidade misto do MarAberto.
 *
 * Simula o padrão de uso real:
 *  - banhista: polling do mapa público (leitura dominante, cache de 10 s);
 *  - operação: leituras autenticadas (equipe, chamados, ocorrências);
 *  - operador: heartbeat/posição do plantão (escrita curta e frequente);
 *  - cidadão: SOS anônimo (escrita + outbox + STOMP);
 *  - coordenação: ocorrência manual + encerramento (escrita transacional);
 *  - banhista: amostra anônima de ocupação;
 *  - login: autenticação real (bcrypt + sessão de refresh).
 *
 * Uso local (backend direto, sem limit_req do Nginx):
 *   docker run --rm -i --network=host -e BASE_URL=http://localhost:8080 \
 *     -e K6_USER=admin@maraberto.dev -e K6_PASS=demo1234 \
 *     grafana/k6 run - < perf/k6/mixed-capacity.js
 *
 * Em produção, aponte BASE_URL para a URL pública e use uma conta dedicada
 * (sem mustChangePassword). Ver perf/k6/README.md para os cuidados.
 */

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const USER = __ENV.K6_USER || "admin@maraberto.dev";
const PASS = __ENV.K6_PASS || "demo1234";

// Ponto de referência: Praia de Atalaia, setor Guarda Vidas (Aracaju/SE).
const LAT = Number(__ENV.LAT || -10.99263);
const LON = Number(__ENV.LON || -37.04812);

const READ_PEAK = Number(__ENV.READ_PEAK || 100);
const AUTH_VUS = Number(__ENV.AUTH_VUS || 20);
const WRITE_VUS = Number(__ENV.WRITE_VUS || 5);
const SOS_RATE = Number(__ENV.SOS_RATE || 6); // chamados por minuto
const OCC_RATE = Number(__ENV.OCC_RATE || 4); // ocorrências por minuto
const LOGIN_RATE = Number(__ENV.LOGIN_RATE || 5); // logins por minuto
const DURATION = __ENV.DURATION || "2m";
const P95 = Number(__ENV.P95 || 500);
const P99 = Number(__ENV.P99 || 1200);
const MAX_FAIL = Number(__ENV.MAX_FAIL || 0.01);

const errorRate = new Rate("errors");
const readLatency = new Trend("read_latency", true);
const writeLatency = new Trend("write_latency", true);

export const options = {
  discardResponseBodies: false,
  scenarios: {
    public_map_read: {
      executor: "ramping-vus",
      startVUs: Math.max(5, Math.round(READ_PEAK * 0.1)),
      stages: [
        { duration: "30s", target: READ_PEAK },
        { duration: DURATION, target: READ_PEAK },
        { duration: "30s", target: 0 },
      ],
      exec: "publicMapFlow",
      tags: { flow: "public_read" },
    },
    auth_read: {
      executor: "constant-vus",
      vus: AUTH_VUS,
      duration: DURATION,
      exec: "authReadFlow",
      tags: { flow: "auth_read" },
    },
    operator_write: {
      executor: "constant-vus",
      vus: WRITE_VUS,
      duration: DURATION,
      exec: "operatorWriteFlow",
      tags: { flow: "operator_write" },
    },
    sos_write: {
      executor: "constant-arrival-rate",
      rate: SOS_RATE,
      timeUnit: "1m",
      duration: DURATION,
      preAllocatedVUs: Math.max(2, Math.ceil(SOS_RATE / 2)),
      maxVUs: Math.max(4, SOS_RATE),
      exec: "sosFlow",
      tags: { flow: "sos_write" },
    },
    occurrence_write: {
      executor: "constant-arrival-rate",
      rate: OCC_RATE,
      timeUnit: "1m",
      duration: DURATION,
      preAllocatedVUs: Math.max(2, OCC_RATE),
      maxVUs: Math.max(4, OCC_RATE * 2),
      exec: "occurrenceFlow",
      tags: { flow: "occurrence_write" },
    },
    occupancy_write: {
      executor: "constant-vus",
      vus: Math.max(2, Math.round(WRITE_VUS / 2)),
      duration: DURATION,
      exec: "occupancyFlow",
      tags: { flow: "occupancy_write" },
    },
    login_write: {
      executor: "constant-arrival-rate",
      rate: LOGIN_RATE,
      timeUnit: "1m",
      duration: DURATION,
      preAllocatedVUs: Math.max(2, LOGIN_RATE),
      maxVUs: Math.max(4, LOGIN_RATE * 2),
      exec: "loginFlow",
      tags: { flow: "login_write" },
    },
  },
  thresholds: {
    http_req_duration: [`p(95)<${P95}`, `p(99)<${P99}`],
    http_req_failed: [`rate<${MAX_FAIL}`],
    errors: [`rate<${MAX_FAIL}`],
  },
};

function randomUnit() {
  const bytes = new Uint32Array(randomBytes(4));
  return bytes[0] / 0x100000000;
}

function jitterCoord(base, spread) {
  return base + (randomUnit() - 0.5) * spread;
}

function uuid() {
  const bytes = new Uint8Array(randomBytes(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function authHeaders(token) {
  return { headers: { Authorization: `Bearer ${token}` } };
}

function jsonHeaders(token) {
  const headers = { "Content-Type": "application/json" };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return { headers };
}

function track(res, kind) {
  const ok = res.status >= 200 && res.status < 300;
  errorRate.add(!ok);
  if (kind === "write") {
    writeLatency.add(res.timings.duration);
  } else {
    readLatency.add(res.timings.duration);
  }
  return ok;
}

function findFirstBeachId() {
  const beaches = http.get(
    `${BASE_URL}/api/v1/beaches?lat=${LAT}&lon=${LON}&radiusM=30000`,
  );
  if (beaches.status !== 200) {
    return null;
  }
  const list = beaches.json()?.beaches;
  return Array.isArray(list) && list.length > 0 ? list[0].id : null;
}

function findPreferredTarget(token) {
  const targetsRes = http.get(
    `${BASE_URL}/api/v1/dispatch/shifts/targets`,
    authHeaders(token),
  );
  if (targetsRes.status !== 200) {
    return null;
  }
  const targets = targetsRes.json();
  if (!Array.isArray(targets)) {
    return null;
  }
  return (
    targets.find((t) => t.kind === "POST") ||
    targets.find((t) => t.kind === "MOBILE_BASE") ||
    null
  );
}

function buildShiftBody(target) {
  if (!target) {
    return { latitude: LAT, longitude: LON, accuracyMeters: 10 };
  }
  if (target.kind === "POST") {
    return { postId: target.id };
  }
  return { mobileBaseId: target.id };
}

export function setup() {
  const login = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ identifier: USER, password: PASS }),
    jsonHeaders(),
  );
  if (login.status !== 200) {
    throw new Error(
      `login falhou (${login.status}) para ${USER}: ${login.body}`,
    );
  }
  const token = login.json("accessToken");
  if (!token) {
    throw new Error("login sem accessToken (MFA habilitado?)");
  }

  const beachId = findFirstBeachId();
  // Prefere um posto fixo ativo; sem estrutura, abre plantão livre com a posição.
  const target = findPreferredTarget(token);
  const shiftBody = buildShiftBody(target);

  const open = http.post(
    `${BASE_URL}/api/v1/dispatch/shifts`,
    JSON.stringify(shiftBody),
    jsonHeaders(token),
  );
  const shiftReady = open.status === 201 || open.status === 409;

  return {
    token,
    beachId,
    shiftReady,
    shiftStatus: open.status,
    shiftTarget: target ? `${target.kind}:${target.name}` : "FREE",
  };
}

export function publicMapFlow(data) {
  group("Mapa público", () => {
    const paths = [
      `/api/v1/beaches?lat=${LAT}&lon=${LON}&radiusM=30000`,
      "/api/v1/flags",
      "/api/v1/stations",
      "/api/v1/unmapped-signals",
      "/api/v1/content",
      "/api/v1/risk-zones",
    ];
    if (data.beachId) {
      paths.push(
        `/api/v1/beaches/${data.beachId}`,
        `/api/v1/beaches/${data.beachId}/map`,
        `/api/v1/occupancy/beaches/${data.beachId}`,
      );
    }
    const responses = http.batch(paths.map((p) => ["GET", `${BASE_URL}${p}`]));
    for (const res of responses) {
      check(res, { "leitura 2xx": (r) => r.status >= 200 && r.status < 300 });
      track(res, "read");
    }
  });
  sleep(2 + randomUnit() * 2);
}

export function authReadFlow(data) {
  const paths = [
    "/api/v1/auth/me",
    "/api/v1/management/team",
    "/api/v1/dispatch/calls",
    "/api/v1/dispatch/calls/assigned",
    "/api/v1/occurrences?limit=20",
    "/api/v1/dispatch/shifts/current",
    "/api/v1/dispatch/shifts/targets",
  ];
  const responses = http.batch(
    paths.map((p) => ["GET", `${BASE_URL}${p}`, null, authHeaders(data.token)]),
  );
  for (const res of responses) {
    check(res, { "autenticado 2xx": (r) => r.status >= 200 && r.status < 300 });
    track(res, "read");
  }
  sleep(2 + randomUnit() * 2);
}

export function operatorWriteFlow(data) {
  if (!data.shiftReady) {
    sleep(5);
    return;
  }
  const heartbeat = http.post(
    `${BASE_URL}/api/v1/dispatch/shifts/current/heartbeat`,
    null,
    authHeaders(data.token),
  );
  check(heartbeat, { heartbeat: (r) => r.status === 204 });
  track(heartbeat, "write");

  const move = http.put(
    `${BASE_URL}/api/v1/dispatch/shifts/current/position`,
    JSON.stringify({
      latitude: jitterCoord(LAT, 0.002),
      longitude: jitterCoord(LON, 0.002),
      accuracyMeters: 5 + randomUnit() * 20,
    }),
    jsonHeaders(data.token),
  );
  check(move, { posicao: (r) => r.status === 200 });
  track(move, "write");
  sleep(5);
}

export function sosFlow() {
  const res = http.post(
    `${BASE_URL}/api/v1/emergency-calls`,
    JSON.stringify({
      clientRequestId: uuid(),
      latitude: jitterCoord(LAT, 0.01),
      longitude: jitterCoord(LON, 0.01),
      accuracyMeters: 10,
    }),
    {
      headers: { "Content-Type": "application/json" },
      // 429 é esperado quando o rate limit do SOS está ligado (padrão).
      responseCallback: http.expectedStatuses(201, 429),
    },
  );
  const ok = res.status === 201 || res.status === 429;
  check(res, { sos: () => ok });
  errorRate.add(!ok);
  writeLatency.add(res.timings.duration);
  sleep(1);
}

export function occurrenceFlow(data) {
  const create = http.post(
    `${BASE_URL}/api/v1/occurrences`,
    JSON.stringify({
      clientRequestId: uuid(),
      origin: "FIELD_ATTENDED",
      nature: "RESCUE",
      occurredAt: new Date().toISOString(),
      latitude: jitterCoord(LAT, 0.01),
      longitude: jitterCoord(LON, 0.01),
      accuracyMeters: 10,
      summary: "Atendimento simulado pelo teste de carga",
    }),
    jsonHeaders(data.token),
  );
  const created = create.status === 201;
  check(create, { "ocorrência criada": () => created });
  errorRate.add(!created);
  writeLatency.add(create.timings.duration);
  if (!created) {
    return;
  }

  let occurrenceId = null;
  try {
    occurrenceId = create.json("detail.occurrence.id");
  } catch (e) {
    occurrenceId = null;
  }
  if (!occurrenceId) {
    return;
  }

  const close = http.post(
    `${BASE_URL}/api/v1/occurrences/${occurrenceId}/close`,
    JSON.stringify({
      outcome: "RESOLVED",
      summary: "Encerrado pelo teste de carga",
    }),
    jsonHeaders(data.token),
  );
  check(close, { "ocorrência encerrada": (r) => r.status === 200 });
  track(close, "write");
  sleep(1);
}

export function occupancyFlow() {
  const res = http.post(
    `${BASE_URL}/api/v1/occupancy/samples`,
    JSON.stringify({
      latitude: jitterCoord(LAT, 0.002),
      longitude: jitterCoord(LON, 0.002),
      token: uuid().replaceAll("-", "").slice(0, 24),
    }),
    jsonHeaders(),
  );
  // 204 = fora de área mapeada; 200 = amostra gravada.
  check(res, { ocupacao: (r) => r.status === 200 || r.status === 204 });
  track(res, "write");
  sleep(5);
}

export function loginFlow() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ identifier: USER, password: PASS }),
    {
      headers: { "Content-Type": "application/json" },
      // 429 é esperado quando o rate limit de login está ligado (padrão).
      responseCallback: http.expectedStatuses(200, 429),
    },
  );
  const ok = res.status === 200 || res.status === 429;
  check(res, { login: () => ok });
  errorRate.add(!ok);
  writeLatency.add(res.timings.duration);
  sleep(1);
}

export function teardown(data) {
  if (data.shiftReady) {
    http.post(
      `${BASE_URL}/api/v1/dispatch/shifts/current/close`,
      null,
      authHeaders(data.token),
    );
  }
}
