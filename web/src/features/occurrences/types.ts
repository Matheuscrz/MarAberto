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

export interface Occurrence {
  readonly id: string;
  /** Número interno legível, mostrado no console. */
  readonly number: number;
  readonly origin: OccurrenceOrigin;
  readonly nature: OccurrenceNature;
  readonly status: OccurrenceStatus;
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
  readonly latitude: number | null;
  readonly longitude: number | null;
  readonly accuracyMeters: number | null;
  readonly ciospStatus: CiospStatus;
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
