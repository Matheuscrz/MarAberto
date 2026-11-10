import { BrowserRouter } from "react-router-dom";
import { AppRoutes } from "@/app/routes";
import { useSessionBootstrap } from "@/features/auth";

function App() {
  useSessionBootstrap();

  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}

export default App;
