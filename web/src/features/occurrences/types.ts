/**
 * Ocorrência operacional como chega de `GET /api/v1/occurrences` (ADR-013).
 *
 * <p>A ocorrência nasce quando o guarda-vidas assume o chamado do SOS: o
 * `occurredAt` é a abertura do chamado e o `createdAt` é o ack. A coordenada é
 * a do banhista e só existe em rota autenticada — o mapa público não a vê.
 */
export type OccurrenceOrigin =
  | "SOS_CITIZEN"
  | "CBM_193"
  | "EXTERNAL_OTHER"
  | "FIELD_ATTENDED";

export type OccurrenceNature =
  | "RESCUE"
  | "FIRST_AID"
  | "DROWNING"
  | "MISSING_PERSON"
  | "MARINE_ANIMAL"
  | "OTHER";

export type OccurrenceStatus =
  | "OPEN"
  | "IN_PROGRESS"
  | "CLOSED"
  | "CANCELLED"
  | "FALSE_ALARM";

export type CiospStatus = "PENDING" | "REGISTERED" | "NOT_REQUIRED";

export type OccurrenceOutcome =
  | "RESOLVED"
  | "REFERRED"
  | "FALSE_ALARM"
  | "CANCELLED";

export interface Occurrence {
  readonly id: string;
  /** Número interno legível, mostrado no console. */
  readonly number: number;
  readonly origin: OccurrenceOrigin;
  readonly nature: OccurrenceNature;
  readonly status: OccurrenceStatus;
  /** Preenchido quando a ocorrência é encerrada. */
  readonly outcome: OccurrenceOutcome | null;
  /** Quando o fato ocorreu (abertura do SOS ou data informada no registro). */
  readonly occurredAt: string;
  /** Quando a ficha foi criada (instante do ack, no caso do SOS). */
  readonly createdAt: string;
  readonly acknowledgedAt: string | null;
  readonly closedAt: string | null;
  readonly emergencyCallId: string | null;
  readonly beachId: string | null;
  readonly beachName: string | null;
  /** Setor resolvido pelo ponto do chamado; nulo quando fora do mapeamento. */
  readonly sectorId: string | null;
  readonly sectorLabel: string | null;
  readonly postId: string | null;
  readonly postName: string | null;
  /** Guarda-vidas que assumiu o atendimento. */
  readonly responderId: string | null;
  readonly responderName: string | null;
  readonly latitude: number | null;
  readonly longitude: number | null;
  readonly accuracyMeters: number | null;
  readonly ciospStatus: CiospStatus;
}

/**
 * Ficha completa (`GET /api/v1/occurrences/{id}`).
 *
 * <p>O resumo é decifrado no servidor, mas a vítima continua mascarada: para
 * ver os dados é preciso uma revelação explícita, com motivo.
 */
export interface OccurrenceDetail {
  readonly occurrence: Occurrence;
  readonly summary: string | null;
  readonly outcomeNotes: string | null;
  readonly referenceText: string | null;
  readonly externalProtocol: string | null;
  readonly ciospProtocol: string | null;
  readonly ciospRegisteredAt: string | null;
  readonly closedBy: string | null;
  readonly closedByName: string | null;
  readonly victim: VictimView;
  readonly version: number;
}

export interface VictimView {
  readonly recorded: boolean;
  readonly revealed: boolean;
  readonly name: string | null;
  readonly age: string | null;
  readonly contact: string | null;
  readonly condition: string | null;
}

export interface CloseOccurrenceInput {
  readonly outcome: OccurrenceOutcome;
  readonly nature?: OccurrenceNature;
  readonly summary: string;
  readonly outcomeNotes?: string;
  readonly referenceText?: string;
  readonly externalProtocol?: string;
  readonly ciospProtocol?: string;
  readonly victimName?: string;
  readonly victimAge?: string;
  readonly victimContact?: string;
  readonly victimCondition?: string;
}

export interface UpdateOccurrenceInput {
  readonly nature?: OccurrenceNature;
  readonly summary?: string;
  readonly outcomeNotes?: string;
  readonly referenceText?: string;
  readonly externalProtocol?: string;
  readonly ciospProtocol?: string;
  readonly victimName?: string;
  readonly victimAge?: string;
  readonly victimContact?: string;
  readonly victimCondition?: string;
}

export const ORIGIN_LABEL: Record<OccurrenceOrigin, string> = {
  SOS_CITIZEN: "SOS do banhista",
  CBM_193: "Chamado do 193",
  EXTERNAL_OTHER: "Rádio, presencial ou outro posto",
  FIELD_ATTENDED: "Atendimento em campo (sem chamado)",
};

export const NATURE_LABEL: Record<OccurrenceNature, string> = {
  RESCUE: "Salvamento",
  FIRST_AID: "Primeiros socorros",
  DROWNING: "Afogamento",
  MISSING_PERSON: "Pessoa desaparecida",
  MARINE_ANIMAL: "Animal marinho",
  OTHER: "Outra",
};

export const STATUS_LABEL: Record<OccurrenceStatus, string> = {
  OPEN: "Aberta",
  IN_PROGRESS: "Em atendimento",
  CLOSED: "Encerrada",
  CANCELLED: "Cancelada",
  FALSE_ALARM: "Falso alarme",
};

export const CIOSP_LABEL: Record<CiospStatus, string> = {
  PENDING: "CIOSP pendente",
  REGISTERED: "CIOSP registrado",
  NOT_REQUIRED: "Sem CIOSP",
};

export const OUTCOME_LABEL: Record<OccurrenceOutcome, string> = {
  RESOLVED: "Atendido e liberado no local",
  REFERRED: "Encaminhado ao serviço de saúde",
  FALSE_ALARM: "Falso alarme",
  CANCELLED: "Cancelado",
};

/** O atendimento ainda está em curso e pode ser encerrado. */
export function isActiveOccurrence(occurrence: Occurrence): boolean {
  return occurrence.status === "OPEN" || occurrence.status === "IN_PROGRESS";
}
