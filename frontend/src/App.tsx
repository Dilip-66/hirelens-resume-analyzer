import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "@/contexts/AuthContext";
import { useAuth } from "@/hooks/useAuth";
import Navbar from "@/components/layout/Navbar";
import Sidebar from "@/components/layout/Sidebar";
import Home from "@/pages/Home";
import Login from "@/pages/Login";
import Signup from "@/pages/Signup";
import Dashboard from "@/pages/Dashboard";
import { AiAssistantProvider } from "@/contexts/AiAssistantContext";
import { AnalysesProvider } from "@/contexts/AnalysesContext";
import { useAnalyses } from "@/hooks/useAnalyses";
import { prioritizedQuestions } from "@/lib/aiSuggestions";
import Upload from "@/pages/Upload";
import Analysis from "@/pages/Analysis";
import Insights from "@/pages/Insights";
import Skills from "@/pages/Skills";
import ATS from "@/pages/ATS";
import JobMatch from "@/pages/JobMatch";
import Recommendations from "@/pages/Recommendations";
import History from "@/pages/History";
import Settings from "@/pages/Settings";
import { useMemo, type ReactNode } from "react";

function LandingLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen">
      <Navbar />
      {children}
    </div>
  );
}

function DashboardShell({ children }: { children: ReactNode }) {
  return (
    // data-app-shell / data-app-main let the print stylesheet relax the
    // fixed-height scroll container when a report is printed.
    <div data-app-shell className="flex h-screen overflow-hidden">
      <Sidebar />
      <main data-app-main className="flex-1 overflow-y-auto pb-20 lg:pb-0">
        <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
          {children}
        </div>
      </main>
    </div>
  );
}

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, loading } = useAuth();
  if (loading) return null;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  // Mounted once around the whole app shell, so the dashboard, the analysis
  // report and the header can all launch the same assistant conversation.
  return (
    <AiAssistantBridge>
      <DashboardShell>{children}</DashboardShell>
    </AiAssistantBridge>
  );
}

/**
 * Supplies the assistant with the user's most recent analysis so it is grounded
 * by default, and derives the questions worth suggesting for that analysis.
 */
function AiAssistantBridge({ children }: { children: ReactNode }) {
  return (
    <AnalysesProvider>
      <GroundedAssistant>{children}</GroundedAssistant>
    </AnalysesProvider>
  );
}

function GroundedAssistant({ children }: { children: ReactNode }) {
  const { analyses, loading } = useAnalyses();
  const latest = analyses[0] ?? null;

  const prioritized = useMemo(() => prioritizedQuestions(latest), [latest]);

  // The provider must render even while analyses are loading: the shell (and
  // its Sidebar) consume the assistant context, so returning bare children
  // during the load would crash the whole app on first paint.
  return (
    <AiAssistantProvider
      latestAnalysis={
        loading || !latest
          ? null
          : { id: latest.id, candidateName: latest.candidateName, jobTitle: latest.jobTitle }
      }
      prioritizedQuestions={prioritized}
    >
      {children}
    </AiAssistantProvider>
  );
}

function PublicRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, loading } = useAuth();
  if (loading) return null;
  if (isAuthenticated) return <Navigate to="/upload" replace />;
  return <>{children}</>;
}

function AppRoutes() {
  const { loading } = useAuth();
  if (loading) return null;

  return (
    <Routes>
      <Route path="/" element={<LandingLayout><Home /></LandingLayout>} />
      <Route path="/login" element={<PublicRoute><Login /></PublicRoute>} />
      <Route path="/signup" element={<PublicRoute><Signup /></PublicRoute>} />

      <Route path="/upload" element={<ProtectedRoute><Upload /></ProtectedRoute>} />
      <Route path="/dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
      <Route path="/history" element={<ProtectedRoute><History /></ProtectedRoute>} />
      <Route path="/settings" element={<ProtectedRoute><Settings /></ProtectedRoute>} />
      <Route path="/analysis/:id" element={<ProtectedRoute><Analysis /></ProtectedRoute>} />
      <Route path="/analysis/:id/overview" element={<ProtectedRoute><Analysis /></ProtectedRoute>} />
      <Route path="/analysis/:id/insights" element={<ProtectedRoute><Insights /></ProtectedRoute>} />
      <Route path="/analysis/:id/skills" element={<ProtectedRoute><Skills /></ProtectedRoute>} />
      <Route path="/analysis/:id/ats" element={<ProtectedRoute><ATS /></ProtectedRoute>} />
      <Route path="/analysis/:id/job-match" element={<ProtectedRoute><JobMatch /></ProtectedRoute>} />
      <Route path="/analysis/:id/recommendations" element={<ProtectedRoute><Recommendations /></ProtectedRoute>} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
