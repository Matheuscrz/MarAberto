import { Radio, Shield } from "lucide-react";
import { Card } from "@/shared/components/Card";
import { PhotoGallery } from "@/shared/components/PhotoGallery";
import { formatDateTime } from "@/shared/lib/format";
import { sanitizeText } from "@/shared/lib/sanitize";
import { STATION_KIND_LABEL, type PublicStation } from "@/features/stations/types";

interface StationListProps {
  readonly stations: readonly PublicStation[];
  /** Texto do estado vazio; a tela escolhe o motivo. */
  readonly emptyTitle: string;
  readonly emptyHint?: string;
}

/**
 * Lista somente leitura de postos fixos e bases móveis.
 *
 * <p>É a mesma lista na página pública de guarda-vidas e no console do
 * operador. O cadastro e a ativação em campo são de quem está no local
 * (`/operador/postos`, com a estrutura do próprio plantão) e a TI mantém o
 * administrativo em `/admin/postos`; esta lista só mostra o que está publicado.
 */
export function StationList({
  stations,
  emptyTitle,
  emptyHint,
}: StationListProps) {
  if (stations.length === 0) {
    return (
      <Card className="border-dashed bg-ocean-50/40 text-center">
        <p className="text-sm font-bold text-ocean-950">{emptyTitle}</p>
        {emptyHint ? (
          <p className="mt-1 text-xs text-ocean-700">{emptyHint}</p>
        ) : null}
      </Card>
    );
  }

  return (
    <ul className="grid gap-3">
      {stations.map((station) => (
        <li key={`${station.kind}:${station.id}`}>
          <Card className="flex flex-col gap-2">
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
                <h2 className="mt-1 text-sm font-black text-ocean-950">
                  {sanitizeText(station.name, 120)}
                </h2>
                {station.beachName ? (
                  <p className="text-[11px] text-ocean-600">
                    {sanitizeText(station.beachName, 120)}
                  </p>
                ) : null}
              </div>
              {station.positionUpdatedAt ? (
                <span className="text-right text-[10px] text-ocean-500">
                  Posição de
                  <br />
                  {formatDateTime(station.positionUpdatedAt)}
                </span>
              ) : null}
            </div>
            <PhotoGallery
              urls={station.photoUrls}
              label={sanitizeText(station.name, 120)}
              compact
              openLabel="Ampliar foto do posto"
            />
          </Card>
        </li>
      ))}
    </ul>
  );
}
