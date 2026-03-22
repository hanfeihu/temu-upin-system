import apiClient from '@/api'

export const mainSaleSpecRuleApi = {
  list(params = {}) {
    return apiClient.get('/platform/temu-main-sale-spec-rules', { params })
  },

  create(payload) {
    return apiClient.post('/platform/temu-main-sale-spec-rules', payload)
  },

  update(id, payload) {
    return apiClient.put(`/platform/temu-main-sale-spec-rules/${id}`, payload)
  },

  delete(id) {
    return apiClient.delete(`/platform/temu-main-sale-spec-rules/${id}`)
  }
}
