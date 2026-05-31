import { http, unwrapApiResponse } from '@/api/http';
import type { AuthUser } from '@/utils/auth';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest extends LoginRequest {
  displayName?: string;
}

export async function login(request: LoginRequest): Promise<AuthUser> {
  const { data } = await http.post<AuthUser | { success: boolean; data: AuthUser; message?: string }>('/api/auth/login', request);
  return unwrapApiResponse(data);
}

export async function register(request: RegisterRequest): Promise<AuthUser> {
  const { data } = await http.post<AuthUser | { success: boolean; data: AuthUser; message?: string }>('/api/auth/register', request);
  return unwrapApiResponse(data);
}

export async function getCurrentUser(): Promise<AuthUser> {
  const { data } = await http.get<AuthUser | { success: boolean; data: AuthUser; message?: string }>('/api/auth/me');
  return unwrapApiResponse(data);
}

export async function logout(): Promise<void> {
  await http.post('/api/auth/logout');
}
