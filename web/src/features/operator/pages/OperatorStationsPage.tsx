import { PageHeader } from "@/shared/components/Card";
import { StationRegistry } from "@/features/stations";

/**
 * Cadastro do ponto em campo (RF39): o guarda-vidas que trabalha no local
 * registra o posto fixo/base móvel com a localização atual do aparelho, sem
 * esperar a TI. A posição é usada só no cadastro (ADR-012).
 */
export function OperatorStationsPage() {
  return (
    <div>
      <PageHeader
        title="Postos e bases"
        subtitle="Cadastre ou corrija o ponto onde você está trabalhando. A localização atual preenche a coordenada; ajuste se precisar."
      />
      <StationRegistry />
    </div>
  );
}
