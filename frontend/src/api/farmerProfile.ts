/**
 * API calls for farmer profile endpoints.
 */

import { apiClient, handleAxiosError } from './apiClient';

export type SoilType =
  | 'ALLUVIAL'
  | 'BLACK'
  | 'RED'
  | 'LATERITE'
  | 'SANDY'
  | 'LOAMY'
  | 'CLAYEY'
  | 'OTHER';

export type IrrigationSource =
  | 'BOREWELL'
  | 'OPEN_WELL'
  | 'CANAL'
  | 'RIVER'
  | 'RAIN_FED'
  | 'TANK'
  | 'DRIP'
  | 'SPRINKLER'
  | 'OTHER';

export type RiskTolerance = 'LOW' | 'MEDIUM' | 'HIGH';

export interface CreateFarmerProfileRequest {
  fullName: string;
  phoneNumber?: string;
  primaryState?: string;
  primaryDistrict?: string;
  totalLandHectares?: number;
  yearsOfExperience: number;
  preferredLanguage: string;
  // Phase 2A fields
  cultivableHectares?: number;
  soilType?: SoilType;
  irrigationSource?: IrrigationSource;
  waterAvailabilityMonths?: number;
  workingCapitalInr?: number;
  riskTolerance?: RiskTolerance;
  // Phase 2B.1 fields
  latitude?: number;
  longitude?: number;
}

export interface UpdateFarmerProfileRequest {
  fullName: string;
  phoneNumber?: string;
  primaryState?: string;
  primaryDistrict?: string;
  totalLandHectares?: number;
  yearsOfExperience: number;
  preferredLanguage: string;
  // Phase 2A fields
  cultivableHectares?: number;
  soilType?: SoilType;
  irrigationSource?: IrrigationSource;
  waterAvailabilityMonths?: number;
  workingCapitalInr?: number;
  riskTolerance?: RiskTolerance;
  // Phase 2B.1 fields
  latitude?: number;
  longitude?: number;
}

export interface FarmerProfileResponse {
  id: number;
  ownerId: number;
  ownerUsername: string;
  ownerEmail: string;
  fullName: string;
  phoneNumber?: string | null;
  primaryState?: string | null;
  primaryDistrict?: string | null;
  totalLandHectares?: number | null;
  yearsOfExperience: number;
  preferredLanguage: string;
  // Phase 2A fields
  cultivableHectares?: number | null;
  soilType?: SoilType | null;
  irrigationSource?: IrrigationSource | null;
  waterAvailabilityMonths?: number | null;
  workingCapitalInr?: number | null;
  riskTolerance?: RiskTolerance | null;
  // Phase 2B.1 fields
  latitude?: number | null;
  longitude?: number | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Create a new farmer profile for the specified user.
 * POST /api/v1/users/{userId}/farmer-profile
 */
export async function createFarmerProfile(
  userId: number,
  data: CreateFarmerProfileRequest
): Promise<FarmerProfileResponse> {
  try {
    const response = await apiClient.post<FarmerProfileResponse>(
      `/users/${userId}/farmer-profile`,
      data
    );
    return response.data;
  } catch (error) {
    return handleAxiosError(error);
  }
}

/**
 * Get the farmer profile for a given user.
 * GET /api/v1/users/{userId}/farmer-profile
 */
export async function getFarmerProfile(userId: number): Promise<FarmerProfileResponse> {
  try {
    const response = await apiClient.get<FarmerProfileResponse>(
      `/users/${userId}/farmer-profile`
    );
    return response.data;
  } catch (error) {
    return handleAxiosError(error);
  }
}

/**
 * Update the existing farmer profile for a given user.
 * PUT /api/v1/users/{userId}/farmer-profile
 */
export async function updateFarmerProfile(
  userId: number,
  data: UpdateFarmerProfileRequest
): Promise<FarmerProfileResponse> {
  try {
    const response = await apiClient.put<FarmerProfileResponse>(
      `/users/${userId}/farmer-profile`,
      data
    );
    return response.data;
  } catch (error) {
    return handleAxiosError(error);
  }
}
