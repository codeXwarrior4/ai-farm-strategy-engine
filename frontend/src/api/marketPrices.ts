import { apiClient, handleAxiosError } from './apiClient';

export type SourceClassification = 'OFFICIAL_API' | 'DEMO_SEEDED' | 'MANUAL_VERIFIED';

export interface MarketPriceItem {
  id: number;
  market: string;
  commodity: string;
  variety: string | null;
  grade: string | null;
  arrivalDate: string; // ISO date 'YYYY-MM-DD'
  minPrice: number;
  maxPrice: number;
  modalPrice: number | null;
  currency: string;
  priceUnit: string;
  retrievalTimestamp: string;
  sourceProvider: string;
  sourceClassification?: SourceClassification;
}

export type FreshnessStatus = 'FRESH' | 'STALE' | 'NO_DATA' | 'NO_LOCATION' | 'DEMO_DATA' | 'API_UNAVAILABLE';

export interface MarketFreshnessMetadata {
  dataAvailable: boolean;
  latestArrivalDate: string | null;
  daysOld: number | null;
  isStale: boolean;
  freshnessThresholdDays: number;
  freshnessStatus: FreshnessStatus;
  sourceClassification?: SourceClassification | null;
  liveSyncStatus?: string;
  freshnessDescription?: string;
}

export interface MarketAttribution {
  providerName: string;
  datasetName: string;
  licenseName: string;
  licenseUrl: string;
  disclaimer: string;
}

export interface FarmerMarketPricesResponse {
  farmerProfileId: number;
  ownerId: number;
  queryState: string | null;
  queryDistrict: string | null;
  queryCommodity: string | null;
  queryMarket?: string | null;
  recordCount: number;
  records: MarketPriceItem[];
  freshness: MarketFreshnessMetadata;
  attribution: MarketAttribution;
  retrievedAt: string;

  // Enhanced market discovery and pagination metadata
  availableMarkets?: string[];
  availableCommodities?: string[];
  page?: number;
  pageSize?: number;
  totalPages?: number;
  totalElements?: number;
}

export interface MarketPriceQueryParams {
  commodity?: string;
  market?: string;
  arrivalDate?: string;
  page?: number;
  limit?: number;
}

export interface MaharashtraDistrict {
  canonicalName: string;
  primaryApiName: string;
  division: string;
  aliases: string[];
}

/**
 * Authoritative client fallback list of all 36 Maharashtra districts across 6 divisions.
 */
export const MAHARASHTRA_DISTRICTS: MaharashtraDistrict[] = [
  { canonicalName: 'Ahmednagar', primaryApiName: 'Ahilyanagar', division: 'Nashik', aliases: ['Ahilyanagar', 'Ahmed Nagar'] },
  { canonicalName: 'Akola', primaryApiName: 'Akola', division: 'Amravati', aliases: [] },
  { canonicalName: 'Amravati', primaryApiName: 'Amravati', division: 'Amravati', aliases: ['Amrawati'] },
  { canonicalName: 'Beed', primaryApiName: 'Beed', division: 'Chhatrapati Sambhajinagar', aliases: ['Bid'] },
  { canonicalName: 'Bhandara', primaryApiName: 'Bhandara', division: 'Nagpur', aliases: [] },
  { canonicalName: 'Buldhana', primaryApiName: 'Buldhana', division: 'Amravati', aliases: ['Buldana'] },
  { canonicalName: 'Chandrapur', primaryApiName: 'Chandrapur', division: 'Nagpur', aliases: ['Chanda'] },
  { canonicalName: 'Chhatrapati Sambhajinagar', primaryApiName: 'Chattrapati Sambhajinagar', division: 'Chhatrapati Sambhajinagar', aliases: ['Chattrapati Sambhajinagar', 'Aurangabad'] },
  { canonicalName: 'Dhule', primaryApiName: 'Dhule', division: 'Nashik', aliases: ['Dhulia'] },
  { canonicalName: 'Gadchiroli', primaryApiName: 'Gadchiroli', division: 'Nagpur', aliases: [] },
  { canonicalName: 'Gondia', primaryApiName: 'Gondia', division: 'Nagpur', aliases: ['Gondiya'] },
  { canonicalName: 'Hingoli', primaryApiName: 'Hingoli', division: 'Chhatrapati Sambhajinagar', aliases: [] },
  { canonicalName: 'Jalgaon', primaryApiName: 'Jalgaon', division: 'Nashik', aliases: ['East Khandesh'] },
  { canonicalName: 'Jalna', primaryApiName: 'Jalna', division: 'Chhatrapati Sambhajinagar', aliases: [] },
  { canonicalName: 'Kolhapur', primaryApiName: 'Kolhapur', division: 'Pune', aliases: [] },
  { canonicalName: 'Latur', primaryApiName: 'Latur', division: 'Chhatrapati Sambhajinagar', aliases: [] },
  { canonicalName: 'Mumbai City', primaryApiName: 'Mumbai', division: 'Konkan', aliases: ['Mumbai', 'Bombay'] },
  { canonicalName: 'Mumbai Suburban', primaryApiName: 'Mumbai Suburban', division: 'Konkan', aliases: ['Bandra Suburban'] },
  { canonicalName: 'Nagpur', primaryApiName: 'Nagpur', division: 'Nagpur', aliases: [] },
  { canonicalName: 'Nanded', primaryApiName: 'Nanded', division: 'Chhatrapati Sambhajinagar', aliases: ['Nanded Waghala'] },
  { canonicalName: 'Nandurbar', primaryApiName: 'Nandurbar', division: 'Nashik', aliases: [] },
  { canonicalName: 'Nashik', primaryApiName: 'Nashik', division: 'Nashik', aliases: ['Nasik'] },
  { canonicalName: 'Dharashiv', primaryApiName: 'Dharashiv', division: 'Chhatrapati Sambhajinagar', aliases: ['Osmanabad'] },
  { canonicalName: 'Palghar', primaryApiName: 'Palghar', division: 'Konkan', aliases: [] },
  { canonicalName: 'Parbhani', primaryApiName: 'Parbhani', division: 'Chhatrapati Sambhajinagar', aliases: [] },
  { canonicalName: 'Pune', primaryApiName: 'Pune', division: 'Pune', aliases: ['Poona'] },
  { canonicalName: 'Raigad', primaryApiName: 'Raigad', division: 'Konkan', aliases: ['Raigarh', 'Alibag'] },
  { canonicalName: 'Ratnagiri', primaryApiName: 'Ratnagiri', division: 'Konkan', aliases: [] },
  { canonicalName: 'Sangli', primaryApiName: 'Sangli', division: 'Pune', aliases: ['Sangli Miraj'] },
  { canonicalName: 'Satara', primaryApiName: 'Satara', division: 'Pune', aliases: [] },
  { canonicalName: 'Sindhudurg', primaryApiName: 'Sindhudurg', division: 'Konkan', aliases: ['Oras'] },
  { canonicalName: 'Solapur', primaryApiName: 'Solapur', division: 'Pune', aliases: ['Sholapur'] },
  { canonicalName: 'Thane', primaryApiName: 'Thane', division: 'Konkan', aliases: ['Thana'] },
  { canonicalName: 'Wardha', primaryApiName: 'Wardha', division: 'Nagpur', aliases: [] },
  { canonicalName: 'Washim', primaryApiName: 'Washim', division: 'Amravati', aliases: ['Basim'] },
  { canonicalName: 'Yavatmal', primaryApiName: 'Yavatmal', division: 'Amravati', aliases: ['Yeotmal'] },
];

