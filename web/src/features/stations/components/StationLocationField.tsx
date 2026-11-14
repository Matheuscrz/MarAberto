import { LocateFixed } from "lucide-react";
import type { DevicePosition } from "@/shared/lib/geolocation";

interface StationLocationFieldProps {
  readonly position: DevicePosition | null;
  readonly locating: boolean;
  readonly error: string | null;
  readonly onRead: () => void;
}

/**
 * Leitura da posição atual usada no cadastro e na reposição da estrutura.
 *
 * <p>O texto deixa claro o que o servidor guarda: a origem (GPS do aparelho) e
 * a precisão informada, para a coordenação julgar o ponto. A coordenada não é
 * salva no aparelho nem em storage web.
 */
export function StationLocationField({
  position,
  locating,
  error,
  onRead,
}: StationLocationFieldProps) {
  return (
    <div className="grid gap-1.5 rounded-xl border border-ocean-200 bg-ocean-50/60 p-3">
      <p className="flex items-center gap-1.5 text-xs font-bold text-ocean-900">
        <LocateFixed aria-hidden="true" className="size-3.5" />
        Posição atual do aparelho
      </p>
      {position ? (
        <output className="text-[11px] leading-snug text-ocean-700">
          {position.latitude.toFixed(5)}, {position.longitude.toFixed(5)}
          {" · "}
          precisão de aproximadamente {Math.round(position.accuracyMeters)} m
        </output>
      ) : (
        <p className="text-[11px] leading-snug text-ocean-700">
          {locating
            ? "Lendo a posição do aparelho..."
            : "A estrutura é cadastrada no ponto em que você está agora."}
        </p>
      )}
      {error ? (
        <p className="text-[11px] font-semibold text-coral-700">{error}</p>
      ) : null}
      <button
        type="button"
        disabled={locating}
        onClick={onRead}
        className="inline-flex min-h-9 w-fit items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 transition hover:bg-ocean-50 disabled:opacity-60"
      >
        <LocateFixed aria-hidden="true" className="size-3.5" />
        {locating
          ? "Lendo"
          : position
            ? "Atualizar posição"
            : "Ler minha localização"}
      </button>
    </div>
  );
}
