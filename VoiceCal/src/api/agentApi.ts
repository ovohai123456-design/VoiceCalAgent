import { http, unwrapApiResponse } from '@/api/http';
import type {
  AgentCancelRequest,
  AgentConfirmRequest,
  AgentExecuteRequest,
  AgentResponse,
  AgentSelectSlotRequest,
  AgentSelectEventRequest,
  WorkflowStep,
} from '@/types/agent';

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

function wait<T>(data: T, delay = 450): Promise<T> {
  return new Promise((resolve) => {
    window.setTimeout(() => resolve(data), delay);
  });
}

function buildMockResponse(request: AgentExecuteRequest): AgentResponse {
  return {
    success: true,
    taskId: `task_${Date.now()}`,
    needConfirm: true,
    confirmToken: `ct_${Date.now()}`,
    needClarify: false,
    replyText: `我将处理“${request.text}”，时间预计为 1 小时，是否确认？`,
    speakText: `我将处理${request.text}，时间预计为一小时，是否确认？`,
    data: {},
  };
}

export async function executeAgent(request: AgentExecuteRequest): Promise<AgentResponse> {
  if (USE_MOCK) {
    return wait(buildMockResponse(request));
  }

  const { data } = await http.post<AgentResponse | { data: AgentResponse; success: boolean; message?: string }>(
    '/api/agent/execute',
    request,
  );
  return unwrapApiResponse<AgentResponse>(data);
}

export async function confirmAgent(request: AgentConfirmRequest): Promise<AgentResponse> {
  if (USE_MOCK) {
    return wait({
      success: true,
      taskId: `task_${Date.now()}`,
      needConfirm: false,
      needClarify: false,
      replyText: '已为你确认并创建日程。',
      speakText: '已为你确认并创建日程。',
      data: {},
    });
  }

  const { data } = await http.post<AgentResponse | { data: AgentResponse; success: boolean; message?: string }>(
    '/api/agent/confirm',
    request,
  );
  return unwrapApiResponse<AgentResponse>(data);
}

export async function cancelAgent(request: AgentCancelRequest): Promise<AgentResponse> {
  if (USE_MOCK) {
    return wait({
      success: true,
      taskId: `task_${Date.now()}`,
      needConfirm: false,
      needClarify: false,
      replyText: '已取消本次操作。',
      speakText: '已取消本次操作。',
      data: {},
    });
  }

  const { data } = await http.post<AgentResponse | { data: AgentResponse; success: boolean; message?: string }>(
    '/api/agent/cancel',
    request,
  );
  return unwrapApiResponse<AgentResponse>(data);
}

export async function selectAgentSlot(request: AgentSelectSlotRequest): Promise<AgentResponse> {
  const { data } = await http.post<AgentResponse | { data: AgentResponse; success: boolean; message?: string }>(
    '/api/agent/select-slot',
    request,
  );
  return unwrapApiResponse<AgentResponse>(data);
}

export async function selectAgentEvent(request: AgentSelectEventRequest): Promise<AgentResponse> {
  const { data } = await http.post<AgentResponse | { data: AgentResponse; success: boolean; message?: string }>(
    '/api/agent/select-event',
    request,
  );
  return unwrapApiResponse<AgentResponse>(data);
}

export async function getWorkflowSteps(taskId: string): Promise<WorkflowStep[]> {
  if (USE_MOCK) {
    return wait([
      {
        stepOrder: 1,
        skillId: 'intent.parse',
        status: 'SUCCESS',
        latencyMs: 30,
      },
      {
        stepOrder: 2,
        skillId: 'calendar.conflict_check',
        status: 'SUCCESS',
        latencyMs: 45,
      },
      {
        stepOrder: 3,
        skillId: 'calendar.create',
        status: 'WAITING_CONFIRM',
        latencyMs: 60,
      },
    ]);
  }

  const { data } = await http.get<WorkflowStep[] | { data: WorkflowStep[]; success: boolean; message?: string }>(
    `/api/agent/tasks/${taskId}/steps`,
  );

  if (Array.isArray(data)) {
    return data;
  }

  return unwrapApiResponse(data);
}
