import { useMemo, useState } from "react";
import { ChevronDown, RefreshCw } from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import { useNearbyBeaches } from "@/features/beaches";
import { ShiftStationCard } from "@/features/operator/components/ShiftStationCard";
import {
  FieldStationList,
  NewStationForm,
  useFieldStations,
  usePublicStations,
  useStationPosition,
} from "@/features/stations";

type StatusFilter = "ACTIVE" | "INACTIVE" | "ALL";

/**
 * Estruturas em campo: o guarda-vidas cadastra o posto/base onde está,
 * gerencia a estrutura do plantão e confere todas as estruturas cadastradas.
 *
 * <p>O cadastro usa a posição atual do aparelho (RF11). A estrutura do plantão
 * aberto pode ser desativada e reativada pelo próprio guarda-vidas; a TI
 * continua alternando qualquer uma em `/admin/postos`. A lista do console
 * inclui a base móvel sem plantão vivo (que o mapa público esconde) e mostra a
 * posição registrada e a distância da leitura atual do aparelho. A aba abre nos
 * ativos — cada base móvel encerrada vira uma linha inativa no histórico —, e o
 * filtro de estado é aplicado pelo servidor.
 */
export function OperatorStationsPage() {
  const nearby = useNearbyBeaches();
  const [filter, setFilter] = useState<StatusFilter>("ACTIVE");
  const {
    stations,
    total,
    hasMore,
    loadingMore,
    status,
    message,
    reload,
    loadMore,
  } = useFieldStations({
    active: filter === "ACTIVE" ? true : filter === "INACTIVE" ? false : undefined,
  });
  const publicStations = usePublicStations();
  const {
    position,
    locating,
    error: locationError,
    read,
  } = useStationPosition();

  const publicKeys = useMemo(
    () =>
      new Set(
        publicStations.stations.map((station) => `${station.kind}:${station.id}`),
      ),
    [publicStations.stations],
  );

  const reloadAll = () => {
    reload();
    publicStations.reload();
  };

  return (
    <div className="grid gap-3">
      <PageHeader
        title="Postos e bases"
        subtitle="Cadastre a estrutura no ponto em que você está e veja onde cada posto ou base foi registrado."
      />

      <ShiftStationCard onChanged={reloadAll} />

      <NewStationForm beaches={nearby.beaches} onCreated={reloadAll} />

      <div className="flex flex-wrap items-center justify-between gap-2">
        <div
          role="tablist"
          aria-label="Filtro por estado da estrutura"
          className="inline-flex w-fit flex-wrap rounded-xl border border-ocean-200 bg-white p-1"
        >
          {(
            [
              { value: "ACTIVE", label: "Ativos" },
              { value: "INACTIVE", label: "Inativos" },
              { value: "ALL", label: "Todos" },
            ] as const
          ).map((option) => (
            <button
              key={option.value}
              type="button"
              role="tab"
              aria-selected={filter === option.value}
              onClick={() => setFilter(option.value)}
              className={[
                "rounded-lg px-3 py-1 text-[11px] font-bold transition",
                filter === option.value
                  ? "bg-ocean-950 text-white"
                  : "text-ocean-700 hover:bg-ocean-50",
              ].join(" ")}
            >
              {option.label}
            </button>
          ))}
        </div>
        <p className="text-[11px] font-semibold text-ocean-600">
          {status === "ready"
            ? `${stations.length} de ${total} ${
                total === 1 ? "estrutura" : "estruturas"
              }`
            : "Carregando postos e bases"}
        </p>
      </div>

      {status === "error" ? (
        <Card className="border-dashed bg-ocean-50/40 text-center">
          <p className="text-sm font-bold text-ocean-950">
            Não foi possível carregar as estruturas
          </p>
          <p className="mt-1 text-xs text-ocean-700">
            {message ?? "Tente de novo em instantes."}
          </p>
          <button
            type="button"
            onClick={reload}
            className="mt-2 inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 hover:bg-ocean-50"
          >
            <RefreshCw aria-hidden="true" className="size-3.5" />
            Tentar de novo
          </button>
        </Card>
      ) : status === "loading" ? (
        <output className="block py-6 text-center text-sm font-semibold text-ocean-600">
          Carregando postos e bases
        </output>
      ) : (
        <>
          <FieldStationList
            stations={stations}
            publicKeys={publicKeys}
            publicReady={publicStations.status === "ready"}
            position={position}
            locating={locating}
            locationError={locationError}
            onLocate={() => {
              void read();
            }}
            emptyTitle={
              filter === "ACTIVE"
                ? "Nenhuma estrutura ativa agora"
                : filter === "INACTIVE"
                  ? "Nenhuma estrutura inativa"
                  : "Nenhum posto ou base cadastrado"
            }
            emptyHint={
              filter === "ACTIVE"
                ? "Bases móveis encerradas ficam no filtro Inativos. Cadastre a estrutura na posição em que você está."
                : "Cadastre a estrutura na posição em que você está."
            }
          />
          {hasMore ? (
            <div className="text-center">
              <button
                type="button"
                onClick={loadMore}
                disabled={loadingMore}
                className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50 disabled:cursor-progress disabled:opacity-70"
              >
                <ChevronDown aria-hidden="true" className="size-3.5" />
                {loadingMore ? "Carregando…" : "Carregar mais estruturas"}
              </button>
            </div>
          ) : null}
        </>
      )}
    </div>
  );
}
