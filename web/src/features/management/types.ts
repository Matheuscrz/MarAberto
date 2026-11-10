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
