import { http, unwrapApiResponse } from '@/api/http';

export interface ContactItem {
  id?: number;
  userId: number;
  name: string;
  phone?: string;
  email?: string;
}

export interface UserPreference {
  userId: number;
  defaultDurationMinutes?: number;
  defaultReminderMinutes?: number;
  defaultLocation?: string;
  defaultEmail?: string;
}

export async function listContacts(userId: string): Promise<ContactItem[]> {
  const { data } = await http.get<ContactItem[] | { success: boolean; data: ContactItem[] }>('/api/contacts', {
    params: { userId },
  });
  return Array.isArray(data) ? data : unwrapApiResponse(data);
}

export async function saveContact(contact: ContactItem): Promise<ContactItem> {
  const { data } = await http.post<ContactItem | { success: boolean; data: ContactItem }>('/api/contacts', contact);
  return unwrapApiResponse(data);
}

export async function deleteContact(id: number, userId: string): Promise<boolean> {
  const { data } = await http.delete<boolean | { success: boolean; data: boolean }>(`/api/contacts/${id}`, {
    params: { userId },
  });
  return unwrapApiResponse(data);
}

export async function getUserPreference(userId: string): Promise<UserPreference> {
  const { data } = await http.get<UserPreference | { success: boolean; data: UserPreference }>('/api/preferences', {
    params: { userId },
  });
  return unwrapApiResponse(data);
}

export async function saveUserPreference(preference: UserPreference): Promise<UserPreference> {
  const { data } = await http.put<UserPreference | { success: boolean; data: UserPreference }>(
    '/api/preferences',
    preference,
  );
  return unwrapApiResponse(data);
}
