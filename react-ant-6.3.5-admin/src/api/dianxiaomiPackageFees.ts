import client from '@/api/client';
import type {
  ApiResponse,
  DianxiaomiPackageFeeImportResultVO,
  DianxiaomiPackageFeeSummaryVO,
  DianxiaomiPackageFeeVO,
  SpringPage,
} from '@/types/api';

export const dianxiaomiPackageFeesApi = {
  list(params?: {
    keyword?: string;
    page?: number;
    pageSize?: number;
  }) {
    return client.get('/platform/dianxiaomi-package-fees', { params }) as Promise<ApiResponse<SpringPage<DianxiaomiPackageFeeVO>>>;
  },

  summary(params?: {
    keyword?: string;
  }) {
    return client.get('/platform/dianxiaomi-package-fees/summary', { params }) as Promise<ApiResponse<DianxiaomiPackageFeeSummaryVO>>;
  },

  clear() {
    return client.delete('/platform/dianxiaomi-package-fees/clear') as Promise<ApiResponse<number>>;
  },

  clearSuccess() {
    return client.delete('/platform/dianxiaomi-package-fees/clear-success') as Promise<ApiResponse<number>>;
  },

  retryFailed() {
    return client.post('/platform/dianxiaomi-package-fees/retry-failed') as Promise<ApiResponse<DianxiaomiPackageFeeImportResultVO>>;
  },

  retryRecord(id: number) {
    return client.post(`/platform/dianxiaomi-package-fees/${id}/retry`) as Promise<ApiResponse<DianxiaomiPackageFeeImportResultVO>>;
  },

  import(payload: {
    packageNumbers: string[];
  }) {
    return client.post('/platform/dianxiaomi-package-fees/import', payload) as Promise<ApiResponse<DianxiaomiPackageFeeImportResultVO>>;
  },
};
