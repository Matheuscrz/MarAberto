import { ClipboardList, MapPinned, Users } from "lucide-react";
import { AreaShell, type AreaNavItem } from "@/app/layout/AreaShell";

const NAV: readonly AreaNavItem[] = [
  // A importação abre a área: é o primeiro passo da coordenação (ADR-016).
  { to: "/gestao", label: "Importar mapa", icon: MapPinned, end: true },
  { to: "/gestao/equipe", label: "Equipe", icon: Users },
  { to: "/gestao/ocorrencias", label: "Ocorrências", icon: ClipboardList },
];

/** Área da coordenação (`COORDINATOR`). */
export function ManagementLayout() {
  return (
    <AreaShell
      title="Coordenação de praia"
      subtitle="Importe o mapeamento do CBM, ative guarda-vidas e acompanhe a equipe e as ocorrências do dia."
      nav={NAV}
    />
  );
}
