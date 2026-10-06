import { useEffect, useState } from 'react';
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  markNotificationUnread,
  type SmartMedNotification,
} from '../api/notifications';
import styles from './NotificationBell.module.css';

const PAGE_SIZE = 10;

function typeLabel(type: SmartMedNotification['type']): string {
  return type.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function timeLabel(value: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<SmartMedNotification[]>([]);
  const [unreadCount, setUnreadCount] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [markingAll, setMarkingAll] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  async function refreshUnreadCount() {
    try {
      setUnreadCount(await getUnreadNotificationCount());
    } catch {
      setError('Could not load notification count. Try again.');
    }
  }

  async function loadFirstPage() {
    setLoading(true);
    setError(null);
    try {
      const [result, count] = await Promise.all([getNotifications({ page: 0, size: PAGE_SIZE }), getUnreadNotificationCount()]);
      setNotifications(result.items);
      setPage(result.page);
      setHasNext(result.hasNext);
      setUnreadCount(count);
    } catch {
      setError('Could not load notifications. Try again.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refreshUnreadCount();
  }, []);

  async function togglePanel() {
    const nextOpen = !open;
    setOpen(nextOpen);
    setStatusMessage(null);
    if (nextOpen) await loadFirstPage();
  }

  async function loadMore() {
    setLoadingMore(true);
    setError(null);
    try {
      const result = await getNotifications({ page: page + 1, size: PAGE_SIZE });
      setNotifications((current) => {
        const existing = new Set(current.map((item) => item.id));
        return [...current, ...result.items.filter((item) => !existing.has(item.id))];
      });
      setPage(result.page);
      setHasNext(result.hasNext);
    } catch {
      setError('Could not load more notifications. Try again.');
    } finally {
      setLoadingMore(false);
    }
  }

  async function toggleReadState(notification: SmartMedNotification) {
    setSavingId(notification.id);
    setError(null);
    setStatusMessage(null);
    try {
      const updated = notification.read
        ? await markNotificationUnread(notification.id)
        : await markNotificationRead(notification.id);
      setNotifications((current) => current.map((item) => item.id === updated.id ? updated : item));
      await refreshUnreadCount();
      setStatusMessage(updated.read ? 'Notification marked read.' : 'Notification marked unread.');
    } catch {
      setError('Could not update this notification. Try again.');
    } finally {
      setSavingId(null);
    }
  }

  async function markAllRead() {
    setMarkingAll(true);
    setError(null);
    setStatusMessage(null);
    try {
      const result = await markAllNotificationsRead();
      setNotifications((current) => current.map((item) => ({ ...item, read: true })));
      setUnreadCount(0);
      setStatusMessage(result.updatedCount === 0 ? 'No unread notifications.' : 'All notifications marked read.');
    } catch {
      setError('Could not mark all notifications read. Try again.');
    } finally {
      setMarkingAll(false);
    }
  }

  return (
    <div className={styles.root}>
      <button
        className={styles.bellButton}
        type="button"
        aria-label={`Notifications${unreadCount ? `, ${unreadCount} unread` : ''}`}
        aria-expanded={open}
        aria-controls="notification-panel"
        onClick={() => void togglePanel()}
      >
        <svg className={styles.bellIcon} viewBox="0 0 24 24" aria-hidden="true">
          <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
        </svg>
        {unreadCount !== null && unreadCount > 0 && <span className={styles.badge}>{unreadCount > 99 ? '99+' : unreadCount}</span>}
      </button>
      {open && <section className={styles.panel} id="notification-panel" aria-label="Notifications">
        <div className={styles.panelHeader}>
          <div><p className={styles.eyebrow}>Your updates</p><h2>Notifications</h2></div>
          <button type="button" className={styles.textButton} onClick={() => void markAllRead()} disabled={markingAll || unreadCount === 0}>
            {markingAll ? 'Saving…' : 'Mark all read'}
          </button>
        </div>
        {error && <p className={styles.error} role="alert">{error}</p>}
        {statusMessage && <p className={styles.success} role="status">{statusMessage}</p>}
        {loading ? <p className={styles.empty} role="status">Loading notifications…</p> : notifications.length === 0 ? (
          <p className={styles.empty}>No notifications yet.</p>
        ) : <div className={styles.list}>
          {notifications.map((notification) => <article className={styles.item} data-read={notification.read} key={notification.id}>
            <div className={styles.itemHeader}>
              <span className={styles.type}>{typeLabel(notification.type)}</span>
              <time dateTime={notification.createdAt}>{timeLabel(notification.createdAt)}</time>
            </div>
            <h3>{notification.title}</h3>
            <p>{notification.message}</p>
            <button className={styles.textButton} type="button" disabled={savingId === notification.id} onClick={() => void toggleReadState(notification)}>
              {savingId === notification.id ? 'Saving…' : notification.read ? 'Mark unread' : 'Mark read'}
            </button>
          </article>)}
        </div>}
        {hasNext && !loading && <button className={styles.loadMore} type="button" onClick={() => void loadMore()} disabled={loadingMore}>
          {loadingMore ? 'Loading…' : 'Load more'}
        </button>}
      </section>}
    </div>
  );
}
