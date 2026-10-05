import { FormEvent, useCallback, useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { fetchCurrentUser, login, register, type UserDto } from '../api/auth';
import {
  createMedication,
  deleteMedication,
  listMedications,
  updateMedication,
  type Medication,
  type MedicationPayload,
} from '../api/medications';
import { clearAccessToken, getAccessToken } from '../auth/tokenStorage';
import styles from './HomePage.module.css';

type AuthMode = 'login' | 'register';
type MedicationFields = Omit<MedicationPayload, 'endDate'> & { endDate: string };

const blankMedication = (): MedicationFields => ({
  name: '', dosage: '', frequency: '', instructions: '',
  startDate: new Date().toISOString().slice(0, 10), endDate: '',
});

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    return error.status === 401 ? 'Your session has expired. Please sign in again.' : error.message;
  }
  return 'Could not reach SmartMed. Check that the backend is running and try again.';
}

export default function HomePage() {
  const [user, setUser] = useState<UserDto | null>(null);
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [medications, setMedications] = useState<Medication[]>([]);
  const [fields, setFields] = useState<MedicationFields>(blankMedication);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [authBusy, setAuthBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refreshMedications = useCallback(async () => {
    setLoading(true);
    try {
      setMedications(await listMedications());
      setError(null);
    } catch (err) {
      setError(messageFor(err));
      if (err instanceof ApiError && err.status === 401) {
        clearAccessToken();
        setUser(null);
      }
    } finally {
      setLoading(false);
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
      })
      .catch((err: unknown) => {
        clearAccessToken();
        setError(messageFor(err));
      });
  }, [refreshMedications]);

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
      await refreshMedications();
    } catch (err) {
      setError(messageFor(err));
    } finally {
      setAuthBusy(false);
    }
  }

  async function handleMedicationSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    const payload: MedicationPayload = { ...fields, endDate: fields.endDate || null };
    try {
      if (editingId === null) await createMedication(payload);
      else await updateMedication(editingId, payload);
      setFields(blankMedication());
      setEditingId(null);
      await refreshMedications();
    } catch (err) {
      setError(messageFor(err));
    } finally {
      setSaving(false);
    }
  }

  function beginEdit(medication: Medication) {
    setEditingId(medication.id);
    setFields({
      name: medication.name,
      dosage: medication.dosage,
      frequency: medication.frequency,
      instructions: medication.instructions ?? '',
      startDate: medication.startDate,
      endDate: medication.endDate ?? '',
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function removeMedication(id: number) {
    if (!window.confirm('Delete this medication?')) return;
    setError(null);
    try {
      await deleteMedication(id);
      setMedications((current) => current.filter((item) => item.id !== id));
      if (editingId === id) {
        setEditingId(null);
        setFields(blankMedication());
      }
    } catch (err) {
      setError(messageFor(err));
    }
  }

  function signOut() {
    clearAccessToken();
    setUser(null);
    setMedications([]);
    setFields(blankMedication());
    setEditingId(null);
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
        <p className={styles.subtitle}>Keep your medication details together in one private place.</p>
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
            <p className={styles.cardText}>Your medication list is private to your account.</p>
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
              <div className={styles.sectionHeading}>
                <div><p className={styles.eyebrow}>Medication management</p><h2 id="medication-form-heading" className={styles.cardTitle}>{editingId === null ? 'Add a medication' : 'Edit medication'}</h2></div>
              </div>
              <form className={styles.form} onSubmit={handleMedicationSubmit}>
                <div className={styles.formGrid}>
                  <label>Medication name<input required maxLength={160} value={fields.name} onChange={(event) => setFields({ ...fields, name: event.target.value })} /></label>
                  <label>Dosage<input required maxLength={120} placeholder="e.g. 1 tablet" value={fields.dosage} onChange={(event) => setFields({ ...fields, dosage: event.target.value })} /></label>
                  <label>Frequency<input required maxLength={120} placeholder="e.g. Once daily" value={fields.frequency} onChange={(event) => setFields({ ...fields, frequency: event.target.value })} /></label>
                  <label>Start date<input type="date" required value={fields.startDate} onChange={(event) => setFields({ ...fields, startDate: event.target.value })} /></label>
                  <label>End date <span className={styles.optional}>(optional)</span><input type="date" min={fields.startDate} value={fields.endDate} onChange={(event) => setFields({ ...fields, endDate: event.target.value })} /></label>
                  <label className={styles.wideField}>Instructions <span className={styles.optional}>(optional)</span><textarea maxLength={2000} rows={3} value={fields.instructions} onChange={(event) => setFields({ ...fields, instructions: event.target.value })} /></label>
                </div>
                <div className={styles.formActions}>
                  <button className={styles.button} type="submit" disabled={saving}>{saving ? 'Saving…' : editingId === null ? 'Add medication' : 'Save changes'}</button>
                  {editingId !== null && <button className={styles.secondaryButton} type="button" onClick={() => { setEditingId(null); setFields(blankMedication()); }}>Cancel</button>}
                </div>
              </form>
            </section>

            <section className={styles.card} aria-labelledby="medications-heading">
              <div className={styles.sectionHeading}>
                <div><p className={styles.eyebrow}>Your list</p><h2 id="medications-heading" className={styles.cardTitle}>Medications</h2></div>
                <button className={styles.secondaryButton} type="button" onClick={() => void refreshMedications()} disabled={loading}>{loading ? 'Loading…' : 'Refresh list'}</button>
              </div>
              {loading && <p className={styles.cardText} role="status">Loading your medications…</p>}
              {!loading && medications.length === 0 && <p className={styles.emptyState}>No medications added yet. Add one above to get started.</p>}
              {!loading && medications.length > 0 && <div className={styles.medicationList}>
                {medications.map((medication) => <article className={styles.medicationItem} key={medication.id}>
                  <div className={styles.medicationInfo}>
                    <h3>{medication.name}</h3>
                    <p><strong>{medication.dosage}</strong> · {medication.frequency}</p>
                    <p className={styles.dateLine}>{medication.startDate}{medication.endDate ? ` – ${medication.endDate}` : ' · No end date'}</p>
                    {medication.instructions && <p className={styles.instructions}>{medication.instructions}</p>}
                  </div>
                  <div className={styles.itemActions}>
                    <button className={styles.textButton} type="button" onClick={() => beginEdit(medication)}>Edit</button>
                    <button className={styles.dangerButton} type="button" onClick={() => void removeMedication(medication.id)}>Delete</button>
                  </div>
                </article>)}
              </div>}
            </section>
          </>
        )}

        <aside className={styles.disclaimer} role="note">
          <strong>Healthcare disclaimer</strong>
          <p>SmartMed is an educational software project. It does not diagnose or prescribe, and does not replace your doctor or pharmacist.</p>
        </aside>
      </main>

      <footer className={styles.footer}><span>© {new Date().getFullYear()} SmartMed</span><span className={styles.footerMuted}>Medication management</span></footer>
    </div>
  );
}
