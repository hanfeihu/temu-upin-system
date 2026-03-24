import apiClient from '@/api'

export const temuPublishSuccessCasesApi = {
  list(params = {}) {
    return apiClient.get('/platform/temu-publish/success-cases', { params })
  },
  get(id) {
    return apiClient.get(`/platform/temu-publish/success-cases/${id}`)
  }
}