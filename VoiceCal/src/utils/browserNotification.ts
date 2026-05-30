export type BrowserNotificationPermission = NotificationPermission | 'unsupported';

export function getBrowserNotificationPermission(): BrowserNotificationPermission {
  if (!('Notification' in window)) {
    return 'unsupported';
  }
  return Notification.permission;
}

export async function requestBrowserNotificationPermission(): Promise<BrowserNotificationPermission> {
  if (!('Notification' in window)) {
    return 'unsupported';
  }
  return Notification.requestPermission();
}

export function showBrowserNotification(title: string, body: string): boolean {
  if (!('Notification' in window) || Notification.permission !== 'granted') {
    return false;
  }
  const notification = new Notification(title, {
    body,
    tag: `voicecal-${title}-${body}`,
  });
  notification.onclick = () => {
    window.focus();
    notification.close();
  };
  return true;
}
