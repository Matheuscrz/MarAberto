import {
  BookOpen,
  ClipboardList,
  Map as MapIcon,
  MapPin,
  MapPinOff,
  MapPinned,
  Users,
} from "lucide-react";
import { useLocation } from "react-router-dom";
import { AreaShell, type AreaNavItem } from "@/app/layout/AreaShell";

const NAV: readonly AreaNavItem[] = [
  // A home é o mapa de situação: a coordenação começa olhando o campo (ADR-026).
  { to: "/gestao", label: "Mapa de situação", icon: MapIcon, end: true },
  { to: "/gestao/importar", label: "Importar mapa", icon: MapPinned },
  { to: "/gestao/equipe", label: "Equipe", icon: Users },
  { to: "/gestao/postos", label: "Postos", icon: MapPin },
  { to: "/gestao/ocorrencias", label: "Ocorrências", icon: ClipboardList },
  {
    to: "/gestao/areas-nao-identificadas",
    label: "Áreas não identificadas",
    icon: MapPinOff,
  },
  { to: "/gestao/aprender", label: "Aprender", icon: BookOpen },
];

/** Área da coordenação (`COORDINATOR`). */
export function ManagementLayout() {
  const { pathname } = useLocation();
  // O mapa de situação ocupa a tela; as demais seções usam a casca com texto.
  const flush = pathname === "/gestao";

  return (
    <AreaShell
      title="Coordenação de praia"
      subtitle="Acompanhe o campo no mapa de situação, importe o mapeamento e ative postos e bases do escopo."
      nav={NAV}
      flush={flush}
    />
  );
}
