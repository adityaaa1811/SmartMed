import { apiGet, apiPost } from './client';
import type { ApiEnvelope } from './health';

export type NotificationType = 'MISSED_DOSE' | 'ADHERENCE_ATTENTION' | 'CARE_RELATIONSHIP';
export type NotificationRelatedEntityType = 'DOSE' | 'CARE_RELATIONSHIP' | 'ADHERENCE_PERIOD';

export interface SmartMedNotification {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  relatedEntityType: NotificationRelatedEntityType;
  relatedEntityId: number;
  read: boolean;
  createdAt: string;
}

export interface NotificationPage {
  items: SmartMedNotification[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface UnreadNotificationCount {
  count: number;
}

export interface MarkAllReadResult {
  updatedCount: number;
}

export async function getNotifications(options: {
  unreadOnly?: boolean;
  page?: number;
  size?: number;
} = {}): Promise<NotificationPage> {
  const query = new URLSearchParams({
    unreadOnly: String(options.unreadOnly ?? false),
    page: String(options.page ?? 0),
    size: String(options.size ?? 10),
  });
  return (await apiGet<ApiEnvelope<NotificationPage>>(`/api/v1/notifications?${query.toString()}`, true)).data;
}

export async function getUnreadNotificationCount(): Promise<number> {
  return (await apiGet<ApiEnvelope<UnreadNotificationCount>>('/api/v1/notifications/unread-count', true)).data.count;
}

export async function markNotificationRead(id: number): Promise<SmartMedNotification> {
  return (await apiPost<ApiEnvelope<SmartMedNotification>>(`/api/v1/notifications/${id}/read`, undefined, { auth: true })).data;
}

export async function markNotificationUnread(id: number): Promise<SmartMedNotification> {
  return (await apiPost<ApiEnvelope<SmartMedNotification>>(`/api/v1/notifications/${id}/unread`, undefined, { auth: true })).data;
}

export async function markAllNotificationsRead(): Promise<MarkAllReadResult> {
  return (await apiPost<ApiEnvelope<MarkAllReadResult>>('/api/v1/notifications/read-all', undefined, { auth: true })).data;
}

export function notifyNotificationsChanged(): void {
  window.dispatchEvent(new Event('smartmed:notifications-changed'));
}
