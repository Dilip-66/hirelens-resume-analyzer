import { Link } from "react-router-dom";
import {
  Brain,
  FileSearch,
  Target,
  Sparkles,
  ArrowRight,
  CheckCircle,
  Upload,
  Zap,
  BarChart3,
  Shield,
} from "lucide-react";

const features = [
  {
    icon: Brain,
    title: "AI Resume Analysis",
    description:
      "Advanced AI analyzes every aspect of your resume, from content quality to formatting, providing deep insights.",
  },
  {
    icon: Sparkles,
    title: "RAG-Powered Insights",
    description:
      "Retrieval-Augmented Generation retrieves relevant information and generates contextual, evidence-based recommendations.",
  },
  {
    icon: FileSearch,
    title: "ATS Compatibility",
    description:
      "Identify problems that may reduce your resume's performance with Applicant Tracking Systems before you apply.",
  },
  {
    icon: Target,
    title: "Skill Gap Detection",
    description:
      "Find missing or weak skills that could make the difference between landing an interview or getting filtered out.",
  },
  {
    icon: BarChart3,
    title: "Job Matching",
    description:
      "Compare your resume against specific job requirements to see exactly how well you match and where to improve.",
  },
  {
    icon: Shield,
    title: "Personalized Recommendations",
    description:
      "Get actionable, prioritized improvements tailored to your specific resume and target role.",
  },
];

