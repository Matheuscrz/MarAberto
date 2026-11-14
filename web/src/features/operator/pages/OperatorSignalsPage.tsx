import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import {
  AlertTriangle,
  Camera,
  CheckCircle,
  Flag,
  LocateFixed,
  RefreshCw,
  Timer,
  Trash2,
  X,
} from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import { formatDateTime, formatDistance } from "@/shared/lib/format";
import { FLAG_COLORS, FLAG_LABELS } from "@/shared/lib/flags";
import {
  GeolocationError,
  getDevicePosition,
  type DevicePosition,
} from "@/shared/lib/geolocation";
import {
  MAX_SIGNAL_PHOTOS,
  mediaErrorMessage,
  photoProblem,
  uploadPhoto,
} from "@/shared/lib/media";
import { sanitizeText } from "@/shared/lib/sanitize";
import type { FlagColor } from "@/shared/types/flags";
import {
  sectorLabel,
  useBeachMap,
  useNearbyBeaches,
} from "@/features/beaches";
import {
  SignalPhotos,
  SignalStateTag,
  closeFlag,
  launchFlag,
  signalErrorMessage,
  signalState,
  useBeachSignals,
} from "@/features/signals";

const FLAG_ORDER: readonly FlagColor[] = [
  "GREEN",
  "YELLOW",
  "RED",
  "PURPLE",
  "BLACK",
];

type SignalMode = "SCOPE" | "LOCATION";

interface Feedback {
  readonly kind: "ok" | "error";
  readonly text: string;
}

interface SelectedPhoto {
  readonly key: string;
  readonly file: File;
  readonly previewUrl: string;
}

/**
 * Lançamento da bandeira pelo guarda-vidas (RF12/RN01).
 *
 * <p>Duas formas de dizer onde a bandeira está plantada, como as duas formas de
 * sinalizar em campo: escolhendo a praia e o setor no console (para a
 * sinalização geral) ou usando a leitura atual do aparelho, quando o servidor
 * resolve praia e setor pelo ponto. A foto é opcional e acompanha o
 * lançamento; ela é re-codificada no servidor, que descarta EXIF e GPS.
 *
 * <p>A bandeira vale para a praia inteira ou para um setor. Lançar de novo no
 * mesmo escopo encerra a anterior, sem apagar o histórico; ao vencer, a
 * sinalização sai do mapa e continua na ficha como "desatualizada". A validade
 * padrão é 12 h e o operador pode encurtar ou estender dentro de 0,5 h a 24 h.
 */
