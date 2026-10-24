import { formatDuration } from './duration';

describe('formatDuration', () => {
  it('returns "In progress" when closeTime is null', () => {
    expect(formatDuration('2026-01-01T00:00:00Z', null)).toBe('In progress');
  });

  it('formats sub-second durations in ms', () => {
    expect(formatDuration('2026-01-01T00:00:00.000Z', '2026-01-01T00:00:00.420Z')).toBe('420ms');
  });

  it('formats sub-minute durations in whole seconds', () => {
    expect(formatDuration('2026-01-01T00:00:00Z', '2026-01-01T00:00:04Z')).toBe('4s');
  });

  it('formats durations over a minute as "Xm Ys"', () => {
    expect(formatDuration('2026-01-01T00:00:00Z', '2026-01-01T00:01:12Z')).toBe('1m 12s');
  });
});
