<template>
  <section class="selection-panel">
    <div class="selection-heading">
      <div>
        <h2>选择目标日程</h2>
        <p>找到多个匹配项。你可以直接说“第一个”或点击目标日程。</p>
      </div>
      <el-button plain type="danger" :disabled="loadingIndex !== null" @click="$emit('cancel')">取消</el-button>
    </div>
    <div class="candidate-grid">
      <button
        v-for="(event, index) in candidates"
        :key="String(event.eventId ?? event.id ?? index)"
        class="candidate-row"
        type="button"
        :disabled="loadingIndex !== null"
        @click="$emit('select', index)"
      >
        <span class="candidate-title">{{ event.title }}</span>
        <span>{{ event.startTime }} - {{ event.endTime }}</span>
        <span>{{ event.location || '未设置地点' }}</span>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { CalendarEventItem } from '@/types/calendar';

defineProps<{
  candidates: CalendarEventItem[];
  loadingIndex: number | null;
}>();

defineEmits<{
  (event: 'select', index: number): void;
  (event: 'cancel'): void;
}>();
</script>

<style scoped>
.selection-panel {
  padding: 16px;
  border: 1px solid rgb(241 197 107 / 46%);
  border-radius: 18px;
  background: linear-gradient(145deg, rgb(92 65 34 / 52%), rgb(21 34 55 / 58%));
  box-shadow: 0 14px 42px rgb(0 6 22 / 24%);
  backdrop-filter: blur(22px);
}

.selection-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 12px;
}

h2,
p {
  margin: 0;
}

h2 {
  font-size: 17px;
}

p {
  margin-top: 4px;
  color: rgb(255 225 169 / 78%);
  font-size: 13px;
}

.candidate-grid {
  display: grid;
  gap: 8px;
}

.candidate-row {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) minmax(220px, 1.5fr) minmax(120px, 1fr);
  gap: 12px;
  width: 100%;
  padding: 12px;
  border: 1px solid rgb(237 205 141 / 26%);
  border-radius: 12px;
  background: rgb(9 27 51 / 48%);
  color: rgb(255 235 199 / 82%);
  text-align: left;
  cursor: pointer;
}

.candidate-row:hover {
  border-color: rgb(241 197 107 / 76%);
  background: rgb(33 56 78 / 62%);
}

.candidate-title {
  color: #f7fbff;
  font-weight: 700;
}

@media (max-width: 720px) {
  .candidate-row {
    grid-template-columns: 1fr;
    gap: 5px;
  }
}
</style>
