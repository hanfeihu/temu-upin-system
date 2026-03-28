import apiClient from '@/api'

export const parserTestApi = {
  getRun(runId) {
    return apiClient.get(`/platform/parser-test/runs/${encodeURIComponent(runId)}`)
  },

  parseAlibaba1688(html) {
    return apiClient.post('/platform/parser-test/alibaba1688', { html })
  },

  parseTemu(html) {
    return apiClient.post('/platform/parser-test/temu', { html })
  }
}
