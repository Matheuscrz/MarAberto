import type { FlagColor } from "@/shared/types/flags";

/**
 * Sinalização de bandeira como chega de `GET /api/v1/flags`.
 *
 * <p>O histórico é append-only: cada lançamento tem validade própria (RN01) e
 * a sinalização vencida sai do mapa, mas continua na ficha da praia como
 * "desatualizada". `sectorId` nulo significa que a bandeira vale para a área
 * inteira; setor sem lançamento próprio herda a cor da praia.
 */
export interface BeachFlagSignal {
  readonly id: string;
  readonly beachId: string;
  readonly beachName: string;
  readonly sectorId: string | null;
  /** Rótulo pronto ("Setor 3" quando o mapeamento não nomeia o setor). */
  readonly sectorLabel: string | null;
  readonly flag: FlagColor;
  readonly notes: string | null;
  readonly latitude: number;
  readonly longitude: number;
  /** Posto que lançou a bandeira, quando identificado. */
  readonly stationName: string | null;
  readonly postedAt: string;
  readonly validUntil: string;
  readonly closedAt: string | null;
  /** Fotos do lançamento, na ordem em que foram enviadas (caminhos da API). */
  readonly photoUrls: readonly string[];
}

/**
 * Pedido de lançamento.
 *
 * <p>Duas formas de dizer onde a bandeira está plantada: o escopo escolhido no
 * console (`beachId`, com `sectorId` opcional) ou a leitura atual do aparelho
 * (`latitude`/`longitude`), quando o servidor resolve praia e setor. Sem setor,
 * a bandeira vale para a praia inteira.
 */
export interface LaunchFlagInput {
  /** Omitida no modo localização. */
  readonly beachId?: string | null;
  readonly sectorId?: string | null;
  readonly flag: FlagColor;
  readonly notes?: string;
  /** Validade em horas (0,5 a 24); omitida usa o padrão da instalação (12 h). */
  readonly validityHours?: number;
  /** Leitura atual do aparelho (modo localização). */
  readonly latitude?: number;
  readonly longitude?: number;
  readonly accuracyMeters?: number;
  /** Fotos já enviadas, até {@link MAX_SIGNAL_PHOTOS}. */
  readonly mediaIds?: readonly string[];
}

/** Resposta do lançamento/encerramento no console do operador. */
export interface LaunchedFlag {
  readonly id: string;
  readonly beachId: string;
  readonly sectorId: string | null;
  readonly flag: FlagColor;
  readonly createdAt: string;
  readonly validUntil: string;
  readonly closedAt: string | null;
  readonly photoUrls: readonly string[];
}

/** O que aconteceu com o lançamento: publicado ou enviado à triagem. */
export type LaunchOutcome = "PLACED" | "UNMAPPED";

/** Estado de triagem de um registro feito fora do mapeamento. */
export type UnmappedSignalStatus =
  | "PENDING"
  | "IDENTIFIED"
  | "NEEDS_MAPPING"
  | "DISCARDED";

/**
 * Sinalização registrada fora do mapeamento.
 *
 * <p>Não é uma bandeira publicada: o ponto não pertence a nenhuma área ativa,
 * então o registro vai para a fila da coordenação, que identifica o local,
 * pede um novo mapeamento ou descarta. A área mais próxima vem como sugestão —
 * nunca como vínculo automático.
 */
export interface UnmappedSignal {
  readonly id: string;
  readonly flag: FlagColor;
  readonly notes: string | null;
  readonly latitude: number;
  readonly longitude: number;
  readonly accuracyMeters: number | null;
  readonly reportedByBadge: string | null;
  readonly reportedPostId: string | null;
  readonly reportedPostName: string | null;
  readonly status: UnmappedSignalStatus;
  readonly beachId: string | null;
  readonly beachName: string | null;
  readonly sectorId: string | null;
  readonly sectorLabel: string | null;
  readonly nearestBeachId: string | null;
  readonly nearestBeachName: string | null;
  readonly nearestBeachDistanceMeters: number | null;
  readonly decisionNotes: string | null;
  readonly createdAt: string;
  readonly decidedAt: string | null;
  readonly photoUrls: readonly string[];
}

/**
 * Desfecho do `POST /operations/flags`.
 *
 * <p>`PLACED` traz a bandeira publicada em `flag`; `UNMAPPED` traz o registro
 * em `unmapped` e nenhuma bandeira no mapa.
 */
export interface LaunchFlagResult {
  readonly outcome: LaunchOutcome;
  readonly flag: LaunchedFlag | null;
  readonly unmapped: UnmappedSignal | null;
}

/** Vigente = aberta e dentro da validade. O resto é histórico. */
export function signalIsCurrent(
  signal: BeachFlagSignal,
  now: number = Date.now(),
): boolean {
  if (signal.closedAt !== null) return false;
  const validUntil = new Date(signal.validUntil).getTime();
  return Number.isFinite(validUntil) && validUntil > now;
}

/** Estado exibido na ficha: vigente, vencida ou encerrada. */
export type SignalState = "CURRENT" | "EXPIRED" | "CLOSED";

export function signalState(
  signal: BeachFlagSignal,
  now: number = Date.now(),
): SignalState {
  if (signal.closedAt !== null) return "CLOSED";
  return signalIsCurrent(signal, now) ? "CURRENT" : "EXPIRED";
}

/** Bandeira da área ou do setor; setor sem lançamento herda a da praia. */
export function flagForSector(
  signals: readonly BeachFlagSignal[],
  sectorId: string,
): BeachFlagSignal | null {
  return (
    signals.find((signal) => signal.sectorId === sectorId) ??
    signals.find((signal) => signal.sectorId === null) ??
    null
  );
}
