import { Crosshair, MapPin, Navigation, Radio, Shield } from "lucide-react";
import { Card } from "@/shared/components/Card";
import { PhotoGallery } from "@/shared/components/PhotoGallery";
import {
  formatAccuracy,
  formatDateTime,
  formatDistance,
} from "@/shared/lib/format";
import { distanceMetersBetween, type Coordinates } from "@/shared/lib/geo";
import { sanitizeText } from "@/shared/lib/sanitize";
import {
  POSITION_SOURCE_LABEL,
  STATION_KIND_LABEL,
  type AdminStation,
} from "@/features/stations/types";

interface FieldStationListProps {
  readonly stations: readonly AdminStation[];
  /** Chaves (`KIND:id`) do que está no mapa público agora. */
  readonly publicKeys: ReadonlySet<string>;
  /** A leitura pública concluiu; sem ela não dá para afirmar o que está no mapa. */
  readonly publicReady: boolean;
  readonly position: Coordinates | null;
  readonly locating: boolean;
  readonly locationError: string | null;
  readonly onLocate: () => void;
}

/**
 * Estruturas cadastradas no console de campo.
 *
 * <p>A lista pública esconde a base móvel sem plantão vivo; esta mantém o
 * cadastro à vista para o guarda-vidas saber onde o posto/base fica e por que
 * ele não está no mapa agora. A distância é calculada de uma leitura pontual do
 * aparelho — nada de `watchPosition` nem de coordenada persistida.
 */
