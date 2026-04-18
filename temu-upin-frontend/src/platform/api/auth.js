import apiClient from '@/api'

export const authApi = {
  login(payload) {
    return apiClient.post('/auth/login', payload)
  },

  me() {
    return apiClient.get('/auth/me')
  }
}
