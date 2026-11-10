import { lazy, Suspense, type ReactNode } from "react";
import { Route, Routes } from "react-router-dom";
import { RootLayout } from "@/app/layout/RootLayout";
import { OperatorLayout } from "@/app/layout/OperatorLayout";
import { ManagementLayout } from "@/app/layout/ManagementLayout";
import { AdminLayout } from "@/app/layout/AdminLayout";
import { RequireRole } from "@/features/auth";

const HomePage = lazy(() =>
  import("@/app/pages/HomePage").then((m) => ({ default: m.HomePage })),
);
const AboutPage = lazy(() =>
  import("@/app/pages/AboutPage").then((m) => ({ default: m.AboutPage })),
);
const NotFoundPage = lazy(() =>
  import("@/app/pages/NotFoundPage").then((m) => ({ default: m.NotFoundPage })),
);
const LoginPage = lazy(() =>
  import("@/features/auth/pages/LoginPage").then((m) => ({
    default: m.LoginPage,
  })),
);
const BeachesPage = lazy(() =>
  import("@/features/beaches").then((m) => ({ default: m.BeachesPage })),
);
const BeachDetailPage = lazy(() =>
  import("@/features/beaches").then((m) => ({ default: m.BeachDetailPage })),
);
const SosPage = lazy(() =>
  import("@/features/sos").then((m) => ({ default: m.SosPage })),
);
const LifeguardsPage = lazy(() =>
  import("@/features/stations").then((m) => ({ default: m.LifeguardsPage })),
);
const TidesPage = lazy(() =>
  import("@/features/tides").then((m) => ({ default: m.TidesPage })),
);
const LearnPage = lazy(() =>
  import("@/features/learn").then((m) => ({ default: m.LearnPage })),
);

const OperatorSosPage = lazy(() =>
  import("@/features/operator").then((m) => ({ default: m.OperatorSosPage })),
);
const OperatorOccurrencesPage = lazy(() =>
  import("@/features/operator").then((m) => ({
    default: m.OperatorOccurrencesPage,
  })),
);
const OperatorStationsPage = lazy(() =>
  import("@/features/operator").then((m) => ({
    default: m.OperatorStationsPage,
  })),
);
const ManagementTeamPage = lazy(() =>
  import("@/features/management").then((m) => ({
    default: m.ManagementTeamPage,
  })),
);
const ManagementOccurrencesPage = lazy(() =>
  import("@/features/management").then((m) => ({
    default: m.ManagementOccurrencesPage,
  })),
);
const AdminHomePage = lazy(() =>
  import("@/features/admin").then((m) => ({ default: m.AdminHomePage })),
);
const AdminUsersPage = lazy(() =>
  import("@/features/admin").then((m) => ({ default: m.AdminUsersPage })),
);
const AdminStationsPage = lazy(() =>
  import("@/features/admin").then((m) => ({ default: m.AdminStationsPage })),
);

function PageFallback() {
  return (
    <output className="block py-8 text-center text-sm font-semibold text-ocean-600">
      Carregando
    </output>
  );
}

function withFallback(node: ReactNode) {
  return <Suspense fallback={<PageFallback />}>{node}</Suspense>;
}

export function AppRoutes() {
  return (
    <Routes>
      {/* Login único: o papel decide a área de destino (ADR-009). */}
      <Route path="login" element={withFallback(<LoginPage />)} />

      {/*
       * Áreas por perfil. A guarda é só UX: o servidor revalida papel e
       * escopo em toda chamada. Layouts ficam no app/ porque compõem a casca.
       */}
      <Route
        path="operador"
        element={
          <RequireRole roles={["LIFEGUARD"]}>
            <OperatorLayout />
          </RequireRole>
        }
      >
        <Route index element={withFallback(<OperatorSosPage />)} />
        <Route
          path="ocorrencias"
          element={withFallback(<OperatorOccurrencesPage />)}
        />
        <Route path="postos" element={withFallback(<OperatorStationsPage />)} />
      </Route>

      <Route
        path="gestao"
        element={
          <RequireRole roles={["COORDINATOR"]}>
            <ManagementLayout />
          </RequireRole>
        }
      >
        <Route index element={withFallback(<ManagementTeamPage />)} />
        <Route
          path="ocorrencias"
          element={withFallback(<ManagementOccurrencesPage />)}
        />
      </Route>

      <Route
        path="admin"
        element={
          <RequireRole roles={["ADMIN"]}>
            <AdminLayout />
          </RequireRole>
        }
      >
        <Route index element={withFallback(<AdminHomePage />)} />
        <Route path="usuarios" element={withFallback(<AdminUsersPage />)} />
        <Route path="postos" element={withFallback(<AdminStationsPage />)} />
      </Route>

      {/* Rotas públicas com o mapa em tela cheia e painel flutuante. */}
      <Route element={<RootLayout />}>
        <Route index element={withFallback(<HomePage />)} />
        <Route path="praias" element={withFallback(<BeachesPage />)} />
        <Route
          path="praias/:beachId"
          element={withFallback(<BeachDetailPage />)}
        />
        <Route path="sos" element={withFallback(<SosPage />)} />
        <Route
          path="guarda-vidas"
          element={withFallback(<LifeguardsPage />)}
        />
        <Route path="mares" element={withFallback(<TidesPage />)} />
        <Route path="aprender" element={withFallback(<LearnPage />)} />
        <Route path="sobre" element={withFallback(<AboutPage />)} />
        <Route path="*" element={withFallback(<NotFoundPage />)} />
      </Route>
    </Routes>
  );
}
