export const OCCUPANCY_BUCKET_MS = 15 * 60 * 1000;

export interface OccupancySampleCoordinates {
  readonly latitude: number;
  readonly longitude: number;
}

/**
 * Registro em memória do último envio por setor e janela de 15 minutos.
 *
 * <p>Nunca persiste em `localStorage`/`sessionStorage`: vive apenas na memória
 * do módulo e é descartado ao recarregar o app.
 */
export interface OccupancyBucketEntry {
  readonly sectorId: string;
  readonly bucketStart: number;
}

export interface OccupancySamplerOptions {
  readonly sendSample: (
    position: OccupancySampleCoordinates,
    token: string,
  ) => Promise<void>;
  readonly token: string;
  readonly now?: () => number;
  readonly isHidden?: () => boolean;
}

export interface OccupancySampleResult {
  readonly sent: boolean;
  readonly bucketStart: number;
}

export function bucketStartOf(nowMs: number = Date.now()): number {
  return Math.floor(nowMs / OCCUPANCY_BUCKET_MS) * OCCUPANCY_BUCKET_MS;
}

function defaultIsHidden(): boolean {
  return (
    typeof document !== "undefined" && document.visibilityState === "hidden"
  );
}

/**
 * Controla o envio de amostras anônimas de ocupação (RF10/RNF04).
 *
 * <p>Garante no máximo 1 amostra por `{sectorId, bucketStart}` em memória,
 * pausando automaticamente enquanto a aba estiver escondida.
 */
export function createOccupancySampler({
  sendSample,
  token,
  now = Date.now,
  isHidden = defaultIsHidden,
}: OccupancySamplerOptions) {
  const sentBuckets = new Map<string, OccupancyBucketEntry>();

  return {
    async record(
      position: OccupancySampleCoordinates,
      sectorId: string | null,
    ): Promise<OccupancySampleResult> {
      const bucketStart = bucketStartOf(now());

      if (isHidden()) {
        return { sent: false, bucketStart };
      }

      if (sectorId) {
        const previous = sentBuckets.get(sectorId);
        if (previous && previous.bucketStart === bucketStart) {
          return { sent: false, bucketStart };
        }
      }

      await sendSample(position, token);

      if (sectorId) {
        sentBuckets.set(sectorId, { sectorId, bucketStart });
      }

      return { sent: true, bucketStart };
    },

    hasSentInCurrentWindow(sectorId: string): boolean {
      const bucketStart = bucketStartOf(now());
      const previous = sentBuckets.get(sectorId);
      return Boolean(previous && previous.bucketStart === bucketStart);
    },

    snapshot(): readonly OccupancyBucketEntry[] {
      return Array.from(sentBuckets.values());
    },

    clear(): void {
      sentBuckets.clear();
    },
  };
}
