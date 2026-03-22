import apiClient from '@/api'

export const temuShopsApi = {
  list(params = {}) {
    return apiClient.get('/platform/temu-shops', { params })
  },

  create(payload) {
    return apiClient.post('/platform/temu-shops', payload)
  },

  update(id, payload) {
    return apiClient.put(`/platform/temu-shops/${id}`, payload)
  },

  delete(id) {
    return apiClient.delete(`/platform/temu-shops/${id}`)
  }
}
