import axios from 'axios';
import { UNAUTHORIZED_EVENT } from '@/utils/auth';

export interface ApiResponseEnvelope<T> {
  success: boolean;
  code?: string;
  message?: string;
  data: T;
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: Number(import.meta.env.VITE_API_TIMEOUT_MS ?? 60000),
  withCredentials: true,
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    }
    return Promise.reject(error);
  },
);

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
