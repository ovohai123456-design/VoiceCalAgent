<template>
  <section class="selection-panel">
    <div class="selection-heading">
      <div>
        <h2>选择目标日程</h2>
        <p>找到多个匹配项，请选择本次需要操作的日程。</p>
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
  border: 1px solid #f1c56b;
  border-radius: 8px;
  background: #fffaf0;
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
  color: #76623f;
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
  border: 1px solid #ead9b4;
  border-radius: 6px;
  background: #fff;
  color: #4d4330;
  text-align: left;
  cursor: pointer;
}

.candidate-row:hover {
  border-color: #d49b2c;
  background: #fffdf8;
}

.candidate-title {
  color: #1f2937;
  font-weight: 700;
}

@media (max-width: 720px) {
  .candidate-row {
    grid-template-columns: 1fr;
    gap: 5px;
  }
}
</style>
