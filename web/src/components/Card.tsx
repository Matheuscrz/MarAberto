import type { ReactNode } from 'react';

export function Card({
  children,
  className = '',
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <section
      className={`rounded-2xl border border-ocean-900/10 bg-white p-5 shadow-sm ${className}`}
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
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-3xl font-black tracking-tight text-ocean-950 md:text-4xl">{title}</h1>
        {subtitle ? <p className="mt-2 max-w-2xl text-ocean-700">{subtitle}</p> : null}
      </div>
      {actions}
    </div>
  );
}