export function FieldStationList({
  stations,
  publicKeys,
  publicReady,
  position,
  locating,
  locationError,
  onLocate,
}: FieldStationListProps) {
  const ordered = [...stations].sort((first, second) => {
    if (first.active !== second.active) {
      return first.active ? -1 : 1;
    }
    return `${first.kind}:${first.name}`.localeCompare(
      `${second.kind}:${second.name}`,
      "pt-BR",
    );
  });

  return (
    <div className="grid gap-3">
      <Card>
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <h2 className="flex items-center gap-1.5 text-sm font-black text-ocean-950">
              <MapPin aria-hidden="true" className="size-4 text-ocean-600" />
              Estruturas cadastradas
            </h2>
            <p className="mt-1 text-[11px] leading-snug text-ocean-600">
              Inclui a base móvel sem plantão vivo, que sai do mapa público mas
              continua cadastrada. A distância é calculada da sua posição agora.
            </p>
          </div>
          <button
            type="button"
            onClick={onLocate}
            disabled={locating}
            className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 transition hover:bg-ocean-50 disabled:cursor-progress disabled:opacity-70"
          >
            <Crosshair aria-hidden="true" className="size-3.5" />
            {locating
              ? "Obtendo…"
              : position
                ? "Atualizar minha localização"
                : "Usar minha localização"}
          </button>
        </div>

        {locationError ? (
          <p className="mt-2 rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-[11px] font-semibold text-amber-900">
            {locationError}
          </p>
        ) : (
          <p className="mt-2 text-[11px] text-ocean-500">
            {position
              ? "Distância calculada da posição lida agora; nada é guardado no aparelho."
              : "Sem a sua posição a lista mostra só a última posição registrada da estrutura."}
          </p>
        )}
      </Card>

      {ordered.length === 0 ? (
        <Card className="border-dashed bg-ocean-50/40 text-center">
          <p className="text-sm font-bold text-ocean-950">
            Nenhum posto ou base cadastrado
          </p>
          <p className="mt-1 text-xs text-ocean-700">
            Cadastre a estrutura na posição em que você está.
          </p>
        </Card>
      ) : (
        <ul className="grid gap-3">
          {ordered.map((station) => {
            const key = `${station.kind}:${station.id}`;
            const onMap = publicReady && publicKeys.has(key);
            const distance = position
              ? distanceMetersBetween(position, station)
              : null;
            const badge = !station.active
              ? {
                  label: "Fora do mapa",
                  className: "border-amber-400 bg-white text-amber-800",
                }
              : !publicReady
                ? {
                    label: "Ativa",
                    className: "border-ocean-400 bg-white text-ocean-800",
                  }
                : onMap
                  ? {
                      label: "No mapa agora",
                      className: "border-emerald-400 bg-white text-emerald-800",
                    }
                  : station.kind === "MOBILE"
                    ? {
                        label: "Aguardando plantão",
                        className: "border-amber-400 bg-white text-amber-800",
                      }
                    : {
                        label: "Fora do mapa",
                        className: "border-amber-400 bg-white text-amber-800",
                      };

            return (
              <li key={key}>
                <Card
                  className={
                    station.active
                      ? undefined
                      : "border-amber-200 bg-amber-50/40"
                  }
                >
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <span
                        className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[10px] font-black uppercase tracking-wider ${
                          station.kind === "MOBILE"
                            ? "border-amber-200 bg-amber-100 text-amber-900"
                            : "border-ocean-200 bg-ocean-100 text-ocean-900"
                        }`}
                      >
                        {station.kind === "MOBILE" ? (
                          <Radio aria-hidden="true" className="size-3" />
                        ) : (
                          <Shield aria-hidden="true" className="size-3" />
                        )}
                        {STATION_KIND_LABEL[station.kind]}
                      </span>
                      <h3 className="mt-1 text-sm font-black text-ocean-950">
                        {sanitizeText(station.name, 120)}
                      </h3>
                      {station.beachName ? (
                        <p className="text-[11px] text-ocean-600">
                          {sanitizeText(station.beachName, 120)}
                        </p>
                      ) : null}
                    </div>
                    <span
                      className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-[10px] font-black uppercase tracking-wider ${badge.className}`}
                    >
                      {badge.label}
                    </span>
                  </div>

                  <dl className="mt-2 grid gap-1 text-[11px] leading-snug text-ocean-700 sm:grid-cols-2">
                    <div>
                      <dt className="font-bold text-ocean-900">Distância</dt>
                      <dd>
                        {distance === null
                          ? "Ative a localização para calcular"
                          : `A ${formatDistance(distance)} de você`}
                      </dd>
                    </div>
                    <div>
                      <dt className="font-bold text-ocean-900">Posição</dt>
                      <dd>
                        {station.positionUpdatedAt
                          ? formatDateTime(station.positionUpdatedAt)
                          : "cadastro sem data"}
                        {station.positionAccuracyM === null
                          ? ""
                          : ` · ${formatAccuracy(station.positionAccuracyM)}`}
                        {station.positionSource
                          ? ` · ${POSITION_SOURCE_LABEL[station.positionSource]}`
                          : ""}
                      </dd>
                    </div>
                    <div>
                      <dt className="font-bold text-ocean-900">Coordenada</dt>
                      <dd>
                        {station.latitude.toFixed(5)},{" "}
                        {station.longitude.toFixed(5)}
                      </dd>
                    </div>
                    <div>
                      <dt className="font-bold text-ocean-900">Como chegar</dt>
                      <dd>
                        <a
                          href={`https://www.google.com/maps/search/?api=1&query=${station.latitude},${station.longitude}`}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex items-center gap-1 font-bold text-ocean-800 underline decoration-2 underline-offset-2"
                        >
                          <Navigation aria-hidden="true" className="size-3" />
                          Abrir no mapa
                        </a>
                      </dd>
                    </div>
                  </dl>

                  {station.active === false ? (
                    <p className="mt-2 rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-[11px] font-semibold text-amber-900">
                      Fora do mapa.
                      {station.deactivatedAt
                        ? ` Desativada em ${formatDateTime(station.deactivatedAt)}.`
                        : ""}
                      {station.statusNote
                        ? ` Motivo: ${sanitizeText(station.statusNote, 200)}`
                        : ""}
                    </p>
                  ) : publicReady && station.kind === "MOBILE" && !onMap ? (
                    <p className="mt-2 rounded-xl border border-ocean-200 bg-ocean-50 px-3 py-2 text-[11px] font-semibold text-ocean-700">
                      A base móvel só entra no mapa com plantão vivo. Abra o
                      plantão nela para publicar o ponto.
                    </p>
                  ) : null}

                  <PhotoGallery
                    urls={station.photoUrls}
                    label={sanitizeText(station.name, 120)}
                    compact
                    openLabel="Ampliar foto do posto"
                  />
                </Card>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
