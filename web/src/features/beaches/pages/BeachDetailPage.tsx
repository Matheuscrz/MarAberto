import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  ArrowLeft,
  MapPin,
  Phone,
  RefreshCw,
  Siren,
  Waves,
} from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import { ApiError } from "@/shared/lib/api-client";
import {
  getDevicePosition,
} from "@/shared/lib/geolocation";
import { DEMO_DATA_NOTICE } from "@/shared/lib/notices";
import { beachErrorMessage, fetchBeachDetail } from "@/features/beaches/api/beaches";
import {
  fetchOccupancy,
  recordOccupancySample,
  type OccupancyEstimate,
  type OccupancyLevel,
} from "@/features/beaches/api/occupancy";
import {
  BEACH_KIND_LABEL,
  sectorLabel,
  type BeachDetail,
} from "@/features/beaches";
import { sanitizeText } from "@/shared/lib/sanitize";
import { NotFoundCard } from "@/shared/components/NotFoundCard";

/**
 * Ficha da área de banho importada do KML: tipo, município e setores na ordem
 * do mapeamento. Bandeira, risco e ocupação são camadas próprias e aparecem
 * aqui quando existirem — o polígono sozinho não inventa esses dados.
 */
export function BeachDetailPage() {
  const { beachId } = useParams<{ beachId: string }>();
  const [detail, setDetail] = useState<BeachDetail | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [attempt, setAttempt] = useState(0);
  const [occupancy, setOccupancy] = useState<{
    beachId: string;
    estimate: OccupancyEstimate | null;
  } | null>(null);

  useEffect(() => {
    if (!beachId) return;
    let cancelled = false;

    void (async () => {
      try {
        const loaded = await fetchBeachDetail(beachId);
        if (cancelled) return;
        setDetail(loaded);

        // Ocupação: a posição só serve para registrar a presença no setor e é
        // descartada pelo servidor. Sem permissão, a faixa atual ainda é lida.
        const position = await getDevicePosition({
          enableHighAccuracy: false,
          timeout: 6_000,
          maximumAge: 300_000,
        }).catch(() => null);
        if (position) {
          await recordOccupancySample(position).catch(() => undefined);
        }

        const estimate = await fetchOccupancy(beachId).catch(() => null);
        if (!cancelled) setOccupancy({ beachId, estimate });
      } catch (caught) {
        if (!cancelled) setError(caught);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [beachId, attempt]);

  if (!beachId) {
    return <NotFoundCard />;
  }

  if (error instanceof ApiError && error.status === 404) {
    return <NotFoundCard />;
  }

  if (error) {
    return (
      <div className="grid gap-3">
        <Link
          to="/praias"
          className="inline-flex w-fit items-center gap-1 text-xs font-bold text-ocean-700 transition hover:text-ocean-900"
        >
          <ArrowLeft aria-hidden="true" className="size-3.5" />
          Voltar para praias
        </Link>
        <Card className="border-dashed bg-ocean-50/40 text-center">
          <p className="text-sm font-bold text-ocean-950">
            Não foi possível abrir a praia
          </p>
          <p className="mt-1 text-xs text-ocean-700">{beachErrorMessage(error)}</p>
          <button
            type="button"
            onClick={() => {
              setError(null);
              setAttempt((value) => value + 1);
            }}
            className="mt-2 inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 hover:bg-ocean-50"
          >
            <RefreshCw aria-hidden="true" className="size-3.5" />
            Tentar de novo
          </button>
        </Card>
      </div>
    );
  }

  if (!detail) {
    return (
      <output className="block py-8 text-center text-sm font-semibold text-ocean-600">
        Carregando a praia
      </output>
    );
  }

  const currentOccupancy =
    occupancy?.beachId === detail.id ? occupancy.estimate : null;

  return (
    <div className="grid gap-3">
      <Link
        to="/praias"
        className="inline-flex w-fit items-center gap-1 text-xs font-bold text-ocean-700 transition hover:text-ocean-900"
      >
        <ArrowLeft aria-hidden="true" className="size-3.5" />
        Voltar para praias
      </Link>

      <PageHeader
        title={sanitizeText(detail.name, 120)}
        subtitle={[detail.municipality, detail.state]
          .filter(Boolean)
          .join(" — ")}
        actions={
          <span className="rounded-full bg-ocean-50 px-2.5 py-1 text-[11px] font-bold text-ocean-700">
            {BEACH_KIND_LABEL[detail.kind]}
          </span>
        }
      />

      <Card>
        <div className="flex flex-wrap items-center justify-between gap-2 text-[11px] text-ocean-600">
          <span className="flex items-center gap-1">
            <MapPin aria-hidden="true" className="size-3.5" />
            {detail.sectorCount === 1
              ? "1 setor mapeado"
              : `${detail.sectorCount} setores mapeados`}
          </span>
          <span className="font-mono">
            {detail.latitude.toFixed(4)}, {detail.longitude.toFixed(4)}
          </span>
        </div>
        <p className="mt-2 text-xs leading-relaxed text-ocean-700">
          Área de banho delimitada pelo mapeamento do CBM. Cada setor é uma
          parte operacional da mesma praia, rio ou barragem.
        </p>

        <dl className="mt-3 grid grid-cols-2 gap-2 rounded-xl border border-ocean-200 bg-ocean-50 p-2.5 text-center">
          <div>
            <dt className="text-[10px] font-bold uppercase text-ocean-500">
              Ocupação estimada
            </dt>
            <dd className="text-sm font-black text-ocean-950">
              {occupancyLabel(currentOccupancy)}
            </dd>
          </div>
          <div>
            <dt className="text-[10px] font-bold uppercase text-ocean-500">
              Janela
            </dt>
            <dd className="text-sm font-black text-ocean-950">
              {currentOccupancy ? `${currentOccupancy.windowMinutes} min` : "—"}
            </dd>
          </div>
        </dl>
        <p className="mt-1.5 text-[11px] leading-snug text-ocean-600">
          Estimativa anônima de aparelhos com o app aberto na área. Abaixo da
          amostra mínima mostramos "dados insuficientes" para proteger quem está
          na praia.
        </p>
      </Card>

      <section aria-labelledby="setores" className="grid gap-2.5">
        <h2
          id="setores"
          className="flex items-center gap-1.5 text-sm font-black text-ocean-950"
        >
          <Waves aria-hidden="true" className="size-4 text-ocean-600" />
          Setores
        </h2>

        {detail.sectors.length === 0 ? (
          <Card className="border-dashed bg-ocean-50/40 text-center">
            <p className="text-xs font-bold text-ocean-950">
              Nenhum setor ativo
            </p>
            <p className="mt-1 text-[11px] text-ocean-700">
              A área inteira vale como um único ponto de referência.
            </p>
          </Card>
        ) : (
          <ul className="grid gap-2.5">
            {detail.sectors.map((sector) => (
              <li key={sector.id}>
                <Card>
                  <div className="flex items-center justify-between gap-2">
                    <h3 className="text-sm font-black text-ocean-950">
                      {sanitizeText(sectorLabel(sector), 120)}
                    </h3>
                    <span className="font-mono text-[10px] text-ocean-500">
                      {sector.latitude.toFixed(4)}, {sector.longitude.toFixed(4)}
                    </span>
                  </div>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </section>

      <Card className="border-coral-200 bg-coral-50">
        <h2 className="text-sm font-black text-coral-950">
          Emergência nesta praia
        </h2>
        <p className="mt-1 text-[11px] leading-snug text-coral-900">
          Ligue 193 e informe o nome da praia e o ponto de referência mais
          próximo. O SOS envia sua localização para a central, mas não substitui
          o 193.
        </p>
        <div className="mt-2.5 grid grid-cols-2 gap-2">
          <Link
            to="/sos"
            className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl bg-coral-600 px-3 text-xs font-bold text-white transition hover:bg-coral-500"
          >
            <Siren aria-hidden="true" className="size-4" />
            Abrir SOS
          </Link>
          <a
            href="tel:193"
            className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 px-3 text-xs font-bold text-white transition hover:bg-ocean-900"
          >
            <Phone aria-hidden="true" className="size-4" />
            Ligar 193
          </a>
        </div>
      </Card>

      <p className="text-[11px] text-ocean-600">{DEMO_DATA_NOTICE}</p>
    </div>
  );
}

const OCCUPANCY_LABEL: Record<OccupancyLevel, string> = {
  LOW: "Baixa",
  MODERATE: "Moderada",
  HIGH: "Alta",
  EXTREME: "Muito alta",
};

function occupancyLabel(estimate: OccupancyEstimate | null): string {
  if (!estimate) {
    return "—";
  }
  if (estimate.status === "INSUFFICIENT" || !estimate.level) {
    return "Dados insuficientes";
  }
  return OCCUPANCY_LABEL[estimate.level];
}
