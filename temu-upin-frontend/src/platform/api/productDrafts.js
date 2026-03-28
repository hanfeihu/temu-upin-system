import apiClient from '@/api'

export const productDraftApi = {
  list(params = {}) {
    return apiClient.get('/platform/product-drafts', { params })
  },

  get(id) {
    return apiClient.get(`/platform/product-drafts/${id}`)
  },

  importDraft(payload) {
    return apiClient.post('/platform/product-drafts/import', payload, { timeout: 240000 })
  },

  pluginImport(payload) {
    return apiClient.post('/platform/product-drafts/plugin-import', payload, { timeout: 240000 })
  },

  update(id, payload) {
    return apiClient.put(`/platform/product-drafts/${id}`, payload)
  },

  delete(id) {
    return apiClient.delete(`/platform/product-drafts/${id}`)
  },

  pushToCollection(id) {
    return apiClient.post(`/platform/product-drafts/${id}/push-to-collection`, null, { timeout: 240000 })
  }
}
