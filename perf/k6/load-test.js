import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate } from "k6/metrics";

const errorRate = new Rate("errors");

const BASE_URL = __ENV.BASE_URL || "http://localhost:3000";
const ADMIN_USER = __ENV.K6_USER || "";
const ADMIN_PASS = __ENV.K6_PASS || "";

export const options = {
  scenarios: {
    public_map_read: {
      executor: "ramping-vus",
      startVUs: 5,
      stages: [
        { duration: "30s", target: 30 },
        { duration: "1m", target: 80 },
        { duration: "30s", target: 0 },
      ],
      exec: "publicMapFlow",
    },
    health_probe: {
      executor: "constant-vus",
      vus: 2,
      duration: "2m",
      exec: "healthFlow",
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<500", "p(99)<1200"],
    http_req_failed: ["rate<0.01"],
    errors: ["rate<0.01"],
  },
};

export function publicMapFlow() {
  group("Mapa público (banhista)", () => {
    const responses = http.batch([
      ["GET", `${BASE_URL}/api/v1/beaches`],
      ["GET", `${BASE_URL}/api/v1/flags`],
      ["GET", `${BASE_URL}/api/v1/stations`],
      ["GET", `${BASE_URL}/api/v1/unmapped-signals`],
      ["GET", `${BASE_URL}/api/v1/content`],
    ]);

    for (const res of responses) {
      const ok = check(res, {
        "status 200": (r) => r.status === 200,
      });
      errorRate.add(!ok);
    }
  });

  if (ADMIN_USER && ADMIN_PASS) {
    group("Autenticação e perfil", () => {
      const loginRes = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ identifier: ADMIN_USER, password: ADMIN_PASS }),
        { headers: { "Content-Type": "application/json" } },
      );
      const ok = check(loginRes, {
        "login 200 ou 429 (rate-limit)": (r) =>
          r.status === 200 || r.status === 429,
      });
      errorRate.add(!ok);
    });
  }

  sleep(1);
}

export function healthFlow() {
  const res = http.get(`${BASE_URL}/actuator/health`);
  const ok = check(res, {
    "health UP": (r) => r.status === 200,
  });
  errorRate.add(!ok);
  sleep(2);
}
