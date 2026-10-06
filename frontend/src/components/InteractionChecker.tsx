import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import {
  checkMedicationInteractions,
  type InteractionCheckResponse,
} from '../api/interactions';
import type { Medication } from '../api/medications';
import styles from './InteractionChecker.module.css';
import { smartMedCalendarDate } from '../utils/calendarDates';

type Props = {
  medications: Medication[];
  loading: boolean;
};

function localDateString(): string {
  return smartMedCalendarDate();
}

export default function InteractionChecker({ medications, loading }: Props) {
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [result, setResult] = useState<InteractionCheckResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [selectionError, setSelectionError] = useState<string | null>(null);
  const [requestError, setRequestError] = useState<string | null>(null);
  const today = localDateString();
  const currentMedications = medications.filter((medication) =>
    medication.startDate <= today && (!medication.endDate || medication.endDate >= today),
  );
  const currentMedicationIds = currentMedications.map((medication) => medication.id);

  useEffect(() => {
    const availableIds = new Set(currentMedicationIds);
    setSelectedIds((selected) => selected.filter((id) => availableIds.has(id)));
  }, [medications, today]);

  function toggleMedication(id: number) {
    setSelectedIds((selected) => selected.includes(id)
      ? selected.filter((selectedId) => selectedId !== id)
      : [...selected, id]);
    setResult(null);
    setSelectionError(null);
    setRequestError(null);
  }

  async function checkInteractions() {
    if (selectedIds.length === 0) {
      setSelectionError('Select at least one current medication to check.');
      setResult(null);
      return;
    }
    setSelectionError(null);
    setRequestError(null);
    setResult(null);
    setBusy(true);
    try {
      setResult(await checkMedicationInteractions(selectedIds));
    } catch (error) {
      if (error instanceof ApiError && error.code === 'INTERACTION_PROVIDER_UNAVAILABLE') {
        const response = error.data as InteractionCheckResponse | undefined;
        setResult(response ?? {
          status: 'PROVIDER_UNAVAILABLE',
          checkedMedicationCount: selectedIds.length,
          providerId: null,
          interactions: [],
        });
      } else {
        setRequestError(error instanceof Error ? error.message : 'Could not check interactions. Try again.');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <section id="interaction-checker" className={styles.card} aria-labelledby="interaction-heading">
      <div className={styles.heading}>
        <div><p className={styles.eyebrow}>Patient tool</p><h2 id="interaction-heading">Interaction checker</h2></div>
        <span className={styles.count}>{selectedIds.length} selected</span>
      </div>
      <p className={styles.intro}>Choose current medications to send their names to the configured interaction provider.</p>

      {loading ? (
        <p className={styles.state} role="status">Loading your medication list…</p>
      ) : currentMedications.length === 0 ? (
        <p className={styles.empty}>No current medications are available to check. Add a current medication first.</p>
      ) : (
        <fieldset className={styles.medicationChoices}>
          <legend>Current medications</legend>
          {currentMedications.map((medication) => (
            <label className={styles.choice} key={medication.id}>
              <input
                type="checkbox"
                checked={selectedIds.includes(medication.id)}
                onChange={() => toggleMedication(medication.id)}
              />
              <span><strong>{medication.name}</strong><small>{medication.dosage} · {medication.frequency}</small></span>
            </label>
          ))}
        </fieldset>
      )}

      {selectionError && <p className={styles.error} role="alert">{selectionError}</p>}
      {requestError && <p className={styles.error} role="alert">{requestError}</p>}
      {currentMedications.length > 0 && <button className={styles.button} type="button" onClick={() => void checkInteractions()} disabled={busy || loading}>
        {busy ? 'Checking…' : 'Check interactions'}
      </button>}

      {result?.status === 'PROVIDER_UNAVAILABLE' && (
        <div className={styles.resultUnavailable} role="status">
          <strong>Interaction checking is currently unavailable.</strong>
          <p>The configured provider could not complete this check. No interaction conclusion is available.</p>
        </div>
      )}
      {result?.status === 'NO_DATA' && (
        <div className={styles.resultNoData} role="status">
          <strong>No interaction data was returned for the selected medications.</strong>
          {result.providerId === 'mock' && <p>The configured mock provider does not contain real clinical interaction data.</p>}
        </div>
      )}
      {result?.status === 'SUCCESS' && <div className={styles.results} aria-live="polite">
        <p className={styles.resultHeading}>Provider returned {result.interactions.length} interaction record{result.interactions.length === 1 ? '' : 's'}.</p>
        {result.interactions.map((interaction, index) => (
          <article className={styles.interaction} key={`${interaction.medicationA}-${interaction.medicationB}-${index}`}>
            <div className={styles.pair}><strong>{interaction.medicationA}</strong><span>with</span><strong>{interaction.medicationB}</strong></div>
            <p className={styles.severity}>Provider severity: {interaction.severity}</p>
            <p className={styles.description}>{interaction.description}</p>
          </article>
        ))}
      </div>}

      <p className={styles.disclaimer}>Interaction results are informational and do not replace advice from a doctor or pharmacist.</p>
    </section>
  );
}
