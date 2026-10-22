/**
 * Centralized API client using Axios.
 *
 * - Base URL comes from environment variable VITE_API_BASE_URL.
 * - All requests include JSON content type by default.
 * - Error responses are passed through as ApiError instances.
 * - No secrets are hardcoded here.
 */

import axios, { type AxiosInstance, type AxiosError } from 'axios';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string) || 'http://localhost:8080/api/v1';

if (!API_BASE_URL) {
  console.warn(
    '[api-client] VITE_API_BASE_URL is not set. ' +
    'Copy frontend/.env.example to .env.local and fill in the value.'
  );
}

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10_000,
});

const TOKEN_KEY = 'farm_engine_jwt';
let inMemoryToken: string | null = null;

export function getAuthToken(): string | null {
  if (inMemoryToken) return inMemoryToken;
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setAuthToken(token: string | null): void {
  inMemoryToken = token;
  try {
    if (token) {
      localStorage.setItem(TOKEN_KEY, token);
    } else {
      localStorage.removeItem(TOKEN_KEY);
    }
  } catch {
    // Ignore storage quota/permission exceptions in restricted environments
  }
}

apiClient.interceptors.request.use((config) => {
  const token = getAuthToken();
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** Structured error returned from the backend. */
export interface ApiErrorData {
  status: number;
  error: string;
  message: string;
  timestamp: string;
  path: string;
  fieldErrors?: Record<string, string>;
}

/** Typed wrapper around Axios errors from our backend. */
export class ApiError extends Error {
  public readonly status: number;
  public readonly data: ApiErrorData;

  constructor(status: number, data: ApiErrorData) {
    super(data.message);
    this.status = status;
    this.data = data;
  }
}

/**
 * Convert an Axios error into an ApiError if the backend returned a
 * structured error body; otherwise re-throw the original error.
 */
export function handleAxiosError(error: unknown): never {
  if (axios.isAxiosError(error)) {
    const axiosErr = error as AxiosError<ApiErrorData>;
    if (axiosErr.response?.data) {
      throw new ApiError(axiosErr.response.status, axiosErr.response.data);
    }
    // Network or timeout error
    throw new ApiError(0, {
      status: 0,
      error: 'Network Error',
      message: axiosErr.message || 'Could not reach the server. Is the backend running?',
      timestamp: new Date().toISOString(),
      path: '',
    });
  }
  throw error;
}
