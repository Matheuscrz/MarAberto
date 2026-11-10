import type { AuthSession, LoginCredentials, OperatorUser } from "@/features/auth/types";

/**
 * Acesso de demonstração — **só em desenvolvimento e sob opt-in**.
 *
 * O login real (`POST /api/v1/auth/login`) é o caminho padrão. Este atalho só
 * existe para validar a interface sem backend e exige `VITE_DEMO_AUTH=true`;
 * nada é enviado ao servidor e nenhum dado é real.
 */
export const DEMO_AUTH_ENABLED: boolean =
  import.meta.env.DEV && import.meta.env.VITE_DEMO_AUTH === "true";

export interface DemoAccount {
  readonly identifier: string;
  readonly password: string;
  readonly user: OperatorUser;
}

const DEMO_PASSWORD = "demo1234";

export const DEMO_ACCOUNTS: readonly DemoAccount[] = [
  {
    identifier: "operador",
    password: DEMO_PASSWORD,
    user: {
      id: "demo-guarda",
      name: "Sgt. Silva",
      role: "LIFEGUARD",
      badgeNumber: "BM-10492",
    },
  },
  {
    identifier: "coordenador",
    password: DEMO_PASSWORD,
    user: {
      id: "demo-coordenacao",
      name: "Ten. Rocha",
      role: "COORDINATOR",
      badgeNumber: "CBM-2041",
    },
  },
  {
    identifier: "admin",
    password: DEMO_PASSWORD,
    user: {
      id: "demo-ti",
      name: "Ana (TI)",
      role: "ADMIN",
      email: "ti@maraberto.dev",
    },
  },
];

export function demoLogin(credentials: LoginCredentials): AuthSession {
  const identifier = credentials.identifier.trim().toLocaleLowerCase("pt-BR");
  const account = DEMO_ACCOUNTS.find((item) => item.identifier === identifier);

  if (!account || credentials.password !== account.password) {
    throw new Error(
      "Credenciais de demonstração inválidas. Use um dos perfis listados abaixo.",
    );
  }

  return {
    user: account.user,
    accessToken: "demo-session",
    expiresAt: Date.now() + 15 * 60 * 1000,
  };
}
