import apiClient from '@/api'

export const imageTranslateRecordsApi = {
  list(params = {}) {
    return apiClient.get('/platform/image-translate-records', { params })
  }
}