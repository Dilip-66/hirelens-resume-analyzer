import { AlertCircle, X } from "lucide-react";
import { cn } from "@/lib/utils";

interface ErrorAlertProps {
  message: string;
  onDismiss?: () => void;
  className?: string;
}

export default function ErrorAlert({ message, onDismiss, className }: ErrorAlertProps) {
  if (!message) return null;

  return (
    <div
      className={cn(
        "flex items-start gap-3 rounded-lg border border-destructive/20 bg-destructive/5 p-4",
        className
      )}
    >
      <AlertCircle className="h-5 w-5 text-destructive shrink-0 mt-0.5" />
      <p className="text-sm text-destructive flex-1">{message}</p>
      {onDismiss && (
        <button
          onClick={onDismiss}
          className="text-destructive/50 hover:text-destructive transition-colors"
        >
          <X className="h-4 w-4" />
        </button>
      )}
    </div>
  );
}
