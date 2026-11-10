import { PageHeader } from "@/shared/components/Card";
import { StationRegistry } from "@/features/stations";

/**
 * Cadastro de postos fixos e bases móveis pela TI. O formulário é o mesmo do
 * guarda-vidas em campo (`/operador/postos`), para não duplicar regra de
 * posição/foto (RF11/RF39).
 */
export function AdminStationsPage() {
  return (
    <div>
      <PageHeader
        title="Postos e bases"
        subtitle="Cadastre pontos de salvamento e mantenha as fotos que o banhista usa para reconhecer o local."
      />
      <StationRegistry />
    </div>
  );
}
