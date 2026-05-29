<template>
  <el-card class="voice-panel" shadow="never">
    <template #header>
      <div class="panel-header">
        <div>
          <h2>语音助手面板</h2>
          <p>支持语音识别、文本修订、确认执行与结果播报</p>
        </div>
        <el-tag :type="inputTagType" effect="plain">{{ inputTagLabel }}</el-tag>
      </div>
    </template>

    <div class="voice-actions">
      <el-button type="primary" size="large" :disabled="isBusy || isListening" @click="$emit('start-voice')">
        开始语音
      </el-button>
      <el-button size="large" :disabled="!isListening" @click="$emit('stop-voice')">
        停止语音
      </el-button>
    </div>

    <el-input
      :model-value="modelValue"
      type="textarea"
      :rows="6"
      resize="none"
      placeholder="你可以说：明天下午三点提醒我开组会"
      @update:model-value="handleModelUpdate"
      @input="$emit('manual-input')"
    />

    <div class="submit-row">
      <el-button
        type="success"
        size="large"
        :loading="isWaitingResponse"
        :disabled="isBusy || isListening"
        @click="$emit('submit')"
      >
        发送指令
      </el-button>
      <span class="status-tip">{{ helperText }}</span>
    </div>

    <el-card class="reply-card" shadow="never">
      <template #header>
        <span>系统回复</span>
      </template>
      <div class="reply-content">
        {{ replyText || '系统处理结果会显示在这里，并同步进行语音播报。' }}
      </div>
    </el-card>

    <div v-if="showConfirmActions" class="confirm-actions">
      <el-button type="primary" size="large" :loading="isExecuting" @click="$emit('confirm')">
        确认
      </el-button>
      <el-button type="danger" plain size="large" :disabled="isExecuting" @click="$emit('cancel')">
        取消
      </el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import type { InputType, PageStatus } from '@/types/agent';

const props = defineProps<{
  modelValue: string;
  replyText: string;
  status: PageStatus;
  lastInputType: InputType;
  showConfirmActions: boolean;
}>();

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void;
  (event: 'manual-input'): void;
  (event: 'start-voice'): void;
  (event: 'stop-voice'): void;
  (event: 'submit'): void;
  (event: 'confirm'): void;
  (event: 'cancel'): void;
}>();

const isListening = computed(() => props.status === 'LISTENING');
const isWaitingResponse = computed(() => props.status === 'WAITING_RESPONSE');
const isExecuting = computed(() => props.status === 'EXECUTING');
const isBusy = computed(() => isWaitingResponse.value || isExecuting.value);

const helperText = computed(() => {
  const map: Record<PageStatus, string> = {
    IDLE: '准备就绪，可直接输入或开始语音。',
    LISTENING: '正在识别中文语音，请清晰表达。',
    RECOGNIZED: '识别完成，可继续修改后发送。',
    WAITING_RESPONSE: '后端处理中，请稍候。',
    WAITING_CONFIRM: '识别完成，等待你确认执行。',
    EXECUTING: '正在执行确认后的操作。',
    DONE: '本轮任务已完成。',
    FAILED: '发生异常，可重试或手动输入。',
    CANCELLED: '本轮操作已取消。',
  };

  return map[props.status];
});

const inputTagLabel = computed(() => (props.lastInputType === 'VOICE' ? '语音输入' : '文本输入'));
const inputTagType = computed(() => (props.lastInputType === 'VOICE' ? 'success' : 'info'));

function handleModelUpdate(value: string | number): void {
  emit('update:modelValue', String(value ?? ''));
}
</script>

<style scoped>
.voice-panel {
  height: 100%;
  border: 1px solid rgba(41, 89, 173, 0.12);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.84);
  backdrop-filter: blur(10px);
}

:deep(.el-card__header) {
  padding: 20px 22px 12px;
  border-bottom: none;
}

:deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 8px 22px 22px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.panel-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
}

.panel-header p {
  margin: 0;
  color: #53627c;
  line-height: 1.6;
}

.voice-actions,
.submit-row,
.confirm-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.status-tip {
  color: #5d6b84;
  font-size: 14px;
}

:deep(.el-textarea__inner) {
  border-radius: 18px;
  padding: 18px;
  font-size: 18px;
  line-height: 1.7;
  background: #f8fbff;
}

.reply-card {
  border-radius: 18px;
  border: 1px solid rgba(95, 131, 209, 0.14);
  background: linear-gradient(180deg, rgba(244, 248, 255, 0.96), rgba(255, 255, 255, 0.98));
}

:deep(.reply-card .el-card__header) {
  padding: 16px 18px 10px;
}

:deep(.reply-card .el-card__body) {
  padding: 0 18px 18px;
}

.reply-content {
  min-height: 120px;
  color: #1f2d3d;
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>
