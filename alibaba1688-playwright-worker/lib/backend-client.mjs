function normalizeBaseUrl(baseUrl) {
  const raw = String(baseUrl || '').trim().replace(/\/+$/, '');
  if (!raw) {
    throw new Error('Missing --base-url');
  }
  return raw.endsWith('/api') ? raw.slice(0, -4) : raw;
}

function buildApiUrl(baseUrl, path) {
  return `${normalizeBaseUrl(baseUrl)}/api${path.startsWith('/') ? path : `/${path}`}`;
}

async function parseResponse(response) {
  const text = await response.text().catch(() => '');
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }

  if (!response.ok) {
    throw new Error(data?.message || `${response.status} ${response.statusText}` || 'Request failed');
  }
  if (data && typeof data.success === 'boolean' && data.success === false) {
    throw new Error(data.message || 'Request failed');
  }
  return data;
}

async function requestJson(baseUrl, path, { method = 'GET', token, workerApiKey, body } = {}) {
  const headers = {};
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (workerApiKey) {
    headers['X-Worker-Key'] = workerApiKey;
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json;charset=utf-8';
  }

  const response = await fetch(buildApiUrl(baseUrl, path), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return parseResponse(response);
}

export async function loginAdmin(baseUrl, username, password) {
  const res = await requestJson(baseUrl, '/auth/login', {
    method: 'POST',
    body: { username, password },
  });
  return res?.data?.accessToken;
}

export async function saveWorkerSession(baseUrl, token, payload) {
  const publicMode = typeof token === 'object' && token?.workerApiKey;
  const res = await requestJson(baseUrl, publicMode ? '/public/alibaba1688-worker/credentials/save-storage' : '/platform/alibaba1688-auth-sessions/worker/save-storage', {
    method: 'POST',
    token: publicMode ? undefined : token,
    workerApiKey: publicMode ? token.workerApiKey : undefined,
    body: payload,
  });
  return res?.data;
}

export async function updateWorkerSessionStatus(baseUrl, token, credentialId, payload) {
  const publicMode = typeof token === 'object' && token?.workerApiKey;
  const res = await requestJson(baseUrl, publicMode ? `/public/alibaba1688-worker/credentials/${credentialId}/status` : `/platform/alibaba1688-auth-sessions/${credentialId}/worker-status`, {
    method: 'POST',
    token: publicMode ? undefined : token,
    workerApiKey: publicMode ? token.workerApiKey : undefined,
    body: payload,
  });
  return res?.data;
}

export async function getWorkerStorageState(baseUrl, token, credentialId) {
  const publicMode = typeof token === 'object' && token?.workerApiKey;
  const res = await requestJson(baseUrl, publicMode ? `/public/alibaba1688-worker/credentials/${credentialId}/storage-state` : `/platform/alibaba1688-auth-sessions/${credentialId}/worker-storage-state`, {
    method: 'GET',
    token: publicMode ? undefined : token,
    workerApiKey: publicMode ? token.workerApiKey : undefined,
  });
  return res?.data;
}

export async function claimNextTask(baseUrl, token, payload) {
  const publicMode = typeof token === 'object' && token?.workerApiKey;
  const res = await requestJson(baseUrl, publicMode ? '/public/alibaba1688-worker/tasks/claim' : '/platform/alibaba1688-detail-tasks/worker/claim', {
    method: 'POST',
    token: publicMode ? undefined : token,
    workerApiKey: publicMode ? token.workerApiKey : undefined,
    body: payload,
  });
  return res?.data;
}

export async function reportTaskSuccess(baseUrl, token, taskId, payload) {
  const publicMode = typeof token === 'object' && token?.workerApiKey;
  const res = await requestJson(baseUrl, publicMode ? `/public/alibaba1688-worker/tasks/${taskId}/success` : `/platform/alibaba1688-detail-tasks/${taskId}/worker/success`, {
    method: 'POST',
    token: publicMode ? undefined : token,
    workerApiKey: publicMode ? token.workerApiKey : undefined,
    body: payload,
  });
  return res?.data;
}

export async function reportTaskFailure(baseUrl, token, taskId, payload) {
  const publicMode = typeof token === 'object' && token?.workerApiKey;
  const res = await requestJson(baseUrl, publicMode ? `/public/alibaba1688-worker/tasks/${taskId}/failure` : `/platform/alibaba1688-detail-tasks/${taskId}/worker/failure`, {
    method: 'POST',
    token: publicMode ? undefined : token,
    workerApiKey: publicMode ? token.workerApiKey : undefined,
    body: payload,
  });
  return res?.data;
}

export async function listWorkerCredentials(baseUrl, workerApiKey) {
  const res = await requestJson(baseUrl, '/public/alibaba1688-worker/credentials', {
    method: 'GET',
    workerApiKey,
  });
  return res?.data || [];
}
