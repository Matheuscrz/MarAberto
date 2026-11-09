import { Link, useParams } from "react-router-dom";
import {
  ArrowLeft,
  Clock,
  MapPin,
  Phone,
  ShieldAlert,
  Siren,
  Waves,
} from "lucide-react";
import { Card, PageHeader } from "../components/Card";
import { FlagBadge, RiskBadge } from "../components/Badges";
import { DEMO_DATA_NOTICE, findBeach } from "../data/demo-data";
import { formatDateTime, formatOccupancy } from "../lib/format";
import { sanitizeText } from "../lib/sanitize";
import { NotFoundPage } from "./NotFoundPage";

/**
 * Detalhe da praia dentro do painel flutuante do mapa: mesma linguagem
 * compacta das demais telas (cards de `rounded-2xl`, textos curtos).
 */
export function BeachDetailPage() {
  const { beachId } = useParams<{ beachId: string }>();
  const beach = findBeach(beachId);

  if (!beach) {
    return <NotFoundPage />;
  }

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
        title={beach.name}
        subtitle={`${beach.municipality} — ${beach.state}`}
        actions={<RiskBadge level={beach.riskLevel} />}
      />

      <Card>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <FlagBadge flag={beach.flag} />
          <span className="flex items-center gap-1 text-[11px] text-ocean-600">
            <Clock aria-hidden="true" className="size-3.5" />
            Atualizado em {formatDateTime(beach.updatedAt)}
          </span>
        </div>

        <p className="mt-2 text-xs leading-relaxed text-ocean-700">
          {sanitizeText(beach.description, 600)}
        </p>

        <dl className="mt-3 grid grid-cols-3 gap-2 rounded-xl border border-ocean-200 bg-ocean-50 p-2.5 text-center">
          <div>
            <dt className="text-[10px] font-bold uppercase text-ocean-500">
              Ocupação
            </dt>
            <dd className="text-sm font-black text-ocean-950">
              {formatOccupancy(beach.occupancyPercent)}
            </dd>
          </div>
          <div>
            <dt className="text-[10px] font-bold uppercase text-ocean-500">
              Riscos
            </dt>
            <dd className="text-sm font-black text-ocean-950">
              {beach.risks.length}
            </dd>
          </div>
          <div>
            <dt className="text-[10px] font-bold uppercase text-ocean-500">
              Coordenadas
            </dt>
            <dd className="font-mono text-[11px] font-bold text-ocean-950">
              {beach.latitude.toFixed(3)}, {beach.longitude.toFixed(3)}
            </dd>
          </div>
        </dl>
      </Card>

      <section aria-labelledby="riscos" className="grid gap-2.5">
        <h2
          id="riscos"
          className="flex items-center gap-1.5 text-sm font-black text-ocean-950"
        >
          <ShieldAlert aria-hidden="true" className="size-4 text-coral-600" />
          Zonas de risco
        </h2>

        {beach.risks.length === 0 ? (
          <Card className="border-dashed bg-ocean-50/40 text-center">
            <p className="text-xs font-bold text-ocean-950">
              Nenhuma zona de risco mapeada
            </p>
            <p className="mt-1 text-[11px] text-ocean-700">
              Siga a bandeira do posto e a orientação do guarda-vidas.
            </p>
          </Card>
        ) : (
          <ul className="grid gap-2.5">
            {beach.risks.map((risk) => (
              <li key={risk.id}>
                <Card>
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <h3 className="text-sm font-black text-ocean-950">
                      {sanitizeText(risk.label, 120)}
                    </h3>
                    <RiskBadge level={risk.severity} />
                  </div>
                  <p className="mt-1.5 text-xs text-ocean-700">
                    {sanitizeText(risk.description, 500)}
                  </p>
                  <h4 className="mt-2.5 text-[10px] font-bold uppercase tracking-wide text-ocean-500">
                    O que fazer
                  </h4>
                  <ul className="mt-1 grid gap-1 text-xs text-ocean-800">
                    {risk.guidance.map((item) => (
                      <li key={item} className="flex gap-1.5">
                        <span aria-hidden="true" className="text-ocean-500">
                          •
                        </span>
                        {sanitizeText(item, 240)}
                      </li>
                    ))}
                  </ul>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </section>

      <Card>
        <h2 className="flex items-center gap-1.5 text-sm font-black text-ocean-950">
          <Waves aria-hidden="true" className="size-4 text-ocean-600" />
          Estrutura
        </h2>
        <ul className="mt-1.5 grid gap-1 text-xs text-ocean-800">
          {beach.facilities.map((facility) => (
            <li key={facility} className="flex items-center gap-1.5">
              <MapPin aria-hidden="true" className="size-3.5 text-ocean-500" />
              {sanitizeText(facility, 80)}
            </li>
          ))}
        </ul>
      </Card>

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
