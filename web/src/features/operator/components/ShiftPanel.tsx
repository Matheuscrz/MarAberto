import { useCallback, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  AlertTriangle,
  CheckCircle,
  Clock,
  Crosshair,
  LogIn,
  LogOut,
  MapPin,
  ShieldCheck,
  Truck,
  X,
} from "lucide-react";
import {
  formatAccuracy,
  formatDateTime,
  formatDistance,
} from "@/shared/lib/format";
import { distanceMetersBetween } from "@/shared/lib/geo";
import {
  GeolocationError,
  getDevicePosition,
} from "@/shared/lib/geolocation";
import { useShift } from "@/features/operator/lib/use-shift";
import type {
  ShiftTarget,
  ShiftTargetKind,
} from "@/features/operator/types";

const KIND_LABEL: Record<ShiftTargetKind, string> = {
  POST: "Posto fixo",
  MOBILE_BASE: "Base móvel",
};

const FEEDBACK_STYLE: Record<"success" | "warning" | "error", string> = {
  success: "border-emerald-300 bg-emerald-50 text-emerald-900",
  warning: "border-amber-300 bg-amber-50 text-amber-900",
  error: "border-coral-300 bg-coral-50 text-coral-900",
};

interface ShiftPanelProps {
  /**
   * Chamado no gesto de entrar de plantão, enquanto o navegador ainda aceita
   * liberar som e notificação. Armar aqui é o que permite o alarme tocar
   * sozinho depois.
   */
  readonly onArmAlerts?: () => void;
}

/**
 * Plantão do operador, com a localização que entra na seleção espacial do SOS.
 *
 * <p>Sem plantão aberto o PostGIS não escolhe o operador para nenhum chamado.
 * Ao abrir, a posição atual do aparelho é capturada (e pode ser corrigida na
 * mão); depois disso o console oferece atualização sob demanda e renova a
 * posição a cada 5 min enquanto a aba está aberta. Nada é guardado no
 * navegador e não há rastreamento em segundo plano.
 */
