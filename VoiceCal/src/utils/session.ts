export const DEFAULT_TIMEZONE = 'Asia/Shanghai';
const SESSION_STORAGE_KEY = 'voicecal.session-id';

export function getSessionId(): string {
  const existing = window.sessionStorage.getItem(SESSION_STORAGE_KEY);
  if (existing) return existing;

  const randomId = typeof window.crypto.randomUUID === 'function'
    ? window.crypto.randomUUID().replace(/-/g, '')
    : `${Date.now()}_${Math.random().toString(16).slice(2)}`;
  const sessionId = `session_${randomId}`;
  window.sessionStorage.setItem(SESSION_STORAGE_KEY, sessionId);
  return sessionId;
}

export function getCurrentTimezone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || DEFAULT_TIMEZONE;
}
