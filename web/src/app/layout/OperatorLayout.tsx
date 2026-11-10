import { ClipboardList, MapPinned, Siren } from "lucide-react";
import { AreaShell, type AreaNavItem } from "@/app/layout/AreaShell";

const NAV: readonly AreaNavItem[] = [
  { to: "/operador", label: "Alertas SOS", icon: Siren, end: true },
  { to: "/operador/ocorrencias", label: "Ocorrências", icon: ClipboardList },
  { to: "/operador/postos", label: "Postos e bases", icon: MapPinned },
];

/** Área do guarda-vidas (`LIFEGUARD`). */
export function OperatorLayout() {
  return (
    <AreaShell
      title="Mesa do guarda-vidas"
      subtitle="Atenda aos chamados do posto, registre as ocorrências do turno e mantenha o ponto em campo."
      nav={NAV}
    />
  );
}
