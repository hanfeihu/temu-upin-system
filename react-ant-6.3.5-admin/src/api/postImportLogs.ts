import client from '@/api/client';
import type { ApiResponse, PostImportRunVO, PublishLogVO } from '@/types/api';

export const postImportLogsApi = {
  listRuns(spuId?: string) {
    const normalized = String(spuId || '').trim();
    if (!normalized) {
      return client.get('/platform/post-import/runs/recent') as Promise<ApiResponse<PostImportRunVO[]>>;
    }

    return client.get('/platform/post-import/runs', {
      params: { spuId: normalized },
    }) as Promise<ApiResponse<PostImportRunVO[]>>;
  },

  listLogs(runId: number) {
    return client.get('/platform/post-import/logs', {
      params: { runId },
    }) as Promise<ApiResponse<PublishLogVO[]>>;
  },

  getSample(runId: number) {
    return client.get('/platform/post-import/sample', {
      params: { runId },
    }) as Promise<ApiResponse<{ sampleJson?: string | null }>>;
  },
};
