<template>
  <div class="voicecal-page">
    <StatusHeader :current-time="currentTime" :status="status" :status-label="statusLabel" />

    <main class="workspace-grid">
      <section class="main-column">
        <VoiceCommandPanel
          v-model="commandText"
          :messages="messages"
          :status="status"
          :last-input-type="lastInputType"
          @manual-input="handleManualInput"
          @start-voice="handleStartVoice"
          @stop-voice="handleStopVoice"
          @submit="handleSubmit"
        />

        <ConflictSuggestionPanel
          v-if="conflictSlots.length"
          :slots="conflictSlots"
          :loading-index="selectingSlotIndex"
          @select="handleSelectSlot"
          @cancel="handleCancel"
        />

        <EventCandidatePanel
          v-if="eventCandidates.length"
          :candidates="eventCandidates"
          :loading-index="selectingEventIndex"
          @select="handleSelectEvent"
          @cancel="handleCancel"
        />

        <CalendarView ref="calendarViewRef" @calendar-change="pollReminders" />
      </section>

      <aside class="side-column">
        <WorkspaceSidebar
          :reminders="reminders"
          :notification-button-text="notificationButtonText"
          @request-notification="enableBrowserNotifications"
          @delete-reminder="handleDeleteReminder"
          @clear-reminders="handleClearReminders"
        />
        <WorkflowTimeline ref="workflowTimelineRef" :task-id="taskId" />
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { executeAgent, selectAgentEvent, selectAgentSlot } from '@/api/agentApi';
import { clearReminderTasks, deleteReminderTask, listReminderTasks, type ReminderTask } from '@/api/reminderApi';
import CalendarView from '@/components/CalendarView.vue';
import ConflictSuggestionPanel from '@/components/ConflictSuggestionPanel.vue';
import EventCandidatePanel from '@/components/EventCandidatePanel.vue';
import StatusHeader from '@/components/StatusHeader.vue';
import VoiceCommandPanel from '@/components/VoiceCommandPanel.vue';
import WorkflowTimeline from '@/components/WorkflowTimeline.vue';
import WorkspaceSidebar from '@/components/WorkspaceSidebar.vue';
import type { AgentResponse, ConversationMessage, InputType, PageStatus, SuggestedSlot } from '@/types/agent';
import type { CalendarEventItem } from '@/types/calendar';
import {
  getBrowserNotificationPermission,
  requestBrowserNotificationPermission,
  showBrowserNotification,
  type BrowserNotificationPermission,
} from '@/utils/browserNotification';
import { DEFAULT_USER_ID, getCurrentTimezone, getSessionId } from '@/utils/session';
import { createSpeechRecognition, speak, startRecognition, stopRecognition } from '@/utils/speech';
import { formatCurrentTime } from '@/utils/time';

const commandText = ref('');
const sessionId = getSessionId();
const messages = ref<ConversationMessage[]>([
  { id: 1, role: 'ASSISTANT', text: '你好，我是 VoiceCal。你可以直接告诉我需要创建、查询、修改或删除什么日程。' },
]);
const status = ref<PageStatus>('IDLE');
const currentTime = ref(formatCurrentTime());
const confirmToken = ref('');
const taskId = ref<string | null>(null);
const lastInputType = ref<InputType>('TEXT');
const speechRecognition = ref<SpeechRecognition | null>(null);
const calendarViewRef = ref<InstanceType<typeof CalendarView> | null>(null);
const workflowTimelineRef = ref<InstanceType<typeof WorkflowTimeline> | null>(null);
const conflictSlots = ref<SuggestedSlot[]>([]);
const selectingSlotIndex = ref<number | null>(null);
const eventCandidates = ref<CalendarEventItem[]>([]);
const selectingEventIndex = ref<number | null>(null);
const reminders = ref<ReminderTask[]>([]);
const browserNotificationPermission = ref<BrowserNotificationPermission>(getBrowserNotificationPermission());
const notifiedReminderIds = new Set<number>();

let timerId: number | null = null;
let reminderTimerId: number | null = null;
let reminderPollingInitialized = false;
let messageSequence = 1;

const statusLabel = computed(() => ({
  IDLE: '空闲',
  LISTENING: '正在识别',
  RECOGNIZED: '已识别',
  WAITING_RESPONSE: '正在分析',
  WAITING_CONFIRM: '等待确认',
  EXECUTING: '执行中',
  DONE: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
}[status.value]));

const notificationButtonText = computed(() => {
  if (browserNotificationPermission.value === 'granted') return '系统通知已开启';
  if (browserNotificationPermission.value === 'denied') return '系统通知已拒绝';
  if (browserNotificationPermission.value === 'unsupported') return '浏览器不支持系统通知';
  return '系统通知未开启';
});

