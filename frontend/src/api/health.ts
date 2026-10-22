/**
 * API calls for health endpoints (backend + ML service).
 */

import axios from 'axios';
import { apiClient, handleAxiosError } from './apiClient';

export interface BackendHealthResponse {
  status: string;
  service: string;
  version: string;
  timestamp: string;
}

export interface MlHealthResponse {
  status: string;
  service: string;
  version: string;
  timestamp: string;
}

export async function checkBackendHealth(): Promise<BackendHealthResponse> {
  try {
    const response = await apiClient.get<BackendHealthResponse>('/health');
    return response.data;
  } catch (error) {
    return handleAxiosError(error);
  }
}

export async function checkMlHealth(): Promise<MlHealthResponse> {
  const ML_URL = import.meta.env.VITE_ML_SERVICE_URL as string;
  try {
    const response = await axios.get<MlHealthResponse>(`${ML_URL}/health`, {
      timeout: 5_000,
    });
    return response.data;
  } catch {
    throw new Error('ML service is not reachable. Is the FastAPI server running on port 8000?');
  }
}
