import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  Filler,
  LinearScale,
  LineElement,
  PointElement,
  ArcElement,
  Tooltip,
  Legend,
} from 'chart.js';
import { Doughnut, Line } from 'react-chartjs-2';
import { ApiError } from '../api/client';
import {
  getAnalyticsSummary,
  getDailyAnalytics,
  getMedicationAnalytics,
  type AnalyticsSummary,
  type DailyAnalytics,
  type MedicationAnalytics,
} from '../api/analytics';
import styles from './AnalyticsDashboard.module.css';
import { smartMedCalendarDate, smartMedDateDaysAgo } from '../utils/calendarDates';

ChartJS.register(CategoryScale, LinearScale, LineElement, PointElement, ArcElement, Tooltip, Legend, Filler);

type RangeChoice = '7' | '30' | 'custom';
type DashboardData = {
  summary: AnalyticsSummary;
  daily: DailyAnalytics[];
  medications: MedicationAnalytics[];
};

function describeError(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) {
    return 'Your session has expired. Sign in again to view your analytics.';
  }
  if (error instanceof ApiError) return error.message;
  return 'Analytics could not be loaded. Check your connection and try again.';
}

export default function AnalyticsDashboard() {
  const today = smartMedCalendarDate();
  const [rangeChoice, setRangeChoice] = useState<RangeChoice>('30');
  const [customFrom, setCustomFrom] = useState(smartMedDateDaysAgo(29));
  const [customTo, setCustomTo] = useState(today);
  const [range, setRange] = useState({ from: smartMedDateDaysAgo(29), to: today });
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rangeError, setRangeError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [summary, daily, medications] = await Promise.all([
        getAnalyticsSummary(range.from, range.to),
        getDailyAnalytics(range.from, range.to),
        getMedicationAnalytics(range.from, range.to),
      ]);
      setData({ summary, daily, medications });
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setLoading(false);
    }
  }, [range.from, range.to]);

  useEffect(() => {
    void load();
  }, [load]);

  const dailyChart = useMemo(() => ({
    labels: data?.daily.map((item) => item.date) ?? [],
    datasets: [{
      label: 'Adherence',
      data: data?.daily.map((item) => item.adherencePercentage) ?? [],
      borderColor: '#356b49',
      backgroundColor: 'rgba(76, 132, 91, 0.14)',
      pointBackgroundColor: '#356b49',
      pointRadius: 3,
      tension: 0.32,
      fill: true,
    }],
  }), [data]);

  const statusChart = useMemo(() => ({
    labels: ['Taken', 'Missed', 'Skipped'],
    datasets: [{
      data: [data?.summary.taken ?? 0, data?.summary.missed ?? 0, data?.summary.skipped ?? 0],
      backgroundColor: ['#6c9d71', '#c96f5e', '#aab2aa'],
      borderColor: '#fffdf6',
      borderWidth: 3,
    }],
  }), [data]);

  function choosePreset(choice: '7' | '30') {
    const from = smartMedDateDaysAgo(Number(choice) - 1);
    const to = smartMedCalendarDate();
    setRangeChoice(choice);
    setRangeError(null);
    setRange({ from, to });
  }

  function applyCustomRange() {
    if (!customFrom || !customTo) {
      setRangeError('Choose both a start date and an end date.');
      return;
    }
    if (customFrom > customTo) {
      setRangeError('Start date must be on or before end date.');
      return;
    }
    setRangeError(null);
    setRangeChoice('custom');
    setRange({ from: customFrom, to: customTo });
  }

  const hasCompletedDoses = (data?.summary.taken ?? 0)
    + (data?.summary.missed ?? 0)
    + (data?.summary.skipped ?? 0) > 0;

  return (
    <section className={styles.dashboard} aria-labelledby="analytics-heading">
      <div className={styles.heading}>
        <div>
          <p className={styles.eyebrow}>Your progress</p>
          <h2 id="analytics-heading">Adherence overview</h2>
          <p className={styles.description}>Trends reflect dose records saved for your account during the selected period.</p>
        </div>
        <button type="button" className={styles.refreshButton} onClick={() => void load()} disabled={loading}>
          {loading ? 'Loading…' : 'Refresh'}
        </button>
      </div>

      <div className={styles.rangeBar} aria-label="Analytics date range">
        <div className={styles.rangeButtons} role="group" aria-label="Choose a date range">
          <button type="button" aria-pressed={rangeChoice === '7'} className={rangeChoice === '7' ? styles.activeRange : styles.rangeButton} onClick={() => choosePreset('7')}>Last 7 days</button>
          <button type="button" aria-pressed={rangeChoice === '30'} className={rangeChoice === '30' ? styles.activeRange : styles.rangeButton} onClick={() => choosePreset('30')}>Last 30 days</button>
          <button type="button" aria-pressed={rangeChoice === 'custom'} className={rangeChoice === 'custom' ? styles.activeRange : styles.rangeButton} onClick={() => setRangeChoice('custom')}>Custom</button>
        </div>
        {rangeChoice === 'custom' && <div className={styles.customRange}>
          <label>From<input type="date" max={customTo || undefined} value={customFrom} onChange={(event) => setCustomFrom(event.target.value)} /></label>
          <label>To<input type="date" min={customFrom || undefined} value={customTo} onChange={(event) => setCustomTo(event.target.value)} /></label>
          <button type="button" className={styles.applyButton} onClick={applyCustomRange}>Apply</button>
        </div>}
        <span className={styles.rangeLabel}>{range.from} — {range.to}</span>
      </div>
      {rangeError && <p className={styles.error} role="alert">{rangeError}</p>}
      {error && <p className={styles.error} role="alert">{error}</p>}

      {loading && !data && <p className={styles.state} role="status">Loading your adherence data…</p>}
      {!loading && error && !data && <p className={styles.state}>Retry with the refresh button when your connection is available.</p>}
      {data && <>
        <div className={styles.metrics} aria-label="Adherence summary">
          <Metric label="Adherence" value={`${data.summary.adherencePercentage.toFixed(1)}%`} emphasis />
          <Metric label="Recorded doses" value={data.summary.totalDoses.toString()} />
          <Metric label="Taken" value={data.summary.taken.toString()} />
          <Metric label="Missed or skipped" value={(data.summary.missed + data.summary.skipped).toString()} />
          <Metric label="Pending" value={data.summary.pending.toString()} />
        </div>

        {data.summary.totalDoses === 0 && <p className={styles.empty}>
          No dose records were saved in this period. Analytics will appear as you record scheduled doses.
        </p>}
        {data.summary.totalDoses > 0 && <div className={styles.charts}>
          <article className={styles.chartCard}>
            <div className={styles.chartHeading}><h3>Daily adherence</h3><p>Pending doses are excluded from each day’s percentage.</p></div>
            {data.daily.length === 0 ? <p className={styles.empty}>No daily records in this range.</p> : <div className={styles.lineChart}>
              <Line data={dailyChart} options={{
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false }, tooltip: { callbacks: { label: (context) => `${context.parsed.y?.toFixed(1) ?? '0.0'}%` } } },
                scales: { y: { min: 0, max: 100, ticks: { callback: (value) => `${value}%` }, grid: { color: 'rgba(55, 85, 52, 0.1)' } }, x: { grid: { display: false } } },
              }} />
            </div>}
            <p className={styles.chartNote}>Only dates with saved dose records are shown.</p>
          </article>
          <article className={styles.chartCard}>
            <div className={styles.chartHeading}><h3>Dose status</h3><p>Pending doses are not included in adherence.</p></div>
            {!hasCompletedDoses ? <p className={styles.empty}>No completed dose statuses in this period.</p> : <div className={styles.doughnutChart}>
              <Doughnut data={statusChart} options={{
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { position: 'bottom', labels: { usePointStyle: true, padding: 18 } } },
                cutout: '66%',
              }} />
            </div>}
          </article>
        </div>}

        {data.medications.length > 0 && <article className={styles.breakdown}>
          <div className={styles.chartHeading}><h3>By medication</h3><p>Adherence uses taken ÷ (taken + missed + skipped).</p></div>
          <div className={styles.tableScroll}><table>
            <thead><tr><th scope="col">Medication</th><th scope="col">Adherence</th><th scope="col">Taken</th><th scope="col">Missed</th><th scope="col">Skipped</th><th scope="col">Pending</th></tr></thead>
            <tbody>{data.medications.map((medication) => <tr key={medication.medicationId}>
              <th scope="row">{medication.medicationName}</th>
              <td>{medication.adherencePercentage.toFixed(1)}%</td>
              <td>{medication.taken}</td><td>{medication.missed}</td><td>{medication.skipped}</td><td>{medication.pending}</td>
            </tr>)}</tbody>
          </table></div>
        </article>}
      </>}
    </section>
  );
}

function Metric({ label, value, emphasis = false }: { label: string; value: string; emphasis?: boolean }) {
  return <article className={`${styles.metric} ${emphasis ? styles.metricEmphasis : ''}`}>
    <span>{label}</span><strong>{value}</strong>
  </article>;
}
