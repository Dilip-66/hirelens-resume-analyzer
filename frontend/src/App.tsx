import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "@/contexts/AuthContext";
import Navbar from "@/components/layout/Navbar";
import Sidebar from "@/components/layout/Sidebar";
import Home from "@/pages/Home";
import Login from "@/pages/Login";
import Signup from "@/pages/Signup";
import Dashboard from "@/pages/Dashboard";
import Upload from "@/pages/Upload";
import Analysis from "@/pages/Analysis";
import Insights from "@/pages/Insights";
import Skills from "@/pages/Skills";
import ATS from "@/pages/ATS";
import JobMatch from "@/pages/JobMatch";
import Recommendations from "@/pages/Recommendations";
import History from "@/pages/History";
import Settings from "@/pages/Settings";
import type { ReactNode } from "react";

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
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      <main className="flex-1 overflow-y-auto pb-20 lg:pb-0">
        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8 py-8">
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
  return <DashboardShell>{children}</DashboardShell>;
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
