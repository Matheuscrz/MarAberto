import { useEffect, useRef } from "react";
import { getDevicePosition, type DevicePosition } from "@/shared/lib/geolocation";
import { fetchNearbyBeaches } from "@/features/beaches/api/beaches";
import { recordOccupancySample } from "@/features/beaches/api/occupancy";
import { bucketStartOf } from "@/features/beaches/lib/occupancy-sampler";

const CHECK_INTERVAL_MS = 60_000;

export interface OccupancyCollectorOptions {
  /** Só coleta após consentimento explícito da localização na UI. */
  readonly enabled: boolean;
  readonly coords: DevicePosition | null;
  readonly sectorId: string | null;
}

/**
 * Envia amostras anônimas de ocupação no mapa público após consentimento
 * explícito, respeitando 1 amostra por janela de 15 min por setor em memória
 * (`{sectorId, bucketStart}`) e pausando com a aba escondida.
 */
export function useOccupancyCollector({
  enabled,
  coords,
  sectorId,
}: OccupancyCollectorOptions): void {
  const lastAttemptBucketRef = useRef<number | null>(null);

  // Envio imediato quando há posição obtida por consentimento explícito.
  useEffect(() => {
    if (!enabled || !coords) return;
    if (document.visibilityState === "hidden") return;

    const currentBucket = bucketStartOf();
    lastAttemptBucketRef.current = currentBucket;

    void recordOccupancySample(coords, sectorId).catch(() => undefined);
  }, [enabled, coords, sectorId]);

  // Renovação por janela de 15 min apenas com a aba visível.
  useEffect(() => {
    if (!enabled) return;

    const collectIfNewWindow = async () => {
      if (document.visibilityState === "hidden") return;
      const currentBucket = bucketStartOf();
      if (lastAttemptBucketRef.current === currentBucket) return;

      lastAttemptBucketRef.current = currentBucket;
      try {
        const position = await getDevicePosition({
          enableHighAccuracy: false,
          timeout: 8_000,
          maximumAge: 60_000,
        });
        const nearby = await fetchNearbyBeaches(position);
        if (document.hidden) return;
        await recordOccupancySample(
          position,
          nearby.position?.sectorId ?? null,
        );
      } catch {
        // Falha pontual de GPS/rede ou 204 fora de área não deve quebrar a UI.
      }
    };

    const onVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        void collectIfNewWindow();
      }
    };

    const timer = window.setInterval(() => {
      void collectIfNewWindow();
    }, CHECK_INTERVAL_MS);
    document.addEventListener("visibilitychange", onVisibilityChange);

    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [enabled]);
}
