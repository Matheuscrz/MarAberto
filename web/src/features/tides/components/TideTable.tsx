import { Droplets, Sparkles } from "lucide-react";
import type { TideDay } from "@/features/tides/types";
import { formatDay, formatHeight } from "@/shared/lib/format";

export function TideTable({ days }: { days: readonly TideDay[] }) {
  if (days.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-ocean-200 bg-ocean-50/40 p-5 text-center">
        <p className="text-sm font-bold text-ocean-950">
          Nenhuma tábua de marés carregada.
        </p>
        <p className="mt-1 text-xs text-ocean-700">
          Os horários de preamar e baixa-mar aparecerão aqui quando integrados.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-2xl border border-ocean-900/10 bg-white shadow-sm">
      <table className="w-full text-left text-xs">
        <caption className="sr-only">
          Previsão de marés dos próximos dias
        </caption>
        <thead className="bg-ocean-950 text-white">
          <tr>
            <th scope="col" className="px-3 py-2.5 font-semibold">
              Dia
            </th>
            <th scope="col" className="px-3 py-2.5 font-semibold">
              Marés
            </th>
          </tr>
        </thead>
        <tbody>
          {days.map((day) => (
            <tr
              key={day.date}
              className="border-t border-ocean-900/10 align-top"
            >
              <th
                scope="row"
                className="whitespace-nowrap px-3 py-3 font-bold text-ocean-950"
              >
                <span className="block capitalize">{day.weekday}</span>
                <span className="text-[11px] font-medium text-ocean-500">
                  {formatDay(day.date)}
                </span>
                {day.syzygy ? (
                  <span className="mt-1.5 inline-flex items-center gap-1 rounded-full bg-violet-100 px-2 py-0.5 text-[10px] font-bold text-violet-900">
                    <Sparkles aria-hidden="true" className="size-3" />
                    Sizígia
                  </span>
                ) : null}
              </th>
              <td className="px-3 py-3">
                <ul className="grid grid-cols-2 gap-1.5">
                  {day.events.map((event) => (
                    <li
                      key={`${day.date}-${event.time}`}
                      className="flex items-center gap-1.5"
                    >
                      <Droplets
                        aria-hidden="true"
                        className={`size-3.5 shrink-0 ${
                          event.type === "HIGH"
                            ? "text-ocean-600"
                            : "text-amber-600"
                        }`}
                      />
                      <span className="font-mono font-bold text-ocean-950">
                        {event.time}
                      </span>
                      <span className="text-ocean-600">
                        {formatHeight(event.heightMeters)}
                      </span>
                    </li>
                  ))}
                </ul>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
