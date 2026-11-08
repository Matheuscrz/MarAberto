import { lazy, Suspense } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';

const HomePage = lazy(() => import('./pages/HomePage').then((m) => ({ default: m.HomePage })));
const BeachesPage = lazy(() =>
  import('./pages/BeachesPage').then((m) => ({ default: m.BeachesPage })),
);
const BeachDetailPage = lazy(() =>
  import('./pages/BeachDetailPage').then((m) => ({ default: m.BeachDetailPage })),
);
const SosPage = lazy(() => import('./pages/SosPage').then((m) => ({ default: m.SosPage })));
const TidesPage = lazy(() => import('./pages/TidesPage').then((m) => ({ default: m.TidesPage })));
const LearnPage = lazy(() => import('./pages/LearnPage').then((m) => ({ default: m.LearnPage })));
const AboutPage = lazy(() => import('./pages/AboutPage').then((m) => ({ default: m.AboutPage })));
const NotFoundPage = lazy(() =>
  import('./pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })),
);

function PageFallback() {
  return (
    <p role="status" className="py-10 text-center font-semibold text-ocean-600">
      Carregando…
    </p>
  );
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route
            index
            element={
              <Suspense fallback={<PageFallback />}>
                <HomePage />
              </Suspense>
            }
          />
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
