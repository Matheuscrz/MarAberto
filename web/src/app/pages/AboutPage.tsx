import { Code2, Lock, ShieldCheck, Waves } from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";

export function AboutPage() {
  return (
    <div className="grid gap-3">
      <Card className="flex items-center justify-center py-6">
        <img
          src="/brand/logo-light.png"
          alt="MarAberto — segurança balnear"
          className="h-12 w-auto max-w-full"
        />
      </Card>

      <PageHeader
        title="Sobre o MarAberto"
        subtitle="Plataforma open-source de apoio à segurança balnear."
      />

      <Card>
        <h2 className="flex items-center gap-1.5 text-sm font-black text-ocean-950">
          <Waves aria-hidden="true" className="size-4 text-ocean-600" />O que é
        </h2>
        <p className="mt-1.5 text-xs leading-relaxed text-ocean-700">
          O MarAberto reúne bandeiramento, zonas de risco, tábua de marés e um
          SOS cidadão. É uma ferramenta de <strong>apoio</strong>: não substitui
          o acionamento oficial (193) nem a orientação presencial do
          guarda-vidas.
        </p>
      </Card>

      <Card>
        <h2 className="flex items-center gap-1.5 text-sm font-black text-ocean-950">
          <ShieldCheck aria-hidden="true" className="size-4 text-emerald-600" />
          Compromisso de segurança
        </h2>
        <ul className="mt-1.5 grid gap-1 text-xs text-ocean-700">
          <li>• O SOS só confirma o envio após resposta do servidor.</li>
          <li>• Sem promessa de envio offline: exige conexão ativa.</li>
          <li>• A localização do SOS nunca é persistida no navegador.</li>
        </ul>
      </Card>

      <Card>
        <h2 className="flex items-center gap-1.5 text-sm font-black text-ocean-950">
          <Lock aria-hidden="true" className="size-4 text-ocean-600" />
          Privacidade e código aberto
        </h2>
        <p className="mt-1.5 text-xs leading-relaxed text-ocean-700">
          Dados sensíveis são protegidos em repouso no servidor (AES-256-GCM) e
          não há rastreadores de terceiros. Distribuído sob licença MIT.
        </p>
        <p className="mt-2 flex items-center gap-1 text-[11px] font-semibold text-ocean-600">
          <Code2 aria-hidden="true" className="size-3.5" />
          Licença MIT · Open Source
        </p>
      </Card>
    </div>
  );
}
