/**
 * Contrato de uma chamada de emergência. Vive em `shared/` porque é consumido
 * por mais de uma área (SOS cidadão, operador e gestão) e é um contrato de API,
 * não uma regra de um domínio só.
 */
export type EmergencyCallStatus =
  | "PENDING"
  | "DISPATCHED"
  | "RESOLVED"
  | "CANCELED";

export interface EmergencyCall {
  readonly id: string;
  readonly clientRequestId: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly accuracyMeters?: number;
  readonly status: EmergencyCallStatus;
  readonly createdAt: string;
  readonly dispatchedAt?: string;
  readonly resolvedAt?: string;
  readonly operatorNotes?: string;
}
