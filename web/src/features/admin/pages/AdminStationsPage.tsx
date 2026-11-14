import { PageHeader } from "@/shared/components/Card";
import { StationAdminTable } from "@/features/stations";

/**
 * Postos fixos e bases móveis no painel da TI.
 *
 * <p>Ativar/desativar é decisão do órgão, não do campo: a estrutura inativa sai
 * do mapa público e da lista de destinos do plantão, mas plantões e ocorrências
 * já registrados continuam apontando para a mesma linha.
 */
export function AdminStationsPage() {
  return (
    <div>
      <PageHeader
        title="Postos e bases"
        subtitle="Tire do mapa público a estrutura inoperante. Desativar não apaga o histórico de plantões e ocorrências."
      />
      <StationAdminTable />
    </div>
  );
}
