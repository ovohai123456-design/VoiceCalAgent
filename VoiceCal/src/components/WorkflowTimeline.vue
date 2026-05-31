<template>
  <section class="timeline-panel">
    <div class="panel-heading">
      <div class="heading-title">
        <h2>Agent 执行链路 Dock</h2>
        <span>{{ taskId ? `任务 ${shortTaskId}` : '执行指令后展示路由、确认、执行、回写全过程' }}</span>
      </div>
      <el-button :icon="Refresh" circle plain size="small" title="刷新执行链路" :disabled="!taskId" @click="refresh" />
    </div>

    <el-empty v-if="!taskId && !steps.length" description="执行指令后显示步骤" :image-size="54" />

    <el-skeleton v-else :loading="loading" animated :rows="2">
      <div class="workflow-dock">
        <div class="runner-track" :style="{ '--runner-left': runnerLeft }">
          <div v-if="steps.length" class="runner">
            <div class="carry">{{ carryIcon }}</div>
            <div class="cow">🐂</div>
          </div>

          <div
            v-for="(step, index) in steps"
            :key="`${step.stepOrder}-${step.skillId}`"
            class="track-node"
            :class="[resolveClass(step.status), { active: index === activeIndex, passed: index < activeIndex }]"
          >
            <div class="dot">{{ index + 1 }}</div>
            <strong>{{ resolveNodeLabel(step.skillId) }}</strong>
            <span>{{ resolveLabel(step.status) }}<template v-if="step.latencyMs !== undefined"> · {{ step.latencyMs }}ms</template></span>
          </div>
        </div>

      </div>
    </el-skeleton>
  </section>
</template>

<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, ref, watch } from 'vue';
import { getWorkflowSteps } from '@/api/agentApi';
import type { WorkflowStep } from '@/types/agent';

const props = defineProps<{ taskId: string | null; liveSteps?: WorkflowStep[] }>();
const steps = ref<WorkflowStep[]>([]);
const loading = ref(false);
const loadRetryDelays = [180, 420, 900];

const shortTaskId = computed(() => {
  if (!props.taskId) return '';
  return props.taskId.length > 18 ? `${props.taskId.slice(0, 10)}...${props.taskId.slice(-6)}` : props.taskId;
});

const activeIndex = computed(() => {
  if (!steps.value.length) return 0;
  const runningIndex = steps.value.findIndex((step) => ['RUNNING', 'IN_PROGRESS', 'EXECUTING', 'WAITING_CONFIRM', 'PENDING_CONFIRM'].includes(step.status));
  if (runningIndex !== -1) return runningIndex;
  const failedIndex = steps.value.findIndex((step) => ['FAILED', 'ERROR'].includes(step.status));
  if (failedIndex !== -1) return failedIndex;
  return steps.value.length - 1;
});

const runnerLeft = computed(() => {
  if (steps.value.length <= 1) return '50%';
  const progress = activeIndex.value / (steps.value.length - 1);
  return `${4 + progress * 92}%`;
});

const carryIcon = computed(() => {
  const skillId = steps.value[activeIndex.value]?.skillId ?? '';
  if (skillId.includes('router')) return '📄';
  if (skillId.includes('calendar')) return '📅';
  if (skillId.includes('pending') || skillId.includes('confirm')) return '✅';
  if (skillId.includes('reminder')) return '⏰';
  if (skillId.includes('mail') || skillId.includes('email')) return '✉️';
  if (skillId.includes('done')) return '🎉';
  return '📦';
});

watch(() => props.taskId, (taskId) => {
  steps.value = [];
  if (taskId) void loadSteps(taskId);
}, { immediate: true });

watch(() => props.liveSteps, (liveSteps) => {
  mergeSteps(liveSteps ?? []);
}, { deep: true });

async function loadSteps(taskId: string): Promise<void> {
  loading.value = true;
  try {
    for (let attempt = 0; attempt <= loadRetryDelays.length; attempt += 1) {
      try {
        mergeSteps(await getWorkflowSteps(taskId));
        return;
      } catch (error) {
        if (props.taskId !== taskId) return;
        if (attempt === loadRetryDelays.length) throw error;
        // SSE can announce a task a moment before its database transaction commits.
        await wait(loadRetryDelays[attempt]);
      }
    }
  } catch (error) {
    console.error(error);
    ElMessage.error('执行链路加载失败');
  } finally {
    if (props.taskId === taskId) loading.value = false;
  }
}

function wait(delay: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, delay));
}

