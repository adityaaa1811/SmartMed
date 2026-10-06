import { apiGet } from './client';
import type { ApiEnvelope } from './health';
import type { AnalyticsSummary, DailyAnalytics, MedicationAnalytics } from './analytics';
import type { Medication, MedicationSchedule, DoseStatus } from './medications';

export interface PatientMonitoringSummary {
  patientId: number;
  patientName: string;
  patientEmail: string;
  adherencePercentage: number;
  taken: number;
  missed: number;
  skipped: number;
  pending: number;
}

export interface MonitoringDose {
  doseId: number;
  medicationName: string;
  scheduledDate: string;
  scheduledTime: string;
  status: DoseStatus;
  takenAt: string | null;
}

export interface PatientAnalytics {
  summary: AnalyticsSummary;
  daily: DailyAnalytics[];
  medications: MedicationAnalytics[];
}

export interface PatientOverview {
  patientId: number;
  patientName: string;
  patientEmail: string;
  medications: Medication[];
  activeSchedules: MedicationSchedule[];
  adherenceSummary: AnalyticsSummary;
}

function rangeSuffix(from?: string, to?: string): string {
  const params = new URLSearchParams();
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  return params.size ? '?' + params.toString() : '';
}

export async function getCaregiverPatients(): Promise<PatientMonitoringSummary[]> {
  return (await apiGet<ApiEnvelope<PatientMonitoringSummary[]>>('/api/v1/caregiver/patients', true)).data;
}

export async function getCaregiverToday(patientId: number): Promise<MonitoringDose[]> {
  return (await apiGet<ApiEnvelope<MonitoringDose[]>>('/api/v1/caregiver/patients/' + patientId + '/today', true)).data;
}

export async function getCaregiverAnalytics(patientId: number, from?: string, to?: string): Promise<PatientAnalytics> {
  return (await apiGet<ApiEnvelope<PatientAnalytics>>(
    '/api/v1/caregiver/patients/' + patientId + '/analytics' + rangeSuffix(from, to), true,
  )).data;
}

export async function getDoctorPatients(): Promise<PatientMonitoringSummary[]> {
  return (await apiGet<ApiEnvelope<PatientMonitoringSummary[]>>('/api/v1/doctor/patients', true)).data;
}

export async function getDoctorOverview(patientId: number): Promise<PatientOverview> {
  return (await apiGet<ApiEnvelope<PatientOverview>>('/api/v1/doctor/patients/' + patientId, true)).data;
}

export async function getDoctorToday(patientId: number): Promise<MonitoringDose[]> {
  return (await apiGet<ApiEnvelope<MonitoringDose[]>>('/api/v1/doctor/patients/' + patientId + '/today', true)).data;
}

export async function getDoctorAnalytics(patientId: number, from?: string, to?: string): Promise<PatientAnalytics> {
  return (await apiGet<ApiEnvelope<PatientAnalytics>>(
    '/api/v1/doctor/patients/' + patientId + '/analytics' + rangeSuffix(from, to), true,
  )).data;
}
