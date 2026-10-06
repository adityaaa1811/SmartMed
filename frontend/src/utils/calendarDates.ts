const smartMedTimezone = import.meta.env.VITE_SMARTMED_TIMEZONE || 'Asia/Kolkata';

const calendarDateFormatter = new Intl.DateTimeFormat('en-US', {
  timeZone: smartMedTimezone,
  calendar: 'gregory',
  numberingSystem: 'latn',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

export function smartMedCalendarDate(now = new Date()): string {
  const parts = Object.fromEntries(calendarDateFormatter.formatToParts(now).map(({ type, value }) => [type, value]));
  return `${parts.year}-${parts.month}-${parts.day}`;
}

export function smartMedDateDaysAgo(days: number, now = new Date()): string {
  const [year, month, day] = smartMedCalendarDate(now).split('-').map(Number);
  const calendarDate = new Date(Date.UTC(year, month - 1, day - days));
  const resultYear = calendarDate.getUTCFullYear();
  const resultMonth = `${calendarDate.getUTCMonth() + 1}`.padStart(2, '0');
  const resultDay = `${calendarDate.getUTCDate()}`.padStart(2, '0');
  return `${resultYear}-${resultMonth}-${resultDay}`;
}
