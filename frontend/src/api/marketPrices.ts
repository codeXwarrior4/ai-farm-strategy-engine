import { apiClient, handleAxiosError } from './apiClient';

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
}

export type FreshnessStatus = 'FRESH' | 'STALE' | 'NO_DATA' | 'NO_LOCATION';

export interface MarketFreshnessMetadata {
  dataAvailable: boolean;
  latestArrivalDate: string | null;
  daysOld: number | null;
  isStale: boolean;
  freshnessThresholdDays: number;
  freshnessStatus: FreshnessStatus;
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
  recordCount: number;
  records: MarketPriceItem[];
  freshness: MarketFreshnessMetadata;
  attribution: MarketAttribution;
  retrievedAt: string;
}

export interface MarketPriceQueryParams {
  commodity?: string;
  arrivalDate?: string;
  limit?: number;
}

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
    if (params?.arrivalDate && params.arrivalDate.trim()) {
      cleanParams.arrivalDate = params.arrivalDate.trim();
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
