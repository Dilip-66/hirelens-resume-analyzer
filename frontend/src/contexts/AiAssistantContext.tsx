import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import QuestionPicker from "@/components/ai-assistance/QuestionPicker";
import AiChatDrawer from "@/components/ai-assistance/AiChatDrawer";

/**
 * Owns the assistant's open/close state and the analysis it is anchored to.
 *
 * <p>Exists so the dashboard card, the post-analysis prompt and the sidebar
 * button can all launch the same assistant without each one re-implementing the
 * picker -> chat handoff. The transcript itself is owned by the drawer, which
 * is the only component that renders it.
 */
export interface AiAssistantContextValue {
  openPicker: (options?: OpenOptions) => void;
  openChat: (question: string, options?: OpenOptions) => void;
  closeAll: () => void;
  /** The analysis the assistant is currently grounded in, if any. */
  activeAnalysisId: string | null;
}

export interface OpenOptions {
  analysisId?: string | null;
  candidateName?: string | null;
  jobTitle?: string | null;
  /** First question, when opened straight from a suggested-question click. */
  question?: string;
}

const AiAssistantContext = createContext<AiAssistantContextValue | null>(null);

type View = "closed" | "picker" | "chat";

export function AiAssistantProvider({
  children,
  /** Analysis to pre-select in the picker; defaults to the user's latest. */
  latestAnalysis,
  prioritizedQuestions = [],
}: {
  children: ReactNode;
  latestAnalysis?: {
    id: string;
    candidateName?: string | null;
    jobTitle?: string | null;
  } | null;
  prioritizedQuestions?: string[];
}) {
  const [view, setView] = useState<View>("closed");
  const [target, setTarget] = useState<OpenOptions | null>(null);
  const [pendingQuestion, setPendingQuestion] = useState<string | null>(null);

  const effective = useMemo<OpenOptions>(
    () => ({
      analysisId: target?.analysisId ?? latestAnalysis?.id ?? null,
      candidateName: target?.candidateName ?? latestAnalysis?.candidateName ?? null,
      jobTitle: target?.jobTitle ?? latestAnalysis?.jobTitle ?? null,
    }),
    [target, latestAnalysis]
  );

  /**
 * Choosing with an explicit analysis overrides the default; choosing with none
 * clears any previous override and falls back to the latest report.
 *
 * <p>Clearing matters: the dashboard card and the sidebar open the assistant
 * with no arguments, and without this a report the user looked at earlier kept
 * the assistant anchored to it - so after running a new analysis the assistant
 * would still answer from the old one.
 */
const openPicker = useCallback((options?: OpenOptions) => {
    setTarget(options ?? null);
    setView("picker");
  }, []);

  const openChat = useCallback((question: string, options?: OpenOptions) => {
    setTarget(options ?? null);
    setView("chat");
    setPendingQuestion(question);
  }, []);

  /**
   * Opens the chat with a question chosen in the picker.
   *
   * <p>The transcript is not touched: an ongoing conversation survives a trip
   * through the picker. The drawer appends the user turn itself when it sends,
   * so nothing is pre-seeded here - doing both left questions sitting in the
   * transcript that were never actually asked.
   */
  const openChatWithQuestion = useCallback((question: string) => {
    setView("chat");
    setPendingQuestion(question);
  }, []);

  const closeAll = useCallback(() => {
    setView("closed");
    setPendingQuestion(null);
    // Released so the next open resolves against the current latest report
    // rather than whichever one this conversation happened to be about.
    setTarget(null);
  }, []);

  const value = useMemo<AiAssistantContextValue>(
    () => ({
      openPicker,
      openChat,
      closeAll,
      activeAnalysisId: effective.analysisId ?? null,
    }),
    [openPicker, openChat, closeAll, effective.analysisId]
  );

  return (
    <AiAssistantContext.Provider value={value}>
      {children}
      {/* Mounted once, here, so any surface can drive the assistant. */}
      <AssistantSurfaces
        view={view}
        pendingQuestion={pendingQuestion}
        clearPending={() => setPendingQuestion(null)}
        options={effective}
        prioritizedQuestions={prioritizedQuestions}
        onOpenChatWithQuestion={openChatWithQuestion}
        onBrowseQuestions={() => setView("picker")}
        onClose={closeAll}
      />
    </AiAssistantContext.Provider>
  );
}

function AssistantSurfaces({
  view,
  pendingQuestion,
  clearPending,
  options,
  prioritizedQuestions,
  onOpenChatWithQuestion,
  onBrowseQuestions,
  onClose,
}: {
  view: View;
  pendingQuestion: string | null;
  clearPending: () => void;
  options: OpenOptions;
  prioritizedQuestions: string[];
  onOpenChatWithQuestion: (question: string) => void;
  onBrowseQuestions: () => void;
  onClose: () => void;
}) {
  return (
    <>
      <QuestionPicker
        open={view === "picker"}
        prioritizedQuestions={prioritizedQuestions}
        onClose={onClose}
        onSelect={(question: string) => onOpenChatWithQuestion(question)}
      />
      <AiChatDrawer
        open={view === "chat"}
        analysisId={options.analysisId ?? null}
        candidateName={options.candidateName}
        jobTitle={options.jobTitle}
        seedQuestion={pendingQuestion}
        onSeedConsumed={clearPending}
        onBrowseQuestions={onBrowseQuestions}
        onClose={onClose}
      />
    </>
  );
}

export function useAiAssistant(): AiAssistantContextValue {
  const ctx = useContext(AiAssistantContext);
  if (!ctx) {
    throw new Error("useAiAssistant must be used within an AiAssistantProvider");
  }
  return ctx;
}
