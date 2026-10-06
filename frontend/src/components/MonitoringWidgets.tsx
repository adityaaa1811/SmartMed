import { useCallback, useEffect, useState } from 'react';
import {
  ArcElement,
  CategoryScale,
  Chart as ChartJS,
  Legend,
  LinearScale,
  LineElement,
  PointElement,
  Tooltip,
} from 'chart.js';
import { Doughnut, Line } from 'react-chartjs-2';
import { ApiError } from '../api/client';
import {
  acceptCareRelationship,
  listCareRelationships,
  rejectCareRelationship,
  type CareRelationship,
  type RelationshipType,
} from '../api/relationships';
import type { MonitoringDose, PatientAnalytics, PatientMonitoringSummary } from '../api/monitoring';
import styles from './MonitoringDashboard.module.css';

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, ArcElement, Tooltip, Legend);

export function errorText(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return 'SmartMed could not load this information. Try again.';
}

export function RelationshipInbox({ type, onChanged }: { type: RelationshipType; onChanged: () => void }) {
  const [requests, setRequests] = useState<CareRelationship[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const all = await listCareRelationships();
      setRequests(all.filter((item) => item.relationshipType === type && item.status === 'PENDING'));
    } catch (cause) {
      setError(errorText(cause));
    } finally {
      setLoading(false);
    }
  }, [type]);

  useEffect(() => { void refresh(); }, [refresh]);

  async function respond(id: number, accept: boolean) {
    setBusyId(id);
    setError(null);
    try {
      if (accept) await acceptCareRelationship(id);
      else await rejectCareRelationship(id);
      await refresh();
      onChanged();
    } catch (cause) {
      setError(errorText(cause));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className={styles.section} aria-labelledby="relationship-inbox-heading">
      <div className={styles.sectionHeading}><div><p className={styles.eyebrow}>Consent requests</p><h2 id="relationship-inbox-heading">Invitations</h2></div></div>
      {error && <p className={styles.error} role="alert">{error}</p>}
      {loading && <p className={styles.muted} role="status">Loading invitations…</p>}
      {!loading && requests.length === 0 && <p className={styles.empty}>No pending invitations.</p>}
      {!loading && requests.map((request) => <article className={styles.invitation} key={request.id}>
        <div><strong>{request.patientName}</strong><span>{request.patientEmail} invited you to connect as a {type.toLowerCase()}.</span></div>
        <div className={styles.actions}>
          <button type="button" onClick={() => void respond(request.id, true)} disabled={busyId !== null}>{busyId === request.id ? 'Saving…' : 'Accept'}</button>
          <button type="button" className={styles.secondary} onClick={() => void respond(request.id, false)} disabled={busyId !== null}>Decline</button>
        </div>
      </article>)}
    </section>
  );
}

export function PatientPicker({
  patients,
  selectedId,
  onSelect,
}: {
  patients: PatientMonitoringSummary[];
  selectedId: number | null;
  onSelect: (id: number) => void;
}) {
  return <div className={styles.patientGrid}>
    {patients.map((patient) => <button
      type="button"
      className={selectedId === patient.patientId ? styles.patientSelected : styles.patientCard}
      aria-pressed={selectedId === patient.patientId}
      key={patient.patientId}
      onClick={() => onSelect(patient.patientId)}
    >
      <span className={styles.patientName}>{patient.patientName}</span>
      <span className={styles.patientEmail}>{patient.patientEmail}</span>
      <span className={styles.patientRate}>{patient.adherencePercentage.toFixed(1)}% adherence</span>
      <span className={styles.patientCounts}>Taken {patient.taken} · Missed {patient.missed} · Skipped {patient.skipped} · Pending {patient.pending}</span>
    </button>)}
  </div>;
}

export function DoseList({ doses, loading }: { doses: MonitoringDose[]; loading: boolean }) {
  if (loading) return <p className={styles.muted} role="status">Loading today’s saved dose records…</p>;
  if (doses.length === 0) return <p className={styles.empty}>No dose records have been saved for today.</p>;
  return <div className={styles.doseList}>
    {doses.map((dose) => <article className={styles.dose} key={dose.doseId}>
      <div><strong>{dose.medicationName}</strong><span>{dose.scheduledDate} · {dose.scheduledTime.slice(0, 5)}</span></div>
      <span className={styles.status} data-status={dose.status}>{dose.status.toLowerCase()}</span>
    </article>)}
  </div>;
}

export function AnalyticsView({ analytics, loading }: { analytics: PatientAnalytics | null; loading: boolean }) {
  if (loading && !analytics) return <p className={styles.muted} role="status">Loading adherence analytics…</p>;
  if (!analytics) return <p className={styles.empty}>Analytics are unavailable for this patient.</p>;
  const daily = analytics.daily;
  const lineData = {
    labels: daily.map((item) => item.date),
    datasets: [{
      label: 'Adherence',
      data: daily.map((item) => item.adherencePercentage),
      borderColor: '#375534',
      pointBackgroundColor: '#6b9071',
      backgroundColor: 'rgba(107, 144, 113, 0.14)',
      fill: true,
      tension: 0.3,
    }],
  };
  const summary = analytics.summary;
  const donutData = {
    labels: ['Taken', 'Missed', 'Skipped'],
    datasets: [{
      data: [summary.taken, summary.missed, summary.skipped],
      backgroundColor: ['#6b9071', '#b87969', '#aeb9ac'],
      borderColor: '#fffdf6',
      borderWidth: 3,
    }],
  };
  const hasCompleted = summary.taken + summary.missed + summary.skipped > 0;

  return <div className={styles.analytics}>
    <div className={styles.miniMetrics}>
      <Metric label="Adherence" value={summary.adherencePercentage.toFixed(1) + '%'} />
      <Metric label="Taken" value={summary.taken.toString()} />
      <Metric label="Missed" value={summary.missed.toString()} />
      <Metric label="Skipped" value={summary.skipped.toString()} />
      <Metric label="Pending" value={summary.pending.toString()} />
    </div>
    {summary.totalDoses === 0 ? <p className={styles.empty}>No dose records in this reporting period.</p> : <div className={styles.chartGrid}>
      <article className={styles.chartCard}>
        <h3>Recent adherence</h3>
        {daily.length === 0 ? <p className={styles.empty}>No daily records available.</p> : <div className={styles.lineChart}>
          <Line data={lineData} options={{
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: { y: { min: 0, max: 100, ticks: { callback: (value) => value + '%' } } },
          }} />
        </div>}
        <p className={styles.chartNote}>Pending doses are excluded; only dates with saved records appear.</p>
      </article>
      <article className={styles.chartCard}>
        <h3>Dose status</h3>
        {!hasCompleted ? <p className={styles.empty}>No completed dose statuses.</p> : <div className={styles.doughnut}>
          <Doughnut data={donutData} options={{ responsive: true, maintainAspectRatio: false, cutout: '66%', plugins: { legend: { position: 'bottom' } } }} />
        </div>}
      </article>
    </div>}
    {loading && <p className={styles.muted} role="status">Updating analytics…</p>}
  </div>;
}

export function Metric({ label, value }: { label: string; value: string }) {
  return <article className={styles.metric}><span>{label}</span><strong>{value}</strong></article>;
}
