<template>
  <el-card class="reminder-panel" shadow="never">
    <template #header>
      <div class="panel-header">
        <div>
          <h2>提醒任务</h2>
          <p>展示待执行、已完成和已取消的日程提醒。</p>
        </div>
        <div class="header-actions">
          <el-tag effect="plain">{{ reminders.length }} 条</el-tag>
          <el-button size="small" plain @click="$emit('request-notification')">
            {{ notificationButtonText }}
          </el-button>
        </div>
      </div>
    </template>

    <el-empty v-if="!reminders.length" description="暂无提醒任务" />
    <div v-else class="reminder-list">
      <div v-for="reminder in reminders" :key="reminder.id" class="reminder-row">
        <div>
          <strong>{{ resolveTitle(reminder) }}</strong>
          <span>{{ reminder.runAt }}</span>
        </div>
        <el-tag :type="resolveType(reminder.status)" effect="plain">
          {{ resolveStatus(reminder.status) }}
        </el-tag>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { ReminderTask } from '@/api/reminderApi';

defineProps<{
  reminders: ReminderTask[];
  notificationButtonText: string;
}>();

defineEmits<{
  (event: 'request-notification'): void;
}>();

function resolveTitle(reminder: ReminderTask): string {
  if (!reminder.jobPayloadJson) {
    return `日程 ${reminder.eventId}`;
  }
  try {
    const payload = JSON.parse(reminder.jobPayloadJson) as { title?: string };
    return payload.title || `日程 ${reminder.eventId}`;
  } catch {
    return `日程 ${reminder.eventId}`;
  }
}

function resolveStatus(status: string): string {
  return {
    PENDING: '待执行',
    EXECUTED: '已完成',
    CANCELED: '已取消',
    FAILED: '失败',
  }[status] ?? status;
}

function resolveType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'PENDING') return 'warning';
  if (status === 'EXECUTED') return 'success';
  if (status === 'FAILED') return 'danger';
  return 'info';
}
</script>

<style scoped>
.reminder-panel {
  border-radius: 8px;
}

.panel-header,
.reminder-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

h2 {
  margin: 0 0 6px;
  font-size: 18px;
}

p {
  margin: 0;
  color: #6b7280;
}

.reminder-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 340px;
  overflow-y: auto;
}

.reminder-row {
  padding: 12px;
  border: 1px solid rgba(103, 134, 201, 0.14);
  border-radius: 6px;
}

.reminder-row div {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.reminder-row span {
  color: #667085;
  font-size: 13px;
}
</style>
