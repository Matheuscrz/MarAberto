import { useState, type FormEvent } from "react";
import { Crosshair } from "lucide-react";
import { sanitizeText } from "@/shared/lib/sanitize";
import { StationLocationField } from "@/features/stations/components/StationLocationField";
import { useStationPosition } from "@/features/stations/hooks/use-station-position";
import { moveStation, stationErrorMessage } from "@/features/stations/api/stations";
import type { AdminStation } from "@/features/stations/types";

interface StationPositionFormProps {
  readonly station: AdminStation;
  readonly onMoved: (station: AdminStation) => void;
  readonly onCancel: () => void;
}

/**
 * Reposição da estrutura com a leitura atual do aparelho (RF11).
 *
 * <p>Trocar a posição não apaga nada: plantões e ocorrências continuam
 * apontando para a mesma estrutura. O painel passa a mostrar a origem
 * {@code DEVICE_GPS} e a precisão informada.
 */
export function StationPositionForm({
  station,
  onMoved,
  onCancel,
}: StationPositionFormProps) {
  const { position, locating, error, read } = useStationPosition();
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!position) {
      setFailure("Leia a posição atual antes de reposicionar.");
      return;
    }
    setBusy(true);
    setFailure(null);
    try {
      onMoved(
        await moveStation(station.kind, station.id, {
          latitude: position.latitude,
          longitude: position.longitude,
          accuracyMeters: position.accuracyMeters,
        }),
      );
    } catch (error_) {
      setFailure(stationErrorMessage(error_));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form
      onSubmit={submit}
      className="grid gap-2 rounded-xl border border-ocean-200 bg-ocean-50/60 p-2.5"
    >
      <p className="text-[11px] leading-snug text-ocean-700">
        <strong>{sanitizeText(station.name, 120)}</strong> passa a ter a
        coordenada do aparelho. Plantões e ocorrências continuam no mesmo
        registro.
      </p>

      <StationLocationField
        position={position}
        locating={locating}
        error={error}
        onRead={() => void read()}
      />

      {failure ? (
        <p className="text-[11px] font-semibold text-coral-700">{failure}</p>
      ) : null}

      <div className="flex gap-1.5">
        <button
          type="submit"
          disabled={busy || !position}
          className="inline-flex min-h-9 flex-1 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 px-3 text-xs font-bold text-white transition hover:bg-ocean-900 disabled:opacity-60"
        >
          <Crosshair aria-hidden="true" className="size-3.5" />
          {busy ? "Atualizando" : "Salvar posição"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="inline-flex min-h-9 items-center justify-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 transition hover:bg-ocean-50"
        >
          Cancelar
        </button>
      </div>
    </form>
  );
}
