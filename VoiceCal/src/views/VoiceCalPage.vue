<template>
  <div class="voicecal-page">
    <StatusHeader :current-time="currentTime" :status="status" :status-label="statusLabel" :user="user" @logout="$emit('logout')" />

    <main class="workspace-grid">
      <section class="voice-zone">
        <VoiceCommandPanel
          v-model="commandText"
          :messages="messages"
          :status="status"
          :last-input-type="lastInputType"
          :continuous-voice-enabled="continuousVoiceEnabled"
          @update:continuous-voice-enabled="handleContinuousVoiceChange"
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

        <ConfirmActionPanel
          v-if="pendingConfirmText"
          :text="pendingConfirmText"
          :loading="confirmingAction"
          @confirm="handleConfirm"
          @cancel="handleCancel"
        />
      </section>

      <section class="calendar-zone">
        <CalendarView ref="calendarViewRef" @calendar-change="refreshReminders" />
      </section>

      <aside class="side-zone">
        <WorkspaceSidebar
          :reminders="reminders"
          :notification-button-text="notificationButtonText"
          @request-notification="enableBrowserNotifications"
          @delete-reminder="handleDeleteReminder"
          @clear-reminders="handleClearReminders"
        />
      </aside>

      <section class="workflow-zone">
        <WorkflowTimeline :task-id="taskId" :live-steps="workflowSteps" />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { cancelAgent, confirmAgent, executeAgent, selectAgentEvent, selectAgentSlot } from '@/api/agentApi';
import { subscribeAgentEvents, type TaskStatusEvent } from '@/api/eventStream';
import { clearReminderTasks, deleteReminderTask, listReminderTasks, type ReminderTask } from '@/api/reminderApi';
import CalendarView from '@/components/CalendarView.vue';
import ConfirmActionPanel from '@/components/ConfirmActionPanel.vue';
import ConflictSuggestionPanel from '@/components/ConflictSuggestionPanel.vue';
import EventCandidatePanel from '@/components/EventCandidatePanel.vue';
import StatusHeader from '@/components/StatusHeader.vue';
import VoiceCommandPanel from '@/components/VoiceCommandPanel.vue';
import WorkflowTimeline from '@/components/WorkflowTimeline.vue';
import WorkspaceSidebar from '@/components/WorkspaceSidebar.vue';
import type { AgentResponse, ConversationMessage, InputType, PageStatus, SuggestedSlot, WorkflowStep } from '@/types/agent';
import type { CalendarEventItem } from '@/types/calendar';
import type { AuthUser } from '@/utils/auth';
import {
  getBrowserNotificationPermission,
  requestBrowserNotificationPermission,
  showBrowserNotification,
  type BrowserNotificationPermission,
} from '@/utils/browserNotification';
import { getCurrentTimezone, getSessionId } from '@/utils/session';
import { createSpeechRecognition, speak, startRecognition, stopRecognition } from '@/utils/speech';
import { formatCurrentTime } from '@/utils/time';

defineProps<{ user: AuthUser }>();
defineEmits<{ (event: 'logout'): void }>();

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
const continuousVoiceEnabled = ref(true);
const calendarViewRef = ref<InstanceType<typeof CalendarView> | null>(null);
const workflowSteps = ref<WorkflowStep[]>([]);
const conflictSlots = ref<SuggestedSlot[]>([]);
const selectingSlotIndex = ref<number | null>(null);
const eventCandidates = ref<CalendarEventItem[]>([]);
const selectingEventIndex = ref<number | null>(null);
const pendingConfirmText = ref('');
const confirmingAction = ref(false);
const reminders = ref<ReminderTask[]>([]);
const browserNotificationPermission = ref<BrowserNotificationPermission>(getBrowserNotificationPermission());
const notifiedReminderIds = new Set<number>();

