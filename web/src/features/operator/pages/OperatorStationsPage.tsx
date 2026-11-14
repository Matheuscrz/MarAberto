import { useMemo } from "react";
import { RefreshCw } from "lucide-react";
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

/**
 * Estruturas em campo: o guarda-vidas cadastra o posto/base onde está,
 * gerencia a estrutura do plantão e confere todas as estruturas cadastradas.
 *
 * <p>O cadastro usa a posição atual do aparelho (RF11). A estrutura do plantão
 * aberto pode ser desativada e reativada pelo próprio guarda-vidas; a TI
 * continua alternando qualquer uma em `/admin/postos`. A lista do console
 * inclui a base móvel sem plantão vivo (que o mapa público esconde) e mostra a
 * posição registrada e a distância da leitura atual do aparelho.
 */
export function OperatorStationsPage() {
  const nearby = useNearbyBeaches();
  const { stations, status, message, reload } = useFieldStations();
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
        />
      )}
    </div>
  );
}
