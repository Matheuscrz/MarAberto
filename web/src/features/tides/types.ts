export type TideType = "HIGH" | "LOW";

export interface TideEvent {
  readonly time: string;
  readonly heightMeters: number;
  readonly type: TideType;
}

export interface TideDay {
  readonly date: string;
  readonly weekday: string;
  readonly events: readonly TideEvent[];
  readonly syzygy: boolean;
  readonly source: string;
  readonly updatedAt: string;
}
