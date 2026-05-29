export type InputType = 'VOICE' | 'TEXT';

export type PageStatus =
  | 'IDLE'
  | 'LISTENING'
  | 'RECOGNIZED'
  | 'WAITING_RESPONSE'
  | 'WAITING_CONFIRM'
  | 'EXECUTING'
  | 'DONE'
  | 'FAILED'
  | 'CANCELLED';

export interface AgentExecuteRequest {
  userId: string;
  sessionId: string;
  inputType: InputType;
  text: string;
  timezone: string;
  currentTime: string;
}

export interface AgentConfirmRequest {
  userId: string;
  sessionId: string;
  confirmToken: string;
}

export interface AgentCancelRequest {
  userId: string;
  sessionId: string;
  confirmToken: string;
}

export interface AgentResponse {
  success: boolean;
  taskId?: string;
  needConfirm?: boolean;
  confirmToken?: string;
  needClarify?: boolean;
  missingFields?: string[];
  replyText: string;
  speakText?: string;
  data?: Record<string, unknown>;
}

export interface WorkflowStep {
  stepOrder: number;
  skillId: string;
  stepName?: string;
  status: string;
  latencyMs?: number;
  errorMessage?: string;
}
