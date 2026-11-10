import { Info } from 'lucide-react';
import { DEMO_DATA_NOTICE } from "@/shared/lib/notices";

export function DemoNotice() {
  return (
    <p className="mb-5 flex items-start gap-2 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-950">
      <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      {DEMO_DATA_NOTICE}
    </p>
  );
}
