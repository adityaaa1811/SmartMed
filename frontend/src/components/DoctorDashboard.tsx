import { useCallback, useEffect, useState } from 'react';
import { getDoctorAnalytics, getDoctorOverview, getDoctorPatients, getDoctorToday, type PatientAnalytics, type PatientMonitoringSummary, type PatientOverview } from '../api/monitoring';
import { AnalyticsView, DoseList, errorText, PatientPicker, RelationshipInbox } from './MonitoringWidgets';
import styles from './MonitoringDashboard.module.css';

export default function DoctorDashboard() {
  const [patients, setPatients] = useState<PatientMonitoringSummary[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [overview, setOverview] = useState<PatientOverview | null>(null);
  const [doses, setDoses] = useState<Awaited<ReturnType<typeof getDoctorToday>>>([]);
  const [analytics, setAnalytics] = useState<PatientAnalytics | null>(null);
  const [loadingPatients, setLoadingPatients] = useState(true);
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refreshPatients = useCallback(async () => {
    setLoadingPatients(true);
    setError(null);
    try {
      const items = await getDoctorPatients();
      setPatients(items);
      setSelectedId((current) => current !== null && items.some((patient) => patient.patientId === current)
        ? current
        : items[0]?.patientId ?? null);
    } catch (cause) {
      setError(errorText(cause));
    } finally {
      setLoadingPatients(false);
    }
  }, []);

  useEffect(() => { void refreshPatients(); }, [refreshPatients]);

  useEffect(() => {
    if (selectedId === null) {
      setOverview(null);
      setDoses([]);
      setAnalytics(null);
      return;
    }
    let current = true;
    setLoadingDetails(true);
    setError(null);
    Promise.all([
      getDoctorOverview(selectedId),
      getDoctorToday(selectedId),
      getDoctorAnalytics(selectedId),
    ])
      .then(([patientOverview, today, savedAnalytics]) => {
        if (!current) return;
        setOverview(patientOverview);
        setDoses(today);
        setAnalytics(savedAnalytics);
      })
      .catch((cause: unknown) => { if (current) setError(errorText(cause)); })
      .finally(() => { if (current) setLoadingDetails(false); });
    return () => { current = false; };
  }, [selectedId]);

  return (
    <div id="doctor-dashboard" className={styles.dashboard}>
      <RelationshipInbox type="DOCTOR" onChanged={() => void refreshPatients()} />
      <section id="patients" className={styles.section} aria-labelledby="doctor-patients-heading">
        <div className={styles.sectionHeading}>
          <div><p className={styles.eyebrow}>Doctor dashboard</p><h2 id="doctor-patients-heading">Connected patients</h2></div>
          <button type="button" className={styles.refresh} onClick={() => void refreshPatients()} disabled={loadingPatients}>{loadingPatients ? 'Loading…' : 'Refresh'}</button>
        </div>
        {error && <p className={styles.error} role="alert">{error}</p>}
        {loadingPatients && <p className={styles.muted} role="status">Loading connected patients…</p>}
        {!loadingPatients && patients.length === 0 && <p className={styles.empty}>No patients are connected yet. Patient invitations will appear above.</p>}
        {!loadingPatients && patients.length > 0 && <PatientPicker patients={patients} selectedId={selectedId} onSelect={setSelectedId} />}
      </section>

      {selectedId !== null && <>
        {error && <p className={styles.error} role="alert">{error}</p>}
        <section className={styles.section} aria-labelledby="doctor-overview-heading">
          <div className={styles.sectionHeading}><div><p className={styles.eyebrow}>Read-only monitoring</p><h2 id="doctor-overview-heading">Patient overview</h2></div></div>
          {loadingDetails && !overview && <p className={styles.muted} role="status">Loading patient overview…</p>}
          {overview && <>
            <div className={styles.patientIdentity}><strong>{overview.patientName}</strong><span>{overview.patientEmail}</span></div>
            <div className={styles.miniMetrics}>
              <Metric label="Adherence" value={overview.adherenceSummary.adherencePercentage.toFixed(1) + '%'} />
              <Metric label="Taken" value={overview.adherenceSummary.taken.toString()} />
              <Metric label="Missed" value={overview.adherenceSummary.missed.toString()} />
              <Metric label="Skipped" value={overview.adherenceSummary.skipped.toString()} />
              <Metric label="Pending" value={overview.adherenceSummary.pending.toString()} />
            </div>
            <div className={styles.detailGrid}>
              <div>
                <h3 className={styles.subheading}>Current medications</h3>
                {overview.medications.length === 0 ? <p className={styles.empty}>No current medications.</p> : <div className={styles.medicationList}>
                  {overview.medications.map((medication) => <article className={styles.medication} key={medication.id}>
                    <h3>{medication.name}</h3>
                    <p>{medication.dosage} · {medication.frequency}</p>
                    <p>{medication.startDate}{medication.endDate ? ' — ' + medication.endDate : ' · Ongoing'}</p>
                    {medication.instructions && <p>{medication.instructions}</p>}
                  </article>)}
                </div>}
              </div>
              <div>
                <h3 className={styles.subheading}>Active schedules</h3>
                {overview.activeSchedules.length === 0 ? <p className={styles.empty}>No active schedules today.</p> : <div className={styles.medicationList}>
                  {overview.activeSchedules.map((schedule) => {
                    const medication = overview.medications.find((item) => item.id === schedule.medicationId);
                    return <div className={styles.schedule} key={schedule.id}>
                      <strong>{medication?.name ?? 'Medication'}</strong>
                      <span>{schedule.frequency.toLowerCase().replaceAll('_', ' ')} · {schedule.timeOfDay.slice(0, 5)}</span>
                    </div>;
                  })}
                </div>}
              </div>
            </div>
          </>}
        </section>
        <section className={styles.section} aria-labelledby="doctor-today-heading">
          <div className={styles.sectionHeading}><div><p className={styles.eyebrow}>Saved dose records</p><h2 id="doctor-today-heading">Today’s doses</h2></div></div>
          <p className={styles.muted}>Monitoring is read-only. Dose records are not created by opening this page.</p>
          <DoseList doses={doses} loading={loadingDetails} />
        </section>
        <section className={styles.section} aria-labelledby="doctor-analytics-heading">
          <div className={styles.sectionHeading}><div><p className={styles.eyebrow}>Recent progress</p><h2 id="doctor-analytics-heading">Adherence analytics</h2></div></div>
          <AnalyticsView analytics={analytics} loading={loadingDetails} />
        </section>
      </>}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return <article className={styles.metric}><span>{label}</span><strong>{value}</strong></article>;
}
