import { useCallback, useEffect, useState } from 'react';
import { fetchHealth, type HealthData } from '../api/health';
import { ApiError } from '../api/client';
import styles from './HomePage.module.css';

type LoadState = 'idle' | 'loading' | 'success' | 'error';

export default function HomePage() {
  const [loadState, setLoadState] = useState<LoadState>('idle');
  const [health, setHealth] = useState<HealthData | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const checkBackend = useCallback(async () => {
    setLoadState('loading');
    setErrorMessage(null);
    try {
      const envelope = await fetchHealth();
      setHealth(envelope.data);
      setLoadState('success');
    } catch (err) {
      setHealth(null);
      setLoadState('error');
      if (err instanceof ApiError) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage('Could not reach the SmartMed API. Is the backend running on port 8080?');
      }
    }
  }, []);

  useEffect(() => {
    void checkBackend();
  }, [checkBackend]);

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.brand}>
          <span className={styles.logoMark} aria-hidden />
          <div>
            <p className={styles.eyebrow}>SmartMed</p>
            <h1 className={styles.title}>Medication adherence, built for trust</h1>
          </div>
        </div>
        <p className={styles.subtitle}>
          AI-powered scheduling, adherence analytics, and informational interaction checking — for
          patients, caregivers, and clinicians.
        </p>
      </header>

      <main className={styles.main}>
        <section className={styles.card} aria-labelledby="foundation-heading">
          <h2 id="foundation-heading" className={styles.cardTitle}>
            Phase 1 — Foundation
          </h2>
          <p className={styles.cardText}>
            Backend API, database connectivity, and this client shell are in place. Clinical features
            arrive in later phases.
          </p>

          <div className={styles.statusRow}>
            <span className={styles.statusLabel}>API health</span>
            {loadState === 'loading' && (
              <span className={styles.badge} data-tone="muted">
                Checking…
              </span>
            )}
            {loadState === 'success' && health && (
              <span className={styles.badge} data-tone="ok">
                {health.status} · {health.apiVersion}
              </span>
            )}
            {loadState === 'error' && (
              <span className={styles.badge} data-tone="error">
                Unreachable
              </span>
            )}
          </div>

          {loadState === 'success' && health && (
            <dl className={styles.meta}>
              <div>
                <dt>Application</dt>
                <dd>{health.application}</dd>
              </div>
              <div>
                <dt>Last check</dt>
                <dd>{new Date(health.timestamp).toLocaleString()}</dd>
              </div>
            </dl>
          )}

          {errorMessage && <p className={styles.errorText}>{errorMessage}</p>}

          <button type="button" className={styles.button} onClick={() => void checkBackend()}>
            Re-check API
          </button>
        </section>

        <aside className={styles.disclaimer} role="note">
          <strong>Healthcare disclaimer</strong>
          <p>
            SmartMed is an educational software project. It does not diagnose, prescribe, or replace
            your doctor or pharmacist. Interaction information is informational only.
          </p>
        </aside>
      </main>

      <footer className={styles.footer}>
        <span>© {new Date().getFullYear()} SmartMed</span>
        <span className={styles.footerMuted}>Portfolio-grade adherence platform</span>
      </footer>
    </div>
  );
}
