import { LayoutDashboard, ScrollText, Shield, Users } from "lucide-react";
import { AreaShell, type AreaNavItem } from "@/app/layout/AreaShell";

const NAV: readonly AreaNavItem[] = [
  { to: "/admin", label: "Visão geral", icon: LayoutDashboard, end: true },
  { to: "/admin/usuarios", label: "Usuários", icon: Users },
  { to: "/admin/postos", label: "Postos e bases", icon: Shield },
  { to: "/admin/auditoria", label: "Auditoria", icon: ScrollText },
];

/** Área da administração/TI (`ADMIN`). */
export function AdminLayout() {
  return (
    <AreaShell
      title="Administração"
      subtitle="Cadastre usuários, defina papéis e mantenha os postos e bases da instalação."
      nav={NAV}
    />
  );
}
