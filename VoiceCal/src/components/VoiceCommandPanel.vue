<template>
  <section class="command-panel">
    <div class="panel-heading">
      <div>
        <h2>语音助手</h2>
        <span>{{ helperText }}</span>
      </div>
      <el-tag :type="lastInputType === 'VOICE' ? 'success' : 'info'" effect="plain" size="small">
        {{ lastInputType === 'VOICE' ? '语音对话' : '文本对话' }}
      </el-tag>
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
          <span>语音识别完成后自动发送</span>
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
}>();
const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void;
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
  gap: 12px;
  padding: 16px;
  border: 1px solid #d9e0e8;
  border-radius: 8px;
  background: #fff;
}

.panel-heading,
.action-row,
.voice-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

h2 {
  margin: 0 0 4px;
  font-size: 17px;
}

.panel-heading span,
.voice-actions span,
.message-bubble span {
  color: #667085;
  font-size: 12px;
}

.message-list {
  display: grid;
  gap: 10px;
  min-height: 240px;
  max-height: 420px;
  overflow-y: auto;
  padding: 14px;
  border-radius: 8px;
  background: #f7f9fb;
}

.message-row {
  display: flex;
}

.message-row-user {
  justify-content: flex-end;
}

.message-bubble {
  max-width: min(78%, 720px);
  padding: 10px 12px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 3px rgb(16 24 40 / 8%);
}

.message-row-user .message-bubble {
  background: #dff5ef;
}

.message-bubble p {
  margin: 4px 0 0;
  color: #344054;
  line-height: 1.6;
  white-space: pre-wrap;
}

.composer {
  display: grid;
  gap: 8px;
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
