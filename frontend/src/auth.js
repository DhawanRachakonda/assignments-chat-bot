// Session token handling. sessionStorage: the sign-in ends when the tab is closed.
const KEY = 'assignments-chatbot-token';

// Empty in dev (Vite proxies /api); set VITE_API_URL to the backend URL in production.
export const apiBase = () => (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');

export function getToken() {
  try {
    return sessionStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function setToken(token) {
  try {
    sessionStorage.setItem(KEY, token);
  } catch {
    /* storage unavailable: user will have to sign in again on reload */
  }
}

export function clearToken() {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
}

export const authHeader = () => {
  const t = getToken();
  return t ? { Authorization: `Bearer ${t}` } : {};
};

/** Reads {"message": "..."} from an error response, if there is one. */
export async function errorMessage(res, fallback) {
  try {
    const body = await res.json();
    return body.message || body.detail || fallback;
  } catch {
    return fallback;
  }
}

/** Asks the backend whether the stored token is still valid. */
export async function checkSession() {
  if (!getToken()) return false;
  try {
    const res = await fetch(`${apiBase()}/api/auth/session`, { headers: authHeader() });
    return res.ok;
  } catch {
    return false;
  }
}

/** Sends the access code for validation. Resolves on success, throws Error(message) otherwise. */
export async function login(code) {
  let res;
  try {
    res = await fetch(`${apiBase()}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code }),
    });
  } catch {
    throw new Error('Cannot reach the server. It may be waking up; try again in a moment.');
  }
  if (!res.ok) throw new Error(await errorMessage(res, `Sign-in failed (${res.status}).`));
  const { token } = await res.json();
  setToken(token);
}
