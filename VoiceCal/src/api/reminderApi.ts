import { http, unwrapApiResponse } from '@/api/http';

export interface ReminderTask {
  id: number;
  userId: number;
  eventId: number;
  jobType: string;
  jobPayloadJson?: string;
  runAt: string;
  status: string;
  retryCount: number;
  maxRetryCount: number;
  lastError?: string;
  executedAt?: string;
  createdAt: string;
}

export async function listReminderTasks(userId: string): Promise<ReminderTask[]> {
  const { data } = await http.get<ReminderTask[] | { data: ReminderTask[]; success: boolean; message?: string }>(
    '/api/reminders',
    { params: { userId } },
  );

  if (Array.isArray(data)) {
    return data;
  }

  return unwrapApiResponse(data);
}

export async function deleteReminderTask(reminderId: number, userId: string): Promise<number> {
  const { data } = await http.delete<number | { data: number; success: boolean; message?: string }>(
    `/api/reminders/${reminderId}`,
    { params: { userId } },
  );

  return typeof data === 'number' ? data : unwrapApiResponse(data);
}

export async function clearReminderTasks(userId: string): Promise<number> {
  const { data } = await http.delete<number | { data: number; success: boolean; message?: string }>(
    '/api/reminders',
    { params: { userId } },
  );

  return typeof data === 'number' ? data : unwrapApiResponse(data);
}
