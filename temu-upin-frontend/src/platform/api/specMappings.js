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

  listProfiles(params = {}) {
    return apiClient.get('/platform/spec-mappings/profiles', { params })
  },

  createProfile(payload) {
    return apiClient.post('/platform/spec-mappings/profiles', payload)
  },

  updateProfile(id, payload) {
    return apiClient.put(`/platform/spec-mappings/profiles/${id}`, payload)
  },

  deleteProfile(id) {
    return apiClient.delete(`/platform/spec-mappings/profiles/${id}`)
  },

  getWorkbench(spuId) {
    return apiClient.get(`/platform/spec-mappings/workbench/${spuId}`)
  },

  listTemuParentSpecs() {
    return apiClient.get('/platform/temu/parent-specs')
  },

  preview(spuId, payload) {
    return apiClient.post(`/platform/spec-mappings/workbench/${spuId}/preview`, payload)
  },

  saveDraft(spuId, payload) {
    return apiClient.post(`/platform/spec-mappings/workbench/${spuId}/drafts`, payload)
  }
}