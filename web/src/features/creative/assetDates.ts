function dateParts(iso: string | null | undefined) {
  if (!iso) return undefined;
  const date = new Date(iso);
  if (!Number.isFinite(date.getTime()) || date.getTime() <= 0) return undefined;
  return Object.fromEntries(new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(date).map(part => [part.type, part.value]));
}

export function assetDay(iso: string | null | undefined) {
  const parts = dateParts(iso);
  return parts ? `${Number(parts.month)}月${Number(parts.day)}日` : '日期未知';
}

export function assetDate(iso: string | null | undefined) {
  const parts = dateParts(iso);
  return parts ? `${parts.year}-${parts.month}-${parts.day}` : '日期未知';
}

export function assetDateTime(iso: string | null | undefined) {
  const parts = dateParts(iso);
  return parts ? `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}` : '日期未知';
}
