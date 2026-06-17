import client from '@/api/client';
import type {
  ApiResponse,
  TemuOrderDashboardResponseVO,
  SpringPage,
  TemuOrderDetailVO,
  TemuOrderLogisticsRefreshPayload,
  TemuOrderLogisticsVO,
  TemuOrderSyncPayload,
  TemuOrderSyncResultVO,
  TemuOrderVO,
} from '@/types/api';

export const temuOrdersApi = {
  list(params?: {
    shopRecordId?: number;
    shopId?: string;
    keyword?: string;
    matchedTemuSkuIdLike?: string;
    agingFilter?: string;
    cancelState?: string;
    aftersaleState?: string;
    orderStatus?: number;
    matchStatus?: string;
    noStockProduct?: boolean;
    orderTimeStartMs?: number;
    orderTimeEndMs?: number;
    updateTimeStartMs?: number;
    updateTimeEndMs?: number;
    page?: number;
    pageSize?: number;
  }) {
    return client.get('/platform/temu-orders', { params }) as Promise<ApiResponse<SpringPage<TemuOrderVO>>>;
  },

  dashboard(params?: {
    shopRecordId?: number;
    shopId?: string;
  }) {
    return client.get('/platform/temu-orders/dashboard', { params }) as Promise<ApiResponse<TemuOrderDashboardResponseVO>>;
  },

  detail(id: number) {
    return client.get(`/platform/temu-orders/${id}`) as Promise<ApiResponse<TemuOrderDetailVO>>;
  },

  sync(payload?: TemuOrderSyncPayload) {
    return client.post('/platform/temu-orders/sync', payload ?? {}) as Promise<ApiResponse<TemuOrderSyncResultVO[]>>;
  },

  syncFromDianxiaomi(payload?: TemuOrderSyncPayload) {
    return client.post('/platform/temu-orders/dianxiaomi-sync', payload ?? {}) as Promise<ApiResponse<TemuOrderSyncResultVO[]>>;
  },

  refreshLogistics(id: number, payload?: TemuOrderLogisticsRefreshPayload) {
    return client.post(`/platform/temu-orders/${id}/logistics/refresh`, payload ?? {}) as Promise<ApiResponse<TemuOrderLogisticsVO>>;
  },

  refreshAllLogistics(payload: { shopRecordId: number }) {
    return client.post('/platform/temu-orders/logistics/refresh-all', payload) as Promise<ApiResponse<number>>;
  },

  updateDianxiaomiPackageNumber(id: number, payload: { packageNumber: string }) {
    return client.put(`/platform/temu-orders/${id}/dianxiaomi-package-number`, payload) as Promise<ApiResponse<null>>;
  },
};
