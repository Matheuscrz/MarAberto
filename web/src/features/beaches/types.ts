import type { FlagColor, RiskLevel } from "@/shared/types/flags";

export type RiskType =
  | "RIP_CURRENT"
  | "RIVER_MOUTH"
  | "ROCKS"
  | "DEEP_WATER"
  | "WILDLIFE"
  | "POLLUTION";

export interface RiskZone {
  readonly id: string;
  readonly type: RiskType;
  readonly label: string;
  readonly description: string;
  readonly severity: RiskLevel;
  readonly guidance: readonly string[];
}

export interface Beach {
  readonly id: string;
  readonly name: string;
  readonly municipality: string;
  readonly state: string;
  readonly description: string;
  readonly flag: FlagColor;
  readonly riskLevel: RiskLevel;
  readonly occupancyPercent: number;
  readonly updatedAt: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly facilities: readonly string[];
  readonly risks: readonly RiskZone[];
}
