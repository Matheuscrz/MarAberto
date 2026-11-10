/**
 * Papéis definidos em `docs/adr/009-autenticacao-embutida.md`. Um único login
 * (`/login`) atende os três; a área de destino depende do papel:
 * `LIFEGUARD` → `/operador`, `COORDINATOR` → `/gestao`, `ADMIN` → `/admin`.
 *
 * A guarda de rota no cliente é só experiência de uso: quem autoriza de fato é
 * o servidor (papel + escopo de posto/praia). Nunca trate esta checagem como
 * fronteira de segurança.
 */
export type Role = "LIFEGUARD" | "COORDINATOR" | "ADMIN";

/**
 * Operador autenticado. Contrato provisório até `GET /api/v1/auth/me` existir;
 * o backend não devolve senha, token nem dados sensíveis aqui.
 */
export interface OperatorUser {
  readonly id: string;
  readonly name: string;
  readonly role: Role;
  readonly badgeNumber?: string;
  readonly email?: string;
}

/**
 * Sessão em memória. O access token **nunca** é persistido (nem em
 * `localStorage`, nem em `sessionStorage`, nem em cookie legível por JS): o
 * refresh opaco fica no cookie `__Secure-maraberto_rt` controlado pelo servidor.
 */
export interface AuthSession {
  readonly user: OperatorUser;
  readonly accessToken: string;
  /** Instante (epoch ms) em que o access token expira. */
  readonly expiresAt: number;
}

export interface LoginCredentials {
  readonly identifier: string;
  readonly password: string;
}
