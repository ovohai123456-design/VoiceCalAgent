<template>
  <section class="calendar-panel">
    <div class="panel-heading">
      <div>
        <h2>日历</h2>
        <span>点击日程查看、编辑或删除</span>
      </div>
      <el-button :icon="Refresh" circle plain title="刷新日历" @click="refresh" />
    </div>
    <div v-loading="loading" class="calendar-body">
      <FullCalendar ref="calendarRef" :options="calendarOptions" />
    </div>
  </section>

  <el-drawer v-model="detailVisible" title="日程详情" size="420px" class="calendar-drawer">
    <div v-if="selectedEvent" class="detail-list">
      <div><span>标题</span><strong>{{ selectedEvent.title }}</strong></div>
      <div><span>开始</span><strong>{{ selectedEvent.startTime }}</strong></div>
      <div><span>结束</span><strong>{{ selectedEvent.endTime }}</strong></div>
      <div><span>地点</span><strong>{{ selectedEvent.location || '未设置' }}</strong></div>
      <div><span>描述</span><strong>{{ selectedEvent.description || '未设置' }}</strong></div>
      <div><span>会议类型</span><strong>{{ resolveMeetingProvider(selectedEvent.meetingProvider) }}</strong></div>
      <div><span>会议号</span><strong>{{ selectedEvent.meetingCode || '未设置' }}</strong></div>
      <div><span>入会链接</span><strong>{{ selectedEvent.meetingUrl || '未设置' }}</strong></div>
    </div>
    <template #footer>
      <div class="drawer-actions">
        <el-button :icon="Delete" type="danger" plain @click="removeSelectedEvent">删除</el-button>
        <el-button :icon="Edit" type="primary" @click="openEdit">编辑</el-button>
      </div>
    </template>
  </el-drawer>

  <el-drawer v-model="editVisible" title="编辑日程" size="460px" class="calendar-drawer">
    <el-form label-position="top">
      <el-form-item label="标题"><el-input v-model="editForm.title" /></el-form-item>
      <el-form-item label="开始时间">
        <el-date-picker
          v-model="editForm.startTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          placeholder="选择开始时间"
        />
      </el-form-item>
      <el-form-item label="结束时间">
        <el-date-picker
          v-model="editForm.endTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          placeholder="选择结束时间"
        />
      </el-form-item>
      <el-form-item label="地点"><el-input v-model="editForm.location" /></el-form-item>
      <el-form-item label="描述"><el-input v-model="editForm.description" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="提前提醒">
        <el-input-number v-model="editForm.reminderMinutes" :min="0" :max="1440" />
        <span class="unit">分钟</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="drawer-actions">
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import type { CalendarOptions, EventClickArg } from '@fullcalendar/core';
import zhCnLocale from '@fullcalendar/core/locales/zh-cn';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import timeGridPlugin from '@fullcalendar/timegrid';
import FullCalendar from '@fullcalendar/vue3';
import { Delete, Edit, Refresh } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ref } from 'vue';

import { deleteCalendarEvent, getCalendarEvents, mapCalendarEventToFullCalendar, updateCalendarEvent } from '@/api/calendarApi';
import type { CalendarEventItem } from '@/types/calendar';
import { formatCurrentTime } from '@/utils/time';

const emit = defineEmits<{ (event: 'calendar-change'): void }>();
const calendarRef = ref<InstanceType<typeof FullCalendar> | null>(null);
const loading = ref(false);
const saving = ref(false);
const detailVisible = ref(false);
const editVisible = ref(false);
const selectedEvent = ref<CalendarEventItem | null>(null);
const editForm = ref({ title: '', startTime: '', endTime: '', location: '', description: '', reminderMinutes: 10 });

const calendarOptions: CalendarOptions = {
  plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin],
  locale: zhCnLocale,
  initialView: 'dayGridMonth',
  headerToolbar: { left: 'prev,next today', center: 'title', right: 'dayGridMonth,timeGridWeek,timeGridDay' },
  buttonText: { today: '今天', month: '月', week: '周', day: '日' },
  height: '100%',
  dayMaxEventRows: 3,
  dayMaxEvents: true,
  events: async (info, successCallback, failureCallback) => {
    loading.value = true;
    try {
      const events = await getCalendarEvents({
        startTime: formatCurrentTime(info.start),
        endTime: formatCurrentTime(info.end),
      });
      successCallback(events.map(mapCalendarEventToFullCalendar));
    } catch (error) {
      failureCallback(error instanceof Error ? error : new Error('日程加载失败'));
      ElMessage.error('日程加载失败');
    } finally {
      loading.value = false;
    }
  },
  eventClick: (arg: EventClickArg) => {
    selectedEvent.value = arg.event.extendedProps.raw as CalendarEventItem;
    detailVisible.value = true;
  },
};

async function refresh(): Promise<void> {
  calendarRef.value?.getApi().refetchEvents();
}

function openEdit(): void {
  if (!selectedEvent.value) return;
  Object.assign(editForm.value, {
    title: selectedEvent.value.title,
    startTime: selectedEvent.value.startTime,
    endTime: selectedEvent.value.endTime,
    location: selectedEvent.value.location ?? '',
    description: selectedEvent.value.description ?? '',
    reminderMinutes: selectedEvent.value.reminderMinutes ?? 10,
  });
  detailVisible.value = false;
  editVisible.value = true;
}

async function saveEdit(): Promise<void> {
  const eventId = selectedEvent.value?.id ?? selectedEvent.value?.eventId;
  if (!eventId) return;
  saving.value = true;
  try {
    await updateCalendarEvent(eventId, { ...editForm.value });
    editVisible.value = false;
    await refresh();
    emit('calendar-change');
    ElMessage.success('日程已更新');
  } finally {
    saving.value = false;
  }
}

