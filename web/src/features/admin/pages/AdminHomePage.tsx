import { Link } from "react-router-dom";
import {
  ArrowRight,
  Fingerprint,
  MapPinned,
  ScrollText,
  ShieldCheck,
  UserCog,
  Users,
} from "lucide-react";
import { PageHeader } from "@/shared/components/Card";
import { useAdminUsers } from "@/features/admin/hooks/use-admin-users";
import { useAdminStations } from "@/features/stations";

/**
 * Visão da TI: o que ela administra na instalação. Sem roadmap, status de
 * integração ou planejamento de desenvolvimento — pendência de produto fica em
 * `docs/`/issue, não na tela do órgão (D-11).
 */
export function AdminHomePage() {
  const { total: usersTotal, status: usersStatus } = useAdminUsers();
  const { total, status } = useAdminStations({ active: true, limit: 1 });

  return (
    <div>
      <PageHeader
        title="Visão geral"
        subtitle="Administração da instalação: usuários, papéis e pontos operacionais."
      />

      <dl className="mb-4 grid gap-2 sm:grid-cols-3">
        <StatCard
          icon={Users}
          label="Usuários cadastrados"
          value={usersStatus === "ready" ? usersTotal : "—"}
          hint={
            usersStatus === "ready"
              ? "ativos e desativados"
              : "Carregando os usuários"
          }
        />
        <StatCard
          icon={ShieldCheck}
          label="Postos e bases ativos"
          value={status === "ready" ? total : "—"}
          hint={
            status === "ready"
              ? "ativos no mapa público"
              : "Carregando as estruturas"
          }
        />
        <StatCard
          icon={UserCog}
          label="Perfis"
          value={3}
          hint="operador · coordenação · TI"
        />
      </dl>

      <div className="grid gap-3 md:grid-cols-2">
        <section className="rounded-2xl border border-ocean-900/10 bg-white p-4 shadow-sm">
          <h2 className="flex items-center gap-1.5 text-sm font-bold text-ocean-950">
            <Fingerprint aria-hidden="true" className="size-4 text-ocean-600" />
            Poder administrativo
          </h2>
          <ul className="mt-2 grid gap-1.5 text-xs leading-relaxed text-ocean-700">
            <li>Cadastrar e convidar usuários, definindo o papel inicial.</li>
            <li>Ativar, desativar e corrigir o papel de qualquer usuário.</li>
            <li>
              Manter postos fixos e bases móveis, tirando do mapa público a
              estrutura inoperante sem perder o histórico.
            </li>
            <li>
              Toda ação administrativa é auditada; a exclusão de usuário
              preserva o histórico das ocorrências.
            </li>
          </ul>

          <div className="mt-3 flex flex-wrap gap-2">
            <Link
              to="/admin/usuarios"
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl bg-ocean-950 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-ocean-900"
            >
              <Users aria-hidden="true" className="size-3.5" />
              Gerenciar usuários
              <ArrowRight aria-hidden="true" className="size-3.5" />
            </Link>
            <Link
              to="/admin/postos"
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-900/15 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50"
            >
              <MapPinned aria-hidden="true" className="size-3.5" />
              Postos e bases
            </Link>
            <Link
              to="/admin/auditoria"
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-900/15 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50"
            >
              <ScrollText aria-hidden="true" className="size-3.5" />
              Auditoria
            </Link>
          </div>
        </section>

        <section className="rounded-2xl border border-ocean-900/10 bg-white p-4 shadow-sm">
          <h2 className="flex items-center gap-1.5 text-sm font-bold text-ocean-950">
            <ShieldCheck aria-hidden="true" className="size-4 text-ocean-600" />
            Limites da área
          </h2>
          <ul className="mt-2 grid gap-1.5 text-xs leading-relaxed text-ocean-700">
            <li>
              A coordenação ativa/desativa guarda-vidas do escopo; o cadastro
              de usuário é da TI.
            </li>
            <li>
              A TI não registra ocorrência nem altera atendimento em nome do
              operador; pode consultar e auditar.
            </li>
            <li>
              Dados de vítima, localização precisa e posição de operador não
              aparecem nesta visão.
            </li>
          </ul>
        </section>
      </div>
    </div>
  );
}

interface StatCardProps {
  readonly icon: typeof Users;
  readonly label: string;
  readonly value: number | string;
  readonly hint: string;
}

function StatCard({ icon: Icon, label, value, hint }: StatCardProps) {
  return (
    <div className="rounded-2xl border border-ocean-900/10 bg-white p-3.5 shadow-sm">
      <dt className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-ocean-500">
        <Icon aria-hidden="true" className="size-3.5" />
        {label}
      </dt>
      <dd className="mt-1 text-2xl font-black text-ocean-950">{value}</dd>
      <dd className="text-[11px] text-ocean-500">{hint}</dd>
    </div>
  );
}
