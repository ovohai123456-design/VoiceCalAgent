import type { EventInput } from '@fullcalendar/core';

import { http, unwrapApiResponse } from '@/api/http';
import type { CalendarEventItem, CalendarQueryParams } from '@/types/calendar';

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

function wait<T>(data: T, delay = 350): Promise<T> {
  return new Promise((resolve) => {
    window.setTimeout(() => resolve(data), delay);
  });
}

function buildMockEvents(startTime: string): CalendarEventItem[] {
  const day = startTime.slice(0, 10);
  return [
    {
      eventId: 'evt_mock_001',
      title: '开组会',
      startTime: `${day} 15:00:00`,
      endTime: `${day} 16:00:00`,
      location: '会议室 A',
      description: '项目周会',
    },
    {
      eventId: 'evt_mock_002',
      title: '论文讨论',
      startTime: `${day} 19:00:00`,
      endTime: `${day} 20:00:00`,
      location: '线上会议',
      description: '答辩前材料确认',
    },
  ];
}

export async function getCalendarEvents(params: CalendarQueryParams): Promise<CalendarEventItem[]> {
  if (USE_MOCK) {
    return wait(buildMockEvents(params.startTime));
  }

  const { data } = await http.get<CalendarEventItem[] | { data: CalendarEventItem[]; success: boolean; message?: string }>(
    '/api/calendar/events',
    { params },
  );

  if (Array.isArray(data)) {
    return data;
  }

  return unwrapApiResponse(data);
}

export function mapCalendarEventToFullCalendar(event: CalendarEventItem): EventInput {
  const eventId = event.eventId ?? event.id ?? `${event.title}-${event.startTime}`;

  return {
    id: String(eventId),
    title: event.title,
    start: event.startTime.replace(' ', 'T'),
    end: event.endTime.replace(' ', 'T'),
    extendedProps: {
      raw: event,
    },
  };
}
