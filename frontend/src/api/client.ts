import { getAccessToken } from '../auth/tokenStorage';

export const DEFAULT_TIMEOUT_MS = 30000;

const getBaseUrl = (): string => {
  try {
    return (import.meta?.env?.VITE_API_BASE_URL ?? '').trim().replace(/\/+$/, '');
  } catch {
    return '';
  }
};

const apiUrl = (path: string) => `${getBaseUrl()}${path.startsWith('/') ? path : `/${path}`}`;

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public code?: string,
    public data?: unknown,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  auth?: boolean;
  timeoutMs?: number;
  signal?: AbortSignal;
}

export type ApiPostOptions = RequestOptions;

async function parseError(response: Response): Promise<ApiError> {
  let message = `Request failed (${response.status})`;
  let code: string | undefined;
  let data: unknown;
  try {
    const body = (await response.json()) as {
      message?: string;
      error?: { code?: string; message?: string };
      data?: unknown;
    };
    data = body.data;
    if (body.error?.message) {
      message = body.error.message;
      code = body.error.code;
    } else if (body.message) {
      message = body.message;
    }
  } catch {
    /* ignore */
  }
  return new ApiError(message, response.status, code, data);
}

function buildHeaders(auth: boolean): HeadersInit {
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  if (auth) {
    const token = getAccessToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }
  return headers;
}

async function fetchWithTimeout(
  url: string,
  init: RequestInit,
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
  userSignal?: AbortSignal,
): Promise<Response> {
  const controller = new AbortController();
  let timedOut = false;

  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);

  let onUserAbort: (() => void) | undefined;
  if (userSignal) {
    if (userSignal.aborted) {
      controller.abort();
    } else {
      onUserAbort = () => controller.abort();
      userSignal.addEventListener('abort', onUserAbort, { once: true });
    }
  }

  try {
    const response = await fetch(url, {
      ...init,
      signal: controller.signal,
    });
    return response;
  } catch (err: unknown) {
    if (timedOut) {
      throw new ApiError(
        'The server is taking too long to respond. Please wait a moment and try again.',
        0,
        'TIMEOUT',
      );
    }
    if (err instanceof ApiError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      throw new ApiError('Request was cancelled.', 0, 'CANCELLED');
    }
    throw new ApiError(
      'Could not reach SmartMed. Check that the backend is running and try again.',
      0,
      'NETWORK_ERROR',
      err,
    );
  } finally {
    clearTimeout(timer);
    if (userSignal && onUserAbort) {
      userSignal.removeEventListener('abort', onUserAbort);
    }
  }
}

export async function apiGet<T>(
  path: string,
  auth = false,
  options: RequestOptions = {},
): Promise<T> {
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const response = await fetchWithTimeout(
    apiUrl(path),
    {
      method: 'GET',
      headers: buildHeaders(auth),
      credentials: 'include',
    },
    timeoutMs,
    options.signal,
  );

  if (!response.ok) {
    throw await parseError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export async function apiPost<T>(
  path: string,
  body?: unknown,
  options: ApiPostOptions = {},
): Promise<T> {
  const method = options.method ?? 'POST';
  const auth = options.auth ?? false;
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;

  const headers = buildHeaders(auth) as Record<string, string>;
  if (body !== undefined && method !== 'GET') {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetchWithTimeout(
    apiUrl(path),
    {
      method,
      headers,
      body: body !== undefined && method !== 'GET' ? JSON.stringify(body) : undefined,
      credentials: 'include',
    },
    timeoutMs,
    options.signal,
  );

  if (!response.ok) {
    throw await parseError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

