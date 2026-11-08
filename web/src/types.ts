export type FlagColor = 'GREEN' | 'YELLOW' | 'RED' | 'BLACK';

export type RiskLevel = 'LOW' | 'MODERATE' | 'HIGH' | 'EXTREME';

export type RiskType =
  | 'RIP_CURRENT'
  | 'RIVER_MOUTH'
  | 'ROCKS'
  | 'DEEP_WATER'
  | 'WILDLIFE'
  | 'POLLUTION';

export interface RiskZone {
  id: string;
  type: RiskType;
  label: string;
  description: string;
  severity: RiskLevel;
  guidance: string[];
}

export interface Beach {
  id: string;
  name: string;
  municipality: string;
  state: string;
  description: string;
  flag: FlagColor;
  riskLevel: RiskLevel;
  occupancyPercent: number;
  updatedAt: string;
  latitude: number;
  longitude: number;
  facilities: string[];
  risks: RiskZone[];
}

export type TideType = 'HIGH' | 'LOW';

export interface TideEvent {
  time: string;
  heightMeters: number;
  type: TideType;
}

export interface TideDay {
  date: string;
  weekday: string;
  events: TideEvent[];
  syzygy: boolean;
  source: string;
  updatedAt: string;
}

export interface EducationalVideo {
  id: string;
  title: string;
  description: string;
  duration: string;
  topic: string;
}

export interface SosPayload {
  clientRequestId: string;
  latitude: number;
  longitude: number;
  accuracyMeters?: number;
}

export interface SosResponse {
  callId: string;
  status: string;
  clientRequestId: string;
  createdAt: string;
}

export interface FieldValidationError {
  field: string;
  message: string;
}

export interface ApiProblem {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  errors?: FieldValidationError[];
}
