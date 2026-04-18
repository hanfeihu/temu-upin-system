import client from '@/api/client';
import type { ApiResponse, PublishLogVO, PublishRunVO } from '@/types/api';

export const publishLogsApi = {
  listRuns(spuId?: string) {
    const normalized = String(spuId || '').trim();
    if (!normalized) {
      return client.get('/platform/temu-publish/runs/recent') as Promise<ApiResponse<PublishRunVO[]>>;
    }

    return client.get('/platform/temu-publish/runs', {
      params: { spuId: normalized },
    }) as Promise<ApiResponse<PublishRunVO[]>>;
  },

  listLogs(runId: number) {
    return client.get('/platform/temu-publish/logs', {
      params: { runId },
    }) as Promise<ApiResponse<PublishLogVO[]>>;
  },
};
