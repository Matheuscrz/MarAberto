import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Clock, MapPin, ShieldAlert, Waves } from 'lucide-react';
import { Card, PageHeader } from '../components/Card';
import { DemoNotice } from '../components/DemoNotice';
import { FlagBadge, RiskBadge } from '../components/Badges';
import { EmergencyPhone, SosCta } from '../components/EmergencyActions';
import { findBeach } from '../data/demo-data';
import { formatDateTime, formatOccupancy } from '../lib/format';
import { sanitizeText } from '../lib/sanitize';
import { NotFoundPage } from './NotFoundPage';

export function BeachDetailPage() {
  const { beachId } = useParams<{ beachId: string }>();
  const beach = findBeach(beachId);

  if (!beach) {
    return <NotFoundPage />;
  }

  return (
    <div>
      <Link
        to="/praias"
        className="mb-4 inline-flex min-h-11 items-center gap-1 font-semibold text-ocean-700 hover:text-ocean-900"
      >
        <ArrowLeft aria-hidden="true" className="size-4" />
        Voltar para praias
      </Link>

      <PageHeader
        title={beach.name}
        subtitle={`${beach.municipality} — ${beach.state}`}
        actions={<RiskBadge level={beach.riskLevel} />}
      />

      <DemoNotice />

      <div className="grid gap-6 lg:grid-cols-[2fr_1fr]">
        <div className="grid gap-6">
          <Card>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <FlagBadge flag={beach.flag} />
              <span className="flex items-center gap-1 text-sm text-ocean-600">
                <Clock aria-hidden="true" className="size-4" />
                Atualizado em {formatDateTime(beach.updatedAt)}
              </span>
            </div>
            <p className="mt-4 text-ocean-800">{sanitizeText(beach.description, 600)}</p>
            <dl className="mt-4 grid gap-4 sm:grid-cols-3">
              <div>
                <dt className="text-sm text-ocean-500">Ocupação estimada</dt>
                <dd className="text-xl font-black text-ocean-950">
                  {formatOccupancy(beach.occupancyPercent)}
                </dd>
              </div>
              <div>
                <dt className="text-sm text-ocean-500">Coordenadas</dt>
                <dd className="font-mono text-sm font-bold text-ocean-950">
                  {beach.latitude.toFixed(4)}, {beach.longitude.toFixed(4)}
                </dd>
              </div>
              <div>
                <dt className="text-sm text-ocean-500">Riscos mapeados</dt>
                <dd className="text-xl font-black text-ocean-950">{beach.risks.length}</dd>
              </div>
            </dl>
          </Card>

          <section aria-labelledby="riscos">
            <h2 id="riscos" className="mb-3 flex items-center gap-2 text-2xl font-black text-ocean-950">
              <ShieldAlert aria-hidden="true" className="size-6 text-coral-600" />
              Zonas de risco
            </h2>
            <ul className="grid gap-4">
              {beach.risks.map((risk) => (
                <li key={risk.id}>
                  <Card>
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <h3 className="text-lg font-bold text-ocean-950">
                        {sanitizeText(risk.label, 120)}
                      </h3>
                      <RiskBadge level={risk.severity} />
                    </div>
                    <p className="mt-2 text-sm text-ocean-700">
                      {sanitizeText(risk.description, 500)}
                    </p>
                    <h4 className="mt-3 text-xs font-bold uppercase tracking-wide text-ocean-500">
                      O que fazer
                    </h4>
                    <ul className="mt-1 grid gap-1 text-sm text-ocean-800">
                      {risk.guidance.map((item) => (
                        <li key={item} className="flex gap-2">
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
          </section>
        </div>

        <aside className="grid content-start gap-4">
          <Card>
            <h2 className="flex items-center gap-2 text-lg font-black text-ocean-950">
              <Waves aria-hidden="true" className="size-5 text-ocean-600" />
              Estrutura
            </h2>
            <ul className="mt-3 grid gap-2 text-sm text-ocean-800">
              {beach.facilities.map((facility) => (
                <li key={facility} className="flex items-center gap-2">
                  <MapPin aria-hidden="true" className="size-4 text-ocean-500" />
                  {sanitizeText(facility, 80)}
                </li>
              ))}
            </ul>
          </Card>

          <Card className="border-coral-200 bg-coral-50">
            <h2 className="text-lg font-black text-coral-950">Emergência nesta praia</h2>
            <p className="mt-1 text-sm text-coral-900">
              Ligue 193 e informe o nome da praia e o ponto de referência mais próximo. O SOS
              envia sua localização para a central, mas não substitui o 193.
            </p>
            <div className="mt-4 grid gap-2">
              <SosCta />
              <EmergencyPhone />
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}
