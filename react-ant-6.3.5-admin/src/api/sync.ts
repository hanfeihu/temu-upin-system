import client from '@/api/client';
import type {
  ActivityDetailVO,
  ActivityEnrollmentVO,
  ActivityMatchResponseVO,
  ActivitySessionQueryResponseVO,
  ActivitySessionVO,
  ActivityVO,
  ApiResponse,
  PriceAdjustBatchPayload,
  PriceAdjustOrderVO,
  PriceReviewBatchPayload,
  PriceReviewOrderVO,
  SyncGoodsRepairJobVO,
  SpringPage,
  SyncConfigResponse,
  SyncGoodsDetailVO,
  SyncGoodsListItemVO,
  SyncTaskCreatePayload,
  SyncTaskProgressVO,
  SyncTaskVO,
  ShopSkuItemVO,
} from '@/types/api';

export const syncApi = {
  getConfigs(shopId: string) {
    return client.get('/sync/config', { params: { shopId } }) as Promise<ApiResponse<SyncConfigResponse>>;
  },

  saveConfigs(payload: { shopId: string; configs: Array<{ configKey: string; configValue: string }> }) {
    return client.post('/sync/config', payload) as Promise<ApiResponse<null>>;
  },

  listSyncTasks(params: { shopId: string; syncType?: string; page?: number; pageSize?: number }) {
    return client.get('/sync/tasks', { params }) as Promise<ApiResponse<SpringPage<SyncTaskVO>>>;
  },

  createSyncTasks(payload: SyncTaskCreatePayload) {
    return client.post('/sync/tasks', payload) as Promise<ApiResponse<SyncTaskVO[]>>;
  },

  getTaskDetail(taskId: number) {
    return client.get(`/sync/tasks/${taskId}`) as Promise<ApiResponse<SyncTaskVO>>;
  },

  getTaskProgress(taskId: number) {
    return client.get(`/sync/tasks/${taskId}/progress`) as Promise<ApiResponse<SyncTaskProgressVO>>;
  },

  retryTask(taskId: number, mode = 'CONTINUE') {
    return client.post(`/sync/tasks/${taskId}/retry`, { mode }) as Promise<ApiResponse<SyncTaskVO>>;
  },

  cancelTask(taskId: number) {
    return client.post(`/sync/tasks/${taskId}/cancel`) as Promise<ApiResponse<null>>;
  },

  clearShopSyncData(shopId: string) {
    return client.delete('/sync/tasks/shop-data', { params: { shopId } }) as Promise<ApiResponse<Record<string, unknown>>>;
  },

  getGoodsList(params: {
    shopId: string;
    keyword?: string;
    skcSiteStatus?: number;
    minSupplierPrice?: number;
    maxSupplierPrice?: number;
    page?: number;
    pageSize?: number;
  }) {
    return client.get('/sync/goods', { params }) as Promise<ApiResponse<SpringPage<SyncGoodsListItemVO>>>;
  },

  getGoodsDetail(id: number) {
    return client.get(`/sync/goods/${id}`) as Promise<ApiResponse<SyncGoodsDetailVO>>;
  },

  getShopSkuList(params: {
    shopId: string;
    productSkcId?: number;
    productSkuId?: number;
    skuExtCode?: string;
    page?: number;
    pageSize?: number;
  }) {
    return client.get('/sync/shop-skus', { params }) as Promise<ApiResponse<SpringPage<ShopSkuItemVO>>>;
  },

  updateShopSkuPurchasePrice(productSkuId: number, payload: { shopId: string; purchasePrice?: number | null }) {
    return client.put(`/sync/shop-skus/${productSkuId}/purchase-price`, payload) as Promise<ApiResponse<ShopSkuItemVO>>;
  },

  repairGoodsDetails(shopId: string) {
    return client.post('/sync/goods/repair-details', null, { params: { shopId } }) as Promise<ApiResponse<SyncGoodsRepairJobVO>>;
  },

  getRepairGoodsDetailsStatus(jobId: string) {
    return client.get('/sync/goods/repair-details/status', { params: { jobId } }) as Promise<ApiResponse<SyncGoodsRepairJobVO>>;
  },

  getLatestRepairGoodsDetailsStatus(shopId: string) {
    return client.get('/sync/goods/repair-details/status/latest', { params: { shopId } }) as Promise<ApiResponse<SyncGoodsRepairJobVO>>;
  },

  getPriceReviewList(params: {
    shopId: string;
    orderStatus?: number;
    reviewAction?: string;
    page?: number;
    pageSize?: number;
  }) {
    return client.get('/sync/price-review', { params }) as Promise<ApiResponse<SpringPage<PriceReviewOrderVO>>>;
  },

  getPriceReviewDetail(id: number) {
    return client.get(`/sync/price-review/${id}`) as Promise<ApiResponse<PriceReviewOrderVO>>;
  },

  batchReviewPrice(payload: PriceReviewBatchPayload) {
    return client.post('/sync/price-review/batch-review', payload) as Promise<ApiResponse<Record<string, unknown>>>;
  },

  getPriceAdjustList(params: {
    shopId: string;
    status?: number;
    reviewAction?: string;
    page?: number;
    pageSize?: number;
  }) {
    return client.get('/sync/price-adjust', { params }) as Promise<ApiResponse<SpringPage<PriceAdjustOrderVO>>>;
  },

  getPriceAdjustDetail(id: number) {
    return client.get(`/sync/price-adjust/${id}`) as Promise<ApiResponse<PriceAdjustOrderVO>>;
  },

  clearPriceAdjustLocalData(shopId: string) {
    return client.delete('/sync/price-adjust/local-data', { params: { shopId } }) as Promise<ApiResponse<Record<string, unknown>>>;
  },

  batchReviewAdjust(payload: PriceAdjustBatchPayload) {
    return client.post('/sync/price-adjust/batch-review', payload) as Promise<ApiResponse<Record<string, unknown>>>;
  },

  getActivityList(params: { shopId: string; activityType?: number }) {
    return client.get('/sync/activity/list', { params }) as Promise<ApiResponse<ActivityVO[]>>;
  },

  getActivitySessions(params: { shopId: string; activityType: number; sessionStatus?: number }) {
    return client.get('/sync/activity/sessions', { params }) as Promise<ApiResponse<ActivitySessionVO[]>>;
  },

  getActivityDetail(params: { shopId: string; activityType: number; activityThematicId?: number }) {
    return client.get('/sync/activity/detail', { params }) as Promise<ApiResponse<ActivityDetailVO>>;
  },

  matchActivityProducts(payload: {
    shopId: string;
    activityType: number;
    activityThematicId?: number;
    searchScrollContext?: string;
    rowCount?: number;
    productIds?: number[];
  }) {
    return client.post('/sync/activity/match-products', payload) as Promise<ApiResponse<ActivityMatchResponseVO>>;
  },

  queryActivitySessions(payload: {
    shopId: string;
    activityType: number;
    activityThematicId?: number;
    startTime?: number;
    endTime?: number;
    productIds?: number[];
  }) {
    return client.post('/sync/activity/sessions/query', payload) as Promise<ApiResponse<ActivitySessionQueryResponseVO>>;
  },

  getEnrollmentList(params: {
    shopId: string;
    activityType?: number;
    enrollStatus?: number;
    page?: number;
    pageSize?: number;
  }) {
    return client.get('/sync/activity/enrollments', { params }) as Promise<ApiResponse<SpringPage<ActivityEnrollmentVO>>>;
  },

  getEnrollmentDetail(id: number) {
    return client.get(`/sync/activity/enrollments/${id}`) as Promise<ApiResponse<ActivityEnrollmentVO>>;
  },

  batchEnroll(payload: Record<string, unknown>) {
    return client.post('/sync/activity/batch-enroll', payload) as Promise<ApiResponse<Record<string, unknown>>>;
  },
};
