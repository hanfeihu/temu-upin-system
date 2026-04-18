import client from '@/api/client';
import type { ApiResponse, ParserTestRunVO } from '@/types/api';

export const parserTestApi = {
  getRun(runId: string) {
    return client.get(`/platform/parser-test/runs/${encodeURIComponent(runId)}`) as Promise<ApiResponse<ParserTestRunVO>>;
  },

  parseAlibaba1688(html: string) {
    return client.post('/platform/parser-test/alibaba1688', { html }) as Promise<ApiResponse<ParserTestRunVO>>;
  },

  parseTemu(html: string) {
    return client.post('/platform/parser-test/temu', { html }) as Promise<ApiResponse<ParserTestRunVO>>;
  },
};
