import { useEffect } from "react";
import { BrowserRouter } from "react-router-dom";
import { AppRoutes } from "@/app/routes";
import { useAuthSession, useSessionBootstrap } from "@/features/auth";
import { hideNativeSplash } from "@/shared/lib/native";

function App() {
  useSessionBootstrap();
  const status = useAuthSession((state) => state.status);

  // O app empacotado abre com a arte da marca (ADR-019). O splash sai quando a
  // sessão é resolvida: antes disso o refresh ainda está em curso e esconder
  // cedo demais faria a tela piscar "Entrar" para quem já está autenticado.
  useEffect(() => {
    if (status !== "unknown") {
      void hideNativeSplash();
      return;
    }
    // Rede de segurança: nenhum boot fica preso atrás do splash.
    const timer = window.setTimeout(() => void hideNativeSplash(), 8000);
    return () => window.clearTimeout(timer);
  }, [status]);

  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}

export default App;