/**
 * Fetch mandi market prices for the given farmer profile location.
 * GET /api/v1/users/{userId}/farmer-profile/market-prices
 */
export async function getFarmerMarketPrices(
  userId: number,
  params?: MarketPriceQueryParams
): Promise<FarmerMarketPricesResponse> {
  try {
    const cleanParams: Record<string, string | number> = {};
    if (params?.commodity && params.commodity.trim()) {
      cleanParams.commodity = params.commodity.trim();
    }
    if (params?.market && params.market.trim()) {
      cleanParams.market = params.market.trim();
    }
    if (params?.arrivalDate && params.arrivalDate.trim()) {
      cleanParams.arrivalDate = params.arrivalDate.trim();
    }
    if (params?.page !== undefined && params.page >= 0) {
      cleanParams.page = params.page;
    }
    if (params?.limit !== undefined && params.limit >= 1 && params.limit <= 50) {
      cleanParams.limit = params.limit;
    }

    const response = await apiClient.get<FarmerMarketPricesResponse>(
      `/users/${userId}/farmer-profile/market-prices`,
      { params: cleanParams }
    );
    return response.data;
  } catch (error) {
    return handleAxiosError(error);
  }
}

/**
 * Retrieve all 36 authoritative Maharashtra districts from backend catalog (with static fallback).
 */
export async function getMaharashtraDistricts(): Promise<MaharashtraDistrict[]> {
  try {
    const response = await apiClient.get<MaharashtraDistrict[]>('/market-prices/districts/maharashtra');
    if (response.data && response.data.length > 0) {
      return response.data;
    }
    return MAHARASHTRA_DISTRICTS;
  } catch {
    return MAHARASHTRA_DISTRICTS;
  }
}

/**
 * Retrieve distinct APMC markets for a specific Maharashtra district.
 */
export async function getDistrictMarkets(district: string): Promise<string[]> {
  try {
    const encoded = encodeURIComponent(district.trim());
    const response = await apiClient.get<string[]>(`/market-prices/districts/maharashtra/${encoded}/markets`);
    return response.data || [];
  } catch {
    return [];
  }
}

/**
 * Retrieve distinct commodities for a specific Maharashtra district.
 */
export async function getDistrictCommodities(district: string): Promise<string[]> {
  try {
    const encoded = encodeURIComponent(district.trim());
    const response = await apiClient.get<string[]>(`/market-prices/districts/maharashtra/${encoded}/commodities`);
    return response.data || [];
  } catch {
    return [];
  }
}
