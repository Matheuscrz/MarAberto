import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import {
  buildUnauthenticatedLoginTarget,
  homePathForRole,
} from "@/features/auth/lib/role-home";
import { useAuthSession } from "@/features/auth/session";
import type { Role } from "@/features/auth/types";

interface RequireRoleProps {
  readonly roles: readonly Role[];
  readonly children: ReactNode;
}

/**
 * Guarda de rota por papel — **só UX**. A autorização real é do servidor:
 * toda chamada carrega o access token e o backend revalida papel e escopo.
 * Aqui só evitamos mostrar uma tela que o usuário não pode usar.
 */
export function RequireRole({ roles, children }: RequireRoleProps) {
  const status = useAuthSession((state) => state.status);
  const user = useAuthSession((state) => state.user);
  const loginRedirect = useAuthSession((state) => state.loginRedirect);
  const location = useLocation();

  if (status === "unknown") {
    return (
      <div className="grid min-h-dvh place-items-center bg-ocean-50">
        <output className="block text-sm font-semibold text-ocean-700">
          Verificando sessão
        </output>
      </div>
    );
  }

  if (status !== "authenticated" || !user) {
    const currentPath = `${location.pathname}${location.search}`;
    return (
      <Navigate
        to={buildUnauthenticatedLoginTarget(currentPath, loginRedirect)}
        replace
      />
    );
  }

  // Senha temporária: qualquer área de negócio leva à troca obrigatória.
  // A tela de troca usa esta mesma guarda, então o próprio caminho é liberado.
  if (user.mustChangePassword && location.pathname !== "/trocar-senha") {
    return <Navigate to="/trocar-senha" replace />;
  }

  if (!roles.includes(user.role)) {
    return <Navigate to={homePathForRole(user.role)} replace />;
  }

  return <>{children}</>;
}
