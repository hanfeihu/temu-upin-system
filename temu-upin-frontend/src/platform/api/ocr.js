import apiClient from '@/api'

export const ocrApi = {
  listTasks(params = {}) {
    return apiClient.get('/platform/ocr-tasks', { params })
  },
  statsTasks(params = {}) {
    return apiClient.get('/platform/ocr-tasks/stats', { params })
  },
  createTask(data) {
    return apiClient.post('/platform/ocr-tasks', data)
  },
  updateTask(id, data) {
    return apiClient.put(`/platform/ocr-tasks/${id}`, data)
  },
  deleteTask(id) {
    return apiClient.delete(`/platform/ocr-tasks/${id}`)
  },

  listFilterWords() {
    return apiClient.get('/platform/ocr-filter-words')
  },
  createFilterWord(data) {
    return apiClient.post('/platform/ocr-filter-words', data)
  },
  updateFilterWord(id, data) {
    return apiClient.put(`/platform/ocr-filter-words/${id}`, data)
  },
  deleteFilterWord(id) {
    return apiClient.delete(`/platform/ocr-filter-words/${id}`)
  },

  listTitleFilterWords() {
    return apiClient.get('/platform/title-filter-words')
  },
  createTitleFilterWord(data) {
    return apiClient.post('/platform/title-filter-words', data)
  },
  updateTitleFilterWord(id, data) {
    return apiClient.put(`/platform/title-filter-words/${id}`, data)
  },
  deleteTitleFilterWord(id) {
    return apiClient.delete(`/platform/title-filter-words/${id}`)
  }
}