export function ShiftPanel({ onArmAlerts }: ShiftPanelProps) {
  const {
    shift,
    targets,
    loading,
    loadError,
    busy,
    feedback,
    open,
    refreshPosition,
    close,
    reload,
    dismissFeedback,
  } = useShift();

  const [formOpen, setFormOpen] = useState(false);
  const [confirmingClose, setConfirmingClose] = useState(false);
  const [kind, setKind] = useState<ShiftTargetKind>("POST");
  const [targetId, setTargetId] = useState("");
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");
  const [accuracy, setAccuracy] = useState<number | null>(null);
  const [locating, setLocating] = useState(false);
  const [geoMessage, setGeoMessage] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const options = targets.filter((target) => target.kind === kind);
  const selected =
    options.find((target) => target.id === targetId) ?? options[0] ?? null;

  // A mesma leitura que abre o plantão serve para dizer a distância até cada
  // destino: o guarda-vidas não escolhe "Base Móvel Orla" sem saber onde ela
  // está. Quando ele digita a coordenada na mão, a conta acompanha.
  const typedLatitude = Number.parseFloat(latitude);
  const typedLongitude = Number.parseFloat(longitude);
  const referencePosition =
    Number.isFinite(typedLatitude) && Number.isFinite(typedLongitude)
      ? { latitude: typedLatitude, longitude: typedLongitude }
      : null;

  const distanceTo = (target: ShiftTarget): number | null =>
    referencePosition === null
      ? null
      : distanceMetersBetween(referencePosition, target);

  const capturePosition = useCallback(async () => {
    setLocating(true);
    setGeoMessage(null);
    try {
      const position = await getDevicePosition();
      setLatitude(position.latitude.toFixed(6));
      setLongitude(position.longitude.toFixed(6));
      setAccuracy(position.accuracyMeters);
      setGeoMessage(
        `Localização atual capturada (${formatAccuracy(position.accuracyMeters)}).`,
      );
    } catch (error) {
      setAccuracy(null);
      setGeoMessage(
        error instanceof GeolocationError
          ? error.message
          : "Não foi possível obter a localização agora.",
      );
    } finally {
      setLocating(false);
    }
  }, []);

  const startForm = () => {
    setFormOpen(true);
    setFormError(null);
    setConfirmingClose(false);
    void capturePosition();
  };

  const resetForm = () => {
    setFormOpen(false);
    setTargetId("");
    setLatitude("");
    setLongitude("");
    setAccuracy(null);
    setGeoMessage(null);
    setFormError(null);
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setFormError(null);

    // Gesto do usuário: é agora que o navegador aceita liberar áudio e
    // permissão de notificação. Antes de qualquer await.
    onArmAlerts?.();

    if (!selected) {
      setFormError("Escolha um posto fixo ou uma base móvel.");
      return;
    }

    const typedLatitude = Number.parseFloat(latitude);
    const typedLongitude = Number.parseFloat(longitude);
    const hasLatitude = Number.isFinite(typedLatitude);
    const hasLongitude = Number.isFinite(typedLongitude);

    if (hasLatitude !== hasLongitude) {
      setFormError("Informe latitude e longitude juntas.");
      return;
    }

    let openedLatitude = typedLatitude;
    let openedLongitude = typedLongitude;
    let openedAccuracy = accuracy;

    if (!hasLatitude) {
      // A posição do aparelho é o que o despacho mede. Se a captura automática
      // ainda não chegou (ou falhou), tentamos de novo aqui, ainda no gesto.
      // Abrir sem posição faria a distância cair no cadastro do posto/base — e
      // um posto longe de onde a pessoa está aparece como centenas de km.
      setLocating(true);
      try {
        const devicePosition = await getDevicePosition();
        openedLatitude = devicePosition.latitude;
        openedLongitude = devicePosition.longitude;
        openedAccuracy = devicePosition.accuracyMeters;
        setLatitude(devicePosition.latitude.toFixed(6));
        setLongitude(devicePosition.longitude.toFixed(6));
        setAccuracy(devicePosition.accuracyMeters);
        setGeoMessage(
          `Localização atual capturada (${formatAccuracy(devicePosition.accuracyMeters)}).`,
        );
      } catch (error) {
        setGeoMessage(
          error instanceof GeolocationError
            ? error.message
            : "Não foi possível obter a localização agora.",
        );
        setFormError(
          "Sem a sua localização o despacho mede a distância pelo posto cadastrado. Capture a posição do aparelho ou informe as coordenadas para entrar de plantão.",
        );
        return;
      } finally {
        setLocating(false);
      }
    }

    if (kind === "MOBILE_BASE" && !Number.isFinite(openedLatitude)) {
      setFormError(
        "A base móvel precisa da posição atual para o despacho medir a distância. Capture pelo aparelho ou informe as coordenadas.",
      );
      return;
    }

    const opened = await open({
      ...(kind === "POST"
        ? { postId: selected.id }
        : { mobileBaseId: selected.id }),
      latitude: openedLatitude,
      longitude: openedLongitude,
      ...(openedAccuracy === null ? {} : { accuracyMeters: openedAccuracy }),
    });

    if (opened) {
      resetForm();
    }
  };

  if (loading && !shift) {
    return (
      <p className="mb-4 rounded-xl border border-ocean-200 bg-white px-3 py-2 text-xs font-semibold text-ocean-600">
        Verificando o plantão…
      </p>
    );
  }

  return (
    <section
      aria-labelledby="shift-title"
      className={`mb-4 rounded-2xl border p-4 shadow-sm ${
        shift
          ? "border-emerald-300 bg-emerald-50/70"
          : "border-amber-300 bg-amber-50/70"
      }`}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex items-start gap-2.5">
          <span
            className={`grid size-9 shrink-0 place-items-center rounded-xl text-white ${
              shift ? "bg-emerald-700" : "bg-amber-600"
            }`}
          >
            {shift ? (
              <ShieldCheck aria-hidden="true" className="size-5" />
            ) : (
              <AlertTriangle aria-hidden="true" className="size-5" />
            )}
          </span>
          <div>
            <h2
              id="shift-title"
              className="text-sm font-black text-ocean-950"
            >
              {shift ? "Você está de plantão" : "Você não está de plantão"}
            </h2>
            <p className="mt-0.5 text-xs text-ocean-700">
              {shift
                ? "O despacho do SOS pode escolher você como destinatário enquanto o plantão estiver aberto."
                : "Sem plantão aberto o despacho não escolhe você para nenhum chamado. Informe onde está para entrar na fila."}
            </p>
          </div>
        </div>

        <span
          className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-[10px] font-black uppercase tracking-wider ${
            shift
              ? "border-emerald-400 bg-white text-emerald-800"
              : "border-amber-400 bg-white text-amber-800"
          }`}
        >
          {shift ? "De plantão" : "Fora de plantão"}
        </span>
      </div>

      {loadError ? (
        <p className="mt-3 flex flex-wrap items-center gap-2 rounded-xl border border-coral-300 bg-coral-50 px-3 py-2 text-xs font-semibold text-coral-900">
          {loadError}
          <button
            type="button"
            onClick={reload}
            className="rounded-lg border border-coral-300 bg-white px-2 py-0.5 text-[11px] font-bold text-coral-900"
          >
            Tentar de novo
          </button>
        </p>
      ) : null}

      {feedback ? (
        <output
          className={`mt-3 flex items-start justify-between gap-3 rounded-xl border px-3 py-2 text-xs font-semibold ${FEEDBACK_STYLE[feedback.tone]}`}
        >
          <span className="flex items-start gap-1.5">
            {feedback.tone === "success" ? (
              <CheckCircle aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
            ) : (
              <AlertTriangle aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
            )}
            {feedback.message}
          </span>
          <button
            type="button"
            onClick={dismissFeedback}
            aria-label="Fechar aviso do plantão"
            className="shrink-0 rounded p-0.5 transition hover:bg-black/5"
          >
            <X aria-hidden="true" className="size-3.5" />
          </button>
        </output>
      ) : null}

      {shift ? (
        <OnShift
          target={
            targets.find(
              (target) => target.id === (shift.postId ?? shift.mobileBaseId),
            ) ?? null
          }
          isMobile={shift.mobileBaseId !== null}
          startedAt={shift.startedAt}
          lastPositionAt={shift.lastPositionAt}
          accuracyMeters={shift.lastPositionAccuracyMeters}
          busy={busy}
          confirmingClose={confirmingClose}
          onRefreshPosition={() => {
            void refreshPosition();
          }}
          onAskClose={() => setConfirmingClose(true)}
          onCancelClose={() => setConfirmingClose(false)}
          onClose={() => {
            void close().then((closed) => {
              if (closed) setConfirmingClose(false);
            });
          }}
        />
      ) : formOpen ? (
        <form onSubmit={handleSubmit} className="mt-4 grid gap-3">
          <fieldset>
            <legend className="text-[11px] font-bold uppercase tracking-wider text-ocean-600">
              Onde você vai trabalhar
            </legend>
            <div className="mt-1.5 flex gap-2">
              {(["POST", "MOBILE_BASE"] as const).map((value) => (
                <label
                  key={value}
                  className={`inline-flex cursor-pointer items-center gap-1.5 rounded-xl border px-3 py-1.5 text-xs font-bold transition ${
                    kind === value
                      ? "border-ocean-950 bg-ocean-950 text-white"
                      : "border-ocean-200 bg-white text-ocean-800 hover:bg-ocean-50"
                  }`}
                >
                  <input
                    type="radio"
                    name="shift-target-kind"
                    value={value}
                    checked={kind === value}
                    onChange={() => setKind(value)}
                    className="sr-only"
                  />
                  {value === "POST" ? (
                    <MapPin aria-hidden="true" className="size-3.5" />
                  ) : (
                    <Truck aria-hidden="true" className="size-3.5" />
                  )}
                  {KIND_LABEL[value]}
                </label>
              ))}
            </div>
          </fieldset>

          {options.length === 0 ? (
            <p className="rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-900">
              Nenhum {kind === "POST" ? "posto fixo" : "base móvel"} ativo
              cadastrado. Cadastre o ponto em{" "}
              <Link
                to="/operador/postos"
                className="underline decoration-2 underline-offset-2"
              >
                Postos e bases
              </Link>{" "}
              e volte para abrir o plantão.
            </p>
          ) : (
            <div className="grid gap-1">
              <label
                htmlFor="shift-target"
                className="text-xs font-semibold text-ocean-800"
              >
                {kind === "POST" ? "Posto fixo" : "Base móvel"}
                <select
                  id="shift-target"
                  value={selected?.id ?? ""}
                  onChange={(event) => setTargetId(event.target.value)}
                  className="mt-1 h-9 w-full rounded-lg border border-ocean-200 bg-white px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
                >
                  {options.map((target) => (
                    <option key={target.id} value={target.id}>
                      {targetLabel(target, distanceTo(target))}
                    </option>
                  ))}
                </select>
              </label>

              {selected ? (
                <div className="rounded-xl border border-ocean-100 bg-ocean-50/60 px-3 py-2 text-[11px] leading-snug text-ocean-700">
                  <p className="font-bold text-ocean-900">
                    {distanceTo(selected) === null
                      ? "Use “Usar minha localização” para ver a distância."
                      : `A ${formatDistance(distanceTo(selected))} de você`}
                    {selected.kind === "POST" && selected.beachName
                      ? ` · ${selected.beachName}`
                      : ""}
                  </p>
                  <p className="mt-0.5">
                    {selected.positionUpdatedAt
                      ? `Posição registrada em ${formatDateTime(selected.positionUpdatedAt)}`
                      : "Posição de cadastro sem data"}
                    {selected.positionAccuracyM === null
                      ? ""
                      : ` · ${formatAccuracy(selected.positionAccuracyM)}`}
                    {selected.coverageRadiusMeters
                      ? ` · cobertura de ${formatDistance(selected.coverageRadiusMeters)}`
                      : ""}
                  </p>
                  <p className="mt-0.5 text-ocean-500">
                    Coordenada {selected.latitude.toFixed(5)},{" "}
                    {selected.longitude.toFixed(5)}
                  </p>
                  {selected.kind === "MOBILE_BASE" ? (
                    <p className="mt-1 text-ocean-600">
                      A base móvel é a estrutura cadastrada neste ponto; a
                      posição muda quando você a reposiciona no console.
                    </p>
                  ) : null}
                </div>
              ) : null}
            </div>
          )}

          <div className="rounded-xl border border-ocean-200 bg-white p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-[11px] font-bold uppercase tracking-wider text-ocean-600">
                Sua localização agora
              </p>
              <button
                type="button"
                onClick={() => {
                  void capturePosition();
                }}
                disabled={locating}
                className="inline-flex min-h-8 items-center gap-1.5 rounded-lg border border-ocean-200 bg-ocean-50 px-2.5 py-1 text-[11px] font-bold text-ocean-800 transition hover:bg-ocean-100 disabled:cursor-progress disabled:opacity-70"
              >
                <Crosshair aria-hidden="true" className="size-3.5" />
                {locating ? "Obtendo…" : "Usar minha localização"}
              </button>
            </div>

            <div className="mt-2 grid gap-2 sm:grid-cols-2">
              <label className="block text-xs font-semibold text-ocean-800">
                Latitude
                <input
                  type="number"
                  step="any"
                  min={-90}
                  max={90}
                  value={latitude}
                  onChange={(event) => {
                    setLatitude(event.target.value);
                    setAccuracy(null);
                  }}
                  placeholder="-9.6658"
                  className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
                />
              </label>
              <label className="block text-xs font-semibold text-ocean-800">
                Longitude
                <input
                  type="number"
                  step="any"
                  min={-180}
                  max={180}
                  value={longitude}
                  onChange={(event) => {
                    setLongitude(event.target.value);
                    setAccuracy(null);
                  }}
                  placeholder="-35.7089"
                  className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
                />
              </label>
            </div>

            {geoMessage ? (
              <p className="mt-2 text-[11px] font-semibold text-ocean-700">
                {geoMessage}
              </p>
            ) : null}
            <p className="mt-1 text-[11px] leading-relaxed text-ocean-500">
              A posição é usada só para elegibilidade do SOS e para o painel da
              coordenação. Não é rastreamento contínuo e não fica guardada no
              navegador.
            </p>
          </div>

          {formError ? (
            <p className="rounded-xl border border-coral-300 bg-coral-50 px-3 py-2 text-xs font-semibold text-coral-900">
              {formError}
            </p>
          ) : null}

          <div className="flex flex-wrap gap-2">
            <button
              type="submit"
              disabled={busy !== null || locating || !selected}
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl bg-ocean-950 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-ocean-900 disabled:cursor-progress disabled:opacity-70"
            >
              <LogIn aria-hidden="true" className="size-3.5" />
              {locating
                ? "Obtendo localização…"
                : busy === "open"
                  ? "Abrindo…"
                  : "Entrar de plantão"}
            </button>
            <button
              type="button"
              onClick={resetForm}
              disabled={busy !== null}
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-200 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50 disabled:opacity-70"
            >
              Cancelar
            </button>
          </div>
        </form>
      ) : (
        <button
          type="button"
          onClick={startForm}
          className="mt-4 inline-flex min-h-10 items-center gap-2 rounded-xl bg-amber-600 px-4 py-2 text-sm font-bold text-white shadow-sm transition hover:bg-amber-700"
        >
          <LogIn aria-hidden="true" className="size-4" />
          Entrar de plantão
        </button>
      )}
    </section>
  );
}

interface OnShiftProps {
  readonly target: ShiftTarget | null;
  readonly isMobile: boolean;
  readonly startedAt: string;
  readonly lastPositionAt: string | null;
  readonly accuracyMeters: number | null;
  readonly busy: "open" | "move" | "close" | null;
  readonly confirmingClose: boolean;
  readonly onRefreshPosition: () => void;
  readonly onAskClose: () => void;
  readonly onCancelClose: () => void;
  readonly onClose: () => void;
}

function OnShift({
  target,
  isMobile,
  startedAt,
  lastPositionAt,
  accuracyMeters,
  busy,
  confirmingClose,
  onRefreshPosition,
  onAskClose,
  onCancelClose,
  onClose,
}: OnShiftProps) {
  return (
    <div className="mt-3">
      <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs font-semibold text-ocean-800">
        <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-400 bg-white px-2.5 py-0.5 font-bold text-emerald-900">
          {isMobile ? (
            <Truck aria-hidden="true" className="size-3.5" />
          ) : (
            <MapPin aria-hidden="true" className="size-3.5" />
          )}
          {target?.name ?? "Posto/base do plantão"}
        </span>
        {target?.kind === "POST" && target.beachName ? (
          <span className="text-ocean-600">{target.beachName}</span>
        ) : null}
        {target?.coverageRadiusMeters ? (
          <span className="text-ocean-600">
            cobertura de {formatDistance(target.coverageRadiusMeters)}
          </span>
        ) : null}
      </p>

      <p className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] font-semibold text-ocean-600">
        <span className="inline-flex items-center gap-1">
          <Clock aria-hidden="true" className="size-3.5" />
          Desde {formatDateTime(startedAt)}
        </span>
        <span className="inline-flex items-center gap-1">
          <Crosshair aria-hidden="true" className="size-3.5" />
          {lastPositionAt
            ? `Última posição ${formatDateTime(lastPositionAt)}${
                accuracyMeters === null
                  ? ""
                  : ` · ${formatAccuracy(accuracyMeters)}`
              }`
            : "Posição do plantão não informada"}
        </span>
      </p>

      {!lastPositionAt ? (
        <p className="mt-2 flex items-start gap-1.5 rounded-lg border border-amber-300 bg-amber-50 px-2.5 py-1.5 text-[11px] font-semibold text-amber-900">
          <AlertTriangle aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
          Sem posição o despacho mede a distância pela estrutura. Toque em
          “Atualizar minha localização” para usar onde você está agora.
        </p>
      ) : null}

      <div className="mt-3 flex flex-wrap gap-2">
        <button
          type="button"
          onClick={onRefreshPosition}
          disabled={busy !== null}
          className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-900/15 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50 disabled:cursor-progress disabled:opacity-70"
        >
          <Crosshair aria-hidden="true" className="size-3.5" />
          {busy === "move"
            ? "Atualizando…"
            : "Atualizar minha localização"}
        </button>

        {confirmingClose ? (
          <>
            <button
              type="button"
              onClick={onClose}
              disabled={busy !== null}
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl bg-coral-600 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-coral-700 disabled:cursor-progress disabled:opacity-70"
            >
              <LogOut aria-hidden="true" className="size-3.5" />
              {busy === "close" ? "Encerrando…" : "Confirmar encerramento"}
            </button>
            <button
              type="button"
              onClick={onCancelClose}
              disabled={busy !== null}
              className="inline-flex min-h-9 items-center rounded-xl border border-ocean-200 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50 disabled:opacity-70"
            >
              Cancelar
            </button>
          </>
        ) : (
          <button
            type="button"
            onClick={onAskClose}
            disabled={busy !== null}
            className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-coral-300 bg-white px-3 py-1.5 text-xs font-bold text-coral-800 transition hover:bg-coral-50 disabled:opacity-70"
          >
            <LogOut aria-hidden="true" className="size-3.5" />
            Encerrar plantão
          </button>
        )}
      </div>
    </div>
  );
}

function targetLabel(
  target: ShiftTarget,
  distance: number | null,
): string {
  const parts = [target.name];
  if (target.kind === "POST" && target.beachName) {
    parts.push(target.beachName);
  }
  if (distance !== null) {
    parts.push(formatDistance(distance));
  }
  return parts.join(" · ");
}
