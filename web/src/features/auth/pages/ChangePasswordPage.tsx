import { KeyRound } from "lucide-react";
import { ChangePasswordForm } from "@/features/auth/components/ChangePasswordForm";
import { useAuthSession } from "@/features/auth/session";

/**
 * Troca obrigatória da senha temporária (ADR-027).
 *
 * <p>Enquanto `mustChangePassword` for verdadeiro o servidor responde 403 em
 * todas as rotas de negócio; esta é a única tela liberada. Ao concluir, o
 * backend revoga as sessões e limpa o cookie — por isso a sessão local também
 * é descartada e o operador volta ao login com a senha definitiva.
 */
export function ChangePasswordPage() {
  const user = useAuthSession((state) => state.user);
  const pending = user?.mustChangePassword ?? false;

  return (
    <div className="flex min-h-dvh flex-col bg-ocean-950 text-white">
      <header className="flex items-center gap-2 px-4 py-4 sm:px-8">
        <img
          src="/brand/mark-96.png"
          alt=""
          aria-hidden="true"
          className="size-9 rounded-xl"
        />
        <span className="text-base font-black tracking-tight">MarAberto</span>
      </header>

      <main className="flex flex-1 items-center justify-center px-4 pb-10">
        <section className="w-full max-w-md rounded-2xl border border-ocean-900/10 bg-white p-5 text-ocean-950 shadow-2xl sm:p-6">
          <div className="flex items-center gap-2.5 border-b border-ocean-100 pb-3">
            <span className="grid size-10 place-items-center rounded-xl bg-ocean-950 text-white">
              <KeyRound aria-hidden="true" className="size-5" />
            </span>
            <div>
              <h1 className="text-base font-black">
                {pending ? "Defina sua senha" : "Trocar senha"}
              </h1>
              <p className="text-[11px] text-ocean-600">
                {pending
                  ? "Sua senha temporária expira em 24 h e precisa ser trocada antes de continuar."
                  : "A troca encerra as sessões abertas e exige novo login."}
              </p>
            </div>
          </div>

          <div className="mt-4">
            <ChangePasswordForm pending={pending} idPrefix="change-password" />
          </div>
        </section>
      </main>
    </div>
  );
}
