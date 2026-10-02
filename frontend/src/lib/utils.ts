import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatDate(dateString: string): string {
  return new Date(dateString).toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export function formatDateTime(dateString: string): string {
  return new Date(dateString).toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function formatFileSize(bytes: number): string {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + " " + sizes[i];
}

export function getScoreColor(score: number): string {
  if (score >= 80) return "text-emerald-600";
  if (score >= 60) return "text-amber-600";
  return "text-red-500";
}

export function getScoreBgColor(score: number): string {
  if (score >= 80) return "bg-emerald-50";
  if (score >= 60) return "bg-amber-50";
  return "bg-red-50";
}

export function getScoreBorderColor(score: number): string {
  if (score >= 80) return "border-emerald-200";
  if (score >= 60) return "border-amber-200";
  return "border-red-200";
}

export type MatchBand = {
  label: string;
  /** Tailwind text colour. */
  text: string;
  /** Tailwind surface colour. */
  surface: string;
  /** Tailwind border colour. */
  border: string;
  /** Faint surface for icon chips. */
  chip: string;
};

/**
 * Human-readable description of a match score.
 *
 * <p>Purely a UI label: the score itself always comes straight from the backend
 * and is never rounded, clamped or re-derived here.
 */
export function getMatchBand(score: number): MatchBand {
  if (score >= 90)
    return {
      label: "Strong Match",
      text: "text-emerald-700",
      surface: "bg-emerald-50",
      border: "border-emerald-200",
      chip: "bg-emerald-100 text-emerald-700",
    };
  if (score >= 70)
    return {
      label: "Good Match",
      text: "text-blue-700",
      surface: "bg-blue-50",
      border: "border-blue-200",
      chip: "bg-blue-100 text-blue-700",
    };
  if (score >= 40)
    return {
      label: "Moderate Match",
      text: "text-amber-700",
      surface: "bg-amber-50",
      border: "border-amber-200",
      chip: "bg-amber-100 text-amber-700",
    };
  if (score >= 1)
    return {
      label: "Needs Improvement",
      text: "text-orange-700",
      surface: "bg-orange-50",
      border: "border-orange-200",
      chip: "bg-orange-100 text-orange-700",
    };
  return {
    label: "Low Match",
    text: "text-slate-600",
    surface: "bg-slate-50",
    border: "border-slate-200",
    chip: "bg-slate-100 text-slate-600",
  };
}

/**
 * Most frequent value in a list, or null when there is nothing to count.
 *
 * <p>Ties resolve to first-seen order so the result is stable between renders
 * rather than depending on Map iteration luck.
 */
export function mostFrequent(values: string[][]): string | null {
  const counts = new Map<string, number>();
  for (const list of values) {
    for (const value of list) {
      counts.set(value, (counts.get(value) ?? 0) + 1);
    }
  }
  let best: string | null = null;
  let bestCount = 0;
  for (const [value, count] of counts) {
    if (count > bestCount) {
      best = value;
      bestCount = count;
    }
  }
  return best;
}

/** Count of distinct values across a list of lists. */
export function countDistinct(values: string[][]): number {
  const seen = new Set<string>();
  for (const list of values) {
    for (const value of list) seen.add(value);
  }
  return seen.size;
}
