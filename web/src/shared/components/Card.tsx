import type { ReactNode } from "react";

export function Card({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <section
      className={`rounded-2xl border border-ocean-900/10 bg-white p-4 shadow-sm ${className}`}
    >
      {children}
    </section>
  );
}

export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-4 flex flex-wrap items-start justify-between gap-2">
      <div>
        <h1 className="text-xl font-black tracking-tight text-ocean-950">
          {title}
        </h1>
        {subtitle ? (
          <p className="mt-1 text-xs text-ocean-700">{subtitle}</p>
        ) : null}
      </div>
      {actions}
    </div>
  );
}
