import { PageHeader } from "@/shared/components/Card";
import { StationAdminTable } from "@/features/stations";

/**
 * Postos fixos e bases móveis na visão da coordenação.
 *
 * <p>A coordenação lê e ativa/desativa qualquer estrutura pela rota de campo
 * (`/api/v1/operations/stations`), sem precisar do papel `ADMIN`. Desativar tira
 * a estrutura do mapa público e do seletor de plantão; plantões e ocorrências
 * já registrados continuam apontando para a mesma linha.
 */
export function ManagementStationsPage() {
  return (
    <div>
      <PageHeader
        title="Postos e bases"
        subtitle="Ative ou desative estruturas do escopo. A estrutura inoperante sai do mapa público e da lista de destinos do plantão, sem apagar o histórico."
      />
      <StationAdminTable source="coordination" />
    </div>
  );
}
