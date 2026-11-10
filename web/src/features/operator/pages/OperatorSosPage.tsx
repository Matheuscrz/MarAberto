import { useMemo, useState } from "react";
import {
  CheckCircle,
  ExternalLink,
  Radio,
  Siren,
  Timer,
} from "lucide-react";
import { PageHeader } from "@/shared/components/Card";
import { formatAccuracy, formatDateTime } from "@/shared/lib/format";
import type {
  EmergencyCall,
  EmergencyCallStatus,
} from "@/shared/types/emergency";

/**
 * Fila de SOS da mesa do guarda-vidas. Enquanto a publicação em tempo real e o
 * `ack` do backend não entram, os chamados são de demonstração e o estado vive
 * só na tela — recarregar volta ao início. As ações aqui serão `POST` com
 * compare-and-set quando o módulo `emergency` fechar o walking skeleton.
 */
const INITIAL_CALLS: readonly EmergencyCall[] = [
  {
    id: "SOS-2411",
    clientRequestId: "demo-request-1",
    latitude: -10.98521,
    longitude: -37.04512,
    accuracyMeters: 12,
    status: "PENDING",
    createdAt: new Date(Date.now() - 3 * 60_000).toISOString(),
  },
  {
    id: "SOS-2408",
    clientRequestId: "demo-request-2",
    latitude: -10.96912,
    longitude: -37.03188,
    accuracyMeters: 28,
    status: "DISPATCHED",
    createdAt: new Date(Date.now() - 18 * 60_000).toISOString(),
    dispatchedAt: new Date(Date.now() - 15 * 60_000).toISOString(),
  },
];

const STATUS_LABEL: Record<EmergencyCallStatus, string> = {
  PENDING: "Aguardando despacho",
  DISPATCHED: "Guarnição despachada",
  RESOLVED: "Atendido",
  CANCELED: "Cancelado",
};

const STATUS_STYLE: Record<EmergencyCallStatus, string> = {
  PENDING: "border-coral-300 bg-coral-50 text-coral-900",
  DISPATCHED: "border-ocean-300 bg-ocean-50 text-ocean-900",
  RESOLVED: "border-emerald-300 bg-emerald-50 text-emerald-900",
  CANCELED: "border-ocean-200 bg-white text-ocean-600",
};

