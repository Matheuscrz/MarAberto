import { useState, type FormEvent } from "react";
import { CheckCircle, Crosshair, Power, RefreshCw, X } from "lucide-react";
import { Card } from "@/shared/components/Card";
import { PhotoGallery } from "@/shared/components/PhotoGallery";
import { formatDateTime } from "@/shared/lib/format";
import { sanitizeText } from "@/shared/lib/sanitize";
import { useAdminStations } from "@/features/stations/hooks/use-admin-stations";
import { StationPositionForm } from "@/features/stations/components/StationPositionForm";
import {
  POSITION_SOURCE_LABEL,
  type AdminStation,
} from "@/features/stations/types";

/**
 * Cadastro de postos e bases no painel da TI.
 *
 * <p>Desativar pede o motivo, que fica registrado na estrutura; reativar não
 * apaga o motivo, só limpa a data. Nada é excluído: plantões e ocorrências
 * continuam apontando para a mesma linha.
 */
export function StationAdminTable() {
  const {
    stations,
    status,
    message,
    feedback,
    busyId,
    reload,
    setActive,
    applyUpdate,
    dismissFeedback,
  } = useAdminStations();
  const [reason, setReason] = useState<{ id: string; note: string } | null>(null);
  const [repositionId, setRepositionId] = useState<string | null>(null);

  if (status === "loading") {
    return (
      <output className="block py-6 text-center text-sm font-semibold text-ocean-600">
        Carregando postos e bases
      </output>
    );
  }

  if (status === "error") {
    return (
      <Card className="border-dashed bg-ocean-50/40 text-center">
        <p className="text-sm font-bold text-ocean-950">
          Não foi possível carregar as estruturas
        </p>
        <p className="mt-1 text-xs text-ocean-700">
          {message ?? "Tente de novo em instantes."}
        </p>
        <button
          type="button"
          onClick={reload}
          className="mt-2 inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 hover:bg-ocean-50"
        >
          <RefreshCw aria-hidden="true" className="size-3.5" />
          Tentar de novo
        </button>
      </Card>
    );
  }

  return (
    <div className="grid gap-3">
      {feedback ? (
        <div className="flex items-start justify-between gap-2 rounded-xl border border-ocean-200 bg-ocean-50 px-3 py-2 text-xs font-semibold text-ocean-900">
          <output className="flex items-start gap-1.5">
            <CheckCircle aria-hidden="true" className="mt-0.5 size-3.5" />
            {sanitizeText(feedback, 200)}
          </output>
          <button
            type="button"
            onClick={dismissFeedback}
            aria-label="Fechar aviso"
            className="grid size-6 shrink-0 place-items-center rounded-lg text-ocean-600 hover:bg-white"
          >
            <X aria-hidden="true" className="size-3.5" />
          </button>
        </div>
      ) : null}

      {stations.length === 0 ? (
        <Card className="border-dashed bg-ocean-50/40 text-center">
          <p className="text-sm font-bold text-ocean-950">
            Nenhum posto ou base cadastrado
          </p>
          <p className="mt-1 text-xs text-ocean-700">
            As estruturas chegam pela importação do mapeamento e pelo cadastro
            do órgão.
          </p>
        </Card>
      ) : (
        <ul className="grid gap-2">
          {stations.map((station) => (
            <li key={`${station.kind}:${station.id}`}>
              <Card className="grid gap-2">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-black text-ocean-950">
                      {sanitizeText(station.name, 120)}
                    </p>
                    <p className="text-[11px] text-ocean-600">
                      {station.kind === "MOBILE" ? "Base móvel" : "Posto fixo"}
                      {station.beachName
                        ? ` • ${sanitizeText(station.beachName, 120)}`
                        : ""}
                    </p>
                    {station.statusNote ? (
                      <p className="mt-1 text-[11px] text-ocean-700">
                        {station.active ? "Última parada" : "Motivo"}:{" "}
                        {sanitizeText(station.statusNote, 200)}
                      </p>
                    ) : null}
                    {!station.active && station.deactivatedAt ? (
                      <p className="text-[11px] text-ocean-500">
                        Desativado em {formatDateTime(station.deactivatedAt)}
                      </p>
                    ) : null}
                    <p className="text-[11px] text-ocean-500">
                      {station.positionSource
                        ? POSITION_SOURCE_LABEL[station.positionSource]
                        : "Sem origem registrada"}
                      {station.positionAccuracyM !== null
                        ? ` · precisão ~${Math.round(station.positionAccuracyM)} m`
                        : ""}
                      {station.positionUpdatedAt
                        ? ` · ${formatDateTime(station.positionUpdatedAt)}`
                        : ""}
                    </p>
                  </div>

                  <span
                    className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                      station.active
                        ? "bg-emerald-100 text-emerald-800"
                        : "bg-slate-100 text-slate-700"
                    }`}
                    title={
                      station.kind === "MOBILE" && station.active
                        ? "A base móvel só aparece no mapa público enquanto houver plantão aberto nela."
                        : undefined
                    }
                  >
                    {!station.active
                      ? "Fora do mapa"
                      : station.kind === "MOBILE"
                        ? "No mapa com plantão"
                        : "No mapa"}
                  </span>
                </div>

                <PhotoGallery
                  urls={station.photoUrls}
                  label={sanitizeText(station.name, 120)}
                  compact
                  openLabel="Ampliar foto do posto"
                />

                {repositionId === station.id ? (
                  <StationPositionForm
                    station={station}
                    onMoved={(updated) => {
                      applyUpdate(updated);
                      setRepositionId(null);
                    }}
                    onCancel={() => setRepositionId(null)}
                  />
                ) : (
                  <button
                    type="button"
                    onClick={() => setRepositionId(station.id)}
                    className="inline-flex min-h-9 w-fit items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 transition hover:bg-ocean-50"
                  >
                    <Crosshair aria-hidden="true" className="size-3.5" />
                    Corrigir posição
                  </button>
                )}

                {!station.active ? (
                  <button
                    type="button"
                    disabled={busyId === station.id}
                    onClick={() => void setActive(station, true, null)}
                    className="inline-flex min-h-9 w-fit items-center gap-1.5 rounded-xl bg-ocean-950 px-3 text-xs font-bold text-white transition hover:bg-ocean-900 disabled:opacity-60"
                  >
                    <Power aria-hidden="true" className="size-3.5" />
                    Reativar no mapa
                  </button>
                ) : reason?.id === station.id ? (
                  <DeactivationForm
                    station={station}
                    busy={busyId === station.id}
                    note={reason.note}
                    onNoteChange={(note) =>
                      setReason({ id: station.id, note })
                    }
                    onCancel={() => setReason(null)}
                    onConfirm={async (note) => {
                      const ok = await setActive(
                        station,
                        false,
                        note.trim() ? note.trim() : null,
                      );
                      if (ok) setReason(null);
                    }}
                  />
                ) : (
                  <button
                    type="button"
                    disabled={busyId === station.id}
                    onClick={() => setReason({ id: station.id, note: "" })}
                    className="inline-flex min-h-9 w-fit items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 transition hover:bg-ocean-50 disabled:opacity-60"
                  >
                    <Power aria-hidden="true" className="size-3.5" />
                    Desativar
                  </button>
                )}
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

interface DeactivationFormProps {
  readonly station: AdminStation;
  readonly busy: boolean;
  readonly note: string;
  readonly onNoteChange: (note: string) => void;
  readonly onCancel: () => void;
  readonly onConfirm: (note: string) => Promise<void>;
}

function DeactivationForm({
  station,
  busy,
  note,
  onNoteChange,
  onCancel,
  onConfirm,
}: DeactivationFormProps) {
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    void onConfirm(note);
  };

  return (
    <form
      onSubmit={submit}
      className="grid gap-2 rounded-xl border border-ocean-200 bg-ocean-50/60 p-2.5"
    >
      <p className="text-[11px] leading-snug text-ocean-700">
        <strong>{sanitizeText(station.name, 120)}</strong> sai do mapa público e
        da lista de destinos do plantão. Plantões e ocorrências já registrados
        continuam no histórico.
      </p>
      <label className="grid gap-1 text-[11px] font-bold text-ocean-800">
        Motivo (opcional)
        <input
          type="text"
          value={note}
          maxLength={200}
          onChange={(event) => onNoteChange(event.target.value)}
          placeholder="Em reforma, sem efetivo, interditado..."
          className="min-h-9 w-full rounded-lg border border-ocean-200 bg-white px-2 text-xs font-medium text-ocean-950 placeholder:text-ocean-400"
        />
      </label>
      <div className="flex gap-1.5">
        <button
          type="submit"
          disabled={busy}
          className="inline-flex min-h-9 flex-1 items-center justify-center gap-1.5 rounded-xl bg-coral-600 px-3 text-xs font-bold text-white transition hover:bg-coral-500 disabled:opacity-60"
        >
          <Power aria-hidden="true" className="size-3.5" />
          {busy ? "Desativando" : "Confirmar desativação"}
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
