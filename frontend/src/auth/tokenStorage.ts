const TOKEN_KEY = 'smartmed_access_token';

export function getAccessToken(): string | null {
  if (typeof sessionStorage === 'undefined') return null;
  return sessionStorage.getItem(TOKEN_KEY);
}

export function setAccessToken(token: string): void {
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.setItem(TOKEN_KEY, token);
  }
}

export function clearAccessToken(): void {
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.removeItem(TOKEN_KEY);
  }
}

