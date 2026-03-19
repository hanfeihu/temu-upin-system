import apiClient from '@/api'

export const postImportLogsApi = {
  listRuns(spuId) {
    const id = String(spuId == null ? '' : spuId).trim()
    if (!id) {
      return apiClient.get('/platform/post-import/runs/recent')
    }
    return apiClient.get('/platform/post-import/runs', { params: { spuId: id } })
  },
  listLogs(runId) {
    return apiClient.get('/platform/post-import/logs', { params: { runId } })
  },
  getSample(runId) {
    return apiClient.get('/platform/post-import/sample', { params: { runId } })
  }
}
