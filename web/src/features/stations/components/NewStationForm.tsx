import { useState, type FormEvent } from "react";
import {
  AlertTriangle,
  Camera,
  CheckCircle,
  MapPinPlus,
  Trash2,
  X,
} from "lucide-react";
import { Card } from "@/shared/components/Card";
import { sanitizeText } from "@/shared/lib/sanitize";
import { photoProblem, uploadPhoto } from "@/shared/lib/media";
import { StationLocationField } from "@/features/stations/components/StationLocationField";
import { useStationPosition } from "@/features/stations/hooks/use-station-position";
import { createStation, stationErrorMessage } from "@/features/stations/api/stations";
import {
  MAX_STATION_PHOTOS,
  type AdminStation,
  type StationKind,
} from "@/features/stations/types";

interface PickedPhoto {
  readonly key: string;
  readonly file: File;
  readonly previewUrl: string;
}

/** Área de banho onde o posto fixo está sendo criado. */
export interface BeachOption {
  readonly id: string;
  readonly name: string;
  readonly municipality: string;
}

interface NewStationFormProps {
  readonly beaches: readonly BeachOption[];
  readonly onCreated?: (station: AdminStation) => void;
}

/**
 * Cadastro de posto fixo ou base móvel com a posição do aparelho (RF11).
 *
 * <p>É a segunda forma de cadastrar estrutura, para o guarda-vidas em campo:
 * ele informa o nome, escolhe o tipo e o posto nasce onde ele está. A
 * coordenação revê o ponto depois, com a origem e a precisão registradas.
 */
