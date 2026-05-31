<template>
  <section class="confirm-panel">
    <div>
      <h2>待确认操作</h2>
      <p>{{ text }}</p>
    </div>
    <div class="confirm-actions">
      <el-button plain :disabled="loading" @click="$emit('cancel')">取消</el-button>
      <el-button type="primary" :loading="loading" @click="$emit('confirm')">确认执行</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{ text: string; loading: boolean }>();
defineEmits<{ (event: 'confirm'): void; (event: 'cancel'): void }>();
</script>

<style scoped>
.confirm-panel {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
  padding: 16px;
  border: 1px solid rgb(241 197 107 / 46%);
  border-radius: 8px;
  background: rgb(22 49 78 / 86%);
  box-shadow: 0 14px 42px rgb(0 6 22 / 24%);
}

h2,
p {
  margin: 0;
}

h2 {
  font-size: 17px;
}

p {
  margin-top: 5px;
  color: rgb(223 237 255 / 82%);
  font-size: 14px;
  line-height: 1.6;
}

.confirm-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

@media (max-width: 720px) {
  .confirm-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .confirm-actions {
    justify-content: flex-end;
  }
}
</style>
