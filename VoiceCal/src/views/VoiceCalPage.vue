<template>
  <div class="voicecal-page">
    <StatusHeader :current-time="currentTime" :status="status" :status-label="statusLabel" />

    <section class="content-grid">
      <VoiceCommandPanel
        v-model="commandText"
        :reply-text="replyText"
        :status="status"
        :last-input-type="lastInputType"
        :show-confirm-actions="showConfirmActions"
        @manual-input="handleManualInput"
        @start-voice="handleStartVoice"
        @stop-voice="handleStopVoice"
        @submit="handleSubmit"
        @confirm="handleConfirm"
        @cancel="handleCancel"
      />

      <CalendarView ref="calendarViewRef" />
    </section>

    <WorkflowTimeline ref="workflowTimelineRef" :task-id="taskId" />
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { cancelAgent, confirmAgent, executeAgent } from '@/api/agentApi';
import CalendarView from '@/components/CalendarView.vue';
import StatusHeader from '@/components/StatusHeader.vue';
import VoiceCommandPanel from '@/components/VoiceCommandPanel.vue';
import WorkflowTimeline from '@/components/WorkflowTimeline.vue';
import type { InputType, PageStatus } from '@/types/agent';
import { createSpeechRecognition, speak, startRecognition, stopRecognition } from '@/utils/speech';
import { DEFAULT_SESSION_ID, DEFAULT_USER_ID, getCurrentTimezone } from '@/utils/session';
import { formatCurrentTime } from '@/utils/time';

const commandText = ref('');
const replyText = ref('');
const status = ref<PageStatus>('IDLE');
const currentTime = ref(formatCurrentTime());
const confirmToken = ref('');
const taskId = ref<string | null>(null);
const lastInputType = ref<InputType>('TEXT');
const speechRecognition = ref<SpeechRecognition | null>(null);
const calendarViewRef = ref<InstanceType<typeof CalendarView> | null>(null);
const workflowTimelineRef = ref<InstanceType<typeof WorkflowTimeline> | null>(null);

let timerId: number | null = null;

const statusLabel = computed(() => {
  const labelMap: Record<PageStatus, string> = {
    IDLE: '空闲中',
    LISTENING: '正在识别',
    RECOGNIZED: '已识别',
    WAITING_RESPONSE: '等待响应',
    WAITING_CONFIRM: '等待确认',
    EXECUTING: '执行中',
    DONE: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
  };

  return labelMap[status.value];
});

const showConfirmActions = computed(() => status.value === 'WAITING_CONFIRM' && Boolean(confirmToken.value));

onMounted(() => {
  timerId = window.setInterval(() => {
    currentTime.value = formatCurrentTime();
  }, 1000);
});

onBeforeUnmount(() => {
  if (timerId !== null) {
    window.clearInterval(timerId);
  }
  stopRecognition(speechRecognition.value);
});

function handleManualInput(): void {
  lastInputType.value = 'TEXT';
}

function ensureRecognition(): SpeechRecognition | null {
  if (speechRecognition.value) {
    return speechRecognition.value;
  }

  const recognition = createSpeechRecognition({
    lang: 'zh-CN',
    continuous: false,
    interimResults: false,
  });

  speechRecognition.value = recognition;
  return recognition;
}

function handleStartVoice(): void {
  const recognition = ensureRecognition();
  if (!recognition) {
    ElMessage.warning('当前浏览器不支持语音识别，请使用 Chrome 或 Edge，或者手动输入。');
    return;
  }

  recognition.onresult = (event: SpeechRecognitionEvent) => {
    const transcript = Array.from({ length: event.results.length }, (_, index) => event.results[index][0].transcript)
      .join('')
      .trim();

    commandText.value = transcript;
    lastInputType.value = 'VOICE';
    status.value = 'RECOGNIZED';
  };

  recognition.onerror = () => {
    status.value = 'FAILED';
    ElMessage.error('语音识别失败，请重试或手动输入。');
  };

  recognition.onend = () => {
    if (status.value === 'LISTENING') {
      status.value = commandText.value.trim() ? 'RECOGNIZED' : 'IDLE';
    }
  };

  try {
    status.value = 'LISTENING';
    startRecognition(recognition);
  } catch (error) {
    console.error(error);
    status.value = 'FAILED';
    ElMessage.error('语音识别失败，请重试或手动输入。');
  }
}

function handleStopVoice(): void {
  stopRecognition(speechRecognition.value);
  if (status.value === 'LISTENING') {
    status.value = commandText.value.trim() ? 'RECOGNIZED' : 'IDLE';
  }
}

async function handleSubmit(): Promise<void> {
  const text = commandText.value.trim();
  if (!text) {
    ElMessage.warning('请输入或说出日程指令');
    return;
  }

  status.value = 'WAITING_RESPONSE';

  try {
    const response = await executeAgent({
      userId: DEFAULT_USER_ID,
      sessionId: DEFAULT_SESSION_ID,
      inputType: lastInputType.value,
      text,
      timezone: getCurrentTimezone(),
      currentTime: formatCurrentTime(),
    });

    taskId.value = response.taskId ?? taskId.value;
    confirmToken.value = response.confirmToken ?? '';
    replyText.value = response.replyText;

    await speak(response.speakText || response.replyText);

    if (response.needConfirm) {
      status.value = 'WAITING_CONFIRM';
    } else {
      status.value = 'DONE';
      await refreshCalendar();
    }

    await refreshTimeline();
  } catch (error) {
    console.error(error);
    status.value = 'FAILED';
    ElMessage.error('系统请求失败，请稍后重试。');
  }
}

async function handleConfirm(): Promise<void> {
  if (!confirmToken.value) {
    ElMessage.warning('当前没有待确认操作。');
    return;
  }

  status.value = 'EXECUTING';

  try {
    const response = await confirmAgent({
      userId: DEFAULT_USER_ID,
      sessionId: DEFAULT_SESSION_ID,
      confirmToken: confirmToken.value,
    });

    taskId.value = response.taskId ?? taskId.value;
    replyText.value = response.replyText;
    confirmToken.value = response.confirmToken ?? '';

    await speak(response.speakText || response.replyText);

    status.value = response.needConfirm ? 'WAITING_CONFIRM' : 'DONE';
    await refreshCalendar();
    await refreshTimeline();
  } catch (error) {
    console.error(error);
    status.value = 'FAILED';
    ElMessage.error('系统请求失败，请稍后重试。');
  }
}

async function handleCancel(): Promise<void> {
  if (!confirmToken.value) {
    ElMessage.warning('当前没有待确认操作。');
    return;
  }

  try {
    const response = await cancelAgent({
      userId: DEFAULT_USER_ID,
      sessionId: DEFAULT_SESSION_ID,
      confirmToken: confirmToken.value,
    });

    replyText.value = response.replyText || '已取消本次操作。';
    confirmToken.value = '';
    status.value = 'CANCELLED';

    await speak(response.speakText || replyText.value);
    await refreshTimeline();
  } catch (error) {
    console.error(error);
    status.value = 'FAILED';
    ElMessage.error('系统请求失败，请稍后重试。');
  }
}

async function refreshCalendar(): Promise<void> {
  await calendarViewRef.value?.refresh();
}

async function refreshTimeline(): Promise<void> {
  await workflowTimelineRef.value?.refresh();
}
</script>

<style scoped>
.voicecal-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 100vh;
  padding: 24px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(320px, 35fr) minmax(480px, 65fr);
  gap: 20px;
  align-items: stretch;
}

@media (max-width: 1100px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
