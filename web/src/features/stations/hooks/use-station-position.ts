import { useCallback, useState } from "react";
import {
  GeolocationError,
  getDevicePosition,
  type DevicePosition,
} from "@/shared/lib/geolocation";

/**
 * Leitura pontual da posição do aparelho para o cadastro/reposição de
 * estrutura (RF11).
 *
 * <p>Cada leitura é sob demanda e nada é guardado no aparelho: a coordenada vai
 * direto para a requisição. Não existe `watchPosition` — o posto não vira
 * rastreamento contínuo.
 */
export function useStationPosition() {
  const [position, setPosition] = useState<DevicePosition | null>(null);
  const [locating, setLocating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const read = useCallback(async () => {
    setLocating(true);
    setError(null);
    try {
      setPosition(await getDevicePosition());
    } catch (failure) {
      setError(
        failure instanceof GeolocationError
          ? failure.message
          : "Não foi possível obter a localização agora.",
      );
    } finally {
      setLocating(false);
    }
  }, []);

  return { position, locating, error, read };
}
