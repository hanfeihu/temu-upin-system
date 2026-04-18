const TOKEN_KEY = 'temu-upin-auth-token'
const USER_KEY = 'temu-upin-auth-user'

export function getPlatformAuthToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function hasPlatformAuth() {
  return !!getPlatformAuthToken()
}

export function savePlatformAuth(payload = {}) {
  const token = payload?.accessToken || ''
  const user = {
    username: payload?.username || '',
    displayName: payload?.displayName || payload?.username || '',
    expiresAt: payload?.expiresAt || null
  }

  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  }
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function getPlatformAuthUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function clearPlatformAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
