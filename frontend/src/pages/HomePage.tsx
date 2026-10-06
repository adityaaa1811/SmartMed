import { FormEvent, useCallback, useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { fetchCurrentUser, login, register, type UserDto } from '../api/auth';
import {
  createMedication,
  createSchedule,
  deleteMedication,
  deleteSchedule,
  getAdherenceHistory,
  getTodaysDoses,
  listMedications,
  listSchedules,
  updateDoseStatus,
  updateMedication,
  updateSchedule,
  type DoseRecord,
  type DoseStatus,
  type Medication,
  type MedicationPayload,
  type MedicationSchedule,
  type ScheduleFrequency,
  type SchedulePayload,
} from '../api/medications';
import { clearAccessToken, getAccessToken } from '../auth/tokenStorage';
import AnalyticsDashboard from '../components/AnalyticsDashboard';
import styles from './HomePage.module.css';

type AuthMode = 'login' | 'register';
type MedicationFields = Omit<MedicationPayload, 'endDate'> & { endDate: string };
type ScheduleFields = Omit<SchedulePayload, 'endDate'> & { endDate: string };

const blankMedication = (): MedicationFields => ({
  name: '', dosage: '', frequency: '', instructions: '',
  startDate: new Date().toISOString().slice(0, 10), endDate: '',
});

const blankSchedule = (): ScheduleFields => ({
  frequency: 'ONCE_DAILY', timeOfDay: '08:00',
  startDate: new Date().toISOString().slice(0, 10), endDate: '',
});

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    return error.status === 401 ? 'Your session has expired. Please sign in again.' : error.message;
  }
  return 'Could not reach SmartMed. Check that the backend is running and try again.';
}

