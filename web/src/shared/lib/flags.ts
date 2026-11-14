import type { FlagColor, RiskLevel } from "@/shared/types/flags";

/** Cores de bandeira (padrão brasileiro). Usadas em badges e marcadores. */
export const FLAG_COLORS: Record<FlagColor, string> = {
  GREEN: "#10b981",
  YELLOW: "#f59e0b",
  RED: "#ef4444",
  PURPLE: "#8b5cf6",
  BLACK: "#111827",
};

export const RISK_ZONE_COLORS: Record<RiskLevel, string> = {
  LOW: "#10b981",
  MODERATE: "#f59e0b",
  HIGH: "#ef4444",
  EXTREME: "#0f172a",
};

export const FLAG_LABELS: Record<FlagColor, string> = {
  GREEN: "Bandeira verde",
  YELLOW: "Bandeira amarela",
  RED: "Bandeira vermelha",
  PURPLE: "Bandeira roxa",
  BLACK: "Bandeira preta",
};
