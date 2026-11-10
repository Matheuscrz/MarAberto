import { Moon, Waves } from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import { TideTable } from "@/features/tides/components/TideTable";
import { tides } from "@/features/tides/api/demo-tides";

export function TidesPage() {
  const nextSyzygy = tides.find((day) => day.syzygy);
  const syzygyText = nextSyzygy
    ? `A próxima sizígia está prevista para ${nextSyzygy.weekday}, ${nextSyzygy.date}. Marés de sizígia têm maior amplitude e podem intensificar correntes.`
    : "Nenhuma sizígia prevista no período exibido.";

  return (
    <div className="grid gap-3">
      <PageHeader
        title="Tábua de marés"
        subtitle="Horários de preamar e baixa-mar para os próximos dias."
      />

      <TideTable days={tides} />

      <div className="grid gap-2.5">
        <Card>
          <h2 className="flex items-center gap-2 text-sm font-black text-ocean-950">
            <Moon aria-hidden="true" className="size-4 text-violet-600" />
            Maré e corrente de retorno
          </h2>
          <p className="mt-1.5 text-xs text-ocean-700">
            A corrente de retorno costuma ficar mais intensa perto da baixa-mar,
            quando a água acumulada na praia escoa de volta. Observe sempre a
            bandeira e o posto local.
          </p>
        </Card>

        <Card>
          <h2 className="flex items-center gap-2 text-sm font-black text-ocean-950">
            <Waves aria-hidden="true" className="size-4 text-ocean-600" />
            Sizígia
          </h2>
          <p className="mt-1.5 text-xs text-ocean-700">{syzygyText}</p>
        </Card>
      </div>

      {tides[0]?.source ? (
        <p className="text-[11px] text-ocean-600">
          Fonte: {tides[0].source}. Consulte sempre a autoridade marítima da sua
          região.
        </p>
      ) : null}
    </div>
  );
}
