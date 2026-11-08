import { Link } from 'react-router-dom';
import { Home, MapPin, Phone } from 'lucide-react';
import { Card } from '../components/Card';

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-xl py-10">
      <Card>
        <p className="text-sm font-bold uppercase tracking-wide text-ocean-500">Erro 404</p>
        <h1 className="mt-1 text-3xl font-black text-ocean-950">Página não encontrada</h1>
        <p className="mt-2 text-ocean-700">
          O endereço acessado não existe ou a praia ainda não está no cadastro desta instalação.
        </p>
        <div className="mt-6 grid gap-2 sm:grid-cols-3">
          <Link
            to="/"
            className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl bg-ocean-950 px-4 py-3 font-bold text-white"
          >
            <Home aria-hidden="true" className="size-5" />
            Início
          </Link>
          <Link
            to="/praias"
            className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border border-ocean-300 bg-white px-4 py-3 font-bold text-ocean-800"
          >
            <MapPin aria-hidden="true" className="size-5" />
            Praias
          </Link>
          <a
            href="tel:193"
            className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border-2 border-ocean-950 bg-white px-4 py-3 font-bold text-ocean-950"
          >
            <Phone aria-hidden="true" className="size-5" />
            Ligar 193
          </a>
        </div>
      </Card>
    </div>
  );
}
