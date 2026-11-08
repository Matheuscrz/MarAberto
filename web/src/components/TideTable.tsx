import { Droplets, Sparkles } from 'lucide-react';
import type { TideDay } from '../types';
import { formatDay, formatHeight } from '../lib/format';

export function TideTable({ days }: { days: TideDay[] }) {
  return (
    <div className="overflow-hidden rounded-2xl border border-ocean-900/10 bg-white shadow-sm">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">Previsão de marés dos próximos dias</caption>
        <thead className="bg-ocean-950 text-white">
          <tr>
            <th scope="col" className="px-4 py-3 font-semibold">Dia</th>
            <th scope="col" className="px-4 py-3 font-semibold">Marés</th>
            <th scope="col" className="hidden px-4 py-3 font-semibold sm:table-cell">Fonte</th>
          </tr>
        </thead>
        <tbody>
          {days.map((day) => (
            <tr key={day.date} className="border-t border-ocean-900/10 align-top">
              <th scope="row" className="whitespace-nowrap px-4 py-4 font-bold text-ocean-950">
                <span className="block capitalize">{day.weekday}</span>
                <span className="text-xs font-medium text-ocean-500">{formatDay(day.date)}</span>
                {day.syzygy ? (
                  <span className="mt-2 inline-flex items-center gap-1 rounded-full bg-violet-100 px-2 py-0.5 text-[11px] font-bold text-violet-900">
                    <Sparkles aria-hidden="true" className="size-3" />
                    Sizígia
                  </span>
                ) : null}
              </th>
              <td className="px-4 py-4">
                <ul className="grid gap-2 sm:grid-cols-2">
                  {day.events.map((event) => (
                    <li key={`${day.date}-${event.time}`} className="flex items-center gap-2">
                      <Droplets
                        aria-hidden="true"
                        className={`size-4 ${event.type === 'HIGH' ? 'text-ocean-600' : 'text-amber-600'}`}
                      />
                      <span className="font-mono font-bold text-ocean-950">{event.time}</span>
                      <span className="text-ocean-600">
                        {event.type === 'HIGH' ? 'preamar' : 'baixa-mar'} {formatHeight(event.heightMeters)}
                      </span>
                    </li>
                  ))}
                </ul>
              </td>
              <td className="hidden px-4 py-4 text-xs text-ocean-500 sm:table-cell">{day.source}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
