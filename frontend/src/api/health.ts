import { apiGet } from './client';

export interface HealthData {
  status: string;
  application: string;
  apiVersion: string;
  timestamp: string;
}

export interface ApiEnvelope<T> {
  success: boolean;
  message: string | null;
  data: T;
}

export function fetchHealth(): Promise<ApiEnvelope<HealthData>> {
  return apiGet<ApiEnvelope<HealthData>>('/api/v1/health');
}
