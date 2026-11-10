export type StationType = "FIXED" | "MOBILE";

export interface LifeguardStation {
  readonly id: string;
  readonly name: string;
  readonly type: StationType;
  readonly status: "ACTIVE" | "INACTIVE";
  readonly beachId?: string;
  readonly beachName?: string;
  readonly municipality: string;
  readonly state: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly operatingHours?: string;
  readonly contactPhone?: string;
  readonly resources?: readonly string[];
  /** Fotos do posto/base para o banhista reconhecer o local. */
  readonly photoUrls?: readonly string[];
  readonly updatedAt: string;
}
