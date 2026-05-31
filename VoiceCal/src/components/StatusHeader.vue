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
  max-width: 1880px;
  min-height: 70px;
  margin: 0 auto;
  padding: 12px 18px;
  border: 1px solid rgb(160 207 255 / 18%);
  border-radius: 20px;
  background: linear-gradient(145deg, rgb(18 51 90 / 60%), rgb(9 27 52 / 52%));
  box-shadow: 0 14px 48px rgb(0 6 22 / 28%), inset 0 1px 0 rgb(255 255 255 / 8%);
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
}

.brand span,
.clock {
  color: rgb(205 227 250 / 68%);
  font-size: 13px;
}

@media (max-width: 560px) {
  .clock {
    display: none;
  }
}
</style>
