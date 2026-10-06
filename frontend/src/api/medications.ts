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

export type ScheduleFrequency = 'ONCE_DAILY' | 'TWICE_DAILY' | 'THREE_TIMES_DAILY' | 'FOUR_TIMES_DAILY';
export type DoseStatus = 'PENDING' | 'TAKEN' | 'MISSED' | 'SKIPPED';

export interface MedicationSchedule {
  id: number;
  medicationId: number;
  frequency: ScheduleFrequency;
  timeOfDay: string;
  startDate: string;
  endDate: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SchedulePayload {
  frequency: ScheduleFrequency;
  timeOfDay: string;
  startDate: string;
  endDate: string | null;
  active?: boolean;
}

export interface DoseRecord {
  id: number;
  scheduleId: number;
  medicationId: number;
  medicationName: string;
  dosage: string;
  scheduledDate: string;
  scheduledTime: string;
  status: DoseStatus;
  takenAt: string | null;
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

export async function listSchedules(medicationId: number): Promise<MedicationSchedule[]> {
  return (await apiGet<ApiEnvelope<MedicationSchedule[]>>(`/api/v1/medications/${medicationId}/schedules`, true)).data;
}

export async function createSchedule(medicationId: number, payload: SchedulePayload): Promise<MedicationSchedule> {
  return (await apiPost<ApiEnvelope<MedicationSchedule>>(
    `/api/v1/medications/${medicationId}/schedules`, payload, { auth: true },
  )).data;
}

export async function updateSchedule(id: number, payload: SchedulePayload & { active: boolean }): Promise<MedicationSchedule> {
  return (await apiPost<ApiEnvelope<MedicationSchedule>>(`/api/v1/schedules/${id}`, payload, {
    method: 'PUT', auth: true,
  })).data;
}

export async function deleteSchedule(id: number): Promise<void> {
  await apiPost<void>(`/api/v1/schedules/${id}`, undefined, { method: 'DELETE', auth: true });
}

export async function getTodaysDoses(): Promise<DoseRecord[]> {
  return (await apiGet<ApiEnvelope<DoseRecord[]>>('/api/v1/adherence/today', true)).data;
}

export async function getAdherenceHistory(from?: string, to?: string): Promise<DoseRecord[]> {
  const query = new URLSearchParams();
  if (from) query.set('from', from);
  if (to) query.set('to', to);
  const suffix = query.size > 0 ? `?${query.toString()}` : '';
  return (await apiGet<ApiEnvelope<DoseRecord[]>>(`/api/v1/adherence/history${suffix}`, true)).data;
}

export async function updateDoseStatus(id: number, status: Exclude<DoseStatus, 'PENDING'>): Promise<DoseRecord> {
  return (await apiPost<ApiEnvelope<DoseRecord>>(`/api/v1/adherence/${id}/${status.toLowerCase()}`, {}, { auth: true })).data;
}