function frequencyLabel(value: ScheduleFrequency): string {
  return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function timeLabel(value: string): string {
  return value.slice(0, 5);
}

export default function HomePage() {
  const [user, setUser] = useState<UserDto | null>(null);
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [medications, setMedications] = useState<Medication[]>([]);
  const [medicationFields, setMedicationFields] = useState<MedicationFields>(blankMedication);
  const [editingMedicationId, setEditingMedicationId] = useState<number | null>(null);
  const [scheduleLists, setScheduleLists] = useState<Record<number, MedicationSchedule[]>>({});
  const [openMedicationId, setOpenMedicationId] = useState<number | null>(null);
  const [scheduleFields, setScheduleFields] = useState<ScheduleFields>(blankSchedule);
  const [editingScheduleId, setEditingScheduleId] = useState<number | null>(null);
  const [todayDoses, setTodayDoses] = useState<DoseRecord[]>([]);
  const [history, setHistory] = useState<DoseRecord[]>([]);
  const [authBusy, setAuthBusy] = useState(false);
  const [loadingMedications, setLoadingMedications] = useState(false);
  const [loadingDoses, setLoadingDoses] = useState(false);
  const [loadingSchedules, setLoadingSchedules] = useState(false);
  const [savingMedication, setSavingMedication] = useState(false);
  const [savingSchedule, setSavingSchedule] = useState(false);
  const [updatingDoseId, setUpdatingDoseId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refreshMedications = useCallback(async () => {
    setLoadingMedications(true);
    try {
      setMedications(await listMedications());
    } catch (err) {
      setError(messageFor(err));
      if (err instanceof ApiError && err.status === 401) {
        clearAccessToken();
        setUser(null);
      }
    } finally {
      setLoadingMedications(false);
    }
  }, []);

  const refreshAdherence = useCallback(async () => {
    setLoadingDoses(true);
    try {
      const [today, savedHistory] = await Promise.all([getTodaysDoses(), getAdherenceHistory()]);
      setTodayDoses(today);
      setHistory(savedHistory);
    } catch (err) {
      setError(messageFor(err));
      if (err instanceof ApiError && err.status === 401) {
        clearAccessToken();
        setUser(null);
      }
    } finally {
      setLoadingDoses(false);
    }
  }, []);

  useEffect(() => {
    if (!getAccessToken()) return;
    void fetchCurrentUser()
      .then((currentUser) => {
        if (currentUser.role !== 'PATIENT') {
          clearAccessToken();
          setError('Medication management is currently available to patient accounts.');
          return;
        }
        setUser(currentUser);
        void refreshMedications();
        void refreshAdherence();
      })
      .catch((err: unknown) => {
        clearAccessToken();
        setError(messageFor(err));
      });
  }, [refreshAdherence, refreshMedications]);

  async function handleAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAuthBusy(true);
    setError(null);
    try {
      const result = authMode === 'register'
        ? await register({ fullName, email, password, role: 'PATIENT' })
        : await login({ email, password });
      if (result.user.role !== 'PATIENT') {
        clearAccessToken();
        setError('Medication management is currently available to patient accounts.');
        return;
      }
      setUser(result.user);
      setPassword('');
      await Promise.all([refreshMedications(), refreshAdherence()]);
    } catch (err) {
      setError(messageFor(err));
    } finally {
      setAuthBusy(false);
    }
  }

  async function handleMedicationSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSavingMedication(true);
    setError(null);
    const payload: MedicationPayload = { ...medicationFields, endDate: medicationFields.endDate || null };
    try {
      if (editingMedicationId === null) await createMedication(payload);
      else await updateMedication(editingMedicationId, payload);
      setMedicationFields(blankMedication());
      setEditingMedicationId(null);
      await refreshMedications();
    } catch (err) {
      setError(messageFor(err));
    } finally {
      setSavingMedication(false);
    }
  }

  function beginEditMedication(medication: Medication) {
    setEditingMedicationId(medication.id);
    setMedicationFields({
      name: medication.name, dosage: medication.dosage, frequency: medication.frequency,
      instructions: medication.instructions ?? '', startDate: medication.startDate,
      endDate: medication.endDate ?? '',
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function removeMedication(id: number) {
    if (!window.confirm('Delete this medication and its schedules?')) return;
    setError(null);
    try {
      await deleteMedication(id);
      setMedications((current) => current.filter((item) => item.id !== id));
      setScheduleLists((current) => { const next = { ...current }; delete next[id]; return next; });
      if (openMedicationId === id) setOpenMedicationId(null);
      if (editingMedicationId === id) {
        setEditingMedicationId(null);
        setMedicationFields(blankMedication());
      }
      await refreshAdherence();
    } catch (err) {
      setError(messageFor(err));
    }
  }

  async function toggleSchedules(medicationId: number) {
    if (openMedicationId === medicationId) {
      setOpenMedicationId(null);
      return;
    }
    setOpenMedicationId(medicationId);
    setLoadingSchedules(true);
    setError(null);
    try {
      const items = await listSchedules(medicationId);
      setScheduleLists((current) => ({ ...current, [medicationId]: items }));
    } catch (err) {
      setError(messageFor(err));
    } finally {
      setLoadingSchedules(false);
    }
  }

  function beginEditSchedule(schedule: MedicationSchedule) {
    setEditingScheduleId(schedule.id);
    setScheduleFields({
      frequency: schedule.frequency,
      timeOfDay: timeLabel(schedule.timeOfDay),
      startDate: schedule.startDate,
      endDate: schedule.endDate ?? '',
    });
  }

  async function handleScheduleSubmit(event: FormEvent<HTMLFormElement>, medicationId: number) {
    event.preventDefault();
    setSavingSchedule(true);
    setError(null);
    const payload: SchedulePayload = { ...scheduleFields, endDate: scheduleFields.endDate || null };
    try {
      if (editingScheduleId === null) await createSchedule(medicationId, payload);
      else await updateSchedule(editingScheduleId, { ...payload, active: true });
      setEditingScheduleId(null);
      setScheduleFields(blankSchedule());
      const [schedules] = await Promise.all([listSchedules(medicationId), refreshAdherence()]);
      setScheduleLists((current) => ({ ...current, [medicationId]: schedules }));
    } catch (err) {
      setError(messageFor(err));
    } finally {
      setSavingSchedule(false);
    }
  }

  async function removeSchedule(schedule: MedicationSchedule) {
    if (!window.confirm('Remove this schedule? Its recorded dose history will remain.')) return;
    setError(null);
    try {
      await deleteSchedule(schedule.id);
      setScheduleLists((current) => ({
        ...current,
        [schedule.medicationId]: (current[schedule.medicationId] ?? []).filter((item) => item.id !== schedule.id),
      }));
      if (editingScheduleId === schedule.id) {
        setEditingScheduleId(null);
        setScheduleFields(blankSchedule());
      }
      await refreshAdherence();
    } catch (err) {
      setError(messageFor(err));
    }
  }

  async function setDoseStatus(doseId: number, status: Exclude<DoseStatus, 'PENDING'>) {
    setUpdatingDoseId(doseId);
    setError(null);
    try {
      await updateDoseStatus(doseId, status);
      await refreshAdherence();
    } catch (err) {
      setError(messageFor(err));
    } finally {
      setUpdatingDoseId(null);
    }
  }

  function signOut() {
    clearAccessToken();
    setUser(null);
    setMedications([]);
    setScheduleLists({});
    setTodayDoses([]);
    setHistory([]);
    setOpenMedicationId(null);
    setEditingScheduleId(null);
    setError(null);
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.brand}>
          <span className={styles.logoMark} aria-hidden />
          <div>
            <p className={styles.eyebrow}>SmartMed · Patient space</p>
            <h1 className={styles.title}>Your medication, clearly organized.</h1>
          </div>
        </div>
        <p className={styles.subtitle}>Medication details, schedules, and dose records in one private place.</p>
        {user && <div className={styles.accountBar}><span>Signed in as {user.fullName}</span><button className={styles.textButton} onClick={signOut}>Sign out</button></div>}
      </header>

      <main className={styles.main}>
        {error && <div className={styles.alert} role="alert">{error}</div>}

        {!user ? (
          <section className={styles.card} aria-labelledby="account-heading">
            <div className={styles.authTabs} role="tablist" aria-label="Account access">
              <button type="button" role="tab" aria-selected={authMode === 'login'} className={authMode === 'login' ? styles.activeTab : styles.tab} onClick={() => { setAuthMode('login'); setError(null); }}>Sign in</button>
              <button type="button" role="tab" aria-selected={authMode === 'register'} className={authMode === 'register' ? styles.activeTab : styles.tab} onClick={() => { setAuthMode('register'); setError(null); }}>Create patient account</button>
            </div>
            <h2 id="account-heading" className={styles.cardTitle}>{authMode === 'login' ? 'Welcome back' : 'Start your patient account'}</h2>
            <p className={styles.cardText}>Your medication and dose records are private to your account.</p>
            <form className={styles.form} onSubmit={handleAuth}>
              {authMode === 'register' && <label>Full name<input autoComplete="name" required minLength={2} maxLength={120} value={fullName} onChange={(event) => setFullName(event.target.value)} /></label>}
              <label>Email<input type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} /></label>
              <label>Password<input type="password" autoComplete={authMode === 'login' ? 'current-password' : 'new-password'} required minLength={8} maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} /></label>
              <button className={styles.button} type="submit" disabled={authBusy}>{authBusy ? 'Please wait…' : authMode === 'login' ? 'Sign in' : 'Create account'}</button>
            </form>
          </section>
        ) : (
          <>
            <section className={styles.card} aria-labelledby="medication-form-heading">
              <p className={styles.eyebrow}>Medication management</p>
              <h2 id="medication-form-heading" className={styles.cardTitle}>{editingMedicationId === null ? 'Add a medication' : 'Edit medication'}</h2>
              <form className={styles.form} onSubmit={handleMedicationSubmit}>
                <div className={styles.formGrid}>
                  <label>Medication name<input required maxLength={160} value={medicationFields.name} onChange={(event) => setMedicationFields({ ...medicationFields, name: event.target.value })} /></label>
                  <label>Dosage<input required maxLength={120} placeholder="e.g. 1 tablet" value={medicationFields.dosage} onChange={(event) => setMedicationFields({ ...medicationFields, dosage: event.target.value })} /></label>
                  <label>Frequency<input required maxLength={120} placeholder="e.g. Once daily" value={medicationFields.frequency} onChange={(event) => setMedicationFields({ ...medicationFields, frequency: event.target.value })} /></label>
                  <label>Start date<input type="date" required value={medicationFields.startDate} onChange={(event) => setMedicationFields({ ...medicationFields, startDate: event.target.value })} /></label>
                  <label>End date <span className={styles.optional}>(optional)</span><input type="date" min={medicationFields.startDate} value={medicationFields.endDate} onChange={(event) => setMedicationFields({ ...medicationFields, endDate: event.target.value })} /></label>
                  <label className={styles.wideField}>Instructions <span className={styles.optional}>(optional)</span><textarea maxLength={2000} rows={3} value={medicationFields.instructions} onChange={(event) => setMedicationFields({ ...medicationFields, instructions: event.target.value })} /></label>
                </div>
                <div className={styles.formActions}>
                  <button className={styles.button} type="submit" disabled={savingMedication}>{savingMedication ? 'Saving…' : editingMedicationId === null ? 'Add medication' : 'Save changes'}</button>
                  {editingMedicationId !== null && <button className={styles.secondaryButton} type="button" onClick={() => { setEditingMedicationId(null); setMedicationFields(blankMedication()); }}>Cancel</button>}
                </div>
              </form>
            </section>

            <section className={styles.card} aria-labelledby="medications-heading">
              <div className={styles.sectionHeading}>
                <div><p className={styles.eyebrow}>Your list</p><h2 id="medications-heading" className={styles.cardTitle}>Medications</h2></div>
                <button className={styles.secondaryButton} type="button" onClick={() => void refreshMedications()} disabled={loadingMedications}>{loadingMedications ? 'Loading…' : 'Refresh list'}</button>
              </div>
              {loadingMedications && <p className={styles.cardText} role="status">Loading your medications…</p>}
              {!loadingMedications && medications.length === 0 && <p className={styles.emptyState}>No medications added yet. Add one above to get started.</p>}
              {!loadingMedications && medications.length > 0 && <div className={styles.medicationList}>
                {medications.map((medication) => <article className={styles.medicationItem} key={medication.id}>
                  <div className={styles.medicationInfo}>
                    <h3>{medication.name}</h3>
                    <p><strong>{medication.dosage}</strong> · {medication.frequency}</p>
                    <p className={styles.dateLine}>{medication.startDate}{medication.endDate ? ` – ${medication.endDate}` : ' · No end date'}</p>
                    {medication.instructions && <p className={styles.instructions}>{medication.instructions}</p>}
                  </div>
                  <div className={styles.itemActions}>
                    <button className={styles.textButton} type="button" onClick={() => void toggleSchedules(medication.id)}>{openMedicationId === medication.id ? 'Hide schedules' : 'Schedules'}</button>
                    <button className={styles.textButton} type="button" onClick={() => beginEditMedication(medication)}>Edit</button>
                    <button className={styles.dangerButton} type="button" onClick={() => void removeMedication(medication.id)}>Delete</button>
                  </div>
                  {openMedicationId === medication.id && <div className={styles.schedulePanel}>
                    <h4>Medication schedules</h4>
                    {loadingSchedules && <p className={styles.cardText}>Loading schedules…</p>}
                    {!loadingSchedules && (scheduleLists[medication.id] ?? []).length === 0 && <p className={styles.mutedText}>No schedules yet.</p>}
                    {(scheduleLists[medication.id] ?? []).map((schedule) => <div className={styles.scheduleItem} key={schedule.id}>
                      <div><strong>{frequencyLabel(schedule.frequency)}</strong><span> · Anchor {timeLabel(schedule.timeOfDay)}</span><small>{schedule.startDate}{schedule.endDate ? ` – ${schedule.endDate}` : ' · Ongoing'}</small></div>
                      <div className={styles.itemActions}>
                        <button className={styles.textButton} type="button" onClick={() => beginEditSchedule(schedule)}>Edit</button>
                        <button className={styles.dangerButton} type="button" onClick={() => void removeSchedule(schedule)}>Delete</button>
                      </div>
                    </div>)}
                    <p className={styles.slotNote}>Additional times are deterministic software schedule slots derived from the anchor time. They are not dosing guidance.</p>
                    <form className={styles.form} onSubmit={(event) => void handleScheduleSubmit(event, medication.id)}>
                      <h4>{editingScheduleId === null ? 'Add schedule' : 'Edit schedule'}</h4>
                      <div className={styles.formGrid}>
                        <label>Frequency<select value={scheduleFields.frequency} onChange={(event) => setScheduleFields({ ...scheduleFields, frequency: event.target.value as ScheduleFrequency })}>
                          <option value="ONCE_DAILY">Once daily</option><option value="TWICE_DAILY">Twice daily</option><option value="THREE_TIMES_DAILY">Three times daily</option><option value="FOUR_TIMES_DAILY">Four times daily</option>
                        </select></label>
                        <label>Daily anchor time<input type="time" required value={scheduleFields.timeOfDay} onChange={(event) => setScheduleFields({ ...scheduleFields, timeOfDay: event.target.value })} /></label>
                        <label>Start date<input type="date" required value={scheduleFields.startDate} onChange={(event) => setScheduleFields({ ...scheduleFields, startDate: event.target.value })} /></label>
                        <label>End date <span className={styles.optional}>(optional)</span><input type="date" min={scheduleFields.startDate} value={scheduleFields.endDate} onChange={(event) => setScheduleFields({ ...scheduleFields, endDate: event.target.value })} /></label>
                      </div>
                      <div className={styles.formActions}>
                        <button className={styles.button} type="submit" disabled={savingSchedule}>{savingSchedule ? 'Saving…' : editingScheduleId === null ? 'Add schedule' : 'Save schedule'}</button>
                        {editingScheduleId !== null && <button className={styles.secondaryButton} type="button" onClick={() => { setEditingScheduleId(null); setScheduleFields(blankSchedule()); }}>Cancel</button>}
                      </div>
                    </form>
                  </div>}
                </article>)}
              </div>}
            </section>

            <AnalyticsDashboard />

            <section className={styles.card} aria-labelledby="today-heading">
              <div className={styles.sectionHeading}>
                <div><p className={styles.eyebrow}>Daily view</p><h2 id="today-heading" className={styles.cardTitle}>Today’s doses</h2></div>
                <button className={styles.secondaryButton} type="button" onClick={() => void refreshAdherence()} disabled={loadingDoses}>{loadingDoses ? 'Loading…' : 'Refresh doses'}</button>
              </div>
              {loadingDoses && <p className={styles.cardText} role="status">Loading today’s doses…</p>}
              {!loadingDoses && todayDoses.length === 0 && <p className={styles.emptyState}>No doses scheduled for today.</p>}
              {!loadingDoses && todayDoses.length > 0 && <div className={styles.doseList}>
                {todayDoses.map((dose) => <DoseCard key={dose.id} dose={dose} onStatus={setDoseStatus} busy={updatingDoseId === dose.id} />)}
              </div>}
            </section>

            <section className={styles.card} aria-labelledby="history-heading">
              <div className={styles.sectionHeading}>
                <div><p className={styles.eyebrow}>Recorded doses</p><h2 id="history-heading" className={styles.cardTitle}>Adherence history</h2></div>
                <button className={styles.secondaryButton} type="button" onClick={() => void refreshAdherence()} disabled={loadingDoses}>{loadingDoses ? 'Loading…' : 'Refresh history'}</button>
              </div>
              {loadingDoses && <p className={styles.cardText} role="status">Loading dose history…</p>}
              {!loadingDoses && history.length === 0 && <p className={styles.emptyState}>Dose records will appear here as scheduled doses are viewed and recorded.</p>}
              {!loadingDoses && history.length > 0 && <div className={styles.historyList}>
                {history.map((dose) => <div className={styles.historyItem} key={dose.id}>
                  <div><strong>{dose.medicationName}</strong><span>{dose.scheduledDate} · {timeLabel(dose.scheduledTime)}</span></div>
                  <span className={styles.status} data-status={dose.status}>{dose.status}</span>
                </div>)}
              </div>}
            </section>
          </>
        )}

        <aside className={styles.disclaimer} role="note">
          <strong>Healthcare disclaimer</strong>
          <p>SmartMed is an educational software project. Scheduling slots are organizational aids only. Follow instructions from your doctor or pharmacist.</p>
        </aside>
      </main>

      <footer className={styles.footer}><span>© {new Date().getFullYear()} SmartMed</span><span className={styles.footerMuted}>Medication management</span></footer>
    </div>
  );
}

function DoseCard({ dose, onStatus, busy }: {
  dose: DoseRecord;
  onStatus: (id: number, status: Exclude<DoseStatus, 'PENDING'>) => void;
  busy: boolean;
}) {
  return (
    <article className={styles.doseItem}>
      <div className={styles.doseMain}>
        <div><h3>{dose.medicationName}</h3><p>{dose.dosage} · {dose.scheduledDate} at {timeLabel(dose.scheduledTime)}</p></div>
        <span className={styles.status} data-status={dose.status}>{dose.status}</span>
      </div>
      {dose.status === 'PENDING' && <div className={styles.doseActions}>
        <button className={styles.button} type="button" disabled={busy} onClick={() => onStatus(dose.id, 'TAKEN')}>{busy ? 'Saving…' : 'Take'}</button>
        <button className={styles.secondaryButton} type="button" disabled={busy} onClick={() => onStatus(dose.id, 'MISSED')}>Mark missed</button>
        <button className={styles.secondaryButton} type="button" disabled={busy} onClick={() => onStatus(dose.id, 'SKIPPED')}>Skip</button>
      </div>}
    </article>
  );
}
