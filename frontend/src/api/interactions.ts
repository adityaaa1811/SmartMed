import { apiPost } from './client';
import type { ApiEnvelope } from './health';

export type InteractionCheckStatus = 'SUCCESS' | 'NO_DATA' | 'PROVIDER_UNAVAILABLE';
export type InteractionSeverity = 'LOW' | 'MODERATE' | 'HIGH' | 'UNKNOWN';

export interface InteractionRecord {
  medicationA: string;
  medicationB: string;
  severity: InteractionSeverity;
  description: string;
}

export interface InteractionCheckResponse {
  status: InteractionCheckStatus;
  checkedMedicationCount: number;
  providerId: string | null;
  interactions: InteractionRecord[];
}

export async function checkMedicationInteractions(medicationIds: number[]): Promise<InteractionCheckResponse> {
  const response = await apiPost<ApiEnvelope<InteractionCheckResponse>>(
    '/api/v1/interactions/check', { medicationIds }, { auth: true },
  );
  return response.data;
}
