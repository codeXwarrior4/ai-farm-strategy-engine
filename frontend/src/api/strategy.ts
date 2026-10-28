/**
 * API calls and domain types for the Composite Farm Strategy Engine.
 */
import { apiClient, handleAxiosError } from './apiClient';
import type { RiskTolerance } from './farmerProfile';

export type { RiskTolerance };

export type GrowingSeason = 'KHARIF' | 'RABI' | 'ZAID';

export type StrategyDecisionTier =
  | 'RECOMMENDED'
  | 'CONDITIONAL'
  | 'DATA_DEFICIENT'
  | 'FINANCIALLY_CONSTRAINED'
  | 'AGRONOMICALLY_EXCLUDED';

export type MarketAttractivenessStatus =
  | 'FAVORABLE'
  | 'MODERATE'
  | 'VOLATILE'
  | 'STALE_DATA'
  | 'NO_MARKET_DATA'
  | 'LOCATION_UNAVAILABLE';

export type WeatherRiskStatus =
  | 'FAVORABLE'
  | 'ELEVATED_RISK'
  | 'ADVERSE_CONDITIONS'
  | 'WEATHER_UNAVAILABLE'
  | 'LOCATION_UNAVAILABLE';

export type SuitabilityLevel =
  | 'HIGHLY_SUITABLE'
  | 'MODERATELY_SUITABLE'
  | 'MARGINAL'
  | 'INCOMPATIBLE'
  | 'INSUFFICIENT_DATA';

export type FinancialFeasibilityStatus =
  | 'FEASIBLE'
  | 'TIGHT_BUDGET'
  | 'INSUFFICIENT_DATA'
  | 'INFEASIBLE';

export type CropCategory =
  | 'CEREAL'
  | 'PULSE'
  | 'OILSEED'
  | 'COMMERCIAL'
  | 'HORTICULTURE'
  | 'FODDER';

export interface ExclusionReason {
  code: string;
  message: string;
}

export interface DataQualityWarning {
  code: string;
  message: string;
  severity: string;
}

export interface SeasonEvaluation {
  season: GrowingSeason;
  sowingWindowStartMonth: number;
  sowingWindowEndMonth: number;
  durationDaysMin: number;
  durationDaysMax: number;
  status: string;
  notes?: string | null;
}

export interface SoilEvaluation {
  farmerSoilType?: string | null;
  suitabilityLevel: string;
  status: string;
  soilNotes?: string | null;
}

export interface WaterEvaluation {
  reportedWaterMonths?: number | null;
  minWaterMonthsRequired?: number | null;
  waterRequirementMmMin?: number | null;
  waterRequirementMmMax?: number | null;
  droughtTolerance?: string | null;
  waterloggingTolerance?: string | null;
  status: string;
  notes?: string | null;
}

export interface CropSuitabilityResult {
  cropId: number;
  canonicalName: string;
  botanicalName?: string | null;
  category: CropCategory;
  overallSuitability: SuitabilityLevel;
  excluded: boolean;
  seasonEvaluation?: SeasonEvaluation | null;
  soilEvaluation?: SoilEvaluation | null;
  waterEvaluation?: WaterEvaluation | null;
  exclusionReasons?: ExclusionReason[];
  dataQualityWarnings?: DataQualityWarning[];
  summaryExplanation?: string | null;
}

export interface CropFinancialFeasibilityResult {
  cropId: number;
  canonicalName: string;
  category: CropCategory;
  status: FinancialFeasibilityStatus;
  costPerHectareA2?: number | null;
  estimatedCapitalRequirement?: number | null;
  availableWorkingCapitalInr?: number | null;
  capitalSurplusOrShortfall?: number | null;
  capitalSufficiencyRatio?: number | null;
  affordableHectares?: number | null;
  benchmarkFound: boolean;
  benchmarkState?: string | null;
  benchmarkSeason?: string | null;
  benchmarkYear?: number | null;
  referencePeriod?: string | null;
  sourcePublication?: string | null;
  evidenceTier?: string | null;
  warnings?: string[];
  explanation?: string | null;
}

export interface MarketAttractivenessResultDto {
  status: MarketAttractivenessStatus;
  commodity?: string | null;
  latestArrivalDate?: string | null;
  daysOld?: number | null;
  modalPrice?: number | null;
  minPrice?: number | null;
  maxPrice?: number | null;
  descriptivePriceSpreadRatio?: number | null;
  mandiName?: string | null;
  state?: string | null;
  district?: string | null;
  warnings?: string[];
  explanation?: string | null;
}

export interface WeatherRiskResultDto {
  status: WeatherRiskStatus;
  precision?: string | null;
  forecastDays?: number | null;
  maxTemperatureCelsius?: number | null;
  totalPrecipitationMm?: number | null;
  maxWindSpeedKmh?: number | null;
  advisoryConditions?: string[];
  warnings?: string[];
  explanation?: string | null;
}

export type EconomicDataSufficiencyStatus =
  | 'SUFFICIENT'
  | 'SUFFICIENT_WITH_WARNINGS'
  | 'DATA_DEFICIENT';

export type EconomicScenarioName = 'CONSERVATIVE' | 'EXPECTED' | 'OPTIMISTIC';