let timerId: number | null = null;
let reminderPollingInitialized = false;
let messageSequence = 1;
let closeEventStream: (() => void) | null = null;
let voiceRestartTimerId: number | null = null;
let recognitionActive = false;
let explicitVoiceStop = false;
let speechPlaybackActive = false;

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
  closeEventStream = subscribeAgentEvents(sessionId, {
    onTaskStatus: handleTaskStatus,
    onWorkflowStep: handleWorkflowStep,
    onReminderChanged: handleReminderChanged,
    onRemindersRefresh: () => { void refreshReminders(); },
    onError: () => console.warn('SSE connection interrupted; browser will reconnect automatically'),
  });
  void refreshReminders();
});

onBeforeUnmount(() => {
  if (timerId !== null) window.clearInterval(timerId);
  closeEventStream?.();
  clearVoiceRestartTimer();
  stopRecognition(speechRecognition.value);
  window.speechSynthesis?.cancel();
});

function handleManualInput(): void {
  lastInputType.value = 'TEXT';
}

function handleStartVoice(): void {
  clearVoiceRestartTimer();
  if (recognitionActive || speechPlaybackActive) return;
  const recognition = speechRecognition.value ?? createSpeechRecognition({ lang: 'zh-CN', continuous: false, interimResults: false });
  if (!recognition) {
    ElMessage.warning('当前浏览器不支持语音识别，请使用 Chrome、Edge 或文本输入。');
    return;
  }
  speechRecognition.value = recognition;
  explicitVoiceStop = false;
  recognition.onresult = (event: SpeechRecognitionEvent) => {
    const transcript = Array.from({ length: event.results.length }, (_, index) => event.results[index][0].transcript).join('').trim();
    if (!transcript) return;
    stopRecognition(recognition);
    void sendConversationMessage(transcript, 'VOICE');
  };
  recognition.onerror = (event: SpeechRecognitionErrorEvent) => {
    recognitionActive = false;
    if (event.error === 'aborted' || event.error === 'no-speech') return;
    if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
      continuousVoiceEnabled.value = false;
      explicitVoiceStop = true;
    }
    status.value = 'FAILED';
    ElMessage.error('语音识别失败，请重试或改用文本输入。');
  };
  recognition.onend = () => {
    recognitionActive = false;
    if (status.value === 'LISTENING') status.value = commandText.value.trim() ? 'RECOGNIZED' : 'IDLE';
    if (continuousVoiceEnabled.value && !explicitVoiceStop) scheduleVoiceRecognition();
  };
  status.value = 'LISTENING';
  try {
    recognitionActive = true;
    startRecognition(recognition);
  } catch (error) {
    recognitionActive = false;
    console.warn('Speech recognition start skipped', error);
  }
}

function handleStopVoice(): void {
  continuousVoiceEnabled.value = false;
  explicitVoiceStop = true;
  clearVoiceRestartTimer();
  stopRecognition(speechRecognition.value);
  if (status.value === 'LISTENING') status.value = commandText.value.trim() ? 'RECOGNIZED' : 'IDLE';
}

function handleContinuousVoiceChange(enabled: boolean): void {
  continuousVoiceEnabled.value = enabled;
  explicitVoiceStop = !enabled;
  if (enabled) {
    handleStartVoice();
    return;
  }
  clearVoiceRestartTimer();
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
  appendMessage('USER', '取消', 'TEXT');
  status.value = 'EXECUTING';
  try {
    await applyAgentResponse(await cancelAgent({
      sessionId,
      confirmToken: confirmToken.value,
    }));
  } catch (error) {
    handleRequestError(error);
  }
}

async function handleConfirm(): Promise<void> {
  if (!confirmToken.value) return;
  confirmingAction.value = true;
  appendMessage('USER', '确认', 'TEXT');
  status.value = 'EXECUTING';
  try {
    await applyAgentResponse(await confirmAgent({
      sessionId,
      confirmToken: confirmToken.value,
    }));
  } catch (error) {
    handleRequestError(error);
  } finally {
    confirmingAction.value = false;
  }
}