onMounted(() => {
  timerId = window.setInterval(() => { currentTime.value = formatCurrentTime(); }, 1000);
  void pollReminders();
  reminderTimerId = window.setInterval(() => { void pollReminders(); }, 15_000);
});

onBeforeUnmount(() => {
  if (timerId !== null) window.clearInterval(timerId);
  if (reminderTimerId !== null) window.clearInterval(reminderTimerId);
  stopRecognition(speechRecognition.value);
});

function handleManualInput(): void {
  lastInputType.value = 'TEXT';
}

function handleStartVoice(): void {
  const recognition = speechRecognition.value ?? createSpeechRecognition({ lang: 'zh-CN', continuous: false, interimResults: false });
  if (!recognition) {
    ElMessage.warning('当前浏览器不支持语音识别，请使用 Chrome、Edge 或文本输入。');
    return;
  }
  speechRecognition.value = recognition;
  recognition.onresult = (event: SpeechRecognitionEvent) => {
    const transcript = Array.from({ length: event.results.length }, (_, index) => event.results[index][0].transcript).join('').trim();
    if (!transcript) return;
    void sendConversationMessage(transcript, 'VOICE');
  };
  recognition.onerror = () => {
    status.value = 'FAILED';
    ElMessage.error('语音识别失败，请重试或改用文本输入。');
  };
  recognition.onend = () => {
    if (status.value === 'LISTENING') status.value = commandText.value.trim() ? 'RECOGNIZED' : 'IDLE';
  };
  status.value = 'LISTENING';
  startRecognition(recognition);
}

function handleStopVoice(): void {
  stopRecognition(speechRecognition.value);
  if (status.value === 'LISTENING') status.value = commandText.value.trim() ? 'RECOGNIZED' : 'IDLE';
}

async function handleSubmit(): Promise<void> {
  const text = commandText.value.trim();
  if (!text) {
    ElMessage.warning('请输入消息，或点击麦克风直接说。');
    return;
  }
  await sendConversationMessage(text, lastInputType.value);
}

async function sendConversationMessage(text: string, inputType: InputType): Promise<void> {
  const content = text.trim();
  if (!content) return;
  commandText.value = '';
  lastInputType.value = inputType;
  appendMessage('USER', content, inputType);
  status.value = 'WAITING_RESPONSE';
  try {
    await applyAgentResponse(await executeAgent({
      userId: DEFAULT_USER_ID,
      sessionId,
      inputType,
      text: content,
      timezone: getCurrentTimezone(),
      currentTime: formatCurrentTime(),
    }));
  } catch (error) {
    handleRequestError(error);
  }
}

async function handleCancel(): Promise<void> {
  if (!confirmToken.value) return;
  await sendConversationMessage('取消', 'TEXT');
}

async function handleSelectSlot(slotIndex: number): Promise<void> {
  if (!confirmToken.value) return;
  selectingSlotIndex.value = slotIndex;
  appendMessage('USER', `第 ${slotIndex + 1} 个`, 'TEXT');
  status.value = 'EXECUTING';
  try {
    await applyAgentResponse(await selectAgentSlot({
      userId: DEFAULT_USER_ID,
      sessionId,
      confirmToken: confirmToken.value,
      slotIndex,
    }));
  } catch (error) {
    handleRequestError(error);
  } finally {
    selectingSlotIndex.value = null;
  }
}

async function handleSelectEvent(candidateIndex: number): Promise<void> {
  if (!confirmToken.value) return;
  selectingEventIndex.value = candidateIndex;
  appendMessage('USER', `第 ${candidateIndex + 1} 个`, 'TEXT');
  status.value = 'WAITING_RESPONSE';
  try {
    await applyAgentResponse(await selectAgentEvent({
      userId: DEFAULT_USER_ID,
      sessionId,
      confirmToken: confirmToken.value,
      candidateIndex,
    }));
  } catch (error) {
    handleRequestError(error);
  } finally {
    selectingEventIndex.value = null;
  }
}

async function applyAgentResponse(response: AgentResponse): Promise<void> {
  taskId.value = response.taskId ?? taskId.value;
  confirmToken.value = response.confirmToken ?? '';
  conflictSlots.value = extractSuggestedSlots(response);
  eventCandidates.value = extractEventCandidates(response);
  appendMessage('ASSISTANT', response.replyText);
  if (response.needConfirm || response.needEventSelection) status.value = 'WAITING_CONFIRM';
  else if (response.needClarify) status.value = 'RECOGNIZED';
  else status.value = 'DONE';
  await speak(response.speakText || response.replyText);
  if (status.value === 'DONE') {
    clearPendingSelection();
    await refreshCalendar();
    await pollReminders();
  }
  await refreshTimeline();
  if (shouldContinueVoiceConversation(response)) {
    handleStartVoice();
  }
}