function mergeSteps(nextSteps: WorkflowStep[]): void {
  const merged = new Map(steps.value.map((step) => [`${step.stepOrder}-${step.skillId}`, step]));
  for (const step of nextSteps) merged.set(`${step.stepOrder}-${step.skillId}`, step);
  steps.value = Array.from(merged.values()).sort((left, right) => left.stepOrder - right.stepOrder);
}

async function refresh(): Promise<void> {
  if (props.taskId) await loadSteps(props.taskId);
}

function resolveNodeLabel(skillId: string): string {
  if (skillId.includes('router')) return '路由识别';
  if (skillId.includes('delete')) return '删除准备';
  if (skillId.includes('create')) return '创建任务';
  if (skillId.includes('update') || skillId.includes('modify')) return '修改日程';
  if (skillId.includes('query') || skillId.includes('search')) return '查询日程';
  if (skillId.includes('pending') || skillId.includes('confirm')) return '等待确认';
  if (skillId.includes('calendar')) return '日历 Agent';
  if (skillId.includes('reminder')) return '提醒任务';
  if (skillId.includes('done')) return '完成';
  return skillId.split('.').join(' · ');
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

defineExpose({ refresh });
</script>

<style scoped>
.timeline-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  height: 100%;
  padding: 12px 18px;
  border: 1px solid rgb(170 220 255 / 18%);
  border-radius: 28px;
  background:
    radial-gradient(circle at 15% 0%, rgb(93 177 255 / 18%), transparent 32%),
    linear-gradient(145deg, rgb(15 44 78 / 70%), rgb(7 22 46 / 58%));
  box-shadow: 0 24px 70px rgb(0 6 22 / 34%), inset 0 1px 0 rgb(255 255 255 / 10%);
  overflow: hidden;
  backdrop-filter: blur(26px);
}

.panel-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 2px;
}

.heading-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 14px;
}

h2 {
  margin: 0;
  color: #f0f8ff;
  font-size: 19px;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.panel-heading span,
.track-node span {
  color: rgb(205 227 250 / 66%);
  font-size: 12px;
}

.panel-heading span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-dock {
  min-height: 0;
  height: 100%;
}

.runner-track {
  position: relative;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(126px, 1fr));
  align-items: end;
  min-height: 94px;
  padding: 2px 48px 0;
}

.runner-track::before {
  position: absolute;
  right: 48px;
  bottom: 36px;
  left: 48px;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgb(117 203 255 / 30%), rgb(156 135 255 / 28%));
  content: "";
}

.runner {
  position: absolute;
  bottom: 55px;
  left: var(--runner-left);
  z-index: 2;
  display: grid;
  justify-items: center;
  transform: translateX(-50%);
  transition: left 0.75s cubic-bezier(.2,.8,.2,1);
}

.carry {
  font-size: 21px;
  line-height: 1;
  animation: bounce 0.62s infinite alternate;
}

.cow {
  font-size: 25px;
  line-height: 1;
  filter: drop-shadow(0 8px 12px rgb(0 8 24 / 42%));
}

.track-node {
  position: relative;
  z-index: 1;
  display: grid;
  justify-items: center;
  gap: 5px;
  min-width: 0;
  text-align: center;
}

.dot {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid rgb(177 223 255 / 32%);
  border-radius: 999px;
  background: rgb(19 55 96 / 92%);
  color: #eaf7ff;
  font-size: 12px;
  font-weight: 800;
  box-shadow: 0 8px 24px rgb(0 6 22 / 28%);
}

.track-node strong {
  overflow: hidden;
  max-width: 168px;
  color: #e9f7ff;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.track-node.success .dot,
.track-node.passed .dot {
  border-color: rgb(75 218 162 / 50%);
  background: rgb(18 143 113 / 85%);
}

.track-node.running .dot,
.track-node.active .dot {
  border-color: rgb(145 216 255 / 62%);
  background: rgb(53 142 232 / 92%);
  box-shadow: 0 0 0 7px rgb(64 156 255 / 13%), 0 8px 24px rgb(0 6 22 / 28%);
}

.track-node.waiting .dot {
  border-color: rgb(255 207 120 / 60%);
  background: rgb(181 121 28 / 88%);
}

.track-node.failed .dot {
  border-color: rgb(255 126 126 / 60%);
  background: rgb(205 65 65 / 90%);
}

.success { color: #33d29f; }
.waiting { color: #f0b84a; }
.running { color: #7cccff; }
.failed { color: #ff7d7d; }

@keyframes bounce {
  from { transform: translateY(0); }
  to { transform: translateY(-4px); }
}
</style>