const steps = [
  {
    number: "01",
    title: "Upload",
    description: "Upload your resume in PDF or DOCX format. Optionally add a target job description.",
    icon: Upload,
  },
  {
    number: "02",
    title: "Extract",
    description: "HireLens extracts and processes your resume content, chunking it intelligently by section.",
    icon: Zap,
  },
  {
    number: "03",
    title: "Analyze",
    description: "The RAG pipeline retrieves relevant passages and generates a comprehensive match analysis.",
    icon: FileSearch,
  },
  {
    number: "04",
    title: "Improve",
    description: "Get personalized recommendations, skill gap analysis, and ATS compatibility insights.",
    icon: Target,
  },
];

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-white">
      <header className="sticky top-0 z-50 border-b bg-white/80 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <Link to="/" className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary">
              <span className="text-sm font-bold text-white">H</span>
            </div>
            <span className="font-display text-xl font-bold text-foreground">HireLens</span>
          </Link>
          <div className="hidden md:flex items-center gap-1">
            <a href="#features" className="rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground hover:text-foreground hover:bg-accent transition-colors">Features</a>
            <a href="#how-it-works" className="rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground hover:text-foreground hover:bg-accent transition-colors">How It Works</a>
          </div>
          <div className="flex items-center gap-3">
            <Link to="/login" className="text-sm font-medium text-muted-foreground hover:text-foreground transition-colors px-3 py-2">
              Sign In
            </Link>
            <Link to="/signup" className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition-all hover:bg-primary/90 shadow-sm">
              Get Started Free
            </Link>
          </div>
        </div>
      </header>

      <section className="gradient-hero relative overflow-hidden">
        <div className="absolute inset-0 opacity-30">
          <div className="absolute top-20 left-10 w-72 h-72 bg-primary/10 rounded-full blur-3xl" />
          <div className="absolute bottom-10 right-10 w-96 h-96 bg-purple-400/10 rounded-full blur-3xl" />
        </div>
        <div className="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 pt-20 pb-20 lg:pt-28 lg:pb-28">
          <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-16">
            <div className="flex-1 max-w-2xl">
              <div className="inline-flex items-center gap-2 rounded-full bg-primary/10 border border-primary/20 px-4 py-1.5 mb-6">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary opacity-75" />
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-primary" />
                </span>
                <span className="text-sm text-primary font-medium">AI-Powered Resume Intelligence</span>
              </div>

              <h1 className="font-display text-4xl sm:text-5xl lg:text-6xl font-bold text-foreground leading-tight">
                Your Resume Deserves{" "}
                <span className="text-gradient">More Than a Glance</span>
              </h1>

              <p className="mt-6 text-lg text-muted-foreground leading-relaxed max-w-xl">
                Every great career starts with a resume that tells the right story.
                HireLens uses AI to make sure yours speaks volumes — to recruiters, to ATS systems,
                and to the future you deserve.
              </p>

              <div className="mt-8 flex flex-col sm:flex-row gap-4">
                <Link
                  to="/signup"
                  className="inline-flex items-center justify-center gap-2 rounded-xl bg-primary px-6 py-3.5 text-base font-semibold text-white transition-all hover:bg-primary/90 shadow-lg shadow-primary/20"
                >
                  Start Your Journey
                  <ArrowRight className="h-4 w-4" />
                </Link>
                <a
                  href="#how-it-works"
                  className="inline-flex items-center justify-center gap-2 rounded-xl border border-border px-6 py-3.5 text-base font-medium text-foreground transition-all hover:bg-accent"
                >
                  See How It Works
                </a>
              </div>
            </div>

            <div className="hidden lg:block flex-1 max-w-md">
              <div className="relative rounded-2xl border border-border bg-white shadow-xl p-6">
                <div className="space-y-4">
                  <div className="flex items-center gap-3 rounded-lg bg-slate-50 border border-border p-4">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-100">
                      <CheckCircle className="h-5 w-5 text-emerald-600" />
                    </div>
                    <div>
                      <p className="text-sm font-medium text-foreground">Resume uploaded</p>
                      <p className="text-xs text-muted-foreground">Processing complete</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3 rounded-lg bg-slate-50 border border-border p-4">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                      <Brain className="h-5 w-5 text-primary animate-pulse" />
                    </div>
                    <div>
                      <p className="text-sm font-medium text-foreground">RAG Analysis</p>
                      <p className="text-xs text-muted-foreground">Running...</p>
                    </div>
                  </div>
                  <div className="rounded-lg bg-slate-50 border border-border p-4">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm font-medium text-foreground">Match Score</span>
                      <span className="text-sm font-bold text-primary">87%</span>
                    </div>
                    <div className="h-2 rounded-full bg-slate-200">
                      <div className="h-2 rounded-full bg-gradient-to-r from-primary to-emerald-500" style={{ width: "87%" }} />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="features" className="py-20 bg-white">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-16">
            <h2 className="font-display text-3xl sm:text-4xl font-bold text-foreground">
              Everything you need to{" "}
              <span className="text-gradient">land your dream role</span>
            </h2>
            <p className="mt-4 text-lg text-muted-foreground">
              A comprehensive suite of AI-powered tools to make your resume stand out.
            </p>
          </div>

          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {features.map((feature, i) => {
              const Icon = feature.icon;
              return (
                <div
                  key={feature.title}
                  className="group rounded-xl border bg-white p-6 shadow-sm transition-all hover:shadow-lg hover:border-primary/20 animate-fade-in"
                  style={{ animationDelay: `${i * 0.1}s`, opacity: 0, animationFillMode: "forwards" }}
                >
                  <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 mb-4 group-hover:bg-primary/20 transition-colors">
                    <Icon className="h-6 w-6 text-primary" />
                  </div>
                  <h3 className="font-display text-lg font-semibold text-foreground mb-2">
                    {feature.title}
                  </h3>
                  <p className="text-sm text-muted-foreground leading-relaxed">
                    {feature.description}
                  </p>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      <section id="how-it-works" className="py-20 bg-slate-50">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-16">
            <h2 className="font-display text-3xl sm:text-4xl font-bold text-foreground">
              How it works
            </h2>
            <p className="mt-4 text-lg text-muted-foreground">
              A transparent, auditable pipeline — not a black box.
            </p>
          </div>

          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-8">
            {steps.map((step, i) => {
              const Icon = step.icon;
              return (
                <div
                  key={step.number}
                  className="relative animate-fade-in"
                  style={{ animationDelay: `${i * 0.15}s`, opacity: 0, animationFillMode: "forwards" }}
                >
                  {i < steps.length - 1 && (
                    <div className="hidden lg:block absolute top-10 left-[calc(50%+40px)] w-[calc(100%-40px)] h-px bg-gradient-to-r from-primary/30 to-slate-200" />
                  )}
                  <div className="relative flex flex-col items-center text-center">
                    <div className="relative z-10 flex h-20 w-20 items-center justify-center rounded-2xl bg-white border-2 border-primary/20 shadow-sm mb-4">
                      <Icon className="h-8 w-8 text-primary" />
                    </div>
                    <span className="font-mono text-xs text-primary font-medium mb-1">
                      Step {step.number}
                    </span>
                    <h3 className="font-display text-lg font-semibold text-foreground mb-2">
                      {step.title}
                    </h3>
                    <p className="text-sm text-muted-foreground leading-relaxed max-w-[200px]">
                      {step.description}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      <section className="py-20 gradient-hero">
        <div className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8 text-center">
          <h2 className="font-display text-3xl sm:text-4xl font-bold text-foreground mb-4">
            Ready to transform your career?
          </h2>
          <p className="text-lg text-muted-foreground mb-8 max-w-xl mx-auto">
            Get AI-powered insights that help you land more interviews. Start analyzing your resume now.
          </p>
          <Link
            to="/signup"
            className="inline-flex items-center gap-2 rounded-xl bg-primary px-8 py-4 text-base font-semibold text-white transition-all hover:bg-primary/90 shadow-lg shadow-primary/20"
          >
            Get Started Free
            <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </section>

      <footer className="border-t bg-white py-8">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <div className="flex h-6 w-6 items-center justify-center rounded-md bg-primary">
              <span className="text-xs font-bold text-white">H</span>
            </div>
            <span className="font-display text-sm font-semibold">HireLens</span>
          </div>
          <p className="text-sm text-muted-foreground">
            AI-powered resume intelligence for smarter career decisions.
          </p>
        </div>
      </footer>
    </div>
  );
}
