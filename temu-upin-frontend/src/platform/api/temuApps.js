import apiClient from '@/api'

export const temuAppsApi = {
  list(params = {}) {
    return apiClient.get('/platform/temu-apps', { params })
  },

  create(payload) {
    return apiClient.post('/platform/temu-apps', payload)
  },

  update(id, payload) {
    return apiClient.put(`/platform/temu-apps/${id}`, payload)
  },

  delete(id) {
    return apiClient.delete(`/platform/temu-apps/${id}`)
  }
}
