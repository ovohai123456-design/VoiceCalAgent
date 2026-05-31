<template>
  <section class="timeline-panel">
    <div class="panel-heading">
      <div>
        <h2>执行链路</h2>
        <span>{{ taskId ? `任务 ${taskId}` : '暂无任务' }}</span>
      </div>
      <el-button :icon="Refresh" circle plain size="small" title="刷新执行链路" :disabled="!taskId" @click="refresh" />
    </div>
    <el-empty v-if="!taskId && !steps.length" description="执行指令后显示步骤" :image-size="64" />
    <el-skeleton v-else :loading="loading" animated :rows="4">
      <div class="step-list">
        <div v-for="step in steps" :key="`${step.stepOrder}-${step.skillId}`" class="step-row">
          <component :is="resolveIcon(step.status)" :class="['step-icon', resolveClass(step.status)]" />
          <div>
            <strong>{{ step.skillId }}</strong>
            <span>Step {{ step.stepOrder }}<template v-if="step.latencyMs !== undefined"> · {{ step.latencyMs }}ms</template></span>
          </div>
          <el-tag :type="resolveType(step.status)" effect="plain" size="small">{{ resolveLabel(step.status) }}</el-tag>
        </div>
      </div>
    </el-skeleton>
  </section>
</template>

<script setup lang="ts">
import { CircleCheckFilled, CircleCloseFilled, Loading, Refresh, WarningFilled } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { ref, watch } from 'vue';
import { getWorkflowSteps } from '@/api/agentApi';
import type { WorkflowStep } from '@/types/agent';

const props = defineProps<{ taskId: string | null }>();
const steps = ref<WorkflowStep[]>([]);
const loading = ref(false);

watch(() => props.taskId, (taskId) => {
  if (taskId) void loadSteps(taskId);
  else steps.value = [];
}, { immediate: true });

async function loadSteps(taskId: string, retries = 2): Promise<void> {
  loading.value = true;
  try {
    steps.value = await getWorkflowSteps(taskId);
    if (retries > 0) window.setTimeout(() => { if (props.taskId === taskId) void loadSteps(taskId, retries - 1); }, 800);
  } catch (error) {
    console.error(error);
    ElMessage.error('执行链路加载失败');
  } finally {
    loading.value = false;
  }
}

async function refresh(): Promise<void> {
  if (props.taskId) await loadSteps(props.taskId);
}

function resolveIcon(status: string) {
  if (['FAILED', 'ERROR'].includes(status)) return CircleCloseFilled;
  if (['RUNNING', 'IN_PROGRESS', 'EXECUTING'].includes(status)) return Loading;
  if (['WAITING_CONFIRM', 'PENDING_CONFIRM'].includes(status)) return WarningFilled;
  return CircleCheckFilled;
}

function resolveClass(status: string): string {
  if (['FAILED', 'ERROR'].includes(status)) return 'failed';
  if (['RUNNING', 'IN_PROGRESS', 'EXECUTING'].includes(status)) return 'running';
  if (['WAITING_CONFIRM', 'PENDING_CONFIRM'].includes(status)) return 'waiting';
  return 'success';
}

function resolveLabel(status: string): string {
  return { SUCCESS: '成功', FAILED: '失败', ERROR: '失败', RUNNING: '执行中', IN_PROGRESS: '执行中', EXECUTING: '执行中', WAITING_CONFIRM: '待确认', PENDING_CONFIRM: '待确认' }[status] ?? status;
}

function resolveType(status: string): 'success' | 'danger' | 'warning' | 'primary' {
  if (['FAILED', 'ERROR'].includes(status)) return 'danger';
  if (['WAITING_CONFIRM', 'PENDING_CONFIRM'].includes(status)) return 'warning';
  if (['RUNNING', 'IN_PROGRESS', 'EXECUTING'].includes(status)) return 'primary';
  return 'success';
}

defineExpose({ refresh });
</script>

<style scoped>
.timeline-panel {
  padding: 14px;
  border: 1px solid rgb(160 207 255 / 18%);
  border-radius: 22px;
  background: linear-gradient(145deg, rgb(17 48 84 / 62%), rgb(8 25 49 / 52%));
  box-shadow: 0 18px 54px rgb(0 6 22 / 28%), inset 0 1px 0 rgb(255 255 255 / 8%);
  backdrop-filter: blur(26px);
}

.panel-heading,
.step-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.panel-heading {
  margin-bottom: 12px;
}

h2 {
  margin: 0 0 4px;
  color: #f0f8ff;
  font-size: 16px;
}

.panel-heading span,
.step-row span {
  color: rgb(205 227 250 / 66%);
  font-size: 12px;
}

.step-list {
  display: grid;
  gap: 8px;
}

.step-row {
  padding: 9px 0;
  border-bottom: 1px solid rgb(160 207 255 / 14%);
}

.step-row div {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 3px;
}

.step-row strong {
  overflow: hidden;
  color: #dff2ff;
  font-size: 13px;
  text-overflow: ellipsis;
}

.step-icon {
  width: 16px;
  height: 16px;
}

.success { color: #238b76; }
.waiting { color: #c48413; }
.running { color: #245b8a; }
.failed { color: #d14343; }
</style>
