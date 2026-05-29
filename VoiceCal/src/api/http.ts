import axios from 'axios';

export interface ApiResponseEnvelope<T> {
  success: boolean;
  code?: string;
  message?: string;
  data: T;
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 15000,
});

export function unwrapApiResponse<T>(payload: ApiResponseEnvelope<T> | T): T {
  if (payload && typeof payload === 'object' && 'success' in payload && 'data' in payload) {
    const response = payload as ApiResponseEnvelope<T>;
    if (!response.success) {
      throw new Error(response.message || '系统请求失败，请稍后重试。');
    }
    return response.data;
  }

  return payload as T;
}
