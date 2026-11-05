export const INBOX_CHANGED = 'lynx:inbox-changed';

export function requestInboxRefresh() {
  window.dispatchEvent(new Event(INBOX_CHANGED));
}
