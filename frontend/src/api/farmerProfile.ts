/**
 * API calls for farmer profile endpoints.
 */

import { apiClient, handleAxiosError } from './apiClient';

export interface CreateFarmerProfileRequest {
  fullName: string;
  phoneNumber?: string;
  primaryState?: string;
  primaryDistrict?: string;
  totalLandHectares?: number;
  yearsOfExperience: number;
  preferredLanguage: string;
}

export interface FarmerProfileResponse {
  id: number;
  ownerId: number;
  ownerUsername: string;
  ownerEmail: string;
  fullName: string;
  phoneNumber?: string;
  primaryState?: string;
  primaryDistrict?: string;
  totalLandHectares?: number;
  yearsOfExperience: number;
  preferredLanguage: string;
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
