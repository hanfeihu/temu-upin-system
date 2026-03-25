import apiClient from '@/api'

export const aiChannelsApi = {
  listAll() {
    return apiClient.get('/channels')
  },
  getOne(id) {
    return apiClient.get(`/channels/${id}`)
  },
  create(payload) {
    return apiClient.post('/channels', payload)
  },
  update(id, payload) {
    return apiClient.put(`/channels/${id}`, payload)
  },
  remove(id) {
    return apiClient.delete(`/channels/${id}`)
  },
  test(id, payload = {}) {
    return apiClient.post(`/channels/${id}/test`, payload)
  }
}
