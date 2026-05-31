<template>
  <header class="status-header">
    <div class="brand">
      <Calendar class="brand-icon" />
      <div>
        <h1>VoiceCal Agent</h1>
        <span>智能日历工作台</span>
      </div>
    </div>
    <div class="header-meta">
      <span class="clock">{{ currentTime }}</span>
      <el-tag :type="tagType" effect="plain">{{ statusLabel }}</el-tag>
    </div>
  </header>
</template>

<script setup lang="ts">
import { Calendar } from '@element-plus/icons-vue';
import { computed } from 'vue';
import type { PageStatus } from '@/types/agent';

const props = defineProps<{ currentTime: string; status: PageStatus; statusLabel: string }>();
const tagType = computed(() => ({
  IDLE: 'info',
  LISTENING: 'primary',
  RECOGNIZED: 'primary',
  WAITING_RESPONSE: 'warning',
  WAITING_CONFIRM: 'warning',
  EXECUTING: 'primary',
  DONE: 'success',
  FAILED: 'danger',
  CANCELLED: 'info',
}[props.status] as 'info' | 'primary' | 'warning' | 'success' | 'danger'));
</script>

<style scoped>
.status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  width: 100%;
  min-height: 64px;
  margin: 0 auto;
  padding: 11px 18px;
  border: 1px solid rgb(170 220 255 / 20%);
  border-radius: 22px;
  background:
    radial-gradient(circle at 22% 0%, rgb(74 144 226 / 18%), transparent 32%),
    linear-gradient(145deg, rgb(18 51 90 / 62%), rgb(9 27 52 / 54%));
  box-shadow: 0 16px 52px rgb(0 6 22 / 30%), inset 0 1px 0 rgb(255 255 255 / 10%);
  backdrop-filter: blur(26px);
}

.brand,
.header-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-icon {
  width: 24px;
  height: 24px;
  color: #8ed8ff;
}

h1 {
  margin: 0;
  color: #f0f8ff;
  font-size: 19px;
  letter-spacing: 0.02em;
}

.brand span,
.clock {
  color: rgb(205 227 250 / 70%);
  font-size: 13px;
}

.header-meta :deep(.el-tag) {
  border-radius: 999px;
}

@media (max-width: 560px) {
  .clock {
    display: none;
  }
}
</style>