export function OperatorSignalsPage() {
  const nearby = useNearbyBeaches();
  const [mode, setMode] = useState<SignalMode>("SCOPE");
  const [beachId, setBeachId] = useState<string | null>(null);
  const [sectorId, setSectorId] = useState("");
  const [flag, setFlag] = useState<FlagColor>("GREEN");
  const [notes, setNotes] = useState("");
  const [validityHours, setValidityHours] = useState("12");
  const [position, setPosition] = useState<DevicePosition | null>(null);
  const [locating, setLocating] = useState(false);
  const [locationError, setLocationError] = useState<string | null>(null);
  const [photos, setPhotos] = useState<readonly SelectedPhoto[]>([]);
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState<string | null>(null);
  const [closingId, setClosingId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const fileInput = useRef<HTMLInputElement | null>(null);

  // Sem escolha explícita, a praia mais próxima da posição atual é a padrão.
  const activeBeachId = beachId ?? nearby.beaches[0]?.id ?? null;
  const beachMap = useBeachMap(activeBeachId);
  const signals = useBeachSignals(activeBeachId);

  const sectors = useMemo(
    () => [...(beachMap.map?.sectors ?? [])].sort(
      (a, b) => a.displayOrder - b.displayOrder,
    ),
    [beachMap.map],
  );

  // Setor escolhido só vale enquanto existir na praia carregada; trocar de
  // praia (ou de mapa) cai para "praia inteira" sem um efeito de sincronização.
  const effectiveSectorId = sectors.some((sector) => sector.id === sectorId)
    ? sectorId
    : "";

  // As miniaturas são URLs de objeto; liberar ao desmontar evita segurar o
  // arquivo na memória do WebView depois de sair da tela.
  const photosRef = useRef<readonly SelectedPhoto[]>([]);
  useEffect(() => {
    photosRef.current = photos;
  }, [photos]);
  useEffect(
    () => () => {
      photosRef.current.forEach((photo) => URL.revokeObjectURL(photo.previewUrl));
    },
    [],
  );

  const readPosition = async () => {
    setLocating(true);
    setLocationError(null);
    try {
      setPosition(await getDevicePosition());
    } catch (error) {
      setLocationError(
        error instanceof GeolocationError
          ? error.message
          : "Não foi possível obter a localização agora.",
      );
    } finally {
      setLocating(false);
    }
  };

  const selectMode = (next: SignalMode) => {
    setMode(next);
    setFeedback(null);
    if (next === "LOCATION" && position === null && !locating) {
      // A troca de modo é um gesto do usuário: é aqui que a permissão do
      // aparelho pode ser pedida sem susto.
      void readPosition();
    }
  };

  const addPhotos = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const next = [...photos];
    for (const file of Array.from(files)) {
      if (next.length >= MAX_SIGNAL_PHOTOS) {
        setFeedback({
          kind: "error",
          text: `Cada lançamento aceita no máximo ${MAX_SIGNAL_PHOTOS} fotos.`,
        });
        break;
      }
      const problem = photoProblem(file);
      if (problem) {
        setFeedback({ kind: "error", text: problem });
        continue;
      }
      next.push({
        key: `${file.name}-${file.lastModified}-${next.length}`,
        file,
        previewUrl: URL.createObjectURL(file),
      });
    }
    setPhotos(next);
    if (fileInput.current) {
      fileInput.current.value = "";
    }
  };

  const removePhoto = (key: string) => {
    setPhotos((current) => {
      const removed = current.find((photo) => photo.key === key);
      if (removed) URL.revokeObjectURL(removed.previewUrl);
      return current.filter((photo) => photo.key !== key);
    });
  };

  const clearPhotos = () => {
    setPhotos((current) => {
      current.forEach((photo) => URL.revokeObjectURL(photo.previewUrl));
      return [];
    });
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (mode === "SCOPE" && !activeBeachId) return;
    if (mode === "LOCATION" && !position) {
      setFeedback({
        kind: "error",
        text: "Leia a localização atual antes de lançar a bandeira.",
      });
      return;
    }

    const hoursText = validityHours.trim();
    const hours = hoursText === "" ? undefined : Number(hoursText);
    if (
      hours !== undefined &&
      (!Number.isFinite(hours) || hours < 0.5 || hours > 24)
    ) {
      setFeedback({
        kind: "error",
        text: "A validade precisa estar entre 0,5 e 24 horas.",
      });
      return;
    }

    setBusy(true);
    setFeedback(null);
    let phase: "upload" | "launch" = "upload";
    try {
      const mediaIds: string[] = [];
      for (const [index, photo] of photos.entries()) {
        setProgress(
          photos.length > 1
            ? `Enviando foto ${index + 1} de ${photos.length}`
            : "Enviando a foto",
        );
        mediaIds.push((await uploadPhoto(photo.file)).id);
      }
      phase = "launch";
      setProgress("Lançando a bandeira");

      const result = await launchFlag(
        mode === "LOCATION" && position
          ? {
              flag,
              notes,
              validityHours: hours,
              latitude: position.latitude,
              longitude: position.longitude,
              accuracyMeters: position.accuracyMeters,
              mediaIds,
            }
          : {
              beachId: activeBeachId,
              sectorId: effectiveSectorId || null,
              flag,
              notes,
              validityHours: hours,
              mediaIds,
            },
      );

      if (result.outcome === "UNMAPPED") {
        const nearest = result.unmapped?.nearestBeachName;
        const distance = result.unmapped?.nearestBeachDistanceMeters;
        setFeedback({
          kind: "ok",
          text:
            `Área não identificada: o registro foi enviado para a coordenação e nenhuma bandeira foi publicada no mapa.${
              nearest
                ? ` A área mapeada mais próxima é ${nearest}${
                    distance != null ? `, a ${formatDistance(distance)}` : ""
                  }.`
                : ""
            }`,
        });
        setNotes("");
        clearPhotos();
        return;
      }

      setFeedback({
        kind: "ok",
        text:
          mode === "LOCATION"
            ? `${FLAG_LABELS[flag]} lançada na área resolvida pela sua posição. O mapa público reflete em até 30 segundos.`
            : `${FLAG_LABELS[flag]} lançada. O mapa público reflete em até 30 segundos.`,
      });
      setNotes("");
      clearPhotos();
      if (result.flag) {
        setBeachId(result.flag.beachId);
        signals.reload();
      }
    } catch (error) {
      setFeedback({
        kind: "error",
        text:
          phase === "upload"
            ? mediaErrorMessage(error)
            : signalErrorMessage(error),
      });
    } finally {
      setProgress(null);
      setBusy(false);
    }
  };

  const handleClose = async (id: string) => {
    setClosingId(id);
    setFeedback(null);
    try {
      await closeFlag(id);
      setFeedback({
        kind: "ok",
        text: "Sinalização encerrada. Ela sai do mapa e continua no histórico.",
      });
      signals.reload();
    } catch (error) {
      setFeedback({ kind: "error", text: signalErrorMessage(error) });
    } finally {
      setClosingId(null);
    }
  };

  return (
    <div className="grid gap-3">
      <PageHeader
        title="Sinalização"
        subtitle="Escolha a praia e o setor ou use a sua localização atual. A foto é opcional e ajuda quem chega depois."
      />

      {feedback ? (
        <Card
          className={
            feedback.kind === "ok"
              ? "flex items-start justify-between gap-2 border-emerald-200 bg-emerald-50"
              : "flex items-start justify-between gap-2 border-coral-200 bg-coral-50"
          }
        >
          <output className="flex items-start gap-1.5 text-xs font-semibold text-ocean-950">
            {feedback.kind === "ok" ? (
              <CheckCircle
                aria-hidden="true"
                className="mt-0.5 size-4 shrink-0 text-emerald-600"
              />
            ) : (
              <AlertTriangle
                aria-hidden="true"
                className="mt-0.5 size-4 shrink-0 text-coral-600"
              />
            )}
            {feedback.text}
          </output>
          <button
            type="button"
            onClick={() => setFeedback(null)}
            aria-label="Fechar aviso"
            className="grid size-7 shrink-0 place-items-center rounded-lg text-ocean-600 hover:bg-white"
          >
            <X aria-hidden="true" className="size-3.5" />
          </button>
        </Card>
      ) : null}

      <Card>
        <form onSubmit={submit} className="grid gap-3">
          <fieldset className="grid gap-1.5">
            <legend className="text-xs font-bold text-ocean-800">
              Como identificar o local
            </legend>
            <div className="grid gap-1.5 sm:grid-cols-2">
              <ModeOption
                checked={mode === "SCOPE"}
                onSelect={() => selectMode("SCOPE")}
                title="Praia e setor"
                description="Para a sinalização geral da área, escolhida no console."
              />
              <ModeOption
                checked={mode === "LOCATION"}
                onSelect={() => selectMode("LOCATION")}
                title="Minha localização"
                description="Para plantar a bandeira aqui: o servidor identifica praia e setor."
              />
            </div>
          </fieldset>

          {mode === "SCOPE" ? (
            nearby.status === "locating" ? (
              <output className="block py-4 text-center text-xs font-semibold text-ocean-600">
                Localizando as praias próximas
              </output>
            ) : nearby.status === "error" || nearby.status === "empty" ? (
              <div className="rounded-xl border border-dashed border-ocean-300 bg-ocean-50/40 p-3 text-center">
                <p className="text-xs font-bold text-ocean-950">
                  {nearby.status === "empty"
                    ? "Nenhuma área de banho próxima"
                    : "Não foi possível localizar você"}
                </p>
                <p className="mt-1 text-[11px] text-ocean-700">
                  {nearby.message ??
                    "A sinalização por praia e setor exige a lista de áreas próximas."}
                </p>
                <button
                  type="button"
                  onClick={nearby.retry}
                  className="mt-2 inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 hover:bg-ocean-50"
                >
                  <RefreshCw aria-hidden="true" className="size-3.5" />
                  Tentar de novo
                </button>
              </div>
            ) : (
              <>
                <label className="grid gap-1 text-xs font-bold text-ocean-800">
                  Área de banho
                  <select
                    value={activeBeachId ?? ""}
                    onChange={(event) => {
                      setBeachId(event.target.value || null);
                      setSectorId("");
                      setFeedback(null);
                    }}
                    className="min-h-10 rounded-xl border border-ocean-200 bg-white px-3 text-sm font-medium text-ocean-950"
                  >
                    {nearby.beaches.map((beach) => (
                      <option key={beach.id} value={beach.id}>
                        {beach.name} — {beach.municipality}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="grid gap-1 text-xs font-bold text-ocean-800">
                  Onde vale
                  <select
                    value={effectiveSectorId}
                    onChange={(event) => setSectorId(event.target.value)}
                    className="min-h-10 rounded-xl border border-ocean-200 bg-white px-3 text-sm font-medium text-ocean-950"
                  >
                    <option value="">Praia inteira</option>
                    {sectors.map((sector) => (
                      <option key={sector.id} value={sector.id}>
                        {sectorLabel(sector)}
                      </option>
                    ))}
                  </select>
                </label>
              </>
            )
          ) : (
            <div className="grid gap-2 rounded-xl border border-ocean-200 bg-ocean-50/60 p-3">
              <p className="flex items-center gap-1.5 text-xs font-bold text-ocean-900">
                <LocateFixed aria-hidden="true" className="size-3.5" />
                Localização atual
              </p>
              {position ? (
                <p className="text-[11px] leading-snug text-ocean-700">
                  {position.latitude.toFixed(5)}, {position.longitude.toFixed(5)}
                  {" · "}
                  precisão de aproximadamente{" "}
                  {Math.round(position.accuracyMeters)} m
                </p>
              ) : (
                <p className="text-[11px] leading-snug text-ocean-700">
                  {locating
                    ? "Lendo a posição do aparelho..."
                    : "Leia a posição para o servidor identificar a praia e o setor."}
                </p>
              )}
              {locationError ? (
                <p className="text-[11px] font-semibold text-coral-700">
                  {locationError}
                </p>
              ) : null}
              <button
                type="button"
                disabled={locating}
                onClick={() => void readPosition()}
                className="inline-flex min-h-9 w-fit items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 transition hover:bg-ocean-50 disabled:opacity-60"
              >
                <LocateFixed aria-hidden="true" className="size-3.5" />
                {locating
                  ? "Lendo"
                  : position
                    ? "Atualizar localização"
                    : "Ler minha localização"}
              </button>
              <p className="text-[11px] leading-snug text-ocean-600">
                Fora de uma área mapeada o lançamento vira um registro de área
                não identificada, enviado para a coordenação — nenhuma bandeira
                aparece no mapa até a triagem.
              </p>
            </div>
          )}

          <fieldset className="grid gap-1.5">
            <legend className="text-xs font-bold text-ocean-800">Bandeira</legend>
            <div className="flex flex-wrap gap-1.5">
              {FLAG_ORDER.map((color) => (
                <button
                  key={color}
                  type="button"
                  onClick={() => setFlag(color)}
                  aria-pressed={flag === color}
                  className={[
                    "inline-flex min-h-9 items-center gap-1.5 rounded-xl border px-3 text-xs font-bold transition",
                    flag === color
                      ? "border-ocean-950 bg-ocean-950 text-white"
                      : "border-ocean-200 bg-white text-ocean-900 hover:bg-ocean-50",
                  ].join(" ")}
                >
                  <span
                    aria-hidden="true"
                    className="size-3 rounded-full border border-white/60"
                    style={{ backgroundColor: FLAG_COLORS[color] }}
                  />
                  {FLAG_LABELS[color]}
                </button>
              ))}
            </div>
          </fieldset>

          <label className="grid gap-1 text-xs font-bold text-ocean-800">
            Observação (opcional)
            <input
              type="text"
              value={notes}
              maxLength={400}
              onChange={(event) => setNotes(event.target.value)}
              placeholder="Corrente de retorno no canto norte, água-viva..."
              className="min-h-10 rounded-xl border border-ocean-200 bg-white px-3 text-sm font-medium text-ocean-950 placeholder:text-ocean-400"
            />
          </label>

          <div className="grid gap-1.5">
            <label
              htmlFor="signal-photos"
              className="flex items-center gap-1 text-xs font-bold text-ocean-800"
            >
              <Camera aria-hidden="true" className="size-3.5" />
              Fotos (opcional, até {MAX_SIGNAL_PHOTOS})
            </label>
            <input
              id="signal-photos"
              ref={fileInput}
              type="file"
              accept="image/jpeg,image/png"
              multiple
              disabled={photos.length >= MAX_SIGNAL_PHOTOS}
              onChange={(event) => addPhotos(event.target.files)}
              className="block w-full cursor-pointer rounded-xl border border-dashed border-ocean-300 bg-ocean-50/60 px-3 py-2.5 text-xs font-semibold text-ocean-800 file:mr-3 file:rounded-lg file:border-0 file:bg-ocean-950 file:px-3 file:py-1.5 file:text-xs file:font-bold file:text-white hover:border-ocean-500 disabled:opacity-60"
            />
            <p className="text-[11px] leading-snug text-ocean-600">
              A foto é reduzida e tem os metadados do aparelho removidos no
              servidor. A localização do pin é a que você enviou, não o EXIF.
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
                      aria-label={`Remover ${photo.file.name}`}
                      className="absolute -right-1.5 -top-1.5 grid size-6 place-items-center rounded-full bg-ocean-950 text-white shadow"
                    >
                      <Trash2 aria-hidden="true" className="size-3" />
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </div>

          <label className="grid gap-1 text-xs font-bold text-ocean-800">
            <span className="flex items-center gap-1">
              <Timer aria-hidden="true" className="size-3.5" />
              Validade em horas (0,5 a 24)
            </span>
            <input
              type="number"
              inputMode="decimal"
              min={0.5}
              max={24}
              step={0.5}
              value={validityHours}
              onChange={(event) => setValidityHours(event.target.value)}
              className="min-h-10 rounded-xl border border-ocean-200 bg-white px-3 text-sm font-medium text-ocean-950"
            />
          </label>

          <p className="text-[11px] leading-snug text-ocean-600">
            Lançar no mesmo escopo encerra a bandeira anterior. Ao vencer, a
            sinalização sai do mapa e a ficha da praia mostra que está
            desatualizada.
          </p>

          <button
            type="submit"
            disabled={
              busy ||
              (mode === "SCOPE" ? !activeBeachId : position === null)
            }
            className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 px-3 text-sm font-bold text-white transition hover:bg-ocean-900 disabled:opacity-60"
          >
            <Flag aria-hidden="true" className="size-4" />
            {busy ? (progress ?? "Lançando") : "Lançar bandeira"}
          </button>
        </form>
      </Card>

      <section aria-labelledby="sinalizacoes" className="grid gap-2">
        <h2
          id="sinalizacoes"
          className="flex items-center gap-1.5 text-sm font-black text-ocean-950"
        >
          <Flag aria-hidden="true" className="size-4 text-ocean-600" />
          Último lançamento de cada escopo
        </h2>

        {signals.status === "loading" ? (
          <output className="block py-3 text-center text-xs font-semibold text-ocean-600">
            Carregando a sinalização
          </output>
        ) : signals.status === "error" ? (
          <Card className="border-dashed bg-ocean-50/40">
            <p className="text-xs font-bold text-ocean-950">
              Não foi possível carregar a sinalização
            </p>
            <p className="mt-1 text-[11px] text-ocean-700">
              {signals.message ?? "Tente de novo em instantes."}
            </p>
            <button
              type="button"
              onClick={signals.reload}
              className="mt-2 inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 hover:bg-ocean-50"
            >
              <RefreshCw aria-hidden="true" className="size-3.5" />
              Tentar de novo
            </button>
          </Card>
        ) : signals.signals.length === 0 ? (
          <Card className="border-dashed bg-ocean-50/40">
            <p className="text-xs font-bold text-ocean-950">
              Nenhuma bandeira lançada nesta área
            </p>
            <p className="mt-1 text-[11px] text-ocean-700">
              O mapa fica sem sinalização até o primeiro lançamento.
            </p>
          </Card>
        ) : (
          <ul className="grid gap-2">
            {signals.signals.map((signal) => {
              const state = signalState(signal);
              return (
                <li key={signal.id}>
                  <Card className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <p className="flex items-center gap-1.5 text-sm font-black text-ocean-950">
                        <span
                          aria-hidden="true"
                          className="size-3.5 rounded-full border border-ocean-900/10"
                          style={{ backgroundColor: FLAG_COLORS[signal.flag] }}
                        />
                        {FLAG_LABELS[signal.flag]}
                      </p>
                      <p className="text-[11px] text-ocean-600">
                        {signal.sectorLabel
                          ? sanitizeText(signal.sectorLabel, 80)
                          : "Praia inteira"}
                        {" • "}
                        {formatDateTime(signal.postedAt)}
                      </p>
                      {signal.notes ? (
                        <p className="mt-1 text-[11px] leading-snug text-ocean-700">
                          {sanitizeText(signal.notes, 300)}
                        </p>
                      ) : null}
                      <SignalPhotos
                        urls={signal.photoUrls}
                        label={signal.sectorLabel ?? signal.beachName}
                        compact
                      />
                      <p className="mt-1 flex items-center gap-1.5 text-[11px] text-ocean-600">
                        <SignalStateTag state={state} compact />
                        <span>
                          {state === "EXPIRED"
                            ? `venceu em ${formatDateTime(signal.validUntil)}`
                            : state === "CURRENT"
                              ? `válida até ${formatDateTime(signal.validUntil)}`
                              : "encerrada"}
                        </span>
                      </p>
                    </div>

                    {state === "CURRENT" ? (
                      <button
                        type="button"
                        disabled={closingId === signal.id}
                        onClick={() => void handleClose(signal.id)}
                        className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-coral-300 bg-white px-3 text-xs font-bold text-coral-800 transition hover:bg-coral-50 disabled:opacity-60"
                      >
                        {closingId === signal.id ? "Encerrando" : "Encerrar"}
                      </button>
                    ) : null}
                  </Card>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}

interface ModeOptionProps {
  readonly checked: boolean;
  readonly onSelect: () => void;
  readonly title: string;
  readonly description: string;
}

function ModeOption({
  checked,
  onSelect,
  title,
  description,
}: ModeOptionProps) {
  return (
    <label
      className={[
        "flex cursor-pointer items-start gap-2 rounded-xl border px-3 py-2 transition",
        checked
          ? "border-ocean-950 bg-ocean-950/5"
          : "border-ocean-200 bg-white hover:bg-ocean-50",
      ].join(" ")}
    >
      <input
        type="radio"
        name="signal-mode"
        checked={checked}
        onChange={onSelect}
        className="mt-0.5 size-4 accent-ocean-950"
      />
      <span className="leading-tight">
        <span className="block text-xs font-bold text-ocean-950">{title}</span>
        <span className="block text-[11px] text-ocean-700">{description}</span>
      </span>
    </label>
  );
}
