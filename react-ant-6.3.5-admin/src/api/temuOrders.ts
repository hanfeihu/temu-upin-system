import client from '@/api/client';
import type {
  ApiResponse,
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
    cancelState?: string;
    aftersaleState?: string;
    orderStatus?: number;
    matchStatus?: string;
    orderTimeStartMs?: number;
    orderTimeEndMs?: number;
    updateTimeStartMs?: number;
    updateTimeEndMs?: number;
    page?: number;
    pageSize?: number;
  }) {
    return client.get('/platform/temu-orders', { params }) as Promise<ApiResponse<SpringPage<TemuOrderVO>>>;
  },

  detail(id: number) {
    return client.get(`/platform/temu-orders/${id}`) as Promise<ApiResponse<TemuOrderDetailVO>>;
  },

  sync(payload?: TemuOrderSyncPayload) {
    return client.post('/platform/temu-orders/sync', payload ?? {}) as Promise<ApiResponse<TemuOrderSyncResultVO[]>>;
  },

  refreshLogistics(id: number, payload?: TemuOrderLogisticsRefreshPayload) {
    return client.post(`/platform/temu-orders/${id}/logistics/refresh`, payload ?? {}) as Promise<ApiResponse<TemuOrderLogisticsVO>>;
  },
};
