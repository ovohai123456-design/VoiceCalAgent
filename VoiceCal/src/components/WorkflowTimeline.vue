<template>
  <el-card class="timeline-panel" shadow="never">
    <template #header>
      <div class="panel-header">
        <div>
          <h2>Workflow 执行时间线</h2>
          <p>展示任务解析、冲突检查、执行确认等关键步骤</p>
        </div>
        <el-button plain :disabled="!taskId" @click="refresh">刷新时间线</el-button>
      </div>
    </template>

    <el-empty v-if="!taskId && !steps.length" description="执行后将在这里展示工作流步骤" />

    <el-skeleton v-else :loading="loading" animated :rows="4">
      <el-timeline>
        <el-timeline-item v-for="step in steps" :key="`${step.stepOrder}-${step.skillId}`" :timestamp="formatTimestamp(step)">
          <template #dot>
            <component :is="resolveIcon(step.status)" :class="['step-icon', resolveClass(step.status)]" />
          </template>
          <div class="step-card">
            <div class="step-row">
              <strong>Step {{ step.stepOrder }}</strong>
              <el-tag :type="resolveTagType(step.status)" effect="plain">{{ resolveLabel(step.status) }}</el-tag>
            </div>
            <div class="step-name">{{ step.stepName || step.skillId }}</div>
            <div class="step-meta">
              <span>Skill: {{ step.skillId }}</span>
              <span v-if="step.latencyMs !== undefined">耗时 {{ step.latencyMs }}ms</span>
            </div>
            <div v-if="step.errorMessage" class="step-error">{{ step.errorMessage }}</div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-skeleton>
  </el-card>
</template>

<script setup lang="ts">
import { CircleCheckFilled, CircleCloseFilled, Loading, WarningFilled } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { ref, watch } from 'vue';

import { getWorkflowSteps } from '@/api/agentApi';
import type { WorkflowStep } from '@/types/agent';

const props = defineProps<{
  taskId: string | null;
}>();

const loading = ref(false);
const steps = ref<WorkflowStep[]>([]);

watch(
  () => props.taskId,
  (value) => {
    if (value) {
      void loadSteps(value);
      return;
    }
    steps.value = [];
  },
  { immediate: true },
);

async function loadSteps(value: string): Promise<void> {
  loading.value = true;
  try {
    steps.value = await getWorkflowSteps(value);
  } catch (error) {
    console.error(error);
    ElMessage.error('系统请求失败，请稍后重试。');
  } finally {
    loading.value = false;
  }
}

async function refresh(): Promise<void> {
  if (!props.taskId) {
    return;
  }
  await loadSteps(props.taskId);
}

function resolveIcon(status: string) {
  if (['FAILED', 'ERROR'].includes(status)) {
    return CircleCloseFilled;
  }
  if (['RUNNING', 'IN_PROGRESS', 'EXECUTING'].includes(status)) {
    return Loading;
  }
  if (['WAITING_CONFIRM', 'PENDING_CONFIRM'].includes(status)) {
    return WarningFilled;
  }
  return CircleCheckFilled;
}

function resolveClass(status: string): string {
  if (['FAILED', 'ERROR'].includes(status)) {
    return 'is-failed';
  }
  if (['RUNNING', 'IN_PROGRESS', 'EXECUTING'].includes(status)) {
    return 'is-running';
  }
  if (['WAITING_CONFIRM', 'PENDING_CONFIRM'].includes(status)) {
    return 'is-waiting';
  }
  return 'is-success';
}

function resolveLabel(status: string): string {
  const labels: Record<string, string> = {
    SUCCESS: '成功',
    FAILED: '失败',
    ERROR: '失败',
    RUNNING: '执行中',
    IN_PROGRESS: '执行中',
    EXECUTING: '执行中',
    WAITING_CONFIRM: '等待确认',
    PENDING_CONFIRM: '等待确认',
  };

  return labels[status] ?? status;
}

function resolveTagType(status: string): 'success' | 'danger' | 'warning' | 'primary' | 'info' {
  if (['FAILED', 'ERROR'].includes(status)) {
    return 'danger';
  }
  if (['RUNNING', 'IN_PROGRESS', 'EXECUTING'].includes(status)) {
    return 'primary';
  }
  if (['WAITING_CONFIRM', 'PENDING_CONFIRM'].includes(status)) {
    return 'warning';
  }
  return 'success';
}

function formatTimestamp(step: WorkflowStep): string {
  return step.latencyMs !== undefined ? `耗时 ${step.latencyMs}ms` : '等待执行';
}

defineExpose({ refresh });
</script>

<style scoped>
.timeline-panel {
  border: 1px solid rgba(41, 89, 173, 0.12);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
}

:deep(.el-card__header) {
  padding: 20px 22px 12px;
  border-bottom: none;
}

:deep(.el-card__body) {
  padding: 8px 22px 22px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.panel-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
}

.panel-header p {
  margin: 0;
  color: #53627c;
}

.step-icon {
  width: 20px;
  height: 20px;
}

.is-success {
  color: #18a77b;
}

.is-failed {
  color: #d64545;
}

.is-running {
  color: #2d7dff;
}

.is-waiting {
  color: #d79510;
}

.step-card {
  padding: 14px 16px;
  border-radius: 16px;
  background: #f7faff;
  border: 1px solid rgba(103, 134, 201, 0.12);
}

.step-row,
.step-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.step-name {
  margin-top: 10px;
  font-size: 16px;
  font-weight: 600;
  color: #1e2f50;
}

.step-meta {
  margin-top: 10px;
  color: #61728d;
  font-size: 13px;
}

.step-error {
  margin-top: 10px;
  color: #d64545;
}
</style>
