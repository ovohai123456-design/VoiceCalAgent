import { http, unwrapApiResponse } from '@/api/http';

export interface ReminderTask {
  id: string;
  title: string;
  triggerTime: string;
  channel: string;
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
