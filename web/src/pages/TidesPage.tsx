import { Moon, Waves } from 'lucide-react';
import { Card, PageHeader } from '../components/Card';
import { DemoNotice } from '../components/DemoNotice';
import { TideTable } from '../components/TideTable';
import { tides } from '../data/demo-data';

export function TidesPage() {
  const nextSyzygy = tides.find((day) => day.syzygy);

  const syzygyText = nextSyzygy
    ? `A próxima sizígia de demonstração está prevista para ${nextSyzygy.weekday}, ${nextSyzygy.date}. Marés de sizígia têm maior amplitude e podem aumentar correntes.`
    : 'Nenhuma sizígia prevista no período exibido.';

  return (
    <div>
      <PageHeader
        title="Tábua de marés"
        subtitle="Horários de preamar e baixa-mar para os próximos dias."
      />

      <DemoNotice />

      <div className="mb-6 grid gap-4 md:grid-cols-2">
        <Card>
          <h2 className="flex items-center gap-2 text-lg font-black text-ocean-950">
            <Moon aria-hidden="true" className="size-5 text-violet-600" />
            Maré e corrente de retorno
          </h2>
          <p className="mt-2 text-sm text-ocean-700">
            A corrente de retorno costuma ficar mais intensa perto da baixa-mar, quando a água
            acumulada na praia escoa de volta. Isso não é regra universal: observe a bandeira e o
            posto local.
          </p>
        </Card>

        <Card>
          <h2 className="flex items-center gap-2 text-lg font-black text-ocean-950">
            <Waves aria-hidden="true" className="size-5 text-ocean-600" />
            Sizígia
          </h2>
          <p className="mt-2 text-sm text-ocean-700">{syzygyText}</p>
        </Card>
      </div>

      <TideTable days={tides} />

      <p className="mt-4 text-sm text-ocean-600">
        Fonte: {tides[0]?.source ?? 'indisponível'}. A tábua oficial é a publicada pela autoridade
        marítima da sua região.
      </p>
    </div>
  );
}
