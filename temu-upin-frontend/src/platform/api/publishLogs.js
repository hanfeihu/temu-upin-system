import apiClient from '@/api'

export const publishLogsApi = {
  listRuns(spuId) {
    const id = String(spuId == null ? '' : spuId).trim()
    if (!id) {
      return apiClient.get('/platform/temu-publish/runs/recent')
    }
    return apiClient.get('/platform/temu-publish/runs', { params: { spuId: id } })
  },
  listLogs(runId) {
    return apiClient.get('/platform/temu-publish/logs', { params: { runId } })
  }
}
