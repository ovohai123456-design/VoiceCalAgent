<template>
  <section class="command-panel">
    <div class="panel-heading">
      <div>
        <h2>语音助手</h2>
        <span>{{ helperText }}</span>
      </div>
      <div class="heading-actions">
        <label class="continuous-voice-control">
          <span>连续语音</span>
          <el-switch
            :model-value="continuousVoiceEnabled"
            @update:model-value="emit('update:continuousVoiceEnabled', Boolean($event))"
          />
        </label>
        <el-tag :type="lastInputType === 'VOICE' ? 'success' : 'info'" effect="plain" size="small">
          {{ lastInputType === 'VOICE' ? '语音对话' : '文本对话' }}
        </el-tag>
      </div>
    </div>

    <div ref="messageListRef" class="message-list">
      <div
        v-for="message in messages"
        :key="message.id"
        class="message-row"
        :class="message.role === 'USER' ? 'message-row-user' : 'message-row-assistant'"
      >
        <div class="message-bubble">
          <span>{{ message.role === 'USER' ? '你' : 'VoiceCal' }}</span>
          <p>{{ message.text }}</p>
        </div>
      </div>
    </div>

    <div class="composer">
      <el-input
        :model-value="modelValue"
        type="textarea"
        :rows="2"
        resize="none"
        :placeholder="inputPlaceholder"
        :disabled="isBusy"
        @update:model-value="emit('update:modelValue', String($event ?? ''))"
        @input="emit('manual-input')"
        @keydown.enter.exact.prevent="emit('submit')"
      />

      <div class="action-row">
        <div class="voice-actions">
          <el-button :icon="Microphone" circle type="primary" title="开始语音识别" :disabled="isBusy || isListening" @click="emit('start-voice')" />
          <el-button :icon="VideoPause" circle plain title="停止语音识别" :disabled="!isListening" @click="emit('stop-voice')" />
          <span>{{ continuousVoiceEnabled ? '播报后自动继续聆听' : '语音识别完成后自动发送' }}</span>
        </div>
        <el-button type="primary" :icon="Promotion" :loading="isWaitingResponse" :disabled="isBusy || isListening || !modelValue.trim()" @click="emit('submit')">
          发送
        </el-button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { Microphone, Promotion, VideoPause } from '@element-plus/icons-vue';
import { computed, nextTick, ref, watch } from 'vue';
import type { ConversationMessage, InputType, PageStatus } from '@/types/agent';

const props = defineProps<{
  modelValue: string;
  messages: ConversationMessage[];
  status: PageStatus;
  lastInputType: InputType;
  continuousVoiceEnabled: boolean;
}>();
const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void;
  (event: 'update:continuousVoiceEnabled', value: boolean): void;
  (event: 'manual-input' | 'start-voice' | 'stop-voice' | 'submit'): void;
}>();
const messageListRef = ref<HTMLElement | null>(null);
const isListening = computed(() => props.status === 'LISTENING');
const isWaitingResponse = computed(() => props.status === 'WAITING_RESPONSE');
const isExecuting = computed(() => props.status === 'EXECUTING');
const isBusy = computed(() => isWaitingResponse.value || isExecuting.value);
const inputPlaceholder = computed(() => (
  props.status === 'WAITING_CONFIRM'
    ? '请回复“确认”或“取消”，也可以继续说新的指令'
    : '输入消息，按 Enter 发送；或点击麦克风直接说'
));
const helperText = computed(() => ({
  IDLE: '可以连续输入，也可以点击麦克风开始语音对话',
  LISTENING: '正在聆听，说完后会自动发送',
  RECOGNIZED: '等待你的下一句话',
  WAITING_RESPONSE: '正在理解你的需求',
  WAITING_CONFIRM: '请直接回复“确认”或“取消”',
  EXECUTING: '正在执行',
  DONE: '可以继续说下一条指令',
  FAILED: '请求失败，可以重新发送',
  CANCELLED: '操作已取消，可以继续对话',
}[props.status]));

watch(
  () => props.messages.length,
  async () => {
    await nextTick();
    const list = messageListRef.value;
    if (list) list.scrollTop = list.scrollHeight;
  },
  { immediate: true },
);
</script>

<style scoped>
.command-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 16px;
  min-height: 0;
  height: 100%;
  padding: 24px;
  border: 1px solid rgb(170 220 255 / 22%);
  border-radius: 30px;
  background:
    radial-gradient(circle at 18% 0%, rgb(74 144 226 / 28%), transparent 34%),
    linear-gradient(160deg, rgb(18 55 96 / 74%), rgb(7 22 46 / 62%));
  box-shadow: 0 30px 90px rgb(0 6 22 / 42%), inset 0 1px 0 rgb(255 255 255 / 12%);
  backdrop-filter: blur(30px);
}

.panel-heading,
.heading-actions,
.continuous-voice-control,
.action-row,
.voice-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.heading-actions,
.continuous-voice-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-heading {
  position: relative;
  padding-bottom: 4px;
}

.panel-heading::after {
  position: absolute;
  right: 0;
  bottom: -8px;
  left: 0;
  height: 1px;
  background: linear-gradient(90deg, rgb(160 220 255 / 18%), transparent);
  content: "";
}

h2 {
  margin: 0 0 6px;
  color: #f3f9ff;
  font-size: 22px;
  letter-spacing: 0.02em;
}

.panel-heading span,
.voice-actions span,
.message-bubble span {
  color: rgb(209 231 253 / 70%);
  font-size: 12px;
}

.message-list {
  display: grid;
  align-content: start;
  gap: 12px;
  min-height: 0;
  max-height: none;
  overflow-y: auto;
  padding: 20px;
  border: 1px solid rgb(160 207 255 / 14%);
  border-radius: 22px;
  background: rgb(3 15 32 / 38%);
  box-shadow: inset 0 1px 28px rgb(0 8 24 / 28%);
}

.message-list::-webkit-scrollbar {
  width: 6px;
}

.message-list::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgb(145 216 255 / 24%);
}

.message-row {
  display: flex;
}

.message-row-user {
  justify-content: flex-end;
}

.message-bubble {
  max-width: min(86%, 360px);
  padding: 13px 15px;
  border: 1px solid rgb(172 214 255 / 16%);
  border-radius: 18px 18px 18px 6px;
  background: linear-gradient(135deg, rgb(22 65 108 / 76%), rgb(15 39 77 / 66%));
  box-shadow: 0 10px 28px rgb(0 8 28 / 20%);
}

.message-row-user .message-bubble {
  border-color: rgb(140 207 255 / 30%);
  border-radius: 18px 18px 6px 18px;
  background: linear-gradient(135deg, rgb(29 116 187 / 88%), rgb(78 88 193 / 74%));
}

.message-bubble p {
  margin: 5px 0 0;
  color: #ecf8ff;
  line-height: 1.65;
  white-space: pre-wrap;
}

.composer {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgb(160 207 255 / 12%);
  border-radius: 22px;
  background: rgb(3 15 32 / 25%);
}

.composer :deep(.el-textarea__inner) {
  min-height: 74px !important;
  border-radius: 16px;
  background: rgb(3 16 35 / 50%);
}

.voice-actions :deep(.el-button) {
  box-shadow: 0 10px 28px rgb(0 6 22 / 22%);
}

.action-row :deep(.el-button--primary:not(.is-circle)) {
  min-width: 84px;
  border-radius: 14px;
  font-weight: 700;
}

@media (max-width: 720px) {
  .action-row {
    align-items: flex-end;
  }

  .voice-actions span {
    display: none;
  }
}
</style>
