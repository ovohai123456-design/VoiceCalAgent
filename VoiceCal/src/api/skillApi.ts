import { http, unwrapApiResponse } from '@/api/http';

export interface SkillExecutor {
  type: string;
  toolKey?: string;
  mockName?: string;
  jobType?: string;
}

export interface SkillDefinition {
  skillId: string;
  name: string;
  description?: string;
  category?: string;
  enabled: boolean;
  triggerExamples?: string[];
  executor?: SkillExecutor;
}

export async function listSkills(): Promise<SkillDefinition[]> {
  const { data } = await http.get<SkillDefinition[] | { success: boolean; data: SkillDefinition[] }>('/api/skills');
  return Array.isArray(data) ? data : unwrapApiResponse(data);
}

export async function reloadSkills(): Promise<SkillDefinition[]> {
  const { data } = await http.post<SkillDefinition[] | { success: boolean; data: SkillDefinition[] }>('/api/skills/reload');
  return Array.isArray(data) ? data : unwrapApiResponse(data);
}
