<template>
  <el-card class="calendar-panel" shadow="never">
    <template #header>
      <div class="panel-header">
        <div>
          <h2>日历视图面板</h2>
          <p>支持月、周、日三种视图，并随任务执行刷新</p>
        </div>
        <el-button plain @click="refresh">刷新日历</el-button>
      </div>
    </template>

    <el-skeleton :loading="loading" animated :rows="10">
      <FullCalendar ref="calendarRef" :options="calendarOptions" />
    </el-skeleton>
  </el-card>

  <el-dialog v-model="detailVisible" title="日程详情" width="480px">
    <template v-if="selectedEvent">
      <div class="detail-item">
        <span>标题</span>
        <strong>{{ selectedEvent.title }}</strong>
      </div>
      <div class="detail-item">
        <span>时间</span>
        <strong>{{ selectedEvent.startTime }} - {{ selectedEvent.endTime }}</strong>
      </div>
      <div class="detail-item">
        <span>地点</span>
        <strong>{{ selectedEvent.location || '未填写' }}</strong>
      </div>
      <div class="detail-item">
        <span>描述</span>
        <strong>{{ selectedEvent.description || '未填写' }}</strong>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import type { CalendarOptions, DatesSetArg, EventClickArg, EventInput } from '@fullcalendar/core';
import zhCnLocale from '@fullcalendar/core/locales/zh-cn';
import timeGridPlugin from '@fullcalendar/timegrid';
import FullCalendar from '@fullcalendar/vue3';
import { ElMessage } from 'element-plus';
import { computed, ref } from 'vue';

import { getCalendarEvents, mapCalendarEventToFullCalendar } from '@/api/calendarApi';
import type { CalendarEventItem } from '@/types/calendar';
import { DEFAULT_USER_ID } from '@/utils/session';
import { formatCurrentTime } from '@/utils/time';

const calendarRef = ref<InstanceType<typeof FullCalendar> | null>(null);
const loading = ref(false);
const events = ref<EventInput[]>([]);
const selectedEvent = ref<CalendarEventItem | null>(null);
const detailVisible = ref(false);
const currentRange = ref<{ start: Date; end: Date } | null>(null);

const calendarOptions = computed<CalendarOptions>(() => ({
  plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin],
  locale: zhCnLocale,
  initialView: 'dayGridMonth',
  headerToolbar: {
    left: 'prev,next today',
    center: 'title',
    right: 'dayGridMonth,timeGridWeek,timeGridDay',
  },
  buttonText: {
    today: '今天',
    month: '月视图',
    week: '周视图',
    day: '日视图',
  },
  height: 700,
  dayMaxEvents: true,
  events: events.value,
  datesSet: (arg: DatesSetArg) => {
    currentRange.value = { start: arg.start, end: arg.end };
    void loadEvents(arg.start, arg.end);
  },
  eventClick: (arg: EventClickArg) => {
    selectedEvent.value = arg.event.extendedProps.raw as CalendarEventItem;
    detailVisible.value = true;
  },
}));

async function loadEvents(start: Date, end: Date): Promise<void> {
  loading.value = true;
  try {
    const result = await getCalendarEvents({
      userId: DEFAULT_USER_ID,
      startTime: formatCurrentTime(start),
      endTime: formatCurrentTime(end),
    });
    events.value = result.map(mapCalendarEventToFullCalendar);
  } catch (error) {
    console.error(error);
    ElMessage.error('日程加载失败。');
  } finally {
    loading.value = false;
  }
}

async function refresh(): Promise<void> {
  if (!currentRange.value) {
    const api = calendarRef.value?.getApi();
    if (api) {
      await loadEvents(api.view.activeStart, api.view.activeEnd);
    }
    return;
  }

  await loadEvents(currentRange.value.start, currentRange.value.end);
}

defineExpose({ refresh });
</script>

<style scoped>
.calendar-panel {
  height: 100%;
  border: 1px solid rgba(41, 89, 173, 0.12);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
}

:deep(.el-card__header) {
  padding: 20px 22px 12px;
  border-bottom: none;
}

:deep(.el-card__body) {
  padding: 8px 22px 22px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.panel-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
}

.panel-header p {
  margin: 0;
  color: #53627c;
}

:deep(.fc) {
  --fc-border-color: rgba(120, 148, 214, 0.18);
  --fc-button-bg-color: #153c78;
  --fc-button-border-color: #153c78;
  --fc-button-hover-bg-color: #1c529f;
  --fc-button-hover-border-color: #1c529f;
  --fc-button-active-bg-color: #0f2e59;
  --fc-button-active-border-color: #0f2e59;
  --fc-event-bg-color: #18a77b;
  --fc-event-border-color: #18a77b;
  --fc-today-bg-color: rgba(45, 125, 255, 0.08);
}

:deep(.fc .fc-toolbar-title) {
  font-size: 22px;
  font-weight: 700;
  color: #1d2d50;
}

:deep(.fc .fc-button) {
  border-radius: 999px;
  padding: 0.55em 1em;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid rgba(101, 127, 180, 0.12);
}

.detail-item:last-child {
  border-bottom: none;
}

.detail-item span {
  color: #60708a;
}

.detail-item strong {
  text-align: right;
  color: #1f2d3d;
}
</style>