function appendMessage(role: ConversationMessage['role'], text: string, inputType?: InputType): void {
  if (!text.trim()) return;
  messages.value.push({ id: ++messageSequence, role, text, inputType });
}

function shouldContinueVoiceConversation(response: AgentResponse): boolean {
  return lastInputType.value === 'VOICE'
    && Boolean(response.needConfirm || response.needClarify || response.needEventSelection);
}

function clearPendingSelection(): void {
  confirmToken.value = '';
  conflictSlots.value = [];
  eventCandidates.value = [];
}

function extractSuggestedSlots(response: AgentResponse): SuggestedSlot[] {
  const data = response.data as { conflictResult?: { suggestedSlots?: SuggestedSlot[] } } | undefined;
  return data?.conflictResult?.suggestedSlots ?? [];
}

function extractEventCandidates(response: AgentResponse): CalendarEventItem[] {
  const data = response.data as { candidates?: CalendarEventItem[] } | undefined;
  return response.needEventSelection ? data?.candidates ?? [] : [];
}

async function refreshCalendar(): Promise<void> {
  await calendarViewRef.value?.refresh();
}

async function refreshTimeline(): Promise<void> {
  await workflowTimelineRef.value?.refresh();
}

async function pollReminders(): Promise<void> {
  try {
    const tasks = await listReminderTasks(DEFAULT_USER_ID);
    reminders.value = tasks;
    const executed = tasks.filter((reminder) => reminder.status === 'EXECUTED');
    if (!reminderPollingInitialized) {
      executed.forEach((reminder) => notifiedReminderIds.add(reminder.id));
      reminderPollingInitialized = true;
      return;
    }
    for (const reminder of executed) {
      if (notifiedReminderIds.has(reminder.id)) continue;
      notifiedReminderIds.add(reminder.id);
      const title = resolveReminderTitle(reminder);
      ElNotification({ title: '日程提醒', message: title, type: 'warning', duration: 0 });
      showBrowserNotification('日程提醒', title);
      await speak(`日程提醒：${title}`);
    }
  } catch (error) {
    console.error('Reminder polling failed', error);
  }
}

async function enableBrowserNotifications(): Promise<void> {
  browserNotificationPermission.value = await requestBrowserNotificationPermission();
  if (browserNotificationPermission.value === 'granted') ElMessage.success('系统通知已开启');
  else ElMessage.warning('系统通知未开启，请检查浏览器站点权限。');
}

async function handleDeleteReminder(reminder: ReminderTask): Promise<void> {
  try {
    await ElMessageBox.confirm('确认删除该提醒任务吗？', '删除提醒任务', { type: 'warning' });
    await deleteReminderTask(reminder.id, DEFAULT_USER_ID);
    await pollReminders();
    ElMessage.success('提醒任务已删除');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('提醒任务删除失败');
  }
}

async function handleClearReminders(): Promise<void> {
  try {
    await ElMessageBox.confirm('确认清空全部提醒任务吗？该操作不可恢复。', '清空提醒任务', { type: 'warning' });
    await clearReminderTasks(DEFAULT_USER_ID);
    notifiedReminderIds.clear();
    await pollReminders();
    ElMessage.success('提醒任务已清空');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('提醒任务清空失败');
  }
}

function resolveReminderTitle(reminder: ReminderTask): string {
  if (!reminder.jobPayloadJson) return `日程 ${reminder.eventId} 即将开始`;
  try {
    const payload = JSON.parse(reminder.jobPayloadJson) as { title?: string };
    return payload.title ? `${payload.title} 即将开始` : `日程 ${reminder.eventId} 即将开始`;
  } catch {
    return `日程 ${reminder.eventId} 即将开始`;
  }
}

function handleRequestError(error: unknown): void {
  console.error(error);
  status.value = 'FAILED';
  const message = error instanceof Error ? error.message : '系统请求失败，请稍后重试。';
  appendMessage('ASSISTANT', message);
  ElMessage.error(message);
}
</script>

<style scoped>
.voicecal-page {
  min-height: 100vh;
  padding: 24px clamp(16px, 3vw, 48px) 44px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 18px;
  max-width: 1880px;
  margin: 18px auto 0;
}

.main-column,
.side-column {
  display: grid;
  align-content: start;
  gap: 18px;
  min-width: 0;
}

@media (max-width: 1180px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }
}
</style>
