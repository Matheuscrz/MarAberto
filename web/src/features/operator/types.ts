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
