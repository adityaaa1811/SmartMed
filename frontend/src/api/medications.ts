import { apiGet, apiPost } from './client';
import type { ApiEnvelope } from './health';

export interface Medication {
  id: number;
  name: string;
  dosage: string;
  frequency: string;
  instructions: string | null;
  startDate: string;
  endDate: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MedicationPayload {
  name: string;
  dosage: string;
  frequency: string;
  instructions: string;
  startDate: string;
  endDate: string | null;
}

export async function listMedications(): Promise<Medication[]> {
  return (await apiGet<ApiEnvelope<Medication[]>>('/api/v1/medications', true)).data;
}

export async function createMedication(payload: MedicationPayload): Promise<Medication> {
  return (await apiPost<ApiEnvelope<Medication>>('/api/v1/medications', payload, { auth: true })).data;
}

export async function updateMedication(id: number, payload: MedicationPayload): Promise<Medication> {
  return (await apiPost<ApiEnvelope<Medication>>(`/api/v1/medications/${id}`, payload, {
    method: 'PUT',
    auth: true,
  })).data;
}

export async function deleteMedication(id: number): Promise<void> {
  await apiPost<void>(`/api/v1/medications/${id}`, undefined, { method: 'DELETE', auth: true });
}
