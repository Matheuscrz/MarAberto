import { Link } from 'react-router-dom';
import { ArrowRight, MapPin, Moon, ShieldCheck, Siren, Waves } from 'lucide-react';
import { Card } from '../components/Card';
import { DemoNotice } from '../components/DemoNotice';
import { EmergencyPhone, SosCta } from '../components/EmergencyActions';
import { FlagBadge, RiskBadge } from '../components/Badges';
import { InstallPrompt } from '../components/InstallPrompt';
import { beaches } from '../data/demo-data';
import { formatOccupancy } from '../lib/format';

const safetyTips = [
  {
    title: 'Respeite a bandeira',
    body: 'Verde libera o banho com atenção. Amarela pede cuidado. Vermelha proíbe. Preta indica risco extremo ou contaminação.',
  },
  {
    title: 'Nunca nade sozinho',
    body: 'Prefira trechos com posto de guarda-vidas ativo e mantenha crianças ao alcance da mão.',
  },
  {
    title: 'Corrente de retorno',
    body: 'Não nade contra. Siga paralelo à areia até sair do fluxo e só então volte para a praia.',
  },
];

export function HomePage() {
  return (
    <div className="grid gap-8">
      <section className="overflow-hidden rounded-3xl bg-gradient-to-br from-ocean-950 via-ocean-900 to-ocean-700 p-6 text-white shadow-lg md:p-10">
        <p className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-bold uppercase tracking-wide">
          <Waves aria-hidden="true" className="size-4" />
          Segurança balnear comunitária
        </p>
        <h1 className="mt-4 max-w-2xl text-3xl font-black leading-tight tracking-tight md:text-5xl">
          Informação clara para você voltar da praia em segurança.
        </h1>
        <p className="mt-4 max-w-2xl text-ocean-100">
          Bandeiramento, riscos, marés e orientação de prevenção em um só lugar. Em uma
          emergência real, o caminho mais rápido continua sendo o{' '}
          <strong className="text-white">193</strong>.
        </p>

        <div className="mt-6 flex flex-col gap-3 sm:flex-row">
          <SosCta />
          <EmergencyPhone className="border-white/30 bg-white/10 text-white hover:bg-white/20" />
        </div>

        <p className="mt-4 flex items-start gap-2 text-xs text-ocean-100">
          <ShieldCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          O SOS do MarAberto avisa a central da instalação, mas não substitui o acionamento
          oficial. Ele exige conexão com a internet.
        </p>
      </section>

      <InstallPrompt />
      <DemoNotice />

      <section aria-labelledby="praias-destaque">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-2">
          <div>
            <h2 id="praias-destaque" className="text-2xl font-black text-ocean-950">
              Praias monitoradas
            </h2>
            <p className="text-sm text-ocean-700">Bandeira e risco mais recentes por praia.</p>
          </div>
          <Link
            to="/praias"
            className="inline-flex min-h-11 items-center gap-1 rounded-lg px-2 font-bold text-ocean-700 hover:text-ocean-900"
          >
            Ver todas
            <ArrowRight aria-hidden="true" className="size-4" />
          </Link>
        </div>

        <div className="grid gap-4 md:grid-cols-3">
          {beaches.map((beach) => (
            <Link
              key={beach.id}
              to={`/praias/${beach.id}`}
              className="group rounded-2xl focus-visible:outline-2"
            >
              <Card className="h-full transition group-hover:-translate-y-0.5 group-hover:shadow-md">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <h3 className="text-lg font-black text-ocean-950">{beach.name}</h3>
                    <p className="flex items-center gap-1 text-sm text-ocean-600">
                      <MapPin aria-hidden="true" className="size-3.5" />
                      {beach.municipality} — {beach.state}
                    </p>
                  </div>
                  <RiskBadge level={beach.riskLevel} />
                </div>

                <div className="mt-4">
                  <FlagBadge flag={beach.flag} />
                </div>

                <dl className="mt-4 grid grid-cols-2 gap-2 text-sm">
                  <div>
                    <dt className="text-ocean-500">Ocupação estimada</dt>
                    <dd className="font-bold text-ocean-950">
                      {formatOccupancy(beach.occupancyPercent)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-ocean-500">Riscos mapeados</dt>
                    <dd className="font-bold text-ocean-950">{beach.risks.length}</dd>
                  </div>
                </dl>
              </Card>
            </Link>
          ))}
        </div>
      </section>

      <section aria-labelledby="atalhos" className="grid gap-4 md:grid-cols-2">
        <Card>
          <h2 id="atalhos" className="flex items-center gap-2 text-xl font-black text-ocean-950">
            <Moon aria-hidden="true" className="size-5 text-ocean-600" />
            Planeje pelo maré
          </h2>
          <p className="mt-2 text-sm text-ocean-700">
            Maré vazante costuma intensificar correntes de retorno. Confira os horários antes de
            entrar no mar.
          </p>
          <Link
            to="/mares"
            className="mt-4 inline-flex min-h-11 items-center gap-1 font-bold text-ocean-700 hover:text-ocean-900"
          >
            Ver tábua de marés
            <ArrowRight aria-hidden="true" className="size-4" />
          </Link>
        </Card>

        <Card>
          <h2 className="flex items-center gap-2 text-xl font-black text-ocean-950">
            <Siren aria-hidden="true" className="size-5 text-coral-600" />
            O que fazer em emergência
          </h2>
          <ol className="mt-2 grid gap-1 text-sm text-ocean-700">
            <li>1. Ligue 193 ou acione o guarda-vidas mais próximo.</li>
            <li>2. Não entre na água para resgatar sem preparo.</li>
            <li>3. Use o SOS para avisar a central com sua localização.</li>
          </ol>
          <Link
            to="/sos"
            className="mt-4 inline-flex min-h-11 items-center gap-1 font-bold text-coral-700 hover:text-coral-800"
          >
            Abrir o SOS
            <ArrowRight aria-hidden="true" className="size-4" />
          </Link>
        </Card>
      </section>

      <section aria-labelledby="prevencao">
        <h2 id="prevencao" className="mb-4 text-2xl font-black text-ocean-950">
          Prevenção em três pontos
        </h2>
        <div className="grid gap-4 md:grid-cols-3">
          {safetyTips.map((tip) => (
            <Card key={tip.title}>
              <h3 className="font-bold text-ocean-950">{tip.title}</h3>
              <p className="mt-1 text-sm text-ocean-700">{tip.body}</p>
            </Card>
          ))}
        </div>
      </section>
    </div>
  );
}
