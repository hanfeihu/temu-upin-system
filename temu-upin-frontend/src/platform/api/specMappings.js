import apiClient from '@/api'

export const specMappingApi = {
  listParentSpecMappings(params = {}) {
    return apiClient.get('/platform/spec-mappings/parent-spec-mappings', { params })
  },

  createParentSpecMapping(payload) {
    return apiClient.post('/platform/spec-mappings/parent-spec-mappings', payload)
  },

  updateParentSpecMapping(id, payload) {
    return apiClient.put(`/platform/spec-mappings/parent-spec-mappings/${id}`, payload)
  },

  deleteParentSpecMapping(id) {
    return apiClient.delete(`/platform/spec-mappings/parent-spec-mappings/${id}`)
  },

  listTemuParentSpecs() {
    return apiClient.get('/platform/temu/parent-specs')
  }
}