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
  meetingProvider?: string;
  meetingCode?: string;
  reminderMinutes?: number;
  source?: string;
  status?: string;
  recurrenceSeriesId?: string;
  recurrenceIndex?: number;
}

export interface CalendarQueryParams {
  userId: string;
  startTime: string;
  endTime: string;
  keyword?: string;
  status?: string;
}

export interface CalendarEventUpdateRequest {
  userId: string;
  title?: string;
  startTime?: string;
  endTime?: string;
  location?: string;
  description?: string;
  meetingUrl?: string;
  reminderMinutes?: number;
}
