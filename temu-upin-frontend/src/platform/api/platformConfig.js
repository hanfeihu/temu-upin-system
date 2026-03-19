import apiClient from '@/api'

export const platformConfigApi = {
  listProfiles() {
    return apiClient.get('/platform/config/profiles')
  },
  createProfile(payload) {
    return apiClient.post('/platform/config/profiles', payload)
  },
  updateProfile(id, payload) {
    return apiClient.put(`/platform/config/profiles/${id}`, payload)
  },
  deleteProfile(id) {
    return apiClient.delete(`/platform/config/profiles/${id}`)
  },
  setDefault(profileId) {
    return apiClient.post('/platform/config/profiles/default', { profileId })
  }
}
