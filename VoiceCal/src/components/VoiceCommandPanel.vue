<template>
  <section class="command-panel">
    <div class="panel-heading">
      <div>
        <h2>日程指令</h2>
        <span>{{ helperText }}</span>
      </div>
      <el-tag :type="lastInputType === 'VOICE' ? 'success' : 'info'" effect="plain" size="small">
        {{ lastInputType === 'VOICE' ? '语音' : '文本' }}
      </el-tag>
    </div>

    <el-input
      :model-value="modelValue"
      type="textarea"
      :rows="3"
      resize="none"
      placeholder="例如：明天下午三点安排项目会，短信提醒张三"
      @update:model-value="emit('update:modelValue', String($event ?? ''))"
      @input="emit('manual-input')"
    />

    <div class="action-row">
      <div class="voice-actions">
        <el-button :icon="Microphone" circle type="primary" title="开始语音识别" :disabled="isBusy || isListening" @click="emit('start-voice')" />
        <el-button :icon="VideoPause" circle plain title="停止语音识别" :disabled="!isListening" @click="emit('stop-voice')" />
      </div>
      <el-button type="primary" :icon="Promotion" :loading="isWaitingResponse" :disabled="isBusy || isListening" @click="emit('submit')">
        执行指令
      </el-button>
    </div>

    <div class="reply-box">
      <span>Agent 回复</span>
      <p>{{ replyText }}</p>
    </div>

    <div v-if="showConfirmActions" class="confirm-row">
      <el-button type="primary" :loading="isExecuting" @click="emit('confirm')">确认执行</el-button>
      <el-button plain type="danger" :disabled="isExecuting" @click="emit('cancel')">取消</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { Microphone, Promotion, VideoPause } from '@element-plus/icons-vue';
import { computed } from 'vue';
import type { InputType, PageStatus } from '@/types/agent';

const props = defineProps<{ modelValue: string; replyText: string; status: PageStatus; lastInputType: InputType; showConfirmActions: boolean }>();
const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void;
  (event: 'manual-input' | 'start-voice' | 'stop-voice' | 'submit' | 'confirm' | 'cancel'): void;
}>();
const isListening = computed(() => props.status === 'LISTENING');
const isWaitingResponse = computed(() => props.status === 'WAITING_RESPONSE');
const isExecuting = computed(() => props.status === 'EXECUTING');
const isBusy = computed(() => isWaitingResponse.value || isExecuting.value);
const helperText = computed(() => ({
  IDLE: '可输入文字或使用语音',
  LISTENING: '正在识别语音',
  RECOGNIZED: '识别完成，可修改后执行',
  WAITING_RESPONSE: '正在解析并生成执行计划',
  WAITING_CONFIRM: '等待确认',
  EXECUTING: '正在执行',
  DONE: '任务已完成',
  FAILED: '请求失败，可修改后重试',
  CANCELLED: '操作已取消',
}[props.status]));
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
.voice-actions,
.confirm-row {
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
.reply-box span {
  color: #667085;
  font-size: 12px;
}

.reply-box {
  padding: 10px 12px;
  border-left: 3px solid #289a83;
  background: #f7faf9;
}

.reply-box p {
  margin: 5px 0 0;
  color: #344054;
  line-height: 1.6;
}
</style>
