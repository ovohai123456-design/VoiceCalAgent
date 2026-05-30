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
  max-width: 1720px;
  min-height: 64px;
  margin: 0 auto;
  padding: 10px 16px;
  border: 1px solid #d9e0e8;
  border-radius: 8px;
  background: #fff;
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
  color: #176b5b;
}

h1 {
  margin: 0;
  color: #182230;
  font-size: 19px;
}

.brand span,
.clock {
  color: #667085;
  font-size: 13px;
}

@media (max-width: 560px) {
  .clock {
    display: none;
  }
}
</style>
