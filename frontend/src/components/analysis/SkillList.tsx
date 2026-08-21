import SkillTag from "./SkillTag";

interface SkillListProps {
  skills: string[];
  variant?: "matched" | "missing" | "neutral";
  emptyMessage?: string;
}

export default function SkillList({
  skills,
  variant = "neutral",
  emptyMessage = "No skills found",
}: SkillListProps) {
  if (skills.length === 0) {
    return (
      <p className="text-sm text-muted-foreground italic">{emptyMessage}</p>
    );
  }

  return (
    <div className="flex flex-wrap gap-2">
      {skills.map((skill) => (
        <SkillTag key={skill} skill={skill} variant={variant} />
      ))}
    </div>
  );
}
