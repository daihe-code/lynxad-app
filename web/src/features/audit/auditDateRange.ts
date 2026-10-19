/** Inclusive last seven calendar days in Asia/Shanghai, independent of browser timezone. */
export function auditDateDefaults(now = new Date()): { from: string; to: string } {
  const day = new Date(now.getTime() + 8 * 3600000).toISOString().slice(0, 10);
  return { from: new Date(Date.parse(day + 'T00:00:00Z') - 6 * 86400000).toISOString().slice(0, 10), to: day };
}
