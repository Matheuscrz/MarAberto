import { ClipboardList, Users } from "lucide-react";
import { AreaShell, type AreaNavItem } from "@/app/layout/AreaShell";

const NAV: readonly AreaNavItem[] = [
  { to: "/gestao", label: "Equipe", icon: Users, end: true },
  { to: "/gestao/ocorrencias", label: "Ocorrências", icon: ClipboardList },
];

/** Área da coordenação (`COORDINATOR`). */
export function ManagementLayout() {
  return (
    <AreaShell
      title="Coordenação de praia"
      subtitle="Ative guarda-vidas, acompanhe a equipe e revise as ocorrências do dia."
      nav={NAV}
    />
  );
}
