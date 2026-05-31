import { http, unwrapApiResponse } from '@/api/http';

export interface ReminderTask {
  id: number;
  relatedReminderIds?: number[];
  userId: number;
  eventId: number;
  jobType: string;
  jobTypes?: string[];
  jobPayloadJson?: string;
  runAt: string;
  status: string;
  retryCount: number;
  maxRetryCount: number;
  lastError?: string;
  executedAt?: string;
  createdAt: string;
}

export async function listReminderTasks(): Promise<ReminderTask[]> {
  const { data } = await http.get<ReminderTask[] | { data: ReminderTask[]; success: boolean; message?: string }>(
    '/api/reminders',
  );

  if (Array.isArray(data)) {
    return data;
  }

  return unwrapApiResponse(data);
}

export async function deleteReminderTask(reminderId: number): Promise<number> {
  const { data } = await http.delete<number | { data: number; success: boolean; message?: string }>(
    `/api/reminders/${reminderId}`,
  );

  return typeof data === 'number' ? data : unwrapApiResponse(data);
}

export async function clearReminderTasks(): Promise<number> {
  const { data } = await http.delete<number | { data: number; success: boolean; message?: string }>(
    '/api/reminders',
  );

  return typeof data === 'number' ? data : unwrapApiResponse(data);
}
