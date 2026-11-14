/**
 * Membro da equipe visto pela coordenação. O cadastro e o convite são da TI
 * (`/admin`); a coordenação só ativa/desativa o guarda-vidas do seu escopo de
 * praia/setor e acompanha o campo. Contrato provisório até o módulo `identity`
 * e a `shift_sessions` existirem.
 */
export type TeamMemberStatus = "ACTIVE" | "INACTIVE";

export interface TeamMemberPosition {
  readonly latitude: number;
  readonly longitude: number;
  readonly accuracyMeters: number;
  readonly at: string;
}

export interface TeamMember {
  readonly id: string;
  readonly name: string;
  readonly badgeNumber: string;
  readonly stationName: string;
  readonly beachName: string;
  readonly sector?: string;
  readonly shift: string;
  readonly status: TeamMemberStatus;
  /** Início do plantão atual; ausente quando não está de plantão. */
  readonly onShiftSince?: string;
  /** Última posição conhecida durante o plantão (RN17). */
  readonly lastPosition?: TeamMemberPosition;
  readonly occurrencesToday: number;
  readonly fieldAttendedToday: number;
  readonly signalsToday: number;
  readonly ciospPending: number;
  readonly updatedAt: string;
}

/**
 * Contrato da importação do mapa de áreas de banho (`POST
 * /api/v1/management/bathing-areas/import`, ADR-016/ADR-021).
 *
 * <p>O relatório devolve uma linha por setor do KML: a área de banho agrupa os
 * setores pelo município e pelo nome, mas quem muda de estado no diff é o
 * setor. `UNCHANGED` também vem na lista — é o que prova que o arquivo
 * reenviado não mexeu no mapa.
 */
export type ImportMode = "UPSERT" | "SYNC";

export type FeatureAction =
  | "CREATED"
  | "UPDATED"
  | "UNCHANGED"
  | "MISSING"
  | "REJECTED";

export interface FeatureChange {
  readonly externalId: string;
  readonly areaExternalId: string | null;
  readonly area: string | null;
  readonly sector: string | null;
  readonly municipality: string | null;
  readonly action: FeatureAction;
  readonly changedFields: readonly string[];
  /** Motivo da rejeição; presente apenas em `REJECTED`. */
  readonly message: string | null;
}

export interface ImportReport {
  readonly sourceFile: string;
  /** SHA-256 do arquivo: reenviar o mesmo arquivo não reescreve nada. */
  readonly sourceVersion: string;
  readonly state: string;
  readonly mode: ImportMode;
  readonly dryRun: boolean;
  readonly applied: boolean;
  readonly created: number;
  readonly updated: number;
  readonly unchanged: number;
  readonly missing: number;
  readonly rejected: number;
  readonly changes: readonly FeatureChange[];
}

/**
 * Área de banho ativa na lista de escolha da coordenação
 * (`GET /api/v1/management/bathing-areas`).
 *
 * <p>É o catálogo para apontar uma área existente num formulário — por
 * exemplo, ao identificar uma sinalização registrada fora do mapeamento.
 */
export interface BathingAreaOption {
  readonly id: string;
  readonly name: string;
  readonly kind: "BEACH" | "RIVER" | "DAM" | "LAGOON" | "OTHER";
  readonly municipality: string;
  readonly stateCode: string;
  readonly sectorCount: number;
}

/** Decisão da coordenação sobre um registro de área não identificada. */
export type UnmappedSignalDecision =
  | "PENDING"
  | "IDENTIFIED"
  | "NEEDS_MAPPING"
  | "DISCARDED";

export interface UnmappedSignalDecisionInput {
  readonly decision: UnmappedSignalDecision;
  readonly beachId?: string | null;
  readonly sectorId?: string | null;
  readonly notes?: string;
}
