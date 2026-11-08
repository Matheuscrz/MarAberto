import { GraduationCap, ShieldCheck } from 'lucide-react';
import { Card, PageHeader } from '../components/Card';
import { VideoFacade } from '../components/VideoFacade';
import { educationalVideos } from '../data/demo-data';

const quickGuides = [
  {
    title: 'Se for pego por uma corrente',
    steps: [
      'Não lute contra a corrente.',
      'Nade paralelo à praia até sair do fluxo.',
      'Se cansar, flutue de costas e peça ajuda.',
    ],
  },
  {
    title: 'Criança na praia',
    steps: [
      'Mantenha ao alcance da mão, sempre.',
      'Combine um ponto de encontro antes de chegar.',
      'Ensine a pedir ajuda a um adulto ou ao guarda-vidas.',
    ],
  },
  {
    title: 'Água-viva e ouriço',
    steps: [
      'Não esfregue o local com areia.',
      'Procure o posto de guarda-vidas.',
      'Em reação intensa, busque atendimento médico.',
    ],
  },
];

export function LearnPage() {
  return (
    <div>
      <PageHeader
        title="Aprender"
        subtitle="Conteúdo curto de prevenção para ler antes de ir à praia."
      />

      <section aria-labelledby="videos" className="mb-8">
        <h2 id="videos" className="mb-3 flex items-center gap-2 text-2xl font-black text-ocean-950">
          <GraduationCap aria-hidden="true" className="size-6 text-ocean-600" />
          Vídeos
        </h2>
        <p className="mb-4 text-sm text-ocean-700">
          Os players só carregam depois do seu toque, a partir do domínio
          <code className="mx-1 rounded bg-ocean-100 px-1 py-0.5 text-xs">youtube-nocookie.com</code>.
          Isso evita rastreamento antes do consentimento.
        </p>
        <ul className="grid gap-4 md:grid-cols-2">
          {educationalVideos.map((video) => (
            <li key={video.id}>
              <VideoFacade video={video} />
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="guias">
        <h2 id="guias" className="mb-3 flex items-center gap-2 text-2xl font-black text-ocean-950">
          <ShieldCheck aria-hidden="true" className="size-6 text-emerald-600" />
          Guias rápidos
        </h2>
        <div className="grid gap-4 md:grid-cols-3">
          {quickGuides.map((guide) => (
            <Card key={guide.title}>
              <h3 className="font-bold text-ocean-950">{guide.title}</h3>
              <ol className="mt-2 grid gap-1 text-sm text-ocean-700">
                {guide.steps.map((step, index) => (
                  <li key={step} className="flex gap-2">
                    <span className="font-bold text-ocean-500">{index + 1}.</span>
                    {step}
                  </li>
                ))}
              </ol>
            </Card>
          ))}
        </div>
      </section>
    </div>
  );
}
