import { Phone, Siren } from 'lucide-react';
import { Link } from 'react-router-dom';

export function EmergencyPhone({ className = '' }: { className?: string }) {
  return (
    <a
      href="tel:193"
      className={`inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border-2 border-ocean-950 bg-white px-5 py-3 font-bold text-ocean-950 transition hover:bg-ocean-50 ${className}`}
    >
      <Phone aria-hidden="true" className="size-5" />
      Ligar 193
    </a>
  );
}

export function SosCta({ className = '' }: { className?: string }) {
  return (
    <Link
      to="/sos"
      className={`inline-flex min-h-14 items-center justify-center gap-3 rounded-2xl bg-coral-600 px-6 py-4 text-lg font-black text-white shadow-lg shadow-coral-600/20 transition hover:bg-coral-500 ${className}`}
    >
      <Siren aria-hidden="true" className="size-6" />
      Abrir SOS
    </Link>
  );
}
