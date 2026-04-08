import apiClient from '@/api'

export const temuAutoPublishLogsApi = {
  // If spuId empty, list recent runs
  listRuns(spuId) {
    const id = String(spuId == null ? '' : spuId).trim()
    if (!id) {
      return apiClient.get('/platform/temu-auto-publish/runs/recent')
    }
    return apiClient.get('/platform/temu-auto-publish/runs', { params: { spuId: id } })
  },
  searchRuns(params) {
    return apiClient.get('/platform/temu-auto-publish/runs/search', { params: params || {} })
  },
  listLogs(runId) {
    return apiClient.get('/platform/temu-auto-publish/logs', { params: { runId } })
  },
  clearAllLogs() {
    return apiClient.delete('/platform/temu-auto-publish/logs')
  },
  sample(runId) {
    return apiClient.get('/platform/temu-auto-publish/sample', { params: { runId } })
  },
  workerStatus() {
    return apiClient.get('/platform/temu-auto-publish/worker/status')
  }
}
