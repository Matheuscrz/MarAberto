import { ClipboardList, FileCheck2, Filter } from "lucide-react";
import { PageHeader } from "@/shared/components/Card";

/**
 * Visão da coordenação sobre as ocorrências (ADR-013): revisar o que foi
 * registrado pelos guarda-vidas e acompanhar o status no CIOSP. A lista real
 * chega junto com o módulo `occurrences`; até lá, esta tela documenta o fluxo.
 */
export function ManagementOccurrencesPage() {
  return (
    <div>
      <PageHeader
        title="Ocorrências da operação"
        subtitle="Acompanhe os atendimentos registrados pela equipe e o andamento no CIOSP."
      />

      <div className="grid gap-3 md:grid-cols-3">
        <FeatureCard
          icon={ClipboardList}
          title="Revisão do registro"
          description="Cada ocorrência traz origem, horário, praia/posto, desfecho e os dados de pessoas quando houver — sempre mascarados na listagem."
        />
        <FeatureCard
          icon={FileCheck2}
          title="Status no CIOSP"
          description="Pendente, registrado ou não aplicável. A ficha de transcrição é copiada manualmente, sem integração automática (ADR-007)."
        />
        <FeatureCard
          icon={Filter}
          title="Filtros por escopo"
          description="A coordenação vê apenas as praias e postos do seu escopo; a autorização é aplicada pelo servidor, não pela tela."
        />
      </div>

      <p className="mt-4 rounded-xl border border-dashed border-ocean-300 bg-white p-4 text-center text-xs font-semibold text-ocean-600">
        Nenhuma ocorrência para revisar. A lista será preenchida quando o módulo
        de ocorrências entrar no ar.
      </p>
    </div>
  );
}

interface FeatureCardProps {
  readonly icon: typeof ClipboardList;
  readonly title: string;
  readonly description: string;
}

function FeatureCard({ icon: Icon, title, description }: FeatureCardProps) {
  return (
    <section className="rounded-2xl border border-ocean-900/10 bg-white p-4 shadow-sm">
      <h2 className="flex items-center gap-1.5 text-sm font-bold text-ocean-950">
        <Icon aria-hidden="true" className="size-4 text-ocean-600" />
        {title}
      </h2>
      <p className="mt-1.5 text-xs leading-relaxed text-ocean-700">
        {description}
      </p>
    </section>
  );
}
