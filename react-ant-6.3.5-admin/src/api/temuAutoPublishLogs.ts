import client from '@/api/client';
import type {
  ApiResponse,
  PublishLogVO,
  SpringPage,
  TemuAutoPublishRunVO,
  TemuAutoPublishSampleVO,
  WorkerStatusVO,
} from '@/types/api';

export interface TemuAutoPublishSearchParams {
  spuId?: string;
  status?: string;
  action?: string;
  q?: string;
  page?: number;
  size?: number;
}

export const temuAutoPublishLogsApi = {
  searchRuns(params: TemuAutoPublishSearchParams) {
    return client.get('/platform/temu-auto-publish/runs/search', {
      params,
    }) as Promise<ApiResponse<SpringPage<TemuAutoPublishRunVO>>>;
  },

  listLogs(runId: number) {
    return client.get('/platform/temu-auto-publish/logs', {
      params: { runId },
    }) as Promise<ApiResponse<PublishLogVO[]>>;
  },

  clearAllLogs() {
    return client.delete('/platform/temu-auto-publish/logs') as Promise<ApiResponse<Record<string, unknown>>>;
  },

  sample(runId: number) {
    return client.get('/platform/temu-auto-publish/sample', {
      params: { runId },
    }) as Promise<ApiResponse<TemuAutoPublishSampleVO>>;
  },

  workerStatus() {
    return client.get('/platform/temu-auto-publish/worker/status') as Promise<ApiResponse<WorkerStatusVO>>;
  },
};
