import { useState } from "react";
import {
  Crosshair,
  ImagePlus,
  MapPin,
  Radio,
  Shield,
  Trash2,
} from "lucide-react";
import { safeExternalUrl } from "@/shared/lib/sanitize";
import { lifeguardStations as initialStations } from "@/features/stations/api/demo-stations";
import type {
  LifeguardStation,
  StationType,
} from "@/features/stations/types";

type PositionSource = "MANUAL" | "DEVICE_GPS";

const POSITION_SOURCE_LABEL: Record<PositionSource, string> = {
  MANUAL: "Informada manualmente",
  DEVICE_GPS: "GPS do dispositivo",
};

/**
 * Cadastro de postos fixos e bases móveis usado pela TI (`/admin`) e pelo
 * guarda-vidas em campo (`/operador`). O `position_source` (ADR-012) registra
 * como a coordenada foi obtida; a posição do dispositivo é usada só neste
 * momento e não fica sendo rastreada. Estado local até o CRUD de
 * `operations`/`beaches` entrar.
 */
export function StationRegistry() {
  const [stations, setStations] =
    useState<readonly LifeguardStation[]>(initialStations);
  const [name, setName] = useState("");
  const [type, setType] = useState<StationType>("FIXED");
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");
  const [operatingHours, setOperatingHours] = useState("08:00 às 17:00");
  const [resources, setResources] = useState("");
  const [photoUrl, setPhotoUrl] = useState("");
  const [positionSource, setPositionSource] =
    useState<PositionSource>("MANUAL");
  const [accuracy, setAccuracy] = useState<number | null>(null);
  const [geoMessage, setGeoMessage] = useState<string | null>(null);

  const useMyLocation = () => {
    setGeoMessage(null);
    if (!("geolocation" in navigator)) {
      setGeoMessage("Este navegador não expõe a localização.");
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLatitude(position.coords.latitude.toFixed(6));
        setLongitude(position.coords.longitude.toFixed(6));
        setAccuracy(position.coords.accuracy);
        setPositionSource("DEVICE_GPS");
        setGeoMessage("Coordenada preenchida com a localização atual.");
      },
      () => {
        setGeoMessage(
          "Não foi possível obter a localização. Informe as coordenadas manualmente.",
        );
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 },
    );
  };

  const handleCreate = (event: React.FormEvent) => {
    event.preventDefault();
    if (!name.trim() || !latitude || !longitude) return;

    const photo = photoUrl.trim() ? safeExternalUrl(photoUrl.trim()) : null;

    const newStation: LifeguardStation = {
      id: `station-${Date.now()}`,
      name: name.trim(),
      type,
      status: "ACTIVE",
      municipality: "Litoral",
      state: "BR",
      latitude: Number.parseFloat(latitude),
      longitude: Number.parseFloat(longitude),
      operatingHours: operatingHours.trim() || undefined,
      resources: resources
        .split(",")
        .map((resource) => resource.trim())
        .filter(Boolean),
      photoUrls: photo ? [photo] : undefined,
      updatedAt: new Date().toISOString(),
    };

    setStations((previous) => [newStation, ...previous]);
    setName("");
    setLatitude("");
    setLongitude("");
    setResources("");
    setPhotoUrl("");
    setPositionSource("MANUAL");
    setAccuracy(null);
    setGeoMessage(null);
  };

  return (
    <div>
      <form
        onSubmit={handleCreate}
        className="mb-4 grid gap-3 rounded-2xl border border-ocean-900/10 bg-white p-4 shadow-sm"
      >
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-bold text-ocean-950">
            Novo ponto operacional
          </h2>
          <button
            type="button"
            onClick={useMyLocation}
            className="inline-flex min-h-8 items-center gap-1.5 rounded-lg border border-ocean-200 bg-ocean-50 px-2.5 py-1 text-[11px] font-bold text-ocean-800 transition hover:bg-ocean-100"
          >
            <Crosshair aria-hidden="true" className="size-3.5" />
            Usar minha localização
          </button>
        </div>

        {geoMessage ? (
          <output className="block rounded-xl border border-ocean-200 bg-ocean-50 px-3 py-2 text-xs font-semibold text-ocean-800">
            {geoMessage}
            {positionSource === "DEVICE_GPS" && accuracy !== null
              ? ` Precisão ±${Math.round(accuracy)} m.`
              : ""}
          </output>
        ) : null}

        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-xs font-semibold text-ocean-800">
            Nome / identificação
            <input
              type="text"
              required
              maxLength={120}
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Ex.: Base móvel Barra Sul"
              className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            />
          </label>

          <label className="block text-xs font-semibold text-ocean-800">
            Tipo de estrutura
            <select
              value={type}
              onChange={(event) =>
                setType(event.target.value as StationType)
              }
              className="mt-1 h-9 w-full rounded-lg border border-ocean-200 bg-white px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            >
              <option value="FIXED">Posto fixo (torre/container)</option>
              <option value="MOBILE">Base móvel (quadriciclo/viatura)</option>
            </select>
          </label>

          <label className="block text-xs font-semibold text-ocean-800">
            Latitude
            <input
              type="number"
              step="any"
              required
              min={-90}
              max={90}
              value={latitude}
              onChange={(event) => {
                setLatitude(event.target.value);
                setPositionSource("MANUAL");
                setAccuracy(null);
              }}
              placeholder="-10.985"
              className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            />
          </label>

          <label className="block text-xs font-semibold text-ocean-800">
            Longitude
            <input
              type="number"
              step="any"
              required
              min={-180}
              max={180}
              value={longitude}
              onChange={(event) => {
                setLongitude(event.target.value);
                setPositionSource("MANUAL");
                setAccuracy(null);
              }}
              placeholder="-37.045"
              className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            />
          </label>

          <label className="block text-xs font-semibold text-ocean-800">
            Horário de operação
            <input
              type="text"
              maxLength={60}
              value={operatingHours}
              onChange={(event) => setOperatingHours(event.target.value)}
              placeholder="08:00 às 17:00"
              className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            />
          </label>

          <label className="block text-xs font-semibold text-ocean-800">
            Recursos (separados por vírgula)
            <input
              type="text"
              maxLength={200}
              value={resources}
              onChange={(event) => setResources(event.target.value)}
              placeholder="DEA, prancha, quadriciclo"
              className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            />
          </label>

          <label className="block text-xs font-semibold text-ocean-800 sm:col-span-2">
            Foto do posto (URL pública)
            <input
              type="url"
              maxLength={500}
              value={photoUrl}
              onChange={(event) => setPhotoUrl(event.target.value)}
              placeholder="https://..."
              className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            />
          </label>
        </div>

        <p className="flex items-center gap-1.5 text-[11px] text-ocean-500">
          <MapPin aria-hidden="true" className="size-3.5" />
          Origem da posição: {POSITION_SOURCE_LABEL[positionSource]}
          {positionSource === "DEVICE_GPS" && accuracy !== null
            ? ` · precisão ±${Math.round(accuracy)} m`
            : ""}
        </p>

        <button
          type="submit"
          className="inline-flex h-9 w-fit items-center gap-1.5 rounded-lg bg-ocean-950 px-4 text-xs font-bold text-white transition hover:bg-ocean-900"
        >
          <ImagePlus aria-hidden="true" className="size-4" />
          Cadastrar ponto
        </button>
      </form>

      <h2 className="mb-2 text-xs font-bold uppercase tracking-wider text-ocean-500">
        Cadastrados ({stations.length})
      </h2>
      <ul className="grid gap-2">
        {stations.map((station) => (
          <li
            key={station.id}
            className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-ocean-900/10 bg-white p-3 text-xs shadow-sm"
          >
            <div className="flex items-center gap-2.5">
              <span
                className={`grid size-8 place-items-center rounded-lg ${
                  station.type === "MOBILE"
                    ? "bg-sand-500 text-ocean-950"
                    : "bg-ocean-600 text-white"
                }`}
              >
                {station.type === "MOBILE" ? (
                  <Radio aria-hidden="true" className="size-4" />
                ) : (
                  <Shield aria-hidden="true" className="size-4" />
                )}
              </span>
              <div>
                <p className="text-sm font-bold text-ocean-950">
                  {station.name}
                </p>
                <p className="text-[11px] text-ocean-600">
                  Lat {station.latitude.toFixed(4)} · Lon{" "}
                  {station.longitude.toFixed(4)}
                  {station.operatingHours
                    ? ` · ${station.operatingHours}`
                    : ""}
                </p>
                {station.photoUrls?.length ? (
                  <p className="text-[10px] text-ocean-500">
                    {station.photoUrls.length} foto(s) para identificação
                  </p>
                ) : null}
              </div>
            </div>

            <button
              type="button"
              onClick={() =>
                setStations((previous) =>
                  previous.filter((item) => item.id !== station.id),
                )
              }
              className="grid size-8 place-items-center rounded-lg text-ocean-400 transition hover:bg-coral-50 hover:text-coral-700"
              aria-label={`Excluir posto ${station.name}`}
            >
              <Trash2 aria-hidden="true" className="size-4" />
            </button>
          </li>
        ))}
      </ul>

      <p className="mt-3 text-[11px] leading-relaxed text-ocean-500">
        A localização do dispositivo é usada só no cadastro e não fica sendo
        rastreada. Postos e bases aparecem no mapa público com a foto da ficha.
      </p>
    </div>
  );
}
