import { Link } from "react-router-dom";
import { Home, MapPin } from "lucide-react";
import { Card } from "../components/Card";

export function NotFoundPage() {
  return (
    <Card>
      <p className="text-xs font-bold uppercase tracking-wide text-ocean-500">
        Erro 404
      </p>
      <h1 className="mt-1 text-lg font-black text-ocean-950">
        Item não encontrado
      </h1>
      <p className="mt-1.5 text-xs text-ocean-700">
        O endereço acessado não existe ou a praia ainda não foi cadastrada nesta
        instalação.
      </p>
      <div className="mt-4 grid grid-cols-2 gap-2">
        <Link
          to="/"
          className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 px-3 py-2 text-xs font-bold text-white"
        >
          <Home aria-hidden="true" className="size-4" />
          Ver mapa
        </Link>
        <Link
          to="/praias"
          className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 py-2 text-xs font-bold text-ocean-800"
        >
          <MapPin aria-hidden="true" className="size-4" />
          Praias
        </Link>
      </div>
    </Card>
  );
}
