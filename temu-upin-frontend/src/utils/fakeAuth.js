const STORAGE_KEY = 'temu-upin-fake-auth'

export function isFakeLoggedIn() {
  return localStorage.getItem(STORAGE_KEY) === '1'
}

export function loginWithFixedAccount(username, password) {
  const matched = username === 'admin' && password === 'admin778899'
  if (matched) {
    localStorage.setItem(STORAGE_KEY, '1')
  }
  return matched
}