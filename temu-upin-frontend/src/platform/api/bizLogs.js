import apiClient from '@/api'

export const bizLogsApi = {
  list(params = {}) {
    return apiClient.get('/platform/biz-logs', { params })
  },
  create(data) {
    return apiClient.post('/platform/biz-logs', data)
  },
  update(id, data) {
    return apiClient.put(`/platform/biz-logs/${id}`, data)
  },
  delete(id) {
    return apiClient.delete(`/platform/biz-logs/${id}`)
  }
}
