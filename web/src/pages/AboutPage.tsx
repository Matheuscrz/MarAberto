import { Code2, Lock, ShieldCheck, Waves } from 'lucide-react';
import { Card, PageHeader } from '../components/Card';
import { EmergencyPhone } from '../components/EmergencyActions';

export function AboutPage() {
  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="Sobre o MarAberto"
        subtitle="Plataforma open source de apoio à segurança balnear."
      />

      <div className="grid gap-6">
        <Card>
          <h2 className="flex items-center gap-2 text-lg font-black text-ocean-950">
            <Waves aria-hidden="true" className="size-5 text-ocean-600" />
            O que é
          </h2>
          <p className="mt-2 text-sm text-ocean-700">
            O MarAberto reúne bandeiramento, zonas de risco, tábua de marés, reportes da comunidade
            e um SOS cidadão para a central de guarda-vidas. É uma ferramenta de <strong>apoio</strong>:
            não substitui o acionamento oficial nem o julgamento do guarda-vidas.
          </p>
        </Card>

        <Card>
          <h2 className="flex items-center gap-2 text-lg font-black text-ocean-950">
            <ShieldCheck aria-hidden="true" className="size-5 text-emerald-600" />
            Compromisso de segurança
          </h2>
          <ul className="mt-2 grid gap-2 text-sm text-ocean-700">
            <li>
              • O SOS só confirma o envio depois da resposta do servidor. Se falhar, a interface
              orienta a ligar 193.
            </li>
            <li>
              • Nada de “envio offline garantido”: o chamado exige conexão com a internet.
            </li>
            <li>• A localização do SOS não é gravada no navegador.</li>
            <li>• Dados vindos da web são sanitizados no cliente e revalidados no servidor.</li>
          </ul>
        </Card>

        <Card>
          <h2 className="flex items-center gap-2 text-lg font-black text-ocean-950">
            <Lock aria-hidden="true" className="size-5 text-ocean-600" />
            Privacidade e dados
          </h2>
          <p className="mt-2 text-sm text-ocean-700">
            Dados pessoais são cifrados em repouso no servidor (AES-256-GCM) e indexados por
            mecanismo cego quando há necessidade de busca. Logs não recebem coordenadas precisas,
            CPF, e-mail ou telefone. A posição do SOS é tratada como dado sensível, com acesso
            restrito à operação.
          </p>
          <p className="mt-2 text-sm text-ocean-700">
            O aplicativo não usa rastreadores de terceiros. Vídeos do YouTube só carregam após o
            seu toque, pelo domínio <code className="rounded bg-ocean-100 px-1">youtube-nocookie.com</code>.
          </p>
        </Card>

        <Card>
          <h2 className="flex items-center gap-2 text-lg font-black text-ocean-950">
            <Code2 aria-hidden="true" className="size-5 text-ocean-600" />
            Código aberto
          </h2>
          <p className="mt-2 text-sm text-ocean-700">
            O projeto é distribuído sob licença MIT. Cada instalação é operada por um órgão ou
            coletivo local, responsável pelos dados e pelo atendimento.
          </p>
        </Card>

        <Card className="border-coral-200 bg-coral-50">
          <h2 className="text-lg font-black text-coral-950">Emergência</h2>
          <p className="mt-1 text-sm text-coral-900">
            Não use o MarAberto como único canal. Ligue 193 e acione o guarda-vidas mais próximo.
          </p>
          <div className="mt-4">
            <EmergencyPhone />
          </div>
        </Card>
      </div>
    </div>
  );
}
