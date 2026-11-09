import { lazy, Suspense } from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Layout } from "./components/Layout";

const BeachesPage = lazy(() =>
  import("./pages/BeachesPage").then((m) => ({ default: m.BeachesPage })),
);
const BeachDetailPage = lazy(() =>
  import("./pages/BeachDetailPage").then((m) => ({
    default: m.BeachDetailPage,
  })),
);
const SosPage = lazy(() =>
  import("./pages/SosPage").then((m) => ({ default: m.SosPage })),
);
const TidesPage = lazy(() =>
  import("./pages/TidesPage").then((m) => ({ default: m.TidesPage })),
);
const LearnPage = lazy(() =>
  import("./pages/LearnPage").then((m) => ({ default: m.LearnPage })),
);
const AboutPage = lazy(() =>
  import("./pages/AboutPage").then((m) => ({ default: m.AboutPage })),
);
const NotFoundPage = lazy(() =>
  import("./pages/NotFoundPage").then((m) => ({ default: m.NotFoundPage })),
);

function PageFallback() {
  return (
    <output className="block py-8 text-center text-sm font-semibold text-ocean-600">
      Carregando
    </output>
  );
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          {/* Rota raiz mostra apenas o mapa limpo (ou a busca se o usuário clicar na barra) */}
          <Route index element={null} />
          <Route
            path="praias"
            element={
              <Suspense fallback={<PageFallback />}>
                <BeachesPage />
              </Suspense>
            }
          />
          <Route
            path="praias/:beachId"
            element={
              <Suspense fallback={<PageFallback />}>
                <BeachDetailPage />
              </Suspense>
            }
          />
          <Route
            path="sos"
            element={
              <Suspense fallback={<PageFallback />}>
                <SosPage />
              </Suspense>
            }
          />
          <Route
            path="mares"
            element={
              <Suspense fallback={<PageFallback />}>
                <TidesPage />
              </Suspense>
            }
          />
          <Route
            path="aprender"
            element={
              <Suspense fallback={<PageFallback />}>
                <LearnPage />
              </Suspense>
            }
          />
          <Route
            path="sobre"
            element={
              <Suspense fallback={<PageFallback />}>
                <AboutPage />
              </Suspense>
            }
          />
          <Route
            path="*"
            element={
              <Suspense fallback={<PageFallback />}>
                <NotFoundPage />
              </Suspense>
            }
          />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
