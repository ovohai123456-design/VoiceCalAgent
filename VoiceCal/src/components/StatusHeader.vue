<template>
  <el-card class="status-header" shadow="never">
    <div class="title-block">
      <p class="eyebrow">Voice Workflow Console</p>
      <h1>VoiceCal Agent 语音日历助手</h1>
    </div>

    <div class="meta-block">
      <div class="meta-item">
        <span class="label">当前时间</span>
        <strong>{{ currentTime }}</strong>
      </div>
      <div class="meta-item">
        <span class="label">运行状态</span>
        <el-tag :type="tagType" effect="dark" round size="large">{{ statusLabel }}</el-tag>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import type { PageStatus } from '@/types/agent';

const props = defineProps<{
  currentTime: string;
  status: PageStatus;
  statusLabel: string;
}>();

const tagType = computed(() => {
  const map: Record<PageStatus, 'info' | 'primary' | 'warning' | 'success' | 'danger'> = {
    IDLE: 'info',
    LISTENING: 'primary',
    RECOGNIZED: 'primary',
    WAITING_RESPONSE: 'warning',
    WAITING_CONFIRM: 'warning',
    EXECUTING: 'primary',
    DONE: 'success',
    FAILED: 'danger',
    CANCELLED: 'info',
  };

  return map[props.status];
});
</script>

<style scoped>
.status-header {
  border: 1px solid rgba(60, 109, 240, 0.14);
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(12, 25, 62, 0.96), rgba(18, 60, 120, 0.92));
  color: #f5f9ff;
}

:deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 28px;
}

.eyebrow {
  margin: 0 0 8px;
  color: rgba(196, 219, 255, 0.8);
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
}

.meta-block {
  display: flex;
  gap: 24px;
  align-items: center;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 160px;
}

.label {
  color: rgba(196, 219, 255, 0.72);
  font-size: 13px;
}

strong {
  font-size: 18px;
  font-weight: 600;
}
</style>