export function NewStationForm({ beaches, onCreated }: NewStationFormProps) {
  const [kind, setKind] = useState<StationKind>("FIXED");
  const [name, setName] = useState("");
  const [beachId, setBeachId] = useState<string>("");
  const { position, locating, error: locationError, read } = useStationPosition();
  const [photos, setPhotos] = useState<readonly PickedPhoto[]>([]);
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<
    { readonly kind: "ok" | "error"; readonly text: string } | null
  >(null);

  const effectiveBeachId = beachId || beaches[0]?.id || "";

  const addPhotos = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const room = MAX_STATION_PHOTOS - photos.length;
    if (room <= 0) {
      setFeedback({
        kind: "error",
        text: `Cada estrutura aceita no máximo ${MAX_STATION_PHOTOS} fotos.`,
      });
      return;
    }
    const accepted: PickedPhoto[] = [];
    for (const file of Array.from(files).slice(0, room)) {
      const problem = photoProblem(file);
      if (problem) {
        setFeedback({ kind: "error", text: problem });
        continue;
      }
      accepted.push({
        key: `${file.name}-${file.lastModified}-${Math.random().toString(36).slice(2)}`,
        file,
        previewUrl: URL.createObjectURL(file),
      });
    }
    if (accepted.length > 0) {
      setPhotos((current) => [...current, ...accepted]);
      setFeedback(null);
    }
  };

  const removePhoto = (key: string) => {
    setPhotos((current) => {
      const target = current.find((photo) => photo.key === key);
      if (target) URL.revokeObjectURL(target.previewUrl);
      return current.filter((photo) => photo.key !== key);
    });
  };

  const clearPhotos = () => {
    setPhotos((current) => {
      for (const photo of current) URL.revokeObjectURL(photo.previewUrl);
      return [];
    });
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!name.trim()) {
      setFeedback({ kind: "error", text: "Informe o nome da estrutura." });
      return;
    }
    if (!position) {
      setFeedback({
        kind: "error",
        text: "Leia a posição atual antes de cadastrar.",
      });
      return;
    }
    if (kind === "FIXED" && !effectiveBeachId) {
      setFeedback({
        kind: "error",
        text: "Escolha a área de banho do posto fixo.",
      });
      return;
    }

    setBusy(true);
    setFeedback(null);
    try {
      const mediaIds: string[] = [];
      for (const photo of photos) {
        setProgress(
          `Enviando foto ${mediaIds.length + 1} de ${photos.length}`,
        );
        mediaIds.push((await uploadPhoto(photo.file)).id);
      }
      setProgress("Cadastrando estrutura");
      const created = await createStation({
        kind,
        name,
        beachId: kind === "FIXED" ? effectiveBeachId : null,
        latitude: position.latitude,
        longitude: position.longitude,
        accuracyMeters: position.accuracyMeters,
        mediaIds,
      });
      setFeedback({
        kind: "ok",
        text: `${sanitizeText(created.name, 120)} cadastrado no ponto atual.`,
      });
      setName("");
      clearPhotos();
      onCreated?.(created);
    } catch (error) {
      setFeedback({ kind: "error", text: stationErrorMessage(error) });
    } finally {
      setBusy(false);
      setProgress(null);
    }
  };

  return (
    <Card>
      <form onSubmit={submit} className="grid gap-3">
        <div>
          <h2 className="flex items-center gap-1.5 text-sm font-black text-ocean-950">
            <MapPinPlus aria-hidden="true" className="size-4 text-ocean-600" />
            Cadastrar estrutura aqui
          </h2>
          <p className="mt-1 text-[11px] leading-snug text-ocean-600">
            O posto ou base nasce na sua posição atual. A coordenação vê a
            origem e a precisão do ponto.
          </p>
        </div>

        {feedback ? (
          <div
            className={[
              "flex items-start justify-between gap-2 rounded-xl border px-2.5 py-2 text-[11px] font-semibold",
              feedback.kind === "ok"
                ? "border-emerald-200 bg-emerald-50 text-ocean-950"
                : "border-coral-200 bg-coral-50 text-ocean-950",
            ].join(" ")}
          >
            <output className="flex items-start gap-1.5">
              {feedback.kind === "ok" ? (
                <CheckCircle
                  aria-hidden="true"
                  className="mt-0.5 size-3.5 shrink-0 text-emerald-600"
                />
              ) : (
                <AlertTriangle
                  aria-hidden="true"
                  className="mt-0.5 size-3.5 shrink-0 text-coral-600"
                />
              )}
              {feedback.text}
            </output>
            <button
              type="button"
              onClick={() => setFeedback(null)}
              aria-label="Fechar aviso"
              className="grid size-6 shrink-0 place-items-center rounded-lg text-ocean-600 hover:bg-white"
            >
              <X aria-hidden="true" className="size-3" />
            </button>
          </div>
        ) : null}

        <fieldset className="grid gap-1.5">
          <legend className="text-xs font-bold text-ocean-800">Tipo</legend>
          <div className="flex flex-wrap gap-1.5">
            {(["FIXED", "MOBILE"] as const).map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => setKind(option)}
                aria-pressed={kind === option}
                className={[
                  "min-h-9 rounded-xl border px-3 text-xs font-bold transition",
                  kind === option
                    ? "border-ocean-950 bg-ocean-950 text-white"
                    : "border-ocean-200 bg-white text-ocean-900 hover:bg-ocean-50",
                ].join(" ")}
              >
                {option === "FIXED" ? "Posto fixo" : "Base móvel"}
              </button>
            ))}
          </div>
        </fieldset>

        <label className="grid gap-1 text-xs font-bold text-ocean-800">
          Nome da estrutura
          <input
            type="text"
            value={name}
            maxLength={120}
            onChange={(event) => setName(event.target.value)}
            placeholder="Posto 04 — Praia do Restinho"
            className="min-h-10 rounded-xl border border-ocean-200 bg-white px-3 text-sm font-medium text-ocean-950 placeholder:text-ocean-400"
          />
        </label>

        {kind === "FIXED" ? (
          beaches.length > 0 ? (
            <label className="grid gap-1 text-xs font-bold text-ocean-800">
              Área de banho
              <select
                value={effectiveBeachId}
                onChange={(event) => setBeachId(event.target.value)}
                className="min-h-10 rounded-xl border border-ocean-200 bg-white px-3 text-sm font-medium text-ocean-950"
              >
                {beaches.map((beach) => (
                  <option key={beach.id} value={beach.id}>
                    {beach.name} — {beach.municipality}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <p className="text-[11px] font-semibold text-coral-700">
              Nenhuma área de banho próxima. Aproxime-se da praia ou cadastre a
              base móvel.
            </p>
          )
        ) : (
          <p className="text-[11px] leading-snug text-ocean-600">
            A base móvel não pertence a uma praia: ela aparece no mapa público
            enquanto houver plantão aberto nela.
          </p>
        )}

        <div className="grid gap-1.5">
          <label
            htmlFor="station-photos"
            className="flex items-center gap-1 text-xs font-bold text-ocean-800"
          >
            <Camera aria-hidden="true" className="size-3.5" />
            Fotos do posto (opcional, até {MAX_STATION_PHOTOS})
          </label>
          <input
            id="station-photos"
            type="file"
            accept="image/jpeg,image/png"
            multiple
            disabled={busy || photos.length >= MAX_STATION_PHOTOS}
            onChange={(event) => {
              addPhotos(event.target.files);
              event.target.value = "";
            }}
            className="block w-full cursor-pointer rounded-xl border border-dashed border-ocean-300 bg-ocean-50/60 px-3 py-2.5 text-xs font-semibold text-ocean-800 file:mr-3 file:rounded-lg file:border-0 file:bg-ocean-950 file:px-3 file:py-1.5 file:text-xs file:font-bold file:text-white hover:border-ocean-500 disabled:opacity-60"
          />
          <p className="text-[11px] leading-snug text-ocean-600">
            A população vê a foto na ficha do posto. O servidor reduz a imagem e
            remove os metadados do aparelho.
          </p>
          {photos.length > 0 ? (
            <ul className="flex flex-wrap gap-2">
              {photos.map((photo) => (
                <li key={photo.key} className="relative">
                  <img
                    src={photo.previewUrl}
                    alt={`Prévia de ${photo.file.name}`}
                    className="size-16 rounded-lg border border-ocean-200 object-cover"
                  />
                  <button
                    type="button"
                    onClick={() => removePhoto(photo.key)}
                    disabled={busy}
                    aria-label={`Remover ${photo.file.name}`}
                    className="absolute -right-1.5 -top-1.5 grid size-6 place-items-center rounded-full bg-ocean-950 text-white shadow disabled:opacity-60"
                  >
                    <Trash2 aria-hidden="true" className="size-3" />
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
        </div>

        <StationLocationField
          position={position}
          locating={locating}
          error={locationError}
          onRead={() => void read()}
        />

        <button
          type="submit"
          disabled={busy}
          className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 px-3 text-sm font-bold text-white transition hover:bg-ocean-900 disabled:opacity-60"
        >
          <MapPinPlus aria-hidden="true" className="size-4" />
          {busy ? (progress ?? "Cadastrando") : "Cadastrar no ponto atual"}
        </button>
      </form>
    </Card>
  );
}
