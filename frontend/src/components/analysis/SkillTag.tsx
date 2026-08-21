import { cn } from "@/lib/utils";

interface SkillTagProps {
  skill: string;
  variant?: "matched" | "missing" | "neutral";
  size?: "sm" | "md";
}

const variantStyles = {
  matched: "bg-emerald-50 text-emerald-700 border-emerald-200",
  missing: "bg-slate-50 text-slate-600 border-slate-200",
  neutral: "bg-primary/5 text-primary border-primary/20",
};

export default function SkillTag({ skill, variant = "neutral", size = "md" }: SkillTagProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border font-medium",
        variantStyles[variant],
        size === "sm" ? "px-2 py-0.5 text-xs" : "px-3 py-1 text-sm"
      )}
    >
      {skill}
    </span>
  );
}
