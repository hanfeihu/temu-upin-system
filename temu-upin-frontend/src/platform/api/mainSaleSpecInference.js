import apiClient from '@/api'

export const mainSaleSpecInferenceApi = {
  listTasks(params = {}) {
    return apiClient.get('/platform/temu-main-sale-spec-inference/tasks', { params })
  },
  getTask(id) {
    return apiClient.get(`/platform/temu-main-sale-spec-inference/tasks/${id}`)
  },
  createTask(spuId) {
    return apiClient.post('/platform/temu-main-sale-spec-inference/tasks', { spuId })
  },
  runTask(id) {
    return apiClient.post(`/platform/temu-main-sale-spec-inference/tasks/${id}/run`)
  },
  deleteTask(id) {
    return apiClient.delete(`/platform/temu-main-sale-spec-inference/tasks/${id}`)
  }
}
