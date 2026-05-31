<template>
  <section class="conflict-panel">
    <div class="heading">
      <div>
        <h2>时间冲突</h2>
        <p>原定时间不可用。你可以直接说“第一个”或点击候选时间。</p>
      </div>
      <el-button plain type="danger" :disabled="loadingIndex !== null" @click="$emit('cancel')">取消</el-button>
    </div>
    <div class="slot-list">
      <el-button
        v-for="(slot, index) in slots"
        :key="`${slot.startTime}-${slot.endTime}`"
        class="slot-button"
        :loading="loadingIndex === index"
        :disabled="loadingIndex !== null"
        @click="$emit('select', index)"
      >
        {{ slot.startTime }} - {{ slot.endTime }}
      </el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { SuggestedSlot } from '@/types/agent';
defineProps<{ slots: SuggestedSlot[]; loadingIndex: number | null }>();
defineEmits<{ (event: 'select', index: number): void; (event: 'cancel'): void }>();
</script>

<style scoped>
.conflict-panel {
  padding: 16px;
  border: 1px solid rgb(241 197 107 / 46%);
  border-radius: 18px;
  background: linear-gradient(145deg, rgb(92 65 34 / 52%), rgb(21 34 55 / 58%));
  box-shadow: 0 14px 42px rgb(0 6 22 / 24%);
  backdrop-filter: blur(22px);
}

.heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
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

.slot-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 8px;
}

.slot-button {
  width: 100%;
  margin: 0;
}
</style>
