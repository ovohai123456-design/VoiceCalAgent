export type InputType = 'VOICE' | 'TEXT';

export interface ConversationMessage {
  id: number;
  role: 'USER' | 'ASSISTANT';
  text: string;
  inputType?: InputType;
}

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

export interface AgentSelectSlotRequest {
  userId: string;
  sessionId: string;
  confirmToken: string;
  slotIndex: number;
}

export interface AgentSelectEventRequest {
  userId: string;
  sessionId: string;
  confirmToken: string;
  candidateIndex: number;
}

export interface SuggestedSlot {
  startTime: string;
  endTime: string;
}

export interface AgentResponse {
  success: boolean;
  taskId?: string;
  needConfirm?: boolean;
  confirmToken?: string;
  needClarify?: boolean;
  needEventSelection?: boolean;
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
