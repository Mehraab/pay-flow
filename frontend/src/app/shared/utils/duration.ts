/** closeTime - startTime, human-formatted; "In progress" while the workflow hasn't closed. */
export function formatDuration(startTime: string, closeTime: string | null): string {
  if (!closeTime) return 'In progress';
  const ms = new Date(closeTime).getTime() - new Date(startTime).getTime();
  if (ms < 1000) return `${ms}ms`;
  const totalSeconds = Math.round(ms / 1000);
  if (totalSeconds < 60) return `${totalSeconds}s`;
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}m ${seconds}s`;
}
