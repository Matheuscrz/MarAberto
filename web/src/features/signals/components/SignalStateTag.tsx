import type { SignalState } from "@/features/signals/types";

const STATE_STYLE: Record<SignalState, string> = {
  CURRENT: "bg-emerald-100 text-emerald-950",
  EXPIRED: "bg-amber-100 text-amber-950",
  CLOSED: "bg-ocean-100 text-ocean-800",
};

const SIGNAL_STATE_LABEL: Record<SignalState, string> = {
  CURRENT: "Sinalização vigente",
  EXPIRED: "Sinalização desatualizada",
  CLOSED: "Sinalização encerrada",
};

/** Selo de estado da sinalização: vigente, vencida ou encerrada. */
export function SignalStateTag({
  state,
  compact = false,
}: {
  readonly state: SignalState;
  readonly compact?: boolean;
}) {
  return (
    <span
      className={[
        "inline-flex items-center rounded-full font-bold",
        compact ? "px-2 py-0.5 text-[10px]" : "px-2.5 py-1 text-[11px]",
        STATE_STYLE[state],
      ].join(" ")}
    >
      {SIGNAL_STATE_LABEL[state]}
    </span>
  );
}
