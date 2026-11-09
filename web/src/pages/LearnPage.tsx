import { GraduationCap, ShieldCheck } from "lucide-react";
import { Card, PageHeader } from "../components/Card";
import { VideoFacade } from "../components/VideoFacade";
import { educationalVideos } from "../data/demo-data";

const quickGuides = [
  {
    title: "Se for pego por uma corrente",
    steps: [
      "Não lute contra a corrente.",
      "Nade paralelo à praia até sair do fluxo.",
      "Se cansar, flutue de costas e peça ajuda.",
    ],
  },
  {
    title: "Criança na praia",
    steps: [
      "Mantenha ao alcance da mão, sempre.",
      "Combine um ponto de encontro antes de chegar.",
      "Ensine a pedir ajuda a um adulto ou ao guarda-vidas.",
    ],
  },
  {
    title: "Água-viva e ouriço",
    steps: [
      "Não esfregue o local com areia.",
      "Procure o posto de guarda-vidas.",
      "Em reação intensa, busque atendimento médico.",
    ],
  },
];

export function LearnPage() {
  return (
    <div className="grid gap-4">
      <PageHeader
        title="Aprender"
        subtitle="Conteúdo rápido de prevenção para ler antes de entrar no mar."
      />

      <section aria-labelledby="guias" className="grid gap-2.5">
        <h2
          id="guias"
          className="flex items-center gap-1.5 text-sm font-black text-ocean-950"
        >
          <ShieldCheck aria-hidden="true" className="size-4 text-emerald-600" />
          Guias rápidos de sobrevivência
        </h2>
        <div className="grid gap-2.5">
          {quickGuides.map((guide) => (
            <Card key={guide.title}>
              <h3 className="text-sm font-bold text-ocean-950">
                {guide.title}
              </h3>
              <ol className="mt-1.5 grid gap-1 text-xs text-ocean-700">
                {guide.steps.map((step, index) => (
                  <li key={step} className="flex gap-1.5">
                    <span className="font-bold text-ocean-500">
                      {index + 1}.
                    </span>
                    {step}
                  </li>
                ))}
              </ol>
            </Card>
          ))}
        </div>
      </section>

      {educationalVideos.length > 0 ? (
        <section aria-labelledby="videos" className="grid gap-2.5">
          <h2
            id="videos"
            className="flex items-center gap-1.5 text-sm font-black text-ocean-950"
          >
            <GraduationCap
              aria-hidden="true"
              className="size-4 text-ocean-600"
            />
            Vídeos educativos
          </h2>
          <ul className="grid gap-3">
            {educationalVideos.map((video) => (
              <li key={video.id}>
                <VideoFacade video={video} />
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}