export function OperatorSosPage() {
  const [calls, setCalls] = useState<readonly EmergencyCall[]>(INITIAL_CALLS);

  const counts = useMemo(() => {
    return calls.reduce<Record<EmergencyCallStatus, number>>(
      (acc, call) => {
        acc[call.status] += 1;
        return acc;
      },
      { PENDING: 0, DISPATCHED: 0, RESOLVED: 0, CANCELED: 0 },
    );
  }, [calls]);

  const updateStatus = (callId: string, status: EmergencyCallStatus) => {
    setCalls((previous) =>
      previous.map((call) => {
        if (call.id !== callId) return call;
        if (status === "DISPATCHED") {
          return { ...call, status, dispatchedAt: new Date().toISOString() };
        }
        if (status === "RESOLVED") {
          return { ...call, status, resolvedAt: new Date().toISOString() };
        }
        return { ...call, status };
      }),
    );
  };

  return (
    <div>
      <PageHeader
        title="Alertas SOS"
        subtitle="Chamados enviados pelo aplicativo do banhista. Confirme o recebimento e despache a guarnição."
        actions={
          <span className="inline-flex items-center gap-1.5 rounded-full border border-coral-300 bg-coral-50 px-3 py-1 text-xs font-bold text-coral-900">
            <span className="size-2 animate-pulse rounded-full bg-coral-600" />
            Tempo real
          </span>
        }
      />

      <dl className="mb-4 grid grid-cols-3 gap-2">
        <SummaryTile
          label="Aguardando"
          value={counts.PENDING}
          tone="border-coral-200 bg-coral-50 text-coral-900"
        />
        <SummaryTile
          label="Despachados"
          value={counts.DISPATCHED}
          tone="border-ocean-200 bg-white text-ocean-900"
        />
        <SummaryTile
          label="Atendidos"
          value={counts.RESOLVED}
          tone="border-emerald-200 bg-emerald-50 text-emerald-900"
        />
      </dl>

      {calls.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-ocean-300 bg-white p-8 text-center text-sm font-semibold text-ocean-600">
          Nenhum chamado de emergência ativo no momento.
        </p>
      ) : (
        <ul className="grid gap-3">
          {calls.map((call) => (
            <li key={call.id}>
              <article
                className={`rounded-2xl border bg-white p-4 shadow-sm ${
                  call.status === "PENDING"
                    ? "border-coral-300 ring-1 ring-coral-200"
                    : "border-ocean-900/10"
                }`}
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="inline-flex items-center gap-1 rounded-md bg-coral-600 px-2 py-0.5 text-[10px] font-black uppercase tracking-wider text-white">
                        <Siren aria-hidden="true" className="size-3" />
                        SOS banhista
                      </span>
                      <span className="font-mono text-xs font-bold text-ocean-900">
                        {call.id}
                      </span>
                      <span
                        className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-bold ${STATUS_STYLE[call.status]}`}
                      >
                        {STATUS_LABEL[call.status]}
                      </span>
                    </div>
                    <p className="mt-1 flex items-center gap-1 text-xs text-ocean-600">
                      <Timer aria-hidden="true" className="size-3.5" />
                      Recebido em {formatDateTime(call.createdAt)}
                      {call.dispatchedAt
                        ? ` · despacho ${formatDateTime(call.dispatchedAt)}`
                        : ""}
                    </p>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    <a
                      href={`https://www.google.com/maps/search/?api=1&query=${call.latitude},${call.longitude}`}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-900/15 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50"
                    >
                      <ExternalLink aria-hidden="true" className="size-3.5" />
                      Ver no GPS
                    </a>

                    {call.status === "PENDING" ? (
                      <button
                        type="button"
                        onClick={() => updateStatus(call.id, "DISPATCHED")}
                        className="inline-flex min-h-9 items-center gap-1.5 rounded-xl bg-ocean-950 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-ocean-900"
                      >
                        <Radio aria-hidden="true" className="size-3.5" />
                        Despachar guarnição
                      </button>
                    ) : null}

                    {call.status === "DISPATCHED" ? (
                      <button
                        type="button"
                        onClick={() => updateStatus(call.id, "RESOLVED")}
                        className="inline-flex min-h-9 items-center gap-1.5 rounded-xl bg-emerald-600 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-emerald-500"
                      >
                        <CheckCircle aria-hidden="true" className="size-3.5" />
                        Concluir atendimento
                      </button>
                    ) : null}
                  </div>
                </div>

                <dl className="mt-3 grid grid-cols-3 gap-2 rounded-xl border border-ocean-100 bg-ocean-50/60 p-2.5 text-center text-xs">
                  <div>
                    <dt className="text-[10px] font-bold uppercase text-ocean-500">
                      Latitude
                    </dt>
                    <dd className="font-mono font-bold text-ocean-900">
                      {call.latitude.toFixed(5)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-[10px] font-bold uppercase text-ocean-500">
                      Longitude
                    </dt>
                    <dd className="font-mono font-bold text-ocean-900">
                      {call.longitude.toFixed(5)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-[10px] font-bold uppercase text-ocean-500">
                      Precisão
                    </dt>
                    <dd className="font-bold text-ocean-900">
                      {formatAccuracy(call.accuracyMeters)}
                    </dd>
                  </div>
                </dl>

                <p className="mt-2 text-[11px] text-ocean-500">
                  Coordenadas precisas do banhista têm uso restrito ao
                  atendimento: não aparecem em listagens públicas nem em logs.
                </p>
              </article>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

interface SummaryTileProps {
  readonly label: string;
  readonly value: number;
  readonly tone: string;
}

function SummaryTile({ label, value, tone }: SummaryTileProps) {
  return (
    <div className={`rounded-xl border p-3 text-center ${tone}`}>
      <dt className="text-[10px] font-bold uppercase tracking-wider opacity-70">
        {label}
      </dt>
      <dd className="text-xl font-black">{value}</dd>
    </div>
  );
}
