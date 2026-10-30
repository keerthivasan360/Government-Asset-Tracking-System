export async function getSession() {
  const response = await fetch('/api/session', { cache: 'no-store' });
  if (!response.ok) throw new Error('Unable to connect to the monitoring station.');
  return response.json();
}
export async function request(path, options = {}) {
  const method = options.method || 'GET';
  const headers = new Headers(options.headers);
  if (method !== 'GET') {
    const current = await getSession();
    headers.set(current.csrfHeader, current.csrfToken);
  }
  if (options.body && !(options.body instanceof URLSearchParams)) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, { ...options, headers, cache: 'no-store' });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    const error = new Error(data.message || (response.status === 401 ? 'Please sign in again.' : response.status === 403 ? 'You do not have permission for this action. Refresh and try again.' : 'The request could not be completed.'));
    error.status = response.status; error.fields = data.fields;
    if (response.status === 401 && path !== '/api/login') window.dispatchEvent(new Event('session-expired'));
    throw error;
  }
  return response.status === 204 || response.status === 201 ? null : response.json();
}
