import type { FlagColor } from "@/shared/types/flags";

export interface BeachFlagSignal {
  readonly id: string;
  readonly beachId: string;
  readonly beachName: string;
  readonly flag: FlagColor;
  readonly latitude: number;
  readonly longitude: number;
  /** Galeria: uma sinalização pode ter mais de uma foto do local. */
  readonly photoUrls: readonly string[];
  readonly lifeguardName: string;
  readonly lifeguardId?: string;
  readonly notes?: string;
  readonly createdAt: string;
}
