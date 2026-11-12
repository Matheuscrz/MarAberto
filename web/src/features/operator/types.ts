/**
 * Contrato provisório da ocorrência registrada pelo guarda-vidas em campo.
 * O módulo `occurrences` (ADR-013) será a fonte real; até lá o formulário
 * mantém o estado só na tela e usa os mesmos nomes do backend.
 */
export type OccurrenceOrigin = "FIELD_ATTENDED" | "CBM_193" | "EXTERNAL_OTHER";

export type OccurrenceNature =
  | "RESCUE"
  | "FIRST_AID"
  | "DROWNING"
  | "MISSING_PERSON"
  | "MARINE_ANIMAL"
  | "OTHER";

export type OccurrenceOutcome =
  | "RESOLVED"
  | "REFERRED"
  | "FALSE_ALARM"
  | "CANCELLED";

export interface OperatorOccurrence {
  readonly id: string;
  readonly number: number;
  readonly clientRequestId: string;
  readonly origin: OccurrenceOrigin;
  readonly nature: OccurrenceNature;
  readonly outcome: OccurrenceOutcome;
  readonly ciospStatus: "PENDING" | "NOT_REQUIRED";
  readonly occurredAt: string;
  readonly beachId: string;
  readonly beachName: string;
  readonly sector?: string;
  readonly reference: string;
  readonly summary: string;
  readonly protocol?: string;
  readonly latitude?: number;
  readonly longitude?: number;
  readonly accuracyMeters?: number;
  readonly createdAt: string;
}

export const ORIGIN_LABEL: Record<OccurrenceOrigin, string> = {
  FIELD_ATTENDED: "Atendimento em campo (sem chamado)",
  CBM_193: "Chamado do 193",
  EXTERNAL_OTHER: "Rádio, presencial ou outro posto",
};

export const NATURE_LABEL: Record<OccurrenceNature, string> = {
  RESCUE: "Salvamento",
  FIRST_AID: "Primeiros socorros",
  DROWNING: "Afogamento",
  MISSING_PERSON: "Pessoa desaparecida",
  MARINE_ANIMAL: "Animal marinho",
  OTHER: "Outra",
};

export const OUTCOME_LABEL: Record<OccurrenceOutcome, string> = {
  RESOLVED: "Atendido e liberado no local",
  REFERRED: "Encaminhado ao serviço de saúde",
  FALSE_ALARM: "Falso alarme",
  CANCELLED: "Cancelado",
};

/**
 * Chamado aberto atribuído ao operador (`GET /api/v1/dispatch/calls`).
 *
 * <p>As coordenadas só existem nesta rota autenticada e restrita aos
 * destinatários; a notificação STOMP leva apenas ids.
 */
export interface DispatchOpenCall {
  readonly callId: string;
  readonly status: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly accuracyMeters: number | null;
  readonly createdAt: string;
  readonly dispatchId: string;
  readonly round: number;
  readonly distanceMeters: number | null;
  readonly positionStale: boolean;
}

/**
 * Chamado assumido pelo operador (`GET /api/v1/dispatch/calls/assigned`).
 *
 * <p>É o atendimento em curso: enquanto existir, o console mantém a localização
 * do banhista na mesa para a rota no mapa e para a ocorrência operacional
 * (ADR-013). A coordenada só chega por esta rota autenticada.
 */
export interface DispatchAssignedCall {
  readonly callId: string;
  readonly status: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly accuracyMeters: number | null;
  readonly createdAt: string;
  readonly acknowledgedAt: string;
  readonly dispatchId: string;
  readonly round: number;
  readonly distanceMeters: number | null;
  readonly positionStale: boolean;
  /**
   * Distância **atual** da última posição do plantão até o banhista, em metros.
   *
   * <p>Calculada no servidor a cada consulta (o console reconcilia de 10 em
   * 10 s), é o que sustenta a decisão de ir a pé ou de veículo depois do ack.
   * `null` quando o plantão não tem posição reportada — aí vale
   * `distanceMeters`, a distância da abertura do chamado.
   */
  readonly currentDistanceMeters: number | null;
  /** Instante da posição usada em `currentDistanceMeters`. */
  readonly currentPositionAt: string | null;
  /** A posição do plantão está ausente ou mais velha que o limite configurado. */
  readonly currentPositionStale: boolean;
}

/** Notificação redigida recebida em `/user/queue/sos` (sem coordenadas). */export interface SosNotification {
  readonly callId: string;
  readonly dispatchId: string;
  readonly round: number;
  readonly status: string;
  readonly occurredAt: string;
}

/** Plantão aberto do operador (`GET /api/v1/dispatch/shifts/current`). */
export interface ShiftSnapshot {
  readonly id: string;
  readonly postId: string | null;
  readonly mobileBaseId: string | null;
  readonly startedAt: string;
  readonly endedAt: string | null;
  /** `MANUAL` (o operador encerrou), `IDLE_TIMEOUT` (sem sinal) ou `LOGOUT`. */
  readonly endedReason: ShiftEndReason | null;
  readonly lastPositionAt: string | null;
  readonly lastPositionAccuracyMeters: number | null;
}

/** Motivo do encerramento do plantão (`GET/POST /dispatch/shifts/current`). */
export type ShiftEndReason = "MANUAL" | "IDLE_TIMEOUT" | "LOGOUT";

/** Posto fixo ou base móvel ativo (`GET /api/v1/dispatch/shifts/targets`). */
export type ShiftTargetKind = "POST" | "MOBILE_BASE";

export interface ShiftTarget {
  readonly id: string;
  readonly kind: ShiftTargetKind;
  readonly name: string;
  readonly beachName: string | null;
  readonly latitude: number;
  readonly longitude: number;
  readonly coverageRadiusMeters: number | null;
}