async function handleSelectSlot(slotIndex: number): Promise<void> {
  if (!confirmToken.value) return;
  selectingSlotIndex.value = slotIndex;
  appendMessage('USER', `第 ${slotIndex + 1} 个`, 'TEXT');
  status.value = 'EXECUTING';
  try {
    await applyAgentResponse(await selectAgentSlot({
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
  pendingConfirmText.value = response.needConfirm && !response.needEventSelection && !conflictSlots.value.length
    ? response.replyText
    : '';
  appendMessage('ASSISTANT', response.replyText);
  if (response.needConfirm || response.needEventSelection) status.value = 'WAITING_CONFIRM';
  else if (response.needClarify) status.value = 'RECOGNIZED';
  else status.value = 'DONE';
  await speakAndResume(response.speakText || response.replyText);
  if (status.value === 'DONE') {
    clearPendingSelection();
    await refreshCalendar();
    await refreshReminders();
  }
  if (shouldContinueVoiceConversation(response)) {
    handleStartVoice();
  }
}

function appendMessage(role: ConversationMessage['role'], text: string, inputType?: InputType): void {
  if (!text.trim()) return;
  messages.value.push({ id: ++messageSequence, role, text, inputType });
}

function shouldContinueVoiceConversation(response: AgentResponse): boolean {
  return !explicitVoiceStop && (
    continuousVoiceEnabled.value
    || lastInputType.value === 'VOICE' && Boolean(response.needConfirm || response.needClarify || response.needEventSelection)
  );
}

function scheduleVoiceRecognition(delay = 350): void {
  clearVoiceRestartTimer();
  if (!continuousVoiceEnabled.value || explicitVoiceStop || speechPlaybackActive || recognitionActive) return;
  if (status.value === 'WAITING_RESPONSE' || status.value === 'EXECUTING') return;
  voiceRestartTimerId = window.setTimeout(() => {
    voiceRestartTimerId = null;
    handleStartVoice();
  }, delay);
}

function clearVoiceRestartTimer(): void {
  if (voiceRestartTimerId === null) return;
  window.clearTimeout(voiceRestartTimerId);
  voiceRestartTimerId = null;
}

async function speakAndResume(text: string): Promise<void> {
  speechPlaybackActive = true;
  if (recognitionActive) stopRecognition(speechRecognition.value);
  await speak(text);
  speechPlaybackActive = false;
  if (continuousVoiceEnabled.value && !explicitVoiceStop) scheduleVoiceRecognition();
}

function clearPendingSelection(): void {
  confirmToken.value = '';
  conflictSlots.value = [];
  eventCandidates.value = [];
  pendingConfirmText.value = '';
}

function extractSuggestedSlots(response: AgentResponse): SuggestedSlot[] {
  const data = response.data as { conflictResult?: { suggestedSlots?: SuggestedSlot[] } } | undefined;
  return data?.conflictResult?.suggestedSlots ?? [];
}

function extractEventCandidates(response: AgentResponse): CalendarEventItem[] {
  const data = response.data as { candidates?: CalendarEventItem[] } | undefined;
  return response.needEventSelection ? data?.candidates ?? [] : [];
}

function handleTaskStatus(event: TaskStatusEvent): void {
  if (!event.taskId || event.taskId === taskId.value) return;
  taskId.value = event.taskId;
  workflowSteps.value = [];
}

function handleWorkflowStep(step: WorkflowStep): void {
  if (!step.taskId) return;
  if (step.taskId !== taskId.value) {
    taskId.value = step.taskId;
    workflowSteps.value = [];
  }
  const key = `${step.stepOrder}-${step.skillId}`;
  const index = workflowSteps.value.findIndex((item) => `${item.stepOrder}-${item.skillId}` === key);
  if (index === -1) workflowSteps.value.push(step);
  else workflowSteps.value.splice(index, 1, step);
  workflowSteps.value.sort((left, right) => left.stepOrder - right.stepOrder);
}

function handleReminderChanged(): void {
  void refreshReminders();
}

async function refreshCalendar(): Promise<void> {
  await calendarViewRef.value?.refresh();
}

async function refreshReminders(): Promise<void> {
  try {
    const tasks = await listReminderTasks();
    reminders.value = tasks;
    const executed = tasks.filter((reminder) => reminder.jobType === 'IN_APP' && reminder.status === 'EXECUTED');
    if (!reminderPollingInitialized) {
      executed.forEach((reminder) => notifiedReminderIds.add(reminder.id));
      reminderPollingInitialized = true;
      return;
    }
    for (const reminder of executed) {
      if (notifiedReminderIds.has(reminder.id)) continue;
      notifiedReminderIds.add(reminder.id);
      const message = resolveReminderMessage(reminder);
      ElNotification({ title: '日程提醒', message, type: 'warning', duration: 0 });
      showBrowserNotification('日程提醒', message);
      await speakAndResume(`日程提醒：${message}`);
    }
  } catch (error) {
    console.error('Reminder refresh failed', error);
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
    const reminderIds = reminder.relatedReminderIds?.length ? reminder.relatedReminderIds : [reminder.id];
    await Promise.all(reminderIds.map((reminderId) => deleteReminderTask(reminderId)));
    await refreshReminders();
    ElMessage.success('提醒任务已删除');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('提醒任务删除失败');
  }
}

async function handleClearReminders(): Promise<void> {
  try {
    await ElMessageBox.confirm('确认清空全部提醒任务吗？该操作不可恢复。', '清空提醒任务', { type: 'warning' });
    await clearReminderTasks();
    notifiedReminderIds.clear();
    await refreshReminders();
    ElMessage.success('提醒任务已清空');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('提醒任务清空失败');
  }
}

function resolveReminderMessage(reminder: ReminderTask): string {
  if (!reminder.jobPayloadJson) return `日程 ${reminder.eventId} 即将开始`;
  try {
    const payload = JSON.parse(reminder.jobPayloadJson) as { title?: string; description?: string };
    if (!payload.title) return `日程 ${reminder.eventId} 即将开始`;
    return payload.description ? `${payload.title}：${payload.description}` : `${payload.title} 即将开始`;
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
  padding: 14px clamp(12px, 1.5vw, 28px) 16px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(430px, 0.95fr) minmax(720px, 1.55fr) minmax(330px, 0.72fr);
  grid-template-rows: minmax(580px, calc(100vh - 302px)) 168px;
  grid-template-areas:
    "voice calendar side"
    "workflow workflow workflow";
  gap: 18px;
  width: 100%;
  margin: 16px auto 0;
  align-items: stretch;
}

.voice-zone {
  grid-area: voice;
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  gap: 14px;
}

.voice-zone > .command-panel {
  flex: 1 1 auto;
  min-height: 0;
}

.calendar-zone {
  grid-area: calendar;
  min-width: 0;
  min-height: 0;
}

.side-zone {
  grid-area: side;
  min-width: 0;
  min-height: 0;
}

.workflow-zone {
  grid-area: workflow;
  min-width: 0;
  min-height: 0;
}

.calendar-zone > :first-child,
.side-zone > :first-child,
.workflow-zone > :first-child {
  height: 100%;
}

@media (max-width: 1560px) {
  .workspace-grid {
    grid-template-columns: minmax(390px, 0.9fr) minmax(620px, 1.4fr);
    grid-template-rows: minmax(560px, auto) minmax(280px, auto) 168px;
    grid-template-areas:
      "voice calendar"
      "voice side"
      "workflow workflow";
  }
}

@media (max-width: 980px) {
  .voicecal-page {
    padding-inline: 14px;
  }

  .workspace-grid {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
    grid-template-areas:
      "voice"
      "calendar"
      "side"
      "workflow";
  }
}
</style>
