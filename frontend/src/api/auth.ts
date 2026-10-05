import { apiGet, apiPost } from './client';
import { setAccessToken } from '../auth/tokenStorage';
import type { ApiEnvelope } from './health';

export type Role = 'PATIENT' | 'CAREGIVER' | 'DOCTOR';

export interface UserDto {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  createdAt: string;
}

export interface AuthData {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserDto;
}

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
  role: Role;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export async function register(payload: RegisterPayload): Promise<AuthData> {
  const envelope = await apiPost<ApiEnvelope<AuthData>>('/api/v1/auth/register', payload);
  setAccessToken(envelope.data.accessToken);
  return envelope.data;
}

export async function login(payload: LoginPayload): Promise<AuthData> {
  const envelope = await apiPost<ApiEnvelope<AuthData>>('/api/v1/auth/login', payload);
  setAccessToken(envelope.data.accessToken);
  return envelope.data;
}

export async function fetchCurrentUser(): Promise<UserDto> {
  const envelope = await apiGet<ApiEnvelope<UserDto>>('/api/v1/users/me', true);
  return envelope.data;
}
