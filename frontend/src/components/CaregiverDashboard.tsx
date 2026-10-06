import { useCallback, useEffect, useState } from 'react';
import { getCaregiverAnalytics, getCaregiverPatients, getCaregiverToday, type PatientAnalytics, type PatientMonitoringSummary } from '../api/monitoring';
import { DoseList, errorText, PatientPicker, RelationshipInbox, AnalyticsView } from './MonitoringWidgets';
import styles from './MonitoringDashboard.module.css';

export default function CaregiverDashboard() {
  const [patients, setPatients] = useState<PatientMonitoringSummary[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [doses, setDoses] = useState<Awaited<ReturnType<typeof getCaregiverToday>>>([]);
  const [analytics, setAnalytics] = useState<PatientAnalytics | null>(null);
  const [loadingPatients, setLoadingPatients] = useState(true);
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refreshPatients = useCallback(async () => {
    setLoadingPatients(true);
    setError(null);
    try {
      const items = await getCaregiverPatients();
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
      setDoses([]);
      setAnalytics(null);
      return;
    }
    let current = true;
    setLoadingDetails(true);
    setError(null);
    Promise.all([getCaregiverToday(selectedId), getCaregiverAnalytics(selectedId)])
      .then(([today, savedAnalytics]) => {
        if (!current) return;
        setDoses(today);
        setAnalytics(savedAnalytics);
      })
      .catch((cause: unknown) => { if (current) setError(errorText(cause)); })
      .finally(() => { if (current) setLoadingDetails(false); });
    return () => { current = false; };
  }, [selectedId]);

  const selected = patients.find((patient) => patient.patientId === selectedId) ?? null;

  return (
    <div id="caregiver-dashboard" className={styles.dashboard}>
      <RelationshipInbox type="CAREGIVER" onChanged={() => void refreshPatients()} />
      <section id="patients" className={styles.section} aria-labelledby="caregiver-patients-heading">
        <div className={styles.sectionHeading}>
          <div><p className={styles.eyebrow}>Caregiver dashboard</p><h2 id="caregiver-patients-heading">Connected patients</h2></div>
          <button type="button" className={styles.refresh} onClick={() => void refreshPatients()} disabled={loadingPatients}>{loadingPatients ? 'Loading…' : 'Refresh'}</button>
        </div>
        {error && <p className={styles.error} role="alert">{error}</p>}
        {loadingPatients && <p className={styles.muted} role="status">Loading connected patients…</p>}
        {!loadingPatients && patients.length === 0 && <p className={styles.empty}>No patients are connected yet. Patient invitations will appear above.</p>}
        {!loadingPatients && patients.length > 0 && <PatientPicker patients={patients} selectedId={selectedId} onSelect={setSelectedId} />}
      </section>

      {selected && <>
        <section className={styles.section} aria-labelledby="caregiver-today-heading">
          <div className={styles.sectionHeading}>
            <div><p className={styles.eyebrow}>Read-only monitoring</p><h2 id="caregiver-today-heading">Today for {selected.patientName}</h2></div>
          </div>
          <p className={styles.muted}>These are dose records already saved by the patient. You cannot change their status.</p>
          <DoseList doses={doses} loading={loadingDetails} />
        </section>
        <section className={styles.section} aria-labelledby="caregiver-analytics-heading">
          <div className={styles.sectionHeading}><div><p className={styles.eyebrow}>Recent progress</p><h2 id="caregiver-analytics-heading">Adherence analytics</h2></div></div>
          <AnalyticsView analytics={analytics} loading={loadingDetails} />
        </section>
      </>}
    </div>
  );
}
