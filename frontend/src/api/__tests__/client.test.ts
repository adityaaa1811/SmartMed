import { describe, it, afterEach } from 'node:test';
import assert from 'node:assert';
import { apiPost, ApiError, DEFAULT_TIMEOUT_MS } from '../client';
import { login } from '../auth';

describe('Frontend API Client & Login Error Handling', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('verifies default timeout constant is 30000ms', () => {
    assert.strictEqual(DEFAULT_TIMEOUT_MS, 30000);
  });

  it('1 & 2: aborts a request exceeding timeout and returns distinguishable TIMEOUT error', async () => {
    let abortedSignal: AbortSignal | undefined;

    globalThis.fetch = (async (_url: string | URL | Request, init?: RequestInit) => {
      abortedSignal = init?.signal as AbortSignal;
      return new Promise<Response>((_, reject) => {
        const signal = init?.signal;
        if (signal) {
          signal.addEventListener('abort', () => {
            const err = new Error('The operation was aborted');
            err.name = 'AbortError';
            reject(err);
          });
        }
      });
    }) as typeof fetch;

    try {
      await apiPost('/api/v1/auth/login', { email: 'test@example.com', password: 'password' }, { timeoutMs: 50 });
      assert.fail('Should have thrown timeout error');
    } catch (err: unknown) {
      assert.ok(err instanceof ApiError);
      assert.strictEqual(err.code, 'TIMEOUT');
      assert.strictEqual(err.status, 0);
      assert.strictEqual(
        err.message,
        'The server is taking too long to respond. Please wait a moment and try again.',
      );
      assert.strictEqual(abortedSignal?.aborted, true);
    }
  });

  it('3: produces a useful error on network failure', async () => {
    globalThis.fetch = (async () => {
      throw new TypeError('Failed to fetch');
    }) as typeof fetch;

    try {
      await login({ email: 'test@example.com', password: 'password' });
      assert.fail('Should have thrown network error');
    } catch (err: unknown) {
      assert.ok(err instanceof ApiError);
      assert.strictEqual(err.code, 'NETWORK_ERROR');
      assert.strictEqual(err.status, 0);
      assert.strictEqual(
        err.message,
        'Could not reach SmartMed. Check that the backend is running and try again.',
      );
    }
  });

  it('4 & 5: preserves existing successful login behavior and handles credentials', async () => {
    const mockUser = {
      id: 1,
      fullName: 'Jane Doe',
      email: 'jane@example.com',
      role: 'PATIENT' as const,
      createdAt: '2026-01-01T00:00:00Z',
    };

    globalThis.fetch = (async () => {
      return new Response(
        JSON.stringify({
          success: true,
          message: null,
          data: {
            accessToken: 'mock-jwt-token',
            tokenType: 'Bearer',
            expiresIn: 86400000,
            user: mockUser,
          },
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      );
    }) as typeof fetch;

    const result = await login({ email: 'jane@example.com', password: 'SecretPassword123' });
    assert.strictEqual(result.accessToken, 'mock-jwt-token');
    assert.strictEqual(result.user.email, 'jane@example.com');
  });

  it('6: returns HTTP API error on invalid credentials', async () => {
    globalThis.fetch = (async () => {
      return new Response(
        JSON.stringify({
          success: false,
          error: { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' },
          data: null,
        }),
        {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        },
      );
    }) as typeof fetch;

    try {
      await login({ email: 'wrong@example.com', password: 'wrong' });
      assert.fail('Should have thrown 401 ApiError');
    } catch (err: unknown) {
      assert.ok(err instanceof ApiError);
      assert.strictEqual(err.status, 401);
      assert.strictEqual(err.code, 'INVALID_CREDENTIALS');
      assert.strictEqual(err.message, 'Invalid email or password');
    }
  });
});
