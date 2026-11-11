/**
 * Leitura pontual da posição do aparelho.
 *
 * <p>Não existe `watchPosition` nem persistência: cada chamada é uma leitura
 * sob demanda para o plantão ou para o cadastro de um ponto. Quem chama decide
 * quando repetir — o console do operador repete a cada 5 minutos com a aba
 * aberta para manter a última posição utilizável na seleção do SOS.
 */

export type GeolocationFailure =
  | "unsupported"
  | "denied"
  | "unavailable"
  | "timeout";

const MESSAGE: Record<GeolocationFailure, string> = {
  unsupported: "Este navegador não expõe a localização.",
  denied:
    "Permissão de localização negada. Libere o acesso nas configurações do navegador ou informe as coordenadas manualmente.",
  unavailable:
    "Não foi possível obter a localização agora. Verifique o GPS do aparelho e tente de novo.",
  timeout:
    "A localização demorou demais para responder. Tente de novo perto de uma janela ou ao ar livre.",
};

export class GeolocationError extends Error {
  readonly reason: GeolocationFailure;

  constructor(reason: GeolocationFailure) {
    super(MESSAGE[reason]);
    this.name = "GeolocationError";
    this.reason = reason;
  }
}

export interface DevicePosition {
  readonly latitude: number;
  readonly longitude: number;
  /** Precisão informada pelo aparelho, em metros. */
  readonly accuracyMeters: number;
}

export function getDevicePosition(
  options: PositionOptions = {},
): Promise<DevicePosition> {
  if (!("geolocation" in navigator)) {
    return Promise.reject(new GeolocationError("unsupported"));
  }

  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracyMeters: position.coords.accuracy,
        });
      },
      (error) => reject(new GeolocationError(reasonOf(error))),
      {
        enableHighAccuracy: true,
        timeout: 10_000,
        maximumAge: 0,
        ...options,
      },
    );
  });
}

function reasonOf(error: GeolocationPositionError): GeolocationFailure {
  switch (error.code) {
    case error.PERMISSION_DENIED:
      return "denied";
    case error.POSITION_UNAVAILABLE:
      return "unavailable";
    case error.TIMEOUT:
      return "timeout";
    default:
      return "unavailable";
  }
}
