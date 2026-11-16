import React, { useEffect, useState, useCallback, useRef } from 'react';
import {
  getFarmerMarketPrices,
  type FarmerMarketPricesResponse,
  type MarketPriceQueryParams,
} from '../api/marketPrices';
import { ApiError } from '../api/apiClient';

interface FarmerMarketPricesSectionProps {
  userId: number;
  primaryState?: string | null;
  primaryDistrict?: string | null;
  onEditLocation?: () => void;
  refreshKey?: number;
  onMarketLoaded?: (data: FarmerMarketPricesResponse | null, loadedUserId?: number) => void;
}

export const FarmerMarketPricesSection: React.FC<FarmerMarketPricesSectionProps> = ({
  userId,
  primaryState,
  primaryDistrict,
  onEditLocation,
  refreshKey,
  onMarketLoaded,
}) => {
  const [marketData, setMarketData] = useState<FarmerMarketPricesResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [errorStatus, setErrorStatus] = useState<number | null>(null);

  // Request identity tracker to prevent asynchronous stale-data race
  const activeRequestIdRef = useRef<number>(0);
  const onMarketLoadedRef = useRef(onMarketLoaded);

  useEffect(() => {
    onMarketLoadedRef.current = onMarketLoaded;
  }, [onMarketLoaded]);

  // Filter form state
  const [commodityFilter, setCommodityFilter] = useState<string>('');
  const [marketFilter, setMarketFilter] = useState<string>('');
  const [arrivalDateFilter, setArrivalDateFilter] = useState<string>('');
  const [limitFilter, setLimitFilter] = useState<number>(20);
  const [currentPage, setCurrentPage] = useState<number>(0);

  // Active query parameters applied
  const [activeParams, setActiveParams] = useState<MarketPriceQueryParams>({
    page: 0,
    limit: 20,
  });

  const hasLocation = Boolean(
    primaryState && primaryState.trim().length > 0 &&
    primaryDistrict && primaryDistrict.trim().length > 0
  );

  const fetchPrices = useCallback(() => {
    const requestId = ++activeRequestIdRef.current;

    if (!userId || userId <= 0) {
      setMarketData(null);
      setError(null);
      onMarketLoadedRef.current?.(null, userId);
      return;
    }

    if (!hasLocation) {
      setMarketData(null);
      setError(null);
      onMarketLoadedRef.current?.(null, userId);
      return;
    }

    setIsLoading(true);
    setError(null);
    setErrorStatus(null);

    getFarmerMarketPrices(userId, activeParams)
      .then((data) => {
        if (requestId !== activeRequestIdRef.current) return;
        setMarketData(data);
        onMarketLoadedRef.current?.(data, userId);
        setIsLoading(false);
      })
      .catch((err: unknown) => {
        if (requestId !== activeRequestIdRef.current) return;
        setIsLoading(false);
        setMarketData(null);
        onMarketLoadedRef.current?.(null, userId);
        if (err instanceof ApiError) {
          setErrorStatus(err.status);
          if (err.status === 401) {
            setError('Authentication expired or missing JWT. Please log in again.');
          } else if (err.status === 403) {
            setError('Access denied. You are not authorized to view market prices for this user.');
          } else if (err.status === 404) {
            setError('Farmer profile not found for this user.');
          } else if (err.status === 400) {
            setError(err.data.message || 'Invalid query parameters. Please check your filter inputs.');
          } else {
            setError(err.data.message || 'Failed to retrieve market prices.');
          }
        } else if (err instanceof Error) {
          setError(err.message);
        } else {
          setError('An unexpected error occurred while fetching market prices.');
        }
      });
  }, [userId, hasLocation, activeParams]);

  // When location changes or refreshKey increments, reset page and re-fetch
  useEffect(() => {
    setCurrentPage(0);
    setActiveParams(prev => ({
      ...prev,
      page: 0,
    }));
  }, [primaryState, primaryDistrict, refreshKey]);

  useEffect(() => {
    fetchPrices();
    return () => {
      activeRequestIdRef.current++;
    };
  }, [fetchPrices]);

  const handleApplyFilters = (e: React.FormEvent) => {
    e.preventDefault();
    setCurrentPage(0);
    setActiveParams({
      commodity: commodityFilter.trim() || undefined,
      market: marketFilter.trim() || undefined,
      arrivalDate: arrivalDateFilter.trim() || undefined,
      page: 0,
      limit: limitFilter,
    });
  };

  const handleClearFilters = () => {
    setCommodityFilter('');
    setMarketFilter('');
    setArrivalDateFilter('');
    setLimitFilter(20);
    setCurrentPage(0);
    setActiveParams({ page: 0, limit: 20 });
  };

  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
    setActiveParams(prev => ({
      ...prev,
      page: newPage,
    }));
  };

  const hasActiveFilters = Boolean(
    (activeParams.commodity && activeParams.commodity.length > 0) ||
    (activeParams.market && activeParams.market.length > 0) ||
    (activeParams.arrivalDate && activeParams.arrivalDate.length > 0) ||
    (activeParams.limit && activeParams.limit !== 20) ||
    (activeParams.page && activeParams.page > 0)
  );

  const formatDateTime = (isoString?: string): string => {
    if (!isoString) return '—';
    try {
      const d = new Date(isoString);
      return d.toLocaleString(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
      });
    } catch {
      return isoString;
    }
  };

  const formatDate = (dateStr?: string): string => {
    if (!dateStr) return '—';
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      });
    } catch {
      return dateStr;
    }
  };

  const totalPages = marketData?.totalPages ?? 1;
  const pageNum = marketData?.page ?? currentPage;
  const totalRecords = marketData?.totalElements ?? marketData?.recordCount ?? 0;

  return (
    <section
      className="glass-card p-6 mt-8 border-indigo-500/20 text-slate-100"
      aria-labelledby="market-prices-heading"
    >
      {/* SECTION HEADER */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-4 border-b border-white/10 gap-3">
        <div className="flex items-center gap-3">
          <span className="text-2xl" role="img" aria-label="Market Icon">
            🏪
          </span>
          <div>
            <h2 id="market-prices-heading" className="text-lg font-bold text-slate-100">
              Mandi Market Prices
            </h2>
            <p className="text-xs text-slate-400">
              Daily wholesale commodity arrival rates for{' '}
              <span className="text-slate-200 font-semibold">
                {primaryDistrict && primaryState
                  ? `${primaryDistrict}, ${primaryState}`
                  : 'Unspecified District'}
              </span>
            </p>
          </div>
        </div>

        {/* Header Badges & Actions */}
        <div className="flex flex-wrap items-center gap-2">
          {isLoading && (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-indigo-500/10 text-indigo-300 border border-indigo-500/20 animate-pulse">
              <span className="inline-block w-2 h-2 rounded-full bg-indigo-400 animate-ping" />
              Loading Mandi Rates...
            </span>
          )}

          {!isLoading && marketData?.freshness && (
            <>
              {marketData.freshness.sourceClassification === 'OFFICIAL_API' && (
                <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/15 text-emerald-300 border border-emerald-500/40">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                  OFFICIAL AGMARKNET DATA
                </span>
              )}

              {marketData.freshness.sourceClassification === 'DEMO_SEEDED' && (
                <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-semibold bg-purple-500/15 text-purple-300 border border-purple-500/40">
                  <span>🧪</span>
                  DEMO DATA
                </span>
              )}

              {marketData.freshness.freshnessStatus === 'FRESH' && (
                <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-300 border border-emerald-500/30">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                  Fresh ({formatDate(marketData.freshness.latestArrivalDate ?? undefined)})
                </span>
              )}

              {marketData.freshness.freshnessStatus === 'STALE' && (
                <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-medium bg-amber-500/15 text-amber-300 border border-amber-500/30">
                  <span>⚠️</span>
                  STALE DATA ({marketData.freshness.daysOld}d old)
                </span>
              )}

              {marketData.freshness.freshnessStatus === 'NO_DATA' && (
                <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-medium bg-slate-500/20 text-slate-300 border border-slate-500/30">
                  <span>ℹ️</span>
                  NO OFFICIAL DATA AVAILABLE
                </span>
              )}

              {marketData.freshness.liveSyncStatus === 'API_KEY_NOT_CONFIGURED' && (
                <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-medium bg-slate-700/50 text-slate-300 border border-slate-600/40" title="DATA_GOV_IN_API_KEY environment variable is not configured on backend">
                  <span>🔒</span>
                  LIVE SYNC UNAVAILABLE
                </span>
              )}
            </>
          )}

          <button
            type="button"
            onClick={fetchPrices}
            disabled={isLoading || !hasLocation}
            className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-white/5 hover:bg-white/10 active:bg-white/15 text-slate-300 border border-white/10 text-xs font-medium transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            title="Refresh market prices"
          >
            <span className={isLoading ? 'animate-spin' : ''}>🔄</span> Refresh
          </button>
        </div>
      </div>

      {/* STATE 1: MISSING LOCATION */}
      {!hasLocation && (
        <div className="mt-6 p-5 rounded-xl bg-amber-950/20 border border-amber-800/30 text-amber-200">
          <div className="flex items-start gap-3">
            <span className="text-xl">📍</span>
            <div>
              <h3 className="text-sm font-semibold mb-1">
                Primary District & State Required
              </h3>
              <p className="text-xs text-amber-200/90 leading-relaxed mb-3">
                Mandi market price queries require a saved primary state and district in your profile. Please configure your district above to view local agricultural commodity rates.
              </p>
              {onEditLocation && (
                <button
                  type="button"
                  onClick={onEditLocation}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-amber-500/20 hover:bg-amber-500/30 text-amber-200 border border-amber-500/40 text-xs font-medium transition-colors"
                >
                  <span>✏️</span> Set Primary District
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* STATE 2: ERROR WITH RETRY */}
      {hasLocation && !isLoading && error && (
        <div className="mt-6 p-5 rounded-xl bg-red-950/20 border border-red-800/30 text-red-200">
          <div className="flex items-start gap-3">
            <span className="text-xl">⚠️</span>
            <div className="flex-1">
              <h3 className="text-sm font-semibold mb-1">
                {errorStatus === 401 || errorStatus === 403
                  ? 'Authorization Required'
                  : errorStatus === 404
                    ? 'Profile Not Found'
                    : 'Market Data Retrieval Failed'}
              </h3>
              <p className="text-xs text-red-200/90 leading-relaxed mb-3">{error}</p>
              <button
                type="button"
                onClick={fetchPrices}
                className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-red-500/20 hover:bg-red-500/30 text-red-200 border border-red-500/40 text-xs font-medium transition-colors"
              >
                <span>🔄</span> Retry Request
              </button>
            </div>
          </div>
        </div>
      )}

      {/* CONTROLS & CONTENT (When location is available) */}
      {hasLocation && (
        <div className="mt-5 space-y-5">
          {/* AVAILABLE APMC MANDIS IN DISTRICT */}
          {marketData?.availableMarkets && marketData.availableMarkets.length > 0 && (
            <div className="p-3.5 rounded-xl bg-slate-900/60 border border-white/5 flex flex-wrap items-center gap-2">
              <span className="text-xs text-slate-400 font-medium flex items-center gap-1.5">
                <span>🏛️</span> APMC Mandis in {primaryDistrict}:
              </span>
              {marketData.availableMarkets.map((mkt) => (
                <button
                  key={mkt}
                  type="button"
                  onClick={() => {
                    const next = marketFilter === mkt ? '' : mkt;
                    setMarketFilter(next);
                    setCurrentPage(0);
                    setActiveParams(prev => ({
                      ...prev,
                      market: next || undefined,
                      page: 0,
                    }));
                  }}
                  className={`text-xs px-2.5 py-0.5 rounded-full border transition-all ${marketFilter === mkt
                    ? 'bg-indigo-600 text-white border-indigo-500 font-semibold'
                    : 'bg-white/5 hover:bg-white/10 text-slate-300 border-white/10'
                    }`}
                >
                  {mkt}
                </button>
              ))}
            </div>
          )}

          {/* FILTER FORM */}
          <form
            onSubmit={handleApplyFilters}
            className="p-4 rounded-xl bg-white/5 border border-white/10 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3 items-end"
          >
            <div>
              <label htmlFor="commodity-filter" className="block text-xs font-medium text-slate-300 mb-1">
                Commodity Filter
              </label>
              <input
                id="commodity-filter"
                type="text"
                value={commodityFilter}
                onChange={(e) => setCommodityFilter(e.target.value)}
                placeholder="e.g. Wheat, Cotton, Soyabean..."
                className="w-full px-3 py-1.5 text-xs rounded-lg bg-slate-900/60 border border-white/10 text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-400"
              />
            </div>

            <div>
              <label htmlFor="market-filter" className="block text-xs font-medium text-slate-300 mb-1">
                APMC Mandi Yard
              </label>
              <input
                id="market-filter"
                type="text"
                value={marketFilter}
                onChange={(e) => setMarketFilter(e.target.value)}
                placeholder="e.g. Kolhapur, Pune..."
                className="w-full px-3 py-1.5 text-xs rounded-lg bg-slate-900/60 border border-white/10 text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-400"
              />
            </div>

            <div>
              <label htmlFor="arrival-date-filter" className="block text-xs font-medium text-slate-300 mb-1">
                Arrival Date
              </label>
              <input
                id="arrival-date-filter"
                type="date"
                value={arrivalDateFilter}
                onChange={(e) => setArrivalDateFilter(e.target.value)}
                className="w-full px-3 py-1.5 text-xs rounded-lg bg-slate-900/60 border border-white/10 text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-400"
              />
            </div>

            <div>
              <label htmlFor="limit-filter" className="block text-xs font-medium text-slate-300 mb-1">
                Results Limit
              </label>
              <select
                id="limit-filter"
                value={limitFilter}
                onChange={(e) => setLimitFilter(Number(e.target.value))}
                className="w-full px-3 py-1.5 text-xs rounded-lg bg-slate-900/60 border border-white/10 text-slate-100 focus:outline-none focus:border-indigo-400"
              >
                <option value={10}>10 records</option>
                <option value={20}>20 records (default)</option>
                <option value={50}>50 records (maximum)</option>
              </select>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="submit"
                disabled={isLoading}
                className="flex-1 px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 active:bg-indigo-700 text-white text-xs font-medium transition-colors disabled:opacity-50"
              >
                Apply Filters
              </button>
              {hasActiveFilters && (
                <button
                  type="button"
                  onClick={handleClearFilters}
                  disabled={isLoading}
                  className="px-2.5 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-slate-300 text-xs font-medium border border-white/10 transition-colors"
                  title="Clear all filters"
                >
                  Clear
                </button>
              )}
            </div>
          </form>

          {/* STALE DATA WARNING BANNER */}
          {marketData?.freshness?.isStale && (
            <div className="p-4 rounded-xl bg-amber-950/20 border border-amber-500/30 text-amber-200">
              <div className="flex items-start gap-3">
                <span className="text-lg">⚠️</span>
                <div>
                  <h4 className="text-xs font-bold text-amber-300 uppercase tracking-wider mb-1">
                    Stale Market Prices Notice
                  </h4>
                  <p className="text-xs text-amber-200/90 leading-relaxed">
                    The latest arrival date recorded for this district is{' '}
                    <span className="font-semibold text-amber-100">
                      {formatDate(marketData.freshness.latestArrivalDate ?? undefined)}
                    </span>{' '}
                    ({marketData.freshness.daysOld} days old). This exceeds the freshness policy threshold of{' '}
                    {marketData.freshness.freshnessThresholdDays} days. These wholesale rates may not reflect current mandi auction prices.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* DEMO DATA NOTICE BANNER */}
          {marketData?.freshness?.freshnessStatus === 'DEMO_DATA' && (
            <div className="p-4 rounded-xl bg-purple-950/20 border border-purple-500/30 text-purple-200">
              <div className="flex items-start gap-3">
                <span className="text-lg">🧪</span>
                <div>
                  <h4 className="text-xs font-bold text-purple-300 uppercase tracking-wider mb-1">
                    Demonstration Mandi Prices Notice
                  </h4>
                  <p className="text-xs text-purple-200/90 leading-relaxed">
                    The rates shown below are seeded demonstration sample records for development and UI preview.
                    Live official observations from the government data.gov.in AGMARKNET feed will replace these records once synchronized by an administrator.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* STATE 3: EMPTY DATA */}
          {!isLoading && !error && marketData && marketData.records.length === 0 && (
            <div className="p-8 rounded-xl bg-white/5 border border-white/10 text-center">
              <span className="text-3xl block mb-2" role="img" aria-label="Empty Box">
                📦
              </span>
              <h3 className="text-sm font-semibold text-slate-200 mb-1">
                No Official AGMARKNET Records Available
              </h3>
              <p className="text-xs text-slate-400 max-w-md mx-auto mb-4">
                {hasActiveFilters
                  ? `No price records match your current filter criteria for ${primaryDistrict}, ${primaryState}. Try modifying your commodity, APMC mandi, or arrival date filters.`
                  : `No official AGMARKNET records are currently available for this district or selected date.`}
              </p>
              {hasActiveFilters && (
                <button
                  type="button"
                  onClick={handleClearFilters}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-600/30 hover:bg-indigo-600/40 text-indigo-200 border border-indigo-500/40 text-xs font-medium transition-colors"
                >
                  <span>↺</span> Reset Filters
                </button>
              )}
            </div>
          )}

          {/* STATE 4: SUCCESS WITH RECORDS */}
          {!isLoading && marketData && marketData.records.length > 0 && (
            <div className="space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between text-xs text-slate-400 px-1 gap-2">
                <span>
                  Showing{' '}
                  <span className="font-medium text-slate-200">{marketData.records.length}</span> of{' '}
                  <span className="font-medium text-slate-200">{totalRecords}</span> recorded arrivals
                  {totalPages > 1 && (
                    <span className="ml-1 text-slate-500">
                      (Page {pageNum + 1} of {totalPages})
                    </span>
                  )}
                </span>
                <div className="flex items-center gap-2">
                  {marketData.queryCommodity && (
                    <span className="bg-indigo-500/10 text-indigo-300 px-2 py-0.5 rounded border border-indigo-500/20 font-mono text-[11px]">
                      Commodity: {marketData.queryCommodity}
                    </span>
                  )}
                  {marketData.queryMarket && (
                    <span className="bg-indigo-500/10 text-indigo-300 px-2 py-0.5 rounded border border-indigo-500/20 font-mono text-[11px]">
                      Mandi: {marketData.queryMarket}
                    </span>
                  )}
                </div>
              </div>

              {/* RECORDS GRID */}
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                {marketData.records.map((item) => (
                  <div
                    key={item.id}
                    className="p-4 rounded-xl bg-slate-900/50 border border-white/10 hover:border-indigo-500/30 transition-all flex flex-col justify-between"
                  >
                    <div>
                      {/* Commodity & Market Header */}
                      <div className="flex items-start justify-between gap-2 mb-2">
                        <div>
                          <h4 className="text-sm font-bold text-slate-100 leading-snug">
                            {item.commodity}
                          </h4>
                          <span className="text-xs text-indigo-300 font-medium block">
                            APMC Mandi: <span className="text-slate-200 font-semibold">{item.market}</span>
                          </span>
                          <span className="text-[11px] text-slate-400 block">
                            District: {primaryDistrict || 'Local District'}
                          </span>
                        </div>
                        <div className="flex flex-col items-end gap-1">
                          <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-white/5 border border-white/10 text-slate-300">
                            {formatDate(item.arrivalDate)}
                          </span>
                          <span
                            className={`text-[10px] font-semibold px-1.5 py-0.5 rounded uppercase tracking-wider ${item.sourceClassification === 'OFFICIAL_API'
                              ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                              : 'bg-purple-500/20 text-purple-300 border border-purple-500/30'
                              }`}
                          >
                            {item.sourceClassification === 'OFFICIAL_API' ? 'Official AGMARKNET' : 'Demo Data'}
                          </span>
                        </div>
                      </div>

                      {/* Variety & Grade tags */}
                      <div className="flex flex-wrap items-center gap-1.5 mb-3 text-[11px]">
                        <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-white/5">
                          Variety: {item.variety || 'Not specified'}
                        </span>
                        <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-white/5">
                          Grade: {item.grade || 'FAQ'}
                        </span>
                      </div>

                      {/* Prices Breakdown */}
                      <div className="space-y-2 pt-2 border-t border-white/5">
                        {/* Modal Price Highlight */}
                        <div className="p-2.5 rounded-lg bg-emerald-950/20 border border-emerald-500/20 flex items-center justify-between">
                          <span className="text-xs text-emerald-300/90 font-medium">
                            Modal Price
                          </span>
                          <span className="text-sm font-bold text-emerald-300">
                            {item.modalPrice !== null && item.modalPrice !== undefined
                              ? `₹ ${Number(item.modalPrice).toLocaleString('en-IN')}`
                              : 'Not reported'}
                          </span>
                        </div>

                        {/* Min / Max Range */}
                        <div className="grid grid-cols-2 gap-2 text-xs">
                          <div className="p-2 rounded-lg bg-white/5 border border-white/5">
                            <span className="text-[11px] text-slate-400 block">Min Rate</span>
                            <span className="text-slate-200 font-semibold">
                              ₹ {Number(item.minPrice).toLocaleString('en-IN')}
                            </span>
                          </div>
                          <div className="p-2 rounded-lg bg-white/5 border border-white/5">
                            <span className="text-[11px] text-slate-400 block">Max Rate</span>
                            <span className="text-slate-200 font-semibold">
                              ₹ {Number(item.maxPrice).toLocaleString('en-IN')}
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Unit and Currency footer */}
                    <div className="mt-3 pt-2 border-t border-white/5 flex items-center justify-between text-[11px] text-slate-500">
                      <span>Unit: {item.priceUnit}</span>
                      <span>Currency: {item.currency}</span>
                    </div>
                  </div>
                ))}
              </div>

              {/* PAGINATION CONTROLS */}
              {totalPages > 1 && (
                <div className="flex items-center justify-between pt-4 border-t border-white/10 text-xs">
                  <button
                    type="button"
                    onClick={() => handlePageChange(Math.max(0, pageNum - 1))}
                    disabled={pageNum === 0 || isLoading}
                    className="px-3 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-slate-300 disabled:opacity-30 disabled:cursor-not-allowed border border-white/10 transition-colors"
                  >
                    ← Previous Page
                  </button>
                  <span className="text-slate-400 font-mono text-[11px]">
                    Page {pageNum + 1} of {totalPages}
                  </span>
                  <button
                    type="button"
                    onClick={() => handlePageChange(Math.min(totalPages - 1, pageNum + 1))}
                    disabled={pageNum >= totalPages - 1 || isLoading}
                    className="px-3 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-slate-300 disabled:opacity-30 disabled:cursor-not-allowed border border-white/10 transition-colors"
                  >
                    Next Page →
                  </button>
                </div>
              )}
            </div>
          )}

          {/* ATTRIBUTION & ADVISORY DISCLAIMER FOOTER */}
          {marketData?.attribution && (
            <div className="mt-6 pt-4 border-t border-white/10 space-y-2 text-xs text-slate-400">
              {/* Attribution */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div>
                  <span className="text-slate-500 font-medium">Source: </span>
                  <span className="text-slate-300 font-medium">{marketData.attribution.providerName}</span>
                  {' — '}
                  <span>{marketData.attribution.datasetName}</span>
                </div>
                <div>
                  <a
                    href={marketData.attribution.licenseUrl}
                    target="_blank"
                    rel="noreferrer noopener"
                    className="text-indigo-400 hover:text-indigo-300 underline font-medium transition-colors"
                  >
                    {marketData.attribution.licenseName}
                  </a>
                </div>
              </div>

              {/* Advisory Disclaimer */}
              <div className="p-3 rounded-lg bg-white/5 border border-white/5 text-[11px] text-slate-400 leading-relaxed">
                <span className="font-semibold text-slate-300">Market Advisory: </span>
                {marketData.attribution.disclaimer}
              </div>

              {/* Timestamp */}
              <div className="text-right text-[11px] text-slate-500 font-mono">
                System Retrieved: {formatDateTime(marketData.retrievedAt)}
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  );
};
