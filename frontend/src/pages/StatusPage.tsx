import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { checkBackendHealth, checkMlHealth } from '../api/health';

type ServiceStatus = 'checking' | 'up' | 'down';

interface ServiceInfo {
  name: string;
  url: string;
  status: ServiceStatus;
  version?: string;
  error?: string;
}

function StatusBadge({ status }: { status: ServiceStatus }) {
  const classes = {
    up: 'status-up',
    down: 'status-down',
    checking: 'status-checking',
  }[status];

  const label = {
    up: '● UP',
    down: '● DOWN',
    checking: '◌ Checking…',
  }[status];

  return <span className={classes}>{label}</span>;
}

function ServiceCard({ service }: { service: ServiceInfo }) {
  return (
    <div className="glass-card p-6 flex flex-col gap-3 hover:bg-white/10 transition-colors duration-200">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h3 className="font-semibold text-slate-100">{service.name}</h3>
          <p className="text-xs text-slate-500 mt-0.5 font-mono">{service.url}</p>
        </div>
        <StatusBadge status={service.status} />
      </div>
      {service.version && (
        <p className="text-xs text-slate-400">Version: {service.version}</p>
      )}
      {service.error && (
        <p className="text-xs text-red-400 bg-red-500/10 rounded-lg px-3 py-2 border border-red-500/20">
          {service.error}
        </p>
      )}
    </div>
  );
}

/**
 * Status Page — Phase 1 landing screen.
 * Shows real-time backend and ML service connectivity.
 * No fabricated data or fake AI results are displayed.
 */
export default function StatusPage() {
  const [services, setServices] = useState<ServiceInfo[]>([
    {
      name: 'Backend API (Spring Boot)',
      url: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1',
      status: 'checking',
    },
    {
      name: 'ML Service (FastAPI)',
      url: (import.meta.env.VITE_ML_SERVICE_URL || 'http://localhost:8000') + '/health',
      status: 'checking',
    },
  ]);

  const checkHealth = useCallback(async () => {
    // Check backend
    try {
      const backend = await checkBackendHealth();
      setServices(prev => prev.map((s, i) =>
        i === 0
          ? { ...s, status: 'up', version: backend.version, error: undefined }
          : s
      ));
    } catch (error: unknown) {
      const msg = error instanceof Error ? error.message : 'Connection failed';
      setServices(prev => prev.map((s, i) =>
        i === 0 ? { ...s, status: 'down', error: msg } : s
      ));
    }

    // Check ML service
    try {
      const ml = await checkMlHealth();
      setServices(prev => prev.map((s, i) =>
        i === 1
          ? { ...s, status: 'up', version: ml.version, error: undefined }
          : s
      ));
    } catch (error: unknown) {
      const msg = error instanceof Error ? error.message : 'Connection failed';
      setServices(prev => prev.map((s, i) =>
        i === 1 ? { ...s, status: 'down', error: msg } : s
      ));
    }
  }, []);

  useEffect(() => {
    checkHealth();
  }, [checkHealth]);

  const allUp = services.every(s => s.status === 'up');
  const anyChecking = services.some(s => s.status === 'checking');

  return (
    <div className="min-h-screen bg-slate-950 overflow-hidden">
      {/* Background gradient orbs */}
      <div className="fixed inset-0 pointer-events-none" aria-hidden="true">
        <div className="absolute top-[-20%] left-[-10%] w-[600px] h-[600px] bg-indigo-600/20 rounded-full blur-3xl" />
        <div className="absolute bottom-[-20%] right-[-10%] w-[500px] h-[500px] bg-purple-600/15 rounded-full blur-3xl" />
      </div>

      <div className="relative max-w-3xl mx-auto px-6 py-16">
        {/* Header */}
        <header className="mb-14">
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-indigo-500/15 border border-indigo-500/30 text-indigo-400 text-xs font-semibold mb-6">
            <span>🌾</span>
            <span>Phase 1 — Foundation</span>
          </div>

          <h1 className="text-4xl sm:text-5xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 via-purple-400 to-indigo-300 leading-tight mb-4">
            AI Farm Strategy &amp;<br />Decision Engine
          </h1>

          <p className="text-slate-400 text-lg max-w-xl leading-relaxed">
            <em className="text-slate-300 not-italic font-medium">
              "From What Should I Grow? to Where &amp; When Should I Sell?"
            </em>
          </p>

          <p className="mt-4 text-slate-500 text-sm max-w-xl">
            This platform helps farmers evaluate land, soil, water, working capital, weather
            and market conditions to make informed decisions. Phase 1 establishes the core infrastructure.
          </p>

          {/* Disclaimer */}
          <div className="mt-6 px-4 py-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-400 text-xs">
            ⚠️ This system provides decision-support information only. It does not guarantee yield,
            profit or prediction accuracy. The farmer remains the final decision-maker.
          </div>
        </header>

        {/* Service Status */}
        <section aria-labelledby="status-heading" className="mb-10">
          <div className="flex items-center justify-between mb-4">
            <h2 id="status-heading" className="text-lg font-semibold text-slate-200">
              Service Status
            </h2>
            <button
              id="refresh-status-btn"
              onClick={checkHealth}
              disabled={anyChecking}
              className="btn-secondary text-xs px-4 py-2"
            >
              {anyChecking ? 'Checking…' : '↺ Refresh'}
            </button>
          </div>

          <div className="grid gap-4">
            {services.map((service, idx) => (
              <ServiceCard key={idx} service={service} />
            ))}
          </div>

          {allUp && (
            <div className="mt-4 px-4 py-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-sm font-medium">
              ✓ All services are operational.
            </div>
          )}
        </section>

        {/* Navigation */}
        <section aria-labelledby="actions-heading" className="mb-10">
          <h2 id="actions-heading" className="text-lg font-semibold text-slate-200 mb-4">
            Phase 1 Features
          </h2>
          <div className="grid sm:grid-cols-2 gap-4">
            <Link
              to="/profile"
              id="go-to-profile-link"
              className="glass-card p-5 hover:bg-white/10 transition-colors duration-200 group"
            >
              <div className="text-2xl mb-2">👤</div>
              <h3 className="font-semibold text-slate-200 group-hover:text-indigo-300 transition-colors">
                Farmer Profile
              </h3>
              <p className="text-sm text-slate-500 mt-1">
                Create and view your farmer profile via the backend API.
              </p>
            </Link>

            <Link
              to="/profile"
              id="go-to-strategy-link"
              className="glass-card p-5 hover:bg-white/10 transition-colors duration-200 group"
            >
              <div className="text-2xl mb-2">📊</div>
              <h3 className="font-semibold text-slate-200 group-hover:text-emerald-300 transition-colors">
                Composite Strategy Engine
              </h3>
              <p className="text-sm text-slate-500 mt-1">
                Evaluate multi-dimensional agronomic, financial, market, and weather crop strategy.
              </p>
            </Link>
          </div>
        </section>

        {/* Phase info */}
        <footer className="text-xs text-slate-600 border-t border-white/5 pt-6">
          <p>Phase 1 — Infrastructure foundation. No AI predictions or market data in this phase.</p>
          <p className="mt-1">Backend: Java 21 + Spring Boot · Database: MySQL 8 + Flyway · ML: FastAPI · Frontend: React + Vite + Tailwind</p>
        </footer>
      </div>
    </div>
  );
}
