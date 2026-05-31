export interface AuthUser {
  id: number;
  username: string;
  displayName?: string;
  timezone?: string;
}

export const UNAUTHORIZED_EVENT = 'voicecal:unauthorized';
