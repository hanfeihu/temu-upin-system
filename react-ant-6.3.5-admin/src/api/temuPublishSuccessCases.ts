import client from '@/api/client';
import type { ApiResponse, SpringPage, SuccessCaseDetailVO, SuccessCaseRowVO } from '@/types/api';

export const temuPublishSuccessCasesApi = {
  list(params: Record<string, unknown>) {
    return client.get('/platform/temu-publish/success-cases', {
      params,
    }) as Promise<ApiResponse<SpringPage<SuccessCaseRowVO>>>;
  },

  get(id: number) {
    return client.get(`/platform/temu-publish/success-cases/${id}`) as Promise<ApiResponse<SuccessCaseDetailVO>>;
  },
};
