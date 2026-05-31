import type { ReminderTask } from '@/api/reminderApi';
import type { WorkflowStep } from '@/types/agent';

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

export interface TaskStatusEvent {
  taskId: string;
  status: string;
}

export interface AgentEventHandlers {
  onTaskStatus?: (event: TaskStatusEvent) => void;
  onWorkflowStep?: (step: WorkflowStep) => void;
  onReminderChanged?: (reminder: ReminderTask) => void;
  onRemindersRefresh?: () => void;
  onError?: () => void;
}

export function subscribeAgentEvents(
  sessionId: string,
  handlers: AgentEventHandlers,
): () => void {
  if (USE_MOCK) return () => undefined;

  const baseUrl = String(import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
  const params = new URLSearchParams({ sessionId });
  const source = new EventSource(`${baseUrl}/api/events/stream?${params.toString()}`, { withCredentials: true });

  addJsonListener<TaskStatusEvent>(source, 'task-status', handlers.onTaskStatus);
  addJsonListener<WorkflowStep>(source, 'workflow-step', handlers.onWorkflowStep);
  addJsonListener<ReminderTask>(source, 'reminder-changed', handlers.onReminderChanged);
  source.addEventListener('reminders-refresh', () => handlers.onRemindersRefresh?.());
  source.onerror = () => handlers.onError?.();

  return () => source.close();
}

function addJsonListener<T>(
  source: EventSource,
  eventName: string,
  handler: ((payload: T) => void) | undefined,
): void {
  source.addEventListener(eventName, (event) => {
    if (!handler) return;
    try {
      handler(JSON.parse((event as MessageEvent<string>).data) as T);
    } catch (error) {
      console.error(`Invalid SSE payload for ${eventName}`, error);
    }
  });
}