export interface EconomicScenarioDto {
  scenarioName: EconomicScenarioName;
  label: string;
  priceAssumptionDescription: string;
  pricePerQuintal?: number | null;
  priceUnit?: string;
  adjustedYieldQuintalsPerHa?: number | null;
  yieldUnit?: string;
  grossRevenuePerHectare?: number | null;
  revenueUnit?: string;
  costPerHectareA2?: number | null;
  costUnit?: string;
  netOperatingMarginPerHectare?: number | null;
  benefitCostRatio?: number | null;
}

export interface CropEconomicResultDto {
  status: EconomicDataSufficiencyStatus;
  cropId: number;
  canonicalName: string;
  marketCommodityName?: string | null;
  state?: string | null;
  district?: string | null;
  season?: GrowingSeason | null;
  evaluationDate?: string | null;
  benchmarkYieldQuintalsPerHa?: number | null;
  yieldUnit?: string;
  yieldBenchmarkYear?: number | null;
  yieldReferencePeriod?: string | null;
  yieldSourcePublication?: string | null;
  yieldSourceOrganization?: string | null;
  yieldStateScope?: string | null;
  suitabilityLevel?: SuitabilityLevel | null;
  suitabilityYieldMultiplier?: number | null;
  suitabilityMultiplierAssumption?: string | null;
  adjustedYieldQuintalsPerHa?: number | null;
  marketModalPrice?: number | null;
  marketMinPrice?: number | null;
  marketMaxPrice?: number | null;
  marketPriceUnit?: string;
  marketPriceArrivalDate?: string | null;
  marketPriceDaysOld?: number | null;
  marketPriceStale?: boolean;
  marketPriceMarket?: string | null;
  marketPriceSourceProvider?: string | null;
  costPerHectareA2?: number | null;
  costUnit?: string;
  costBenchmarkYear?: number | null;
  costReferencePeriod?: string | null;
  costSourcePublication?: string | null;
  costSourceOrganization?: string | null;
  mspInrPerQuintal?: number | null;
  mspPriceUnit?: string;
  mspCropYear?: number | null;
  mspMarketingSeason?: string | null;
  mspSourcePublication?: string | null;
  mspComparisonNote?: string | null;
  conservativeScenario?: EconomicScenarioDto | null;
  expectedScenario?: EconomicScenarioDto | null;
  optimisticScenario?: EconomicScenarioDto | null;
  expectedGrossRevenuePerHectare?: number | null;
  expectedNetOperatingMarginPerHectare?: number | null;
  expectedBenefitCostRatio?: number | null;
  disclaimer?: string;
  warnings?: string[];
  missingDataReasons?: string[];
  summaryExplanation?: string | null;
}

export interface CropStrategyResultDto {
  cropId: number;
  canonicalName: string;
  botanicalName?: string | null;
  category: CropCategory;
  decisionTier: StrategyDecisionTier;
  rankOrder: number;
  agronomicSuitability: CropSuitabilityResult;
  financialFeasibility: CropFinancialFeasibilityResult;
  marketAttractiveness: MarketAttractivenessResultDto;
  weatherRisk: WeatherRiskResultDto;
  economicEvaluation?: CropEconomicResultDto | null;
  decisionExplanation: string;
  primaryWarnings?: string[];
  missingDataPrompts?: string[];
  recommendedHectares?: number | null;
  affordableHectares?: number | null;
  acreageSizingExplanation?: string | null;
  acreageAllocationModel?: string | null;
}

export interface StrategySummaryDto {
  totalCatalogCropsEvaluated: number;
  recommendedCount: number;
  conditionalCount: number;
  dataDeficientCount: number;
  financiallyConstrainedCount: number;
  agronomicallyExcludedCount: number;
  tightBudgetBufferRatio: number;
  provisionalBufferAssumption: string;
}

export interface FarmStrategyEvaluationRequest {
  targetSeason: GrowingSeason;
  planningYear?: number | null;
  evaluationDate?: string | null; // ISO string (e.g. YYYY-MM-DDTHH:mm:ss)
  cultivableHectaresOverride?: number | null;
  workingCapitalOverride?: number | null;
  riskToleranceOverride?: RiskTolerance | null;
  candidateCropIds?: number[] | null;
}

export interface FarmStrategyEvaluationResponse {
  farmerProfileId: number;
  targetSeason: GrowingSeason;
  planningYear?: number | null;
  evaluatedAt?: string | null;
  primaryState?: string | null;
  primaryDistrict?: string | null;
  evaluatedCultivableHectares?: number | null;
  evaluatedWorkingCapitalInr?: number | null;
  candidateCrops: CropStrategyResultDto[];
  excludedCrops: CropStrategyResultDto[];
  summary: StrategySummaryDto;
  metadata?: Record<string, unknown>;
}

/**
 * Evaluates composite multi-dimensional farm strategy for candidate crops.
 * POST /api/v1/users/{userId}/farmer-profile/strategy/evaluate
 */
export async function evaluateFarmStrategy(
  userId: number,
  request: FarmStrategyEvaluationRequest
): Promise<FarmStrategyEvaluationResponse> {
  try {
    const response = await apiClient.post<FarmStrategyEvaluationResponse>(
      `/users/${userId}/farmer-profile/strategy/evaluate`,
      request
    );
    return response.data;
  } catch (error) {
    return handleAxiosError(error);
  }
}
