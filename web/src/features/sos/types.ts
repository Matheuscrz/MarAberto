export interface SosPayload {
  readonly clientRequestId: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly accuracyMeters?: number;
}

export interface SosResponse {
  readonly callId: string;
  readonly status: string;
  readonly clientRequestId: string;
  readonly createdAt: string;
}
