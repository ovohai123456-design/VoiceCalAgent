<template>
  <section class="calendar-panel">
    <div class="panel-heading">
      <div>
        <h2>日历</h2>
        <span>点击日程可查看、编辑或删除</span>
      </div>
      <el-button :icon="Refresh" circle plain title="刷新日历" @click="refresh" />
    </div>
    <div v-loading="loading" class="calendar-body">
      <FullCalendar ref="calendarRef" :options="calendarOptions" />
    </div>
  </section>

  <el-dialog v-model="detailVisible" title="日程详情" width="480px">
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
      <el-button :icon="Edit" @click="openEdit">编辑</el-button>
      <el-button :icon="Delete" type="danger" plain @click="removeSelectedEvent">删除</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="editVisible" title="编辑日程" width="520px">
    <el-form label-position="top">
      <el-form-item label="标题"><el-input v-model="editForm.title" /></el-form-item>
      <el-form-item label="开始时间"><el-input v-model="editForm.startTime" placeholder="yyyy-MM-dd HH:mm:ss" /></el-form-item>
      <el-form-item label="结束时间"><el-input v-model="editForm.endTime" placeholder="yyyy-MM-dd HH:mm:ss" /></el-form-item>
      <el-form-item label="地点"><el-input v-model="editForm.location" /></el-form-item>
      <el-form-item label="描述"><el-input v-model="editForm.description" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="提前提醒">
        <el-input-number v-model="editForm.reminderMinutes" :min="0" :max="1440" />
        <span class="unit">分钟</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
    </template>
  </el-dialog>
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
import { DEFAULT_USER_ID } from '@/utils/session';
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
  height: 690,
  dayMaxEvents: true,
  events: async (info, successCallback, failureCallback) => {
    loading.value = true;
    try {
      const events = await getCalendarEvents({
        userId: DEFAULT_USER_ID,
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
    await updateCalendarEvent(eventId, { userId: DEFAULT_USER_ID, ...editForm.value });
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
    await deleteCalendarEvent(eventId, DEFAULT_USER_ID, scope);
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
  padding: 18px;
  border: 1px solid rgb(160 207 255 / 18%);
  border-radius: 22px;
  background: linear-gradient(145deg, rgb(17 48 84 / 62%), rgb(8 25 49 / 52%));
  box-shadow: 0 18px 54px rgb(0 6 22 / 30%), inset 0 1px 0 rgb(255 255 255 / 8%);
  backdrop-filter: blur(26px);
}

.panel-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

h2 {
  margin: 0 0 4px;
  color: #f0f8ff;
  font-size: 17px;
}

.panel-heading span,
.unit {
  color: rgb(205 227 250 / 66%);
  font-size: 12px;
}

.unit {
  margin-left: 8px;
}

:deep(.fc) {
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
  color: rgb(225 240 255 / 84%);
}

:deep(.fc .fc-toolbar-title) {
  color: #edf8ff;
  font-size: 18px;
}

:deep(.fc .fc-button) {
  border-radius: 4px;
}

.detail-list {
  display: grid;
}

.detail-list div {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px solid rgb(160 207 255 / 16%);
}

.detail-list span {
  color: rgb(205 227 250 / 66%);
}
</style>
