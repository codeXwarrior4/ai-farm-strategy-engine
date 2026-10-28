import React, { useState } from 'react';
import {
  evaluateFarmStrategy,
  type GrowingSeason,
  type StrategyDecisionTier,
  type FarmStrategyEvaluationResponse,
  type CropStrategyResultDto,
  type RiskTolerance,
} from '../api/strategy';
import { ApiError } from '../api/apiClient';

interface FarmerStrategySectionProps {
  userId: number;
  hasProfile: boolean;
  cultivableHectaresDefault?: number | null;
  workingCapitalInrDefault?: number | null;
  riskToleranceDefault?: RiskTolerance | null;
  primaryState?: string | null;
  primaryDistrict?: string | null;
}

export const FarmerStrategySection: React.FC<FarmerStrategySectionProps> = ({
  userId,
  hasProfile,
  cultivableHectaresDefault,
  workingCapitalInrDefault,
  riskToleranceDefault,
  primaryState,
  primaryDistrict,
}) => {
  const [targetSeason, setTargetSeason] = useState<GrowingSeason>('KHARIF');
  const [planningYear, setPlanningYear] = useState<string>('2026');
  const [showOverrides, setShowOverrides] = useState<boolean>(false);
  const [cultivableOverride, setCultivableOverride] = useState<string>('');
  const [capitalOverride, setCapitalOverride] = useState<string>('');
  const [riskToleranceOverride, setRiskToleranceOverride] = useState<string>('');
  const [evaluationDate, setEvaluationDate] = useState<string>('');

  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [errorStatus, setErrorStatus] = useState<number | null>(null);
  const [response, setResponse] = useState<FarmStrategyEvaluationResponse | null>(null);

  // Stale detection
  const [isStale, setIsStale] = useState<boolean>(false);
  const [tierFilter, setTierFilter] = useState<'ALL' | StrategyDecisionTier>('ALL');

  const handleSeasonChange = (s: GrowingSeason) => {
    setTargetSeason(s);
    if (response) setIsStale(true);
  };

  const handleEvaluate = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!userId || userId <= 0 || !hasProfile) {
      setError('A valid farmer profile must be loaded before running strategy evaluation.');
      return;
    }

    setIsLoading(true);
    setError(null);
    setErrorStatus(null);

    const yearNum = planningYear.trim() ? parseInt(planningYear.trim(), 10) : undefined;
    const landNum = cultivableOverride.trim() ? parseFloat(cultivableOverride.trim()) : undefined;
    const capNum = capitalOverride.trim() ? parseFloat(capitalOverride.trim()) : undefined;
    const evalDateStr = evaluationDate.trim() ? new Date(evaluationDate.trim()).toISOString() : undefined;
    const riskOverride = (riskToleranceOverride === 'LOW' || riskToleranceOverride === 'MEDIUM' || riskToleranceOverride === 'HIGH')
      ? (riskToleranceOverride as RiskTolerance)
      : undefined;

    try {
      const result = await evaluateFarmStrategy(userId, {
        targetSeason,
        planningYear: isNaN(yearNum as number) ? undefined : yearNum,
        cultivableHectaresOverride: isNaN(landNum as number) ? undefined : landNum,
        workingCapitalOverride: isNaN(capNum as number) ? undefined : capNum,
        riskToleranceOverride: riskOverride,
        evaluationDate: evalDateStr,
      });
      setResponse(result);
      setIsStale(false);
    } catch (err: unknown) {
      setResponse(null);
      if (err instanceof ApiError) {
        setErrorStatus(err.status);
        if (err.status === 401) {
          setError('Authentication required or session expired. Please supply a valid JWT token.');
        } else if (err.status === 403) {
          setError('Authorization failed (403): You are not authorized to evaluate strategy for this profile.');
        } else if (err.status === 404) {
          setError('Farmer profile not found for this user. Please save a profile first.');
        } else if (err.status === 400) {
          const fieldMsgs = err.data.fieldErrors
            ? Object.entries(err.data.fieldErrors)
                .map(([field, msg]) => `${field}: ${msg}`)
                .join(', ')
            : err.data.message;
          setError(`Validation error (400): ${fieldMsgs}`);
        } else {
          setError(err.data.message || `Server error (${err.status}) during strategy evaluation.`);
        }
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('An unexpected error occurred during strategy evaluation.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const renderTierBadge = (tier: StrategyDecisionTier) => {
    switch (tier) {
      case 'RECOMMENDED':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/40">
            <span>✨</span> RECOMMENDED
          </span>
        );
      case 'CONDITIONAL':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-500/20 text-amber-300 border border-amber-500/40">
            <span>⚠️</span> CONDITIONAL
          </span>
        );
      case 'DATA_DEFICIENT':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-sky-500/20 text-sky-300 border border-sky-500/40">
            <span>ℹ️</span> DATA DEFICIENT
          </span>
        );
      case 'FINANCIALLY_CONSTRAINED':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-orange-500/20 text-orange-400 border border-orange-500/40">
            <span>🪙</span> FINANCIALLY CONSTRAINED
          </span>
        );
      case 'AGRONOMICALLY_EXCLUDED':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-rose-500/20 text-rose-400 border border-rose-500/40">
            <span>🚫</span> AGRONOMICALLY EXCLUDED
          </span>
        );
    }
  };

  const filteredCandidates = response?.candidateCrops.filter((crop) => {
    if (tierFilter === 'ALL') return true;
    return crop.decisionTier === tierFilter;
  });

  return (
    <section className="glass-card p-6 mt-8 border-indigo-500/30" aria-labelledby="strategy-engine-heading">
      {/* Section Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between pb-4 border-b border-white/10 gap-2">
        <div>
          <h2 id="strategy-engine-heading" className="text-xl font-bold text-slate-100 flex items-center gap-2">
            <span>🎯</span> Composite Farm Strategy Decision Engine
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Evaluates agronomic suitability, benchmark Cost A2 capital capacity, wholesale market prices, operational weather, and risk-adjusted economic revenue scenarios.
          </p>
        </div>
        <div className="flex items-center gap-2 self-start md:self-center">
          <span className="text-xs px-2.5 py-1 rounded-md bg-white/5 border border-white/10 text-slate-300 font-mono">
            Phase 4 · Economic Decision Engine
          </span>
        </div>
      </div>

      {/* Control Bar: Season & Evaluation Inputs */}
      <div className="mt-6 bg-white/[0.03] border border-white/10 rounded-xl p-4">
        <form onSubmit={handleEvaluate} className="space-y-4">
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4 items-end">
            {/* Target Season Selection */}
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                Target Growing Season <span className="text-rose-400">*</span>
              </label>
              <div className="grid grid-cols-3 gap-1 bg-black/40 p-1 rounded-xl border border-white/10">
                {(['KHARIF', 'RABI', 'ZAID'] as GrowingSeason[]).map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => handleSeasonChange(s)}
                    className={`py-1.5 text-xs font-semibold rounded-lg transition-all ${
                      targetSeason === s
                        ? 'bg-indigo-600 text-white shadow-md'
                        : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    {s}
                  </button>
                ))}
              </div>
            </div>

            {/* Planning Year */}
            <div>
              <label htmlFor="strategyPlanningYear" className="block text-xs font-medium text-slate-300 mb-1.5">
                Planning Year
              </label>
              <input
                id="strategyPlanningYear"
                type="number"
                min="2024"
                max="2035"
                value={planningYear}
                onChange={(e) => {
                  setPlanningYear(e.target.value);
                  if (response) setIsStale(true);
                }}
                className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-slate-100 text-xs focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                placeholder="2026"
              />
            </div>

            {/* Overrides Toggle */}
            <div className="flex items-center">
              <button
                type="button"
                onClick={() => setShowOverrides(!showOverrides)}
                className="text-xs text-indigo-400 hover:text-indigo-300 underline font-medium flex items-center gap-1 py-2"
              >
                <span>{showOverrides ? '▼ Hide' : '▶ Show'} Speculative 'What-If' Overrides</span>
              </button>
            </div>

            {/* Submit Action */}
            <div>
              <button
                type="submit"
                disabled={isLoading || !hasProfile}
                className="w-full btn-primary py-2 text-xs font-bold shadow-lg shadow-indigo-500/20 flex items-center justify-center gap-2"
              >
                {isLoading ? (
                  <>
                    <span className="inline-block animate-spin">⏳</span>
                    Evaluating Strategy...
                  </>
                ) : (
                  <>
                    <span>⚡</span>
                    Evaluate Strategy
                  </>
                )}
              </button>
            </div>
          </div>

          {/* Collapsible Speculative Scenarios Panel */}
          {showOverrides && (
            <div className="mt-3 pt-3 border-t border-white/10 grid sm:grid-cols-2 lg:grid-cols-4 gap-4 text-xs">
              <div>
                <label htmlFor="cultivableOverride" className="block text-slate-400 mb-1">
                  Speculative Cultivable Hectares (ha)
                </label>
                <input
                  id="cultivableOverride"
                  type="number"
                  step="0.01"
                  min="0.01"
                  max="10000"
                  value={cultivableOverride}
                  onChange={(e) => {
                    setCultivableOverride(e.target.value);
                    if (response) setIsStale(true);
                  }}
                  placeholder={cultivableHectaresDefault ? `Default: ${cultivableHectaresDefault} ha` : 'e.g. 2.5'}
                  className="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-slate-100 text-xs focus:outline-none focus:ring-1 focus:ring-indigo-500"
                />
              </div>

              <div>
                <label htmlFor="capitalOverride" className="block text-slate-400 mb-1">
                  Speculative Working Capital (₹)
                </label>
                <input
                  id="capitalOverride"
                  type="number"
                  step="100"
                  min="0"
                  value={capitalOverride}
                  onChange={(e) => {
                    setCapitalOverride(e.target.value);
                    if (response) setIsStale(true);
                  }}
                  placeholder={workingCapitalInrDefault ? `Default: ₹ ${workingCapitalInrDefault}` : 'e.g. 50000'}
                  className="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-slate-100 text-xs focus:outline-none focus:ring-1 focus:ring-indigo-500"
                />
              </div>

              <div>
                <label htmlFor="riskToleranceOverrideSelect" className="block text-slate-400 mb-1">
                  Speculative Risk Appetite
                </label>
                <select
                  id="riskToleranceOverrideSelect"
                  value={riskToleranceOverride}
                  onChange={(e) => {
                    setRiskToleranceOverride(e.target.value);
                    if (response) setIsStale(true);
                  }}
                  className="w-full px-3 py-2 rounded-lg bg-[#141b2d] border border-white/10 text-slate-100 text-xs focus:outline-none focus:ring-1 focus:ring-indigo-500"
                >
                  <option value="">Profile Default ({riskToleranceDefault || 'MEDIUM'})</option>
                  <option value="LOW">LOW (70% land cap, 25% capital reserve)</option>
                  <option value="MEDIUM">MEDIUM (100% land cap, 15% capital reserve)</option>
                  <option value="HIGH">HIGH (100% land cap, 0% reserve)</option>
                </select>
                <span className="text-[10px] text-slate-500 mt-0.5 block">
                  Simulates risk-adjusted buffers without saving to profile.
                </span>
              </div>

              <div>
                <label htmlFor="evaluationDateInput" className="block text-slate-400 mb-1">
                  Explicit Evaluation Date (Freshness Audit)
                </label>
                <input
                  id="evaluationDateInput"
                  type="datetime-local"
                  value={evaluationDate}
                  onChange={(e) => {
                    setEvaluationDate(e.target.value);
                    if (response) setIsStale(true);
                  }}
                  className="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-slate-100 text-xs focus:outline-none focus:ring-1 focus:ring-indigo-500"
                />
                <span className="text-[10px] text-slate-500 mt-0.5 block">
                  Leave empty for deterministic mode without clock fallback.
                </span>
              </div>
            </div>
          )}

          {/* Stale Results Warning */}
          {isStale && response && (
            <div className="bg-amber-500/10 border border-amber-500/30 rounded-lg p-2.5 text-xs text-amber-300 flex items-center justify-between">
              <span>⚠️ Evaluation inputs modified since last run. Click "Evaluate Strategy" to recalculate.</span>
              <button
                type="button"
                onClick={() => handleEvaluate()}
                className="text-xs underline font-semibold hover:text-amber-200"
              >
                Re-evaluate
              </button>
            </div>
          )}
        </form>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="mt-4 p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-start gap-3">
          <span className="text-base">⚠️</span>
          <div>
            <div className="font-semibold mb-0.5">Evaluation Unsuccessful {errorStatus ? `(${errorStatus})` : ''}</div>
            <div className="text-rose-400">{error}</div>
          </div>
        </div>
      )}

      {/* Initial Guidance when not yet evaluated */}
      {!response && !isLoading && !error && (
        <div className="mt-8 text-center py-12 px-4 border border-dashed border-white/10 rounded-2xl">
          <span className="text-3xl block mb-2">🌾</span>
          <h3 className="text-sm font-semibold text-slate-200">Ready to Evaluate Farm Strategy</h3>
          <p className="text-xs text-slate-400 max-w-md mx-auto mt-1">
            {hasProfile
              ? `Evaluate ${primaryDistrict ? `${primaryDistrict}, ` : ''}${primaryState || 'farm'} crops against biological soil requirements, working capital capacity, mandi wholesale auctions, and 7-day weather.`
              : 'Please load or create a farmer profile above to run composite strategy planning.'}
          </p>
        </div>
      )}

      {/* Evaluation Results Section */}
      {response && (
        <div className="mt-8 space-y-6">
          {/* Summary KPI Banner */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            <div className="bg-white/5 border border-white/10 rounded-xl p-3 text-center">
              <span className="text-slate-400 text-[11px] block">Evaluated</span>
              <span className="text-xl font-bold text-slate-100">{response.summary.totalCatalogCropsEvaluated}</span>
              <span className="text-[10px] text-slate-500 block">Catalog Crops</span>
            </div>

            <div className="bg-emerald-500/10 border border-emerald-500/30 rounded-xl p-3 text-center">
              <span className="text-emerald-400 text-[11px] block">Recommended</span>
              <span className="text-xl font-bold text-emerald-300">{response.summary.recommendedCount}</span>
              <span className="text-[10px] text-emerald-500/80 block">All 4 Dims Aligned</span>
            </div>

            <div className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-3 text-center">
              <span className="text-amber-400 text-[11px] block">Conditional</span>
              <span className="text-xl font-bold text-amber-300">{response.summary.conditionalCount}</span>
              <span className="text-[10px] text-amber-500/80 block">Operational Caveats</span>
            </div>

            <div className="bg-sky-500/10 border border-sky-500/30 rounded-xl p-3 text-center">
              <span className="text-sky-400 text-[11px] block">Data Deficient</span>
              <span className="text-xl font-bold text-sky-300">{response.summary.dataDeficientCount}</span>
              <span className="text-[10px] text-sky-500/80 block">Missing Cost/Capital</span>
            </div>

            <div className="bg-orange-500/10 border border-orange-500/30 rounded-xl p-3 text-center">
              <span className="text-orange-400 text-[11px] block">Constrained</span>
              <span className="text-xl font-bold text-orange-300">{response.summary.financiallyConstrainedCount}</span>
              <span className="text-[10px] text-orange-500/80 block">Capital Shortfall</span>
            </div>

            <div className="bg-rose-500/10 border border-rose-500/30 rounded-xl p-3 text-center">
              <span className="text-rose-400 text-[11px] block">Excluded</span>
              <span className="text-xl font-bold text-rose-300">{response.summary.agronomicallyExcludedCount}</span>
              <span className="text-[10px] text-rose-500/80 block">Off-Season / Soil</span>
            </div>
          </div>

          {/* Context & Policy Notice Bar */}
          <div className="bg-black/30 border border-white/5 rounded-xl p-3 text-xs text-slate-400 flex flex-col md:flex-row md:items-center justify-between gap-2">
            <div>
              <span className="font-medium text-slate-300">Evaluated Baseline: </span>
              <span>
                {response.evaluatedCultivableHectares ? `${response.evaluatedCultivableHectares} ha` : 'Unreported Area'} ·{' '}
                {response.evaluatedWorkingCapitalInr ? `₹ ${response.evaluatedWorkingCapitalInr.toLocaleString('en-IN')} Capital` : 'Unreported Capital'} ·{' '}
                {response.primaryDistrict}, {response.primaryState}
              </span>
            </div>
            <div className="text-[11px] text-slate-500">
              Liquidity buffer ratio: <span className="font-mono text-slate-300">1.15x</span> ({response.summary.provisionalBufferAssumption})
            </div>
          </div>

          {/* Tier Filter Tabs for Candidate Crops */}
          <div className="flex flex-wrap gap-1.5 border-b border-white/10 pb-3">
            {(
              [
                ['ALL', `All Candidates (${response.candidateCrops.length})`],
                ['RECOMMENDED', `Recommended (${response.summary.recommendedCount})`],
                ['CONDITIONAL', `Conditional (${response.summary.conditionalCount})`],
                ['DATA_DEFICIENT', `Data Deficient (${response.summary.dataDeficientCount})`],
                ['FINANCIALLY_CONSTRAINED', `Financially Constrained (${response.summary.financiallyConstrainedCount})`],
              ] as const
            ).map(([val, label]) => (
              <button
                key={val}
                type="button"
                onClick={() => setTierFilter(val)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                  tierFilter === val
                    ? 'bg-indigo-600 text-white font-semibold'
                    : 'bg-white/5 text-slate-400 hover:text-slate-200'
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          {/* Candidate Crops List */}
          <div className="space-y-4">
            <h3 className="text-sm font-semibold text-slate-200 flex items-center justify-between">
              <span>🌾 Agronomically Viable Candidate Crops</span>
              <span className="text-xs text-slate-400 font-normal">
                Ranked deterministically by Tier → Agronomic → Financial → Market Freshness → Capital Ratio → BCR → Net Margin → Alphabetical
              </span>
            </h3>

            {(!filteredCandidates || filteredCandidates.length === 0) ? (
              <div className="p-8 text-center bg-white/[0.02] border border-white/5 rounded-xl text-xs text-slate-400">
                No candidate crops match the selected tier filter.
              </div>
            ) : (
              filteredCandidates.map((crop) => renderCandidateCropCard(crop))
            )}
          </div>

          {/* Agronomically Excluded Crops Section (Strictly Separated) */}
          {response.excludedCrops && response.excludedCrops.length > 0 && (
            <div className="mt-12 pt-6 border-t border-rose-500/20 space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-rose-400 flex items-center gap-2">
                    <span>🚫</span> Agronomically Excluded Crops ({response.excludedCrops.length})
                  </h3>
                  <p className="text-xs text-slate-400 mt-0.5">
                    These crops are biologically incompatible with the target season or reported soil type. Excluded by agronomic rules; never promoted to candidates.
                  </p>
                </div>
                <span className="text-xs text-slate-500 font-mono">Alphabetical Order</span>
              </div>

              <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {response.excludedCrops.map((crop) => renderExcludedCropCard(crop))}
              </div>
            </div>
          )}

          {/* Regulatory & Methodology Disclaimer */}
          <div className="bg-slate-900/60 border border-white/5 rounded-xl p-4 text-[11px] text-slate-500 leading-relaxed space-y-1.5 mt-6">
            <p className="font-semibold text-slate-400">Decision Engine Methodology &amp; Attribution Disclaimers:</p>
            <p>
              • <strong>Market Prices:</strong> Mandi modal prices represent empirical wholesale market auction rates recorded by AGMARKNET (data.gov.in) under the Government Open Data License (GODL). They do not guarantee net farmgate prices received by an individual farmer.
            </p>
            <p>
              • <strong>Cost Benchmarks:</strong> Cost A2 benchmarks are sourced from State-level surveys issued by the Commission for Agricultural Costs and Prices (CACP) and Directorate of Economics &amp; Statistics (DES). Micro-climatic pumping or regional variations may apply.
            </p>
            <p>
              • <strong>Yield Benchmarks:</strong> Historical yields are sourced from DES empirical surveys and adjusted by agronomic parcel suitability heuristics. They do not guarantee future harvest volume.
            </p>
            <p>
              • <strong>Operational Sizing:</strong> Recommended hectares represent single-crop planning scenarios calculated using risk-adjusted capital safety reserves and land caps. Area decisions remain solely with the farmer.
            </p>
          </div>
        </div>
      )}
    </section>
  );

  function renderCandidateCropCard(crop: CropStrategyResultDto) {
    const fin = crop.financialFeasibility;
    const suit = crop.agronomicSuitability;
    const mkt = crop.marketAttractiveness;
    const wx = crop.weatherRisk;
    const isEconDataDeficient = !crop.economicEvaluation || crop.economicEvaluation.status === 'DATA_DEFICIENT';

    return (
      <div
        key={crop.cropId}
        className="glass-card p-5 border border-white/10 hover:border-indigo-500/30 transition-all rounded-xl space-y-4"
      >
        {/* Card Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-white/5 pb-3">
          <div className="flex items-center gap-3">
            <span className="inline-flex items-center justify-center w-7 h-7 rounded-lg bg-indigo-600/30 border border-indigo-500/40 text-indigo-300 font-bold text-xs">
              #{crop.rankOrder}
            </span>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold text-slate-100 text-base">{crop.canonicalName}</span>
                {crop.botanicalName && (
                  <span className="text-xs italic text-slate-400">({crop.botanicalName})</span>
                )}
                <span className="text-[10px] px-2 py-0.5 rounded bg-white/5 border border-white/10 text-slate-300 font-mono">
                  {crop.category}
                </span>
              </div>
            </div>
          </div>
          <div>{renderTierBadge(crop.decisionTier)}</div>
        </div>

        {/* Decision Explanation Banner */}
        <div className="bg-white/[0.02] border border-white/5 rounded-lg p-2.5 text-xs text-slate-300 leading-relaxed">
          <span className="font-semibold text-slate-200">Evaluation: </span>
          {crop.decisionExplanation}
        </div>

        {/* 4 Independent Evaluation Dimensions */}
        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
          {/* Dimension 1: Agronomic */}
          <div className="bg-white/5 border border-white/5 rounded-lg p-3 space-y-1.5">
            <div className="flex items-center justify-between">
              <span className="text-slate-400 font-medium">1. Agronomic</span>
              <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                suit.overallSuitability === 'HIGHLY_SUITABLE' ? 'bg-emerald-500/20 text-emerald-400' :
                suit.overallSuitability === 'MODERATELY_SUITABLE' ? 'bg-emerald-500/10 text-emerald-300' :
                suit.overallSuitability === 'MARGINAL' ? 'bg-amber-500/20 text-amber-300' :
                'bg-sky-500/20 text-sky-300'
              }`}>
                {suit.overallSuitability}
              </span>
            </div>
            <div className="text-[11px] text-slate-300">
              Soil: {suit.soilEvaluation?.soilNotes || suit.soilEvaluation?.suitabilityLevel || 'Standard suitability'}
            </div>
            <div className="text-[10px] text-slate-500">
              Window: Months {suit.seasonEvaluation?.sowingWindowStartMonth}–{suit.seasonEvaluation?.sowingWindowEndMonth} · Duration {suit.seasonEvaluation?.durationDaysMin}–{suit.seasonEvaluation?.durationDaysMax}d
            </div>
          </div>

          {/* Dimension 2: Financial */}
          <div className="bg-white/5 border border-white/5 rounded-lg p-3 space-y-1.5">
            <div className="flex items-center justify-between">
              <span className="text-slate-400 font-medium">2. Financial</span>
              <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                fin?.status === 'FEASIBLE' ? 'bg-emerald-500/20 text-emerald-400' :
                fin?.status === 'TIGHT_BUDGET' ? 'bg-amber-500/20 text-amber-300' :
                fin?.status === 'INSUFFICIENT_DATA' ? 'bg-sky-500/20 text-sky-300' :
                'bg-orange-500/20 text-orange-400'
              }`}>
                {fin?.status || 'N/A'}
              </span>
            </div>
            <div className="text-[11px] text-slate-300">
              {fin?.costPerHectareA2 ? `Cost A2: ₹ ${fin.costPerHectareA2.toLocaleString('en-IN')}/ha` : 'Cost A2: Pending CACP Survey'}
            </div>
            <div className="text-[10px] text-slate-500">
              {fin?.capitalSufficiencyRatio ? `Buffer: ${fin.capitalSufficiencyRatio}x coverage` : 'Capital ratio: Unavailable'}
              {fin?.sourcePublication && ` · ${fin.sourcePublication}`}
            </div>
          </div>

          {/* Dimension 3: Market */}
          <div className="bg-white/5 border border-white/5 rounded-lg p-3 space-y-1.5">
            <div className="flex items-center justify-between">
              <span className="text-slate-400 font-medium">3. Market</span>
              <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                mkt?.status === 'FAVORABLE' ? 'bg-emerald-500/20 text-emerald-400' :
                mkt?.status === 'MODERATE' ? 'bg-emerald-500/10 text-emerald-300' :
                mkt?.status === 'VOLATILE' ? 'bg-amber-500/20 text-amber-300' :
                mkt?.status === 'STALE_DATA' ? 'bg-orange-500/20 text-orange-300' :
                'bg-slate-500/20 text-slate-400'
              }`}>
                {mkt?.status || 'NO_DATA'}
              </span>
            </div>
            <div className="text-[11px] text-slate-300">
              {mkt?.modalPrice ? `Modal: ₹ ${mkt.modalPrice.toLocaleString('en-IN')}/qtl` : 'Wholesale price: Unavailable'}
            </div>
            <div className="text-[10px] text-slate-500">
              {mkt?.mandiName ? `${mkt.mandiName} · ` : ''}
              {mkt?.descriptivePriceSpreadRatio !== null && mkt?.descriptivePriceSpreadRatio !== undefined
                ? `Spread: ${mkt.descriptivePriceSpreadRatio}`
                : 'Spread: N/A'}
            </div>
          </div>

          {/* Dimension 4: Weather */}
          <div className="bg-white/5 border border-white/5 rounded-lg p-3 space-y-1.5">
            <div className="flex items-center justify-between">
              <span className="text-slate-400 font-medium">4. Weather (7-Day)</span>
              <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                wx?.status === 'FAVORABLE' ? 'bg-emerald-500/20 text-emerald-400' :
                wx?.status === 'ELEVATED_RISK' ? 'bg-amber-500/20 text-amber-300' :
                wx?.status === 'ADVERSE_CONDITIONS' ? 'bg-rose-500/20 text-rose-400' :
                'bg-slate-500/20 text-slate-400'
              }`}>
                {wx?.status || 'UNAVAILABLE'}
              </span>
            </div>
            <div className="text-[11px] text-slate-300">
              {wx?.maxTemperatureCelsius ? `Max ${wx.maxTemperatureCelsius}°C` : 'Temp: N/A'} ·{' '}
              {wx?.totalPrecipitationMm !== null && wx?.totalPrecipitationMm !== undefined ? `${wx.totalPrecipitationMm}mm Rain` : 'Rain: N/A'}
            </div>
            <div className="text-[10px] text-slate-500">
              {wx?.precision ? `${wx.precision} Precision` : 'Location context'}
            </div>
          </div>
        </div>

        {/* Dimension 5: Economic Evaluation & Margins (Phase 4) */}
        {renderEconomicSection(crop)}

        {/* Operational Sizing Guidance */}
        <div className="bg-black/20 border border-white/5 rounded-lg p-3 space-y-2.5 text-xs">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1 border-b border-white/5 pb-2">
            <span className="font-semibold text-slate-300 flex items-center gap-1.5">
              <span>📐</span> Operational Parcel Sizing &amp; Risk Buffers
            </span>
            <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 font-mono">
              {crop.acreageAllocationModel || 'SINGLE_CROP_SCENARIO'}
            </span>
          </div>

          <div className="grid sm:grid-cols-2 gap-4">
            <div>
              <span className="text-slate-400 block text-[11px]">
                {isEconDataDeficient ? 'Maximum Capital-Fundable Area — Returns Unverified' : 'Recommended Parcel Sizing'}
              </span>
              <div className="text-sm font-semibold text-slate-100 flex items-center gap-2 mt-0.5">
                {crop.recommendedHectares !== null && crop.recommendedHectares !== undefined ? (
                  <>
                    <span>{crop.recommendedHectares} hectares</span>
                    <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded border ${
                      isEconDataDeficient
                        ? 'bg-sky-500/20 text-sky-300 border-sky-500/30'
                        : 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30'
                    }`}>
                      {isEconDataDeficient ? 'CAPITAL CAPACITY' : 'SINGLE-CROP'}
                    </span>
                  </>
                ) : (
                  <span className="text-slate-400">Unavailable (Requires Cost A2 &amp; capital)</span>
                )}
              </div>
              <span className="text-[10px] text-slate-400 block mt-1 leading-snug">
                {isEconDataDeficient
                  ? 'Reflects estimated capital capacity based on reported working capital and Cost A2 only. This is an affordability boundary, NOT an economically validated crop recommendation or guarantee of profitability.'
                  : (crop.acreageSizingExplanation || 'Risk-adjusted sizing based on cultivable land caps and capital reserve safety buffers.')}
              </span>
            </div>

            <div>
              <span className="text-slate-400 block text-[11px]">Affordable Parcel Scale (Unbuffered Cost A2)</span>
              <div className="text-sm font-semibold text-slate-300 mt-0.5">
                {crop.affordableHectares !== null && crop.affordableHectares !== undefined
                  ? `${crop.affordableHectares} hectares`
                  : '— (Requires Cost A2 benchmark and reported capital)'}
              </div>
              <span className="text-[10px] text-slate-500 block mt-1 leading-snug">
                Theoretical physical scale fundable with reported working capital under Cost A2 before applying risk tolerance safety buffers.
              </span>
            </div>
          </div>

          <div className="text-[10px] text-amber-300/90 bg-amber-500/10 border border-amber-500/20 rounded px-2.5 py-1.5 flex items-start gap-1.5">
            <span>⚠️</span>
            <span>
              <strong>Mutually Exclusive Parcel Commitment:</strong> Recommended hectares represents a single-crop parcel commitment. Do NOT sum recommended hectares across different crop cards; each represents an independent, alternative cropping choice for your parcel.
            </span>
          </div>
        </div>

        {/* Actionable Missing Data Prompts & Warnings */}
        {((crop.missingDataPrompts && crop.missingDataPrompts.length > 0) ||
          (crop.primaryWarnings && crop.primaryWarnings.length > 0)) && (
          <div className="space-y-1.5 pt-1">
            {crop.missingDataPrompts?.map((prompt, idx) => (
              <div key={idx} className="text-[11px] text-sky-300 bg-sky-500/10 border border-sky-500/20 rounded px-2.5 py-1 flex items-center gap-1.5">
                <span>ℹ️</span>
                <span><strong>Data Action:</strong> {prompt}</span>
              </div>
            ))}

            {crop.primaryWarnings?.map((warning, idx) => (
              <div key={idx} className="text-[11px] text-amber-300 bg-amber-500/10 border border-amber-500/20 rounded px-2.5 py-1 flex items-center gap-1.5">
                <span>⚠️</span>
                <span><strong>Operational Caveat:</strong> {warning}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    );
  }

  function renderEconomicSection(crop: CropStrategyResultDto) {
    const econ = crop.economicEvaluation;

    if (!econ || econ.status === 'DATA_DEFICIENT') {
      return (
        <div className="bg-sky-950/20 border border-sky-500/20 rounded-xl p-3.5 space-y-2 text-xs">
          <div className="flex items-center justify-between border-b border-white/5 pb-2">
            <span className="font-semibold text-sky-300 flex items-center gap-1.5">
              <span>📊</span> Dimension 5: Economic Evaluation &amp; Margins
            </span>
            <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-sky-500/20 text-sky-300 border border-sky-500/30">
              DATA DEFICIENT
            </span>
          </div>

          <div className="text-[11px] text-slate-300">
            {econ?.summaryExplanation ||
              'Official economic evaluation could not be computed due to missing empirical cost, yield, or mandi data.'}
          </div>

          {econ?.missingDataReasons && econ.missingDataReasons.length > 0 && (
            <div className="space-y-1 bg-black/20 p-2 rounded border border-white/5">
              <span className="text-[10px] text-slate-400 font-medium block">Missing Benchmark Requirements:</span>
              {econ.missingDataReasons.map((reason, idx) => (
                <div key={idx} className="text-[11px] text-sky-300 flex items-center gap-1.5">
                  <span>•</span>
                  <span>{reason}</span>
                </div>
              ))}
            </div>
          )}

          <div className="text-[10px] text-slate-500 italic">
            Revenue scenarios, net operating margins, and Benefit-Cost Ratios (BCR) are withheld to prevent fabricated financial returns.
          </div>
        </div>
      );
    }

    const hasWarnings = econ.status === 'SUFFICIENT_WITH_WARNINGS';
    const netMargin = econ.expectedNetOperatingMarginPerHectare ?? 0;
    const isMarginPositive = netMargin >= 0;

    return (
      <div className="bg-white/[0.03] border border-indigo-500/20 rounded-xl p-4 space-y-3.5 text-xs">
        {/* Economic Section Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-white/5 pb-2.5">
          <div className="flex items-center gap-2">
            <span className="font-bold text-slate-200 text-sm flex items-center gap-1.5">
              <span>📊</span> Dimension 5: Official Economic Feasibility &amp; Scenarios
            </span>
            <span className="text-[10px] text-slate-400 font-mono">Phase 4</span>
          </div>
          <div>
            {hasWarnings ? (
              <span className="inline-flex items-center gap-1 text-[10px] font-bold px-2.5 py-0.5 rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/30">
                <span>⚠️</span> SUFFICIENT WITH CAVEATS
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 text-[10px] font-bold px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                <span>✓</span> OFFICIAL BENCHMARKS SUFFICIENT
              </span>
            )}
          </div>
        </div>

        {/* Top 4 KPI Metrics (Expected Scenario) */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
          <div className="bg-black/30 border border-white/5 rounded-lg p-2.5">
            <span className="text-slate-400 text-[10px] block">Expected Gross Revenue</span>
            <span className="text-sm font-bold text-slate-100">
              {econ.expectedGrossRevenuePerHectare !== null && econ.expectedGrossRevenuePerHectare !== undefined
                ? `₹ ${Math.round(econ.expectedGrossRevenuePerHectare).toLocaleString('en-IN')}`
                : '—'}
            </span>
            <span className="text-[10px] text-slate-500 block mt-0.5">
              per hectare ({econ.adjustedYieldQuintalsPerHa ?? '—'} q/ha)
            </span>
          </div>

          <div className="bg-black/30 border border-white/5 rounded-lg p-2.5">
            <span className="text-slate-400 text-[10px] block">Net Operating Margin (Cost A2)</span>
            <span className={`text-sm font-bold ${isMarginPositive ? 'text-emerald-400' : 'text-rose-400'}`}>
              {econ.expectedNetOperatingMarginPerHectare !== null && econ.expectedNetOperatingMarginPerHectare !== undefined
                ? `${isMarginPositive ? '+' : ''}₹ ${Math.round(econ.expectedNetOperatingMarginPerHectare).toLocaleString('en-IN')}`
                : '—'}
            </span>
            <span className="text-[10px] text-slate-500 block mt-0.5">
              per ha over Cost A2 expenses
            </span>
          </div>

          <div className="bg-black/30 border border-white/5 rounded-lg p-2.5">
            <span className="text-slate-400 text-[10px] block">Benefit-Cost Ratio (BCR)</span>
            <span className="text-sm font-bold text-indigo-300">
              {econ.expectedBenefitCostRatio !== null && econ.expectedBenefitCostRatio !== undefined
                ? `${Number(econ.expectedBenefitCostRatio).toFixed(2)}x`
                : '—'}
            </span>
            <span className="text-[10px] text-slate-500 block mt-0.5">
              {econ.expectedBenefitCostRatio && econ.expectedBenefitCostRatio >= 1.5
                ? 'Favorable return (>1.5x)'
                : econ.expectedBenefitCostRatio && econ.expectedBenefitCostRatio >= 1.0
                ? 'Positive return (>1.0x)'
                : 'Below Cost A2 break-even'}
            </span>
          </div>

          <div className="bg-black/30 border border-white/5 rounded-lg p-2.5">
            <span className="text-slate-400 text-[10px] block">Adjusted Yield</span>
            <span className="text-sm font-bold text-slate-200">
              {econ.adjustedYieldQuintalsPerHa !== null && econ.adjustedYieldQuintalsPerHa !== undefined
                ? `${econ.adjustedYieldQuintalsPerHa} q/ha`
                : '—'}
            </span>
            <span className="text-[10px] text-slate-500 block mt-0.5 truncate" title={econ.suitabilityMultiplierAssumption || ''}>
              {econ.suitabilityYieldMultiplier ? `${Math.round(econ.suitabilityYieldMultiplier * 100)}% of DES base` : 'Official DES base'}
            </span>
          </div>
        </div>

        {/* 3 Scenario Projections (Conservative, Expected, Optimistic) */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between text-[11px] text-slate-400 font-medium">
            <span>Price &amp; Margin Scenario Analysis</span>
            <span className="text-[10px] text-slate-500">Mandi Min → Modal → Max</span>
          </div>
          <div className="grid sm:grid-cols-3 gap-2 text-[11px]">
            {/* Conservative */}
            <div className="bg-white/5 border border-white/5 rounded-lg p-2.5 space-y-1">
              <div className="flex items-center justify-between font-semibold text-slate-300">
                <span>Conservative</span>
                <span className="text-slate-400 text-[10px]">Min Mandi Price</span>
              </div>
              <div className="text-slate-400 text-[10px]">
                Price: <span className="text-slate-200">₹ {econ.conservativeScenario?.pricePerQuintal?.toLocaleString('en-IN') ?? '—'}/qtl</span>
              </div>
              <div className="text-slate-400 text-[10px]">
                Gross Revenue: <span className="text-slate-200">₹ {Math.round(econ.conservativeScenario?.grossRevenuePerHectare || 0).toLocaleString('en-IN')}/ha</span>
              </div>
              <div className="text-slate-400 text-[10px]">
                Net Margin: <span className={((econ.conservativeScenario?.netOperatingMarginPerHectare || 0) >= 0) ? 'text-emerald-400' : 'text-rose-400'}>
                  ₹ {Math.round(econ.conservativeScenario?.netOperatingMarginPerHectare || 0).toLocaleString('en-IN')}/ha
                </span>
              </div>
              <div className="text-slate-400 text-[10px]">
                BCR: <span className="text-indigo-300 font-bold">{Number(econ.conservativeScenario?.benefitCostRatio || 0).toFixed(2)}x</span>
              </div>
            </div>

            {/* Expected */}
            <div className="bg-indigo-950/20 border border-indigo-500/30 rounded-lg p-2.5 space-y-1">
              <div className="flex items-center justify-between font-semibold text-indigo-300">
                <span>Expected</span>
                <span className="text-indigo-400 text-[10px]">Modal Mandi Price</span>
              </div>
              <div className="text-slate-400 text-[10px]">
                Price: <span className="text-slate-200">₹ {econ.expectedScenario?.pricePerQuintal?.toLocaleString('en-IN') ?? '—'}/qtl</span>
              </div>
              <div className="text-slate-400 text-[10px]">
                Gross Revenue: <span className="text-slate-200">₹ {Math.round(econ.expectedScenario?.grossRevenuePerHectare || 0).toLocaleString('en-IN')}/ha</span>
              </div>
              <div className="text-slate-400 text-[10px]">
                Net Margin: <span className={((econ.expectedScenario?.netOperatingMarginPerHectare || 0) >= 0) ? 'text-emerald-400' : 'text-rose-400'}>
                  ₹ {Math.round(econ.expectedScenario?.netOperatingMarginPerHectare || 0).toLocaleString('en-IN')}/ha
                </span>
              </div>
              <div className="text-slate-400 text-[10px]">
                BCR: <span className="text-indigo-300 font-bold">{Number(econ.expectedScenario?.benefitCostRatio || 0).toFixed(2)}x</span>
              </div>
            </div>

            {/* Optimistic */}
            <div className="bg-white/5 border border-white/5 rounded-lg p-2.5 space-y-1">
              <div className="flex items-center justify-between font-semibold text-slate-300">
                <span>Optimistic</span>
                <span className="text-slate-400 text-[10px]">Max Mandi Price</span>
              </div>
              <div className="text-slate-400 text-[10px]">
                Price: <span className="text-slate-200">₹ {econ.optimisticScenario?.pricePerQuintal?.toLocaleString('en-IN') ?? '—'}/qtl</span>
              </div>
              <div className="text-slate-400 text-[10px]">
                Gross Revenue: <span className="text-slate-200">₹ {Math.round(econ.optimisticScenario?.grossRevenuePerHectare || 0).toLocaleString('en-IN')}/ha</span>
              </div>
              <div className="text-slate-400 text-[10px]">
                Net Margin: <span className={((econ.optimisticScenario?.netOperatingMarginPerHectare || 0) >= 0) ? 'text-emerald-400' : 'text-rose-400'}>
                  ₹ {Math.round(econ.optimisticScenario?.netOperatingMarginPerHectare || 0).toLocaleString('en-IN')}/ha
                </span>
              </div>
              <div className="text-slate-400 text-[10px]">
                BCR: <span className="text-indigo-300 font-bold">{Number(econ.optimisticScenario?.benefitCostRatio || 0).toFixed(2)}x</span>
              </div>
            </div>
          </div>
        </div>

        {/* Provenance Metadata Grid */}
        <div className="bg-black/20 border border-white/5 rounded-lg p-2.5 grid sm:grid-cols-3 gap-2 text-[10px] text-slate-400">
          <div>
            <span className="text-slate-300 font-medium block mb-0.5">🌾 Yield Benchmark:</span>
            <div>Source: {econ.yieldSourcePublication || 'DES, MoA&FW'}</div>
            <div>Period: {econ.yieldReferencePeriod || 'Official'} ({econ.yieldBenchmarkYear ?? '—'})</div>
            <div>Scope: {econ.yieldStateScope || 'State'}</div>
          </div>

          <div>
            <span className="text-slate-300 font-medium block mb-0.5">🪙 Cost A2 Benchmark:</span>
            <div>Cost A2: {econ.costPerHectareA2 ? `₹ ${econ.costPerHectareA2.toLocaleString('en-IN')}/ha` : 'Pending'}</div>
            <div>Source: {econ.costSourcePublication || 'CACP Comprehensive Scheme'}</div>
            <div>Period: {econ.costReferencePeriod || 'Official'} ({econ.costBenchmarkYear ?? '—'})</div>
          </div>

          <div>
            <span className="text-slate-300 font-medium block mb-0.5">🏬 Mandi Market Observation:</span>
            <div>Mandi: {econ.marketPriceMarket || 'District Mandi'}</div>
            <div>
              Arrival: {econ.marketPriceArrivalDate || 'Recent'} ({econ.marketPriceDaysOld ?? '—'}d old)
            </div>
            {econ.marketPriceStale && (
              <span className="text-amber-400 font-semibold block">⚠️ Stale Price (&gt;7 days)</span>
            )}
          </div>
        </div>

        {/* Statutory MSP Reference (if available) */}
        {econ.mspInrPerQuintal !== null && econ.mspInrPerQuintal !== undefined && (
          <div className="bg-emerald-950/20 border border-emerald-500/20 rounded-lg p-2.5 text-[11px] text-emerald-300 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <span className="font-semibold text-emerald-200">🏛️ Statutory Minimum Support Price (MSP): </span>
              <span>₹ {econ.mspInrPerQuintal.toLocaleString('en-IN')} / qtl</span>
              <span className="text-emerald-400/80 text-[10px] ml-1.5">
                ({econ.mspMarketingSeason || 'Statutory floor'} · {econ.mspSourcePublication || 'CACP / Gazette'})
              </span>
              {econ.mspComparisonNote && (
                <div className="text-[10px] text-emerald-400 mt-0.5">{econ.mspComparisonNote}</div>
              )}
            </div>
            <span className="text-[9px] text-slate-400 max-w-xs text-right hidden sm:block">
              Informational procurement reference; not a guaranteed private buyer rate.
            </span>
          </div>
        )}

        {/* Specific Warnings */}
        {econ.warnings && econ.warnings.length > 0 && (
          <div className="space-y-1">
            {econ.warnings.map((w, i) => (
              <div key={i} className="text-[10px] text-amber-300 bg-amber-500/10 border border-amber-500/20 rounded px-2 py-1 flex items-center gap-1.5">
                <span>⚠️</span>
                <span>{w}</span>
              </div>
            ))}
          </div>
        )}

        {/* Descriptive Scenario Disclaimer */}
        <div className="text-[10px] text-slate-500 italic leading-relaxed pt-1 border-t border-white/5">
          ⚖️ <strong>Descriptive Scenario Estimates Only:</strong> Projections are heuristic modeling based on historical government cost/yield surveys and recent mandi arrival prices. They do not constitute guaranteed future income, financial advice, or comprehensive accounting profit. Net Operating Margin over Cost A2 reflects paid-out operational expenses and excludes family labor, rental value of owned land, and capital depreciation (Cost C2).
        </div>
      </div>
    );
  }

  function renderExcludedCropCard(crop: CropStrategyResultDto) {
    const suit = crop.agronomicSuitability;

    return (
      <div
        key={crop.cropId}
        className="bg-rose-950/10 border border-rose-500/20 rounded-xl p-4 text-xs space-y-2 hover:border-rose-500/40 transition-all"
      >
        <div className="flex items-center justify-between">
          <span className="font-bold text-slate-200">{crop.canonicalName}</span>
          <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-rose-500/20 text-rose-300 border border-rose-500/30">
            EXCLUDED
          </span>
        </div>

        {crop.botanicalName && (
          <div className="text-[11px] italic text-slate-500">{crop.botanicalName}</div>
        )}

        <div className="text-rose-300 bg-rose-500/10 p-2 rounded border border-rose-500/20 text-[11px]">
          {suit.summaryExplanation || 'Excluded due to agronomic incompatibility.'}
        </div>

        {suit.exclusionReasons && suit.exclusionReasons.length > 0 && (
          <div className="text-[10px] text-slate-400 space-y-0.5">
            {suit.exclusionReasons.map((r, i) => (
              <div key={i} className="flex items-center gap-1">
                <span>•</span>
                <span className="font-mono text-rose-400">{r.code}:</span>
                <span>{r.message}</span>
              </div>
            ))}
          </div>
        )}

        <div className="text-[10px] text-slate-500 pt-1 border-t border-white/5 space-y-0.5">
          <div>
            Sizing: <strong className="text-rose-400">0.00 hectares</strong> (Agronomically Incompatible with current season/soil).
          </div>
          <div>Economic evaluation is not applicable to excluded crops.</div>
        </div>
      </div>
    );
  }
};
