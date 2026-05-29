export interface CalendarEventItem {
  id?: string | number;
  eventId?: string | number;
  userId?: string | number;
  title: string;
  startTime: string;
  endTime: string;
  location?: string;
  description?: string;
  meetingUrl?: string;
  reminderMinutes?: number;
  source?: string;
  status?: string;
}

export interface CalendarQueryParams {
  userId: string;
  startTime: string;
  endTime: string;
  keyword?: string;
  status?: string;
}
