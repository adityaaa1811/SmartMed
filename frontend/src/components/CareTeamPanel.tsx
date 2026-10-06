import { FormEvent, useCallback, useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import {
  createCareRelationship,
  listCareRelationships,
  revokeCareRelationship,
  type CareRelationship,
  type RelationshipType,
} from '../api/relationships';
import styles from './CareTeamPanel.module.css';

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return 'Care team information could not be loaded. Try again.';
}

export default function CareTeamPanel() {
  const [relationships, setRelationships] = useState<CareRelationship[]>([]);
  const [email, setEmail] = useState('');
  const [type, setType] = useState<RelationshipType>('CAREGIVER');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRelationships(await listCareRelationships());
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  async function addRelationship(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      await createCareRelationship({ relatedUserEmail: email.trim(), relationshipType: type });
      setEmail('');
      setNotice('Your request is pending until the invitee responds.');
      await refresh();
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setSaving(false);
    }
  }

  async function revoke(relationship: CareRelationship) {
    if (!window.confirm('Revoke ' + relationship.relationshipType.toLowerCase() + ' access for ' + relationship.relatedUserName + '?')) return;
    setError(null);
    setNotice(null);
    try {
      await revokeCareRelationship(relationship.id);
      await refresh();
    } catch (cause) {
      setError(errorMessage(cause));
    }
  }

  return (
    <section id="care-team" className={styles.panel} aria-labelledby="care-team-heading">
      <div className={styles.heading}>
        <div><p className={styles.eyebrow}>Consent and access</p><h2 id="care-team-heading">Care Team</h2></div>
        <p>Only people you invite and who accept can monitor your records.</p>
      </div>

      <form className={styles.form} onSubmit={addRelationship}>
        <label>Invite by email<input type="email" required maxLength={255} value={email} onChange={(event) => setEmail(event.target.value)} placeholder="name@example.com" /></label>
        <label>Role<select value={type} onChange={(event) => setType(event.target.value as RelationshipType)}>
          <option value="CAREGIVER">Caregiver</option><option value="DOCTOR">Doctor</option>
        </select></label>
        <button type="submit" disabled={saving}>{saving ? 'Sending…' : 'Send request'}</button>
      </form>

      {error && <p className={styles.error} role="alert">{error}</p>}
      {notice && <p className={styles.notice} role="status">{notice}</p>}
      {loading && <p className={styles.state} role="status">Loading your care team…</p>}
      {!loading && relationships.length === 0 && <p className={styles.empty}>No care team members yet. Send an invitation when you are ready.</p>}
      {!loading && relationships.length > 0 && <div className={styles.list}>
        {relationships.map((relationship) => <article className={styles.item} key={relationship.id}>
          <div className={styles.person}>
            <strong>{relationship.relatedUserName}</strong>
            <span>{relationship.relatedUserEmail} · {relationship.relationshipType === 'DOCTOR' ? 'Doctor' : 'Caregiver'}</span>
          </div>
          <span className={styles.status} data-status={relationship.status}>{relationship.status.toLowerCase()}</span>
          {relationship.status === 'PENDING' && <span className={styles.helper}>Waiting for response</span>}
          {relationship.status === 'ACTIVE' && <button className={styles.revoke} type="button" onClick={() => void revoke(relationship)}>Revoke access</button>}
          {relationship.status === 'REVOKED' && <span className={styles.helper}>Access revoked</span>}
        </article>)}
      </div>}
    </section>
  );
}
