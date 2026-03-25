import apiClient from '@/api'

export const specMappingApi = {
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

  preview(spuId, payload) {
    return apiClient.post(`/platform/spec-mappings/workbench/${spuId}/preview`, payload)
  },

  saveDraft(spuId, payload) {
    return apiClient.post(`/platform/spec-mappings/workbench/${spuId}/drafts`, payload)
  }
}