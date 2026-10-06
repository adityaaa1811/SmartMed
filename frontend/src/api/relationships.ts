import { apiGet, apiPost } from './client';
import type { ApiEnvelope } from './health';
import { notifyNotificationsChanged } from './notifications';

export type RelationshipType = 'CAREGIVER' | 'DOCTOR';
export type RelationshipStatus = 'PENDING' | 'ACTIVE' | 'REVOKED';

export interface CareRelationship {
  id: number;
  patientName: string;
  patientEmail: string;
  relatedUserName: string;
  relatedUserEmail: string;
  relationshipType: RelationshipType;
  status: RelationshipStatus;
  createdAt: string;
  updatedAt: string;
}

export async function listCareRelationships(): Promise<CareRelationship[]> {
  return (await apiGet<ApiEnvelope<CareRelationship[]>>('/api/v1/relationships', true)).data;
}

export async function createCareRelationship(payload: {
  relatedUserEmail: string;
  relationshipType: RelationshipType;
}): Promise<CareRelationship> {
  const result = (await apiPost<ApiEnvelope<CareRelationship>>('/api/v1/relationships', payload, { auth: true })).data;
  notifyNotificationsChanged();
  return result;
}

export async function acceptCareRelationship(id: number): Promise<CareRelationship> {
  const result = (await apiPost<ApiEnvelope<CareRelationship>>('/api/v1/relationships/' + id + '/accept', {}, { auth: true })).data;
  notifyNotificationsChanged();
  return result;
}

export async function rejectCareRelationship(id: number): Promise<CareRelationship> {
  const result = (await apiPost<ApiEnvelope<CareRelationship>>('/api/v1/relationships/' + id + '/reject', {}, { auth: true })).data;
  notifyNotificationsChanged();
  return result;
}

export async function revokeCareRelationship(id: number): Promise<void> {
  await apiPost<void>('/api/v1/relationships/' + id, undefined, { method: 'DELETE', auth: true });
  notifyNotificationsChanged();
}
