import apiClient from '@/api'

export const temuAttrAiFillApi = {
  listTasks(params = {}) {
    return apiClient.get('/platform/temu-attr-ai-fill/tasks', { params })
  },
  getTask(id) {
    return apiClient.get(`/platform/temu-attr-ai-fill/tasks/${id}`)
  },
  createTask(spuId) {
    return apiClient.post('/platform/temu-attr-ai-fill/tasks', { spuId })
  },
  runTask(id) {
    return apiClient.post(`/platform/temu-attr-ai-fill/tasks/${id}/run`)
  }
}
