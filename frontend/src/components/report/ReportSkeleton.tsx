import { cn } from "@/lib/utils";

function Block({ className }: { className?: string }) {
  return <div className={cn("rounded-lg bg-slate-200/70", className)} />;
}

/**
 * Placeholder for the report while the stored analysis loads.
 *
 * <p>Shaped like the finished page so nothing jumps when the data arrives. The
 * score ring is left hollow rather than faked with a random value.
 */
export default function ReportSkeleton() {
  return (
    <div className="animate-pulse" aria-busy="true" aria-live="polite">
      <span className="sr-only">Loading your analysis report</span>

      <div>
        <Block className="h-3 w-32" />
        <Block className="mt-6 h-5 w-40" />
        <Block className="mt-4 h-9 w-72 max-w-full" />
        <Block className="mt-3 h-4 w-96 max-w-full" />
      </div>

      <div className="mt-10 grid gap-10 rounded-2xl border border-slate-200 bg-white p-7 shadow-sm sm:p-10 md:grid-cols-[auto_minmax(0,1fr)] md:gap-14">
        <div className="flex flex-col items-center gap-6 md:items-start">
          <div className="flex h-44 w-44 items-center justify-center rounded-full border-[7px] border-slate-100" />
          <Block className="h-7 w-28" />
        </div>
        <div className="space-y-4">
          <Block className="h-4 w-48" />
          <Block className="h-4 w-full" />
          <Block className="h-4 w-11/12" />
          <Block className="h-4 w-4/5" />
          <div className="grid grid-cols-2 gap-4 pt-4 sm:grid-cols-4">
            {[0, 1, 2, 3].map((i) => (
              <Block key={i} className="h-14" />
            ))}
          </div>
        </div>
      </div>

      <div className="mt-16 space-y-10">
        <div className="max-w-2xl space-y-3">
          <Block className="h-5 w-56" />
          <Block className="h-3.5 w-full" />
          <Block className="h-2.5 w-full" />
        </div>
        <div className="space-y-3">
          <Block className="h-5 w-64" />
          <Block className="h-3.5 w-80 max-w-full" />
          <div className="flex gap-2 pt-2">
            {["w-24", "w-28", "w-20", "w-32", "w-24"].map((w) => (
              <Block key={w} className={`h-7 ${w}`} />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