async function removeSelectedEvent(): Promise<void> {
  const eventId = selectedEvent.value?.id ?? selectedEvent.value?.eventId;
  if (!eventId) return;
  const eventTitle = selectedEvent.value?.title ?? '该日程';
  try {
    let scope: 'SINGLE' | 'SERIES' = 'SINGLE';
    if (selectedEvent.value?.recurrenceSeriesId) {
      try {
        await ElMessageBox.confirm('删除整个重复系列，或仅删除当前一次？', '删除重复日程', {
          distinguishCancelAndClose: true,
          confirmButtonText: '删除整个系列',
          cancelButtonText: '仅删除本次',
          type: 'warning',
        });
        scope = 'SERIES';
      } catch (action) {
        if (action === 'close') return;
      }
    }
    await ElMessageBox.confirm(`确认删除“${eventTitle}”吗？`, '删除日程', { type: 'warning' });
    await deleteCalendarEvent(eventId, scope);
    detailVisible.value = false;
    await refresh();
    emit('calendar-change');
    ElMessage.success('日程已删除');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('日程删除失败');
  }
}

function resolveMeetingProvider(provider?: string): string {
  if (provider === 'TENCENT_MEETING') return '腾讯会议';
  return provider || '未设置';
}

defineExpose({ refresh });
</script>

<style scoped>
.calendar-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  height: 100%;
  padding: 18px;
  border: 1px solid rgb(170 220 255 / 20%);
  border-radius: 30px;
  background:
    radial-gradient(circle at 70% 0%, rgb(65 105 225 / 22%), transparent 36%),
    linear-gradient(145deg, rgb(17 48 84 / 66%), rgb(8 25 49 / 54%));
  box-shadow: 0 26px 76px rgb(0 6 22 / 34%), inset 0 1px 0 rgb(255 255 255 / 10%);
  backdrop-filter: blur(26px);
}

.panel-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

h2 {
  margin: 0 0 5px;
  color: #f0f8ff;
  font-size: 22px;
  letter-spacing: 0.02em;
}

.panel-heading span,
.unit {
  color: rgb(205 227 250 / 68%);
  font-size: 12px;
}

.calendar-body {
  min-height: 0;
  overflow: hidden;
  border: 1px solid rgb(160 207 255 / 10%);
  border-radius: 18px;
  background: rgb(3 15 32 / 32%);
}

.unit {
  margin-left: 8px;
}

:deep(.fc) {
  height: 100%;
  --fc-border-color: rgb(161 205 248 / 16%);
  --fc-button-bg-color: rgb(39 103 164 / 74%);
  --fc-button-border-color: rgb(137 198 255 / 22%);
  --fc-button-hover-bg-color: rgb(49 127 197 / 86%);
  --fc-button-hover-border-color: rgb(162 214 255 / 38%);
  --fc-button-active-bg-color: rgb(62 117 189 / 92%);
  --fc-button-active-border-color: rgb(177 220 255 / 44%);
  --fc-event-bg-color: rgb(34 160 167 / 84%);
  --fc-event-border-color: rgb(116 235 234 / 58%);
  --fc-page-bg-color: transparent;
  --fc-neutral-bg-color: rgb(12 34 63 / 46%);
  --fc-list-event-hover-bg-color: rgb(42 96 145 / 38%);
  --fc-today-bg-color: rgb(45 133 182 / 20%);
  color: rgb(225 240 255 / 86%);
}

:deep(.fc .fc-view-harness) {
  min-height: 0;
}

:deep(.fc .fc-toolbar) {
  gap: 12px;
  flex-wrap: wrap;
  padding: 0 0 10px;
}

:deep(.fc .fc-toolbar-title) {
  color: #edf8ff;
  font-size: 20px;
  font-weight: 800;
}

:deep(.fc .fc-button) {
  border-radius: 10px;
  padding: 6px 12px;
  font-weight: 700;
  box-shadow: 0 8px 20px rgb(0 7 25 / 18%);
}

:deep(.fc .fc-col-header-cell) {
  padding: 7px 0;
  background: rgb(8 28 54 / 62%);
}

:deep(.fc .fc-daygrid-day-number) {
  padding: 7px 9px;
  color: rgb(230 245 255 / 86%);
  font-size: 13px;
  font-weight: 700;
}

:deep(.fc-event) {
  border-color: transparent;
  border-radius: 999px;
  color: #eafaff;
  padding: 1px 7px;
  font-size: 11px;
  font-weight: 700;
}

:deep(.fc-event .fc-event-main) {
  color: #eafaff;
}

:deep(.fc .fc-daygrid-event) {
  margin: 2px 6px 0;
}

:deep(.fc .fc-day-other .fc-daygrid-day-number) {
  color: rgb(166 192 217 / 42%);
}

:deep(.fc-event.calendar-event-meeting) {
  background: rgb(42 139 224 / 92%);
}

:deep(.fc-event.calendar-event-focus) {
  background: rgb(10 173 122 / 90%);
}

:deep(.fc-event.calendar-event-general) {
  background: rgb(128 91 224 / 90%);
}

.detail-list {
  display: grid;
  gap: 2px;
}

.detail-list div {
  display: grid;
  gap: 7px;
  padding: 13px 0;
  border-bottom: 1px solid rgb(160 207 255 / 16%);
}

.detail-list span {
  color: rgb(205 227 250 / 66%);
  font-size: 12px;
}

.detail-list strong {
  color: #eff9ff;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

:deep(.el-date-editor.el-input),
:deep(.el-date-editor.el-input__wrapper) {
  width: 100%;
}
</style>
