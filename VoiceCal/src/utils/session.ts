export const DEFAULT_USER_ID = '1';
export const DEFAULT_SESSION_ID = 'session_001';
export const DEFAULT_TIMEZONE = 'Asia/Shanghai';

export function getCurrentTimezone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || DEFAULT_TIMEZONE;
}
