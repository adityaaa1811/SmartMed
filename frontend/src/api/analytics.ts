import { apiGet } from './client';
import type { ApiEnvelope } from './health';

export interface AnalyticsSummary {
  from: string;
  to: string;
  totalDoses: number;
  taken: number;
  missed: number;
  skipped: number;
  pending: number;
  adherencePercentage: number;
}

export interface DailyAnalytics {
  date: string;
  taken: number;
  missed: number;
  skipped: number;
  pending: number;
  adherencePercentage: number;
}

export interface MedicationAnalytics {
  medicationId: number;
  medicationName: string;
  taken: number;
  missed: number;
  skipped: number;
  pending: number;
  adherencePercentage: number;
}

function queryForRange(from: string, to: string): string {
  const query = new URLSearchParams({ from, to });
  return `?${query.toString()}`;
}

export async function getAnalyticsSummary(from: string, to: string): Promise<AnalyticsSummary> {
  return (await apiGet<ApiEnvelope<AnalyticsSummary>>(`/api/v1/analytics/summary${queryForRange(from, to)}`, true)).data;
}

export async function getDailyAnalytics(from: string, to: string): Promise<DailyAnalytics[]> {
  return (await apiGet<ApiEnvelope<DailyAnalytics[]>>(`/api/v1/analytics/daily${queryForRange(from, to)}`, true)).data;
}

export async function getMedicationAnalytics(from: string, to: string): Promise<MedicationAnalytics[]> {
  return (await apiGet<ApiEnvelope<MedicationAnalytics[]>>(`/api/v1/analytics/medications${queryForRange(from, to)}`, true)).data;
}
