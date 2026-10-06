import { getAccessToken } from '../auth/tokenStorage';

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

type ApiPostOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  auth?: boolean;
};

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

export async function apiGet<T>(path: string, auth = false): Promise<T> {
  const response = await fetch(path, {
    method: 'GET',
    headers: buildHeaders(auth),
    credentials: 'include',
  });
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
  body: unknown,
  options: ApiPostOptions = {},
): Promise<T> {
  const method = options.method ?? 'POST';
  const auth = options.auth ?? false;

  const headers = buildHeaders(auth) as Record<string, string>;
  if (body !== undefined && method !== 'GET') {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body !== undefined && method !== 'GET' ? JSON.stringify(body) : undefined,
    credentials: 'include',
  });

  if (!response.ok) {
    throw await parseError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}
