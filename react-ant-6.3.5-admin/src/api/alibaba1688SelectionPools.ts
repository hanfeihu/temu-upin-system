import client from '@/api/client';
import type {
  Alibaba1688SelectionPoolCategoryOption,
  Alibaba1688SelectionPoolAiReportWorkerConfigPayload,
  Alibaba1688SelectionPoolAiReportWorkerConfigVO,
  Alibaba1688SelectionPoolAiReportWorkerStatusVO,
  Alibaba1688SelectionPoolAutoPushConfigPayload,
  Alibaba1688SelectionPoolAutoPushConfigVO,
  Alibaba1688SelectionPoolAutoPushLogVO,
  Alibaba1688SelectionPoolAutoPushStatusVO,
  Alibaba1688SelectionPoolBatchImportPayload,
  Alibaba1688SelectionPoolBatchImportResult,
  Alibaba1688SelectionPoolDetailVO,
  Alibaba1688SelectionPoolImportFromDetailPayload,
  Alibaba1688SelectionPoolPushPayload,
  Alibaba1688SelectionPoolPushResult,
  Alibaba1688SelectionPoolReportVO,
  Alibaba1688SelectionPoolVO,
  ApiResponse,
  SpringPage,
} from '@/types/api';

export const alibaba1688SelectionPoolsApi = {
  list(params: {
    keyword?: string;
    category?: string;
    status?: string;
    reportStatus?: string;
    detailRecordId?: number;
    skuPriceMin?: number;
    skuPriceMax?: number;
    moqMin?: number;
    moqMax?: number;
    startBatchQtyMin?: number;
    startBatchQtyMax?: number;
    aiMaxDimensionCmMin?: number;
    aiMaxDimensionCmMax?: number;
    aiMaxWeightGMin?: number;
    aiMaxWeightGMax?: number;
    aiSelectionDecision?: string;
    aiContainsLiquid?: boolean;
    aiFragile?: boolean;
    temuSiteExceptionBlocked?: boolean;
    pushed?: boolean;
    page?: number;
    size?: number;
  }) {
    return client.get('/platform/alibaba1688-selection-pools', {
      params,
    }) as Promise<ApiResponse<SpringPage<Alibaba1688SelectionPoolVO>>>;
  },

  detail(id: number) {
    return client.get(`/platform/alibaba1688-selection-pools/${id}`) as Promise<ApiResponse<Alibaba1688SelectionPoolDetailVO>>;
  },

  categories() {
    return client.get('/platform/alibaba1688-selection-pools/categories') as Promise<ApiResponse<Alibaba1688SelectionPoolCategoryOption[]>>;
  },

  importFromDetail(payload: Alibaba1688SelectionPoolImportFromDetailPayload) {
    return client.post('/platform/alibaba1688-selection-pools/import-from-detail', payload) as Promise<ApiResponse<Alibaba1688SelectionPoolVO>>;
  },

  batchImportFromDetail(payload: Alibaba1688SelectionPoolBatchImportPayload) {
    return client.post('/platform/alibaba1688-selection-pools/batch-import-from-detail', payload) as Promise<
      ApiResponse<Alibaba1688SelectionPoolBatchImportResult>
    >;
  },

  createReport(id: number, payload?: {
    reportType?: string;
    reportTitle?: string;
    reportSummary?: string;
    reportContent?: string;
    reportJson?: string;
    status?: string;
    score?: number;
    sourceType?: string;
    modelName?: string;
  }) {
    return client.post(`/platform/alibaba1688-selection-pools/${id}/reports`, payload ?? {}) as Promise<ApiResponse<Alibaba1688SelectionPoolReportVO>>;
  },

  pushToProductCollection(id: number, payload?: Alibaba1688SelectionPoolPushPayload) {
    return client.post(`/platform/alibaba1688-selection-pools/${id}/push-to-product-collection`, payload ?? {}) as Promise<
      ApiResponse<Alibaba1688SelectionPoolPushResult>
    >;
  },

  aiReportWorkerConfig() {
    return client.get('/platform/alibaba1688-selection-pools/ai-report-worker/config') as Promise<ApiResponse<Alibaba1688SelectionPoolAiReportWorkerConfigVO>>;
  },

  updateAiReportWorkerConfig(payload: Alibaba1688SelectionPoolAiReportWorkerConfigPayload) {
    return client.put('/platform/alibaba1688-selection-pools/ai-report-worker/config', payload) as Promise<ApiResponse<Alibaba1688SelectionPoolAiReportWorkerConfigVO>>;
  },

  aiReportWorkerStatus() {
    return client.get('/platform/alibaba1688-selection-pools/ai-report-worker/status') as Promise<ApiResponse<Alibaba1688SelectionPoolAiReportWorkerStatusVO>>;
  },

  startAiReportWorker() {
    return client.post('/platform/alibaba1688-selection-pools/ai-report-worker/start') as Promise<ApiResponse<unknown>>;
  },

  stopAiReportWorker() {
    return client.post('/platform/alibaba1688-selection-pools/ai-report-worker/stop') as Promise<ApiResponse<unknown>>;
  },

  retryFailedAiReportWorker() {
    return client.post('/platform/alibaba1688-selection-pools/ai-report-worker/retry-failed') as Promise<ApiResponse<Alibaba1688SelectionPoolAiReportWorkerStatusVO>>;
  },

  autoPushConfig() {
    return client.get('/platform/alibaba1688-selection-pools/auto-push/config') as Promise<ApiResponse<Alibaba1688SelectionPoolAutoPushConfigVO>>;
  },

  updateAutoPushConfig(payload: Alibaba1688SelectionPoolAutoPushConfigPayload) {
    return client.put('/platform/alibaba1688-selection-pools/auto-push/config', payload) as Promise<ApiResponse<Alibaba1688SelectionPoolAutoPushStatusVO>>;
  },

  autoPushStatus() {
    return client.get('/platform/alibaba1688-selection-pools/auto-push/status') as Promise<ApiResponse<Alibaba1688SelectionPoolAutoPushStatusVO>>;
  },

  startAutoPush() {
    return client.post('/platform/alibaba1688-selection-pools/auto-push/start') as Promise<ApiResponse<Alibaba1688SelectionPoolAutoPushStatusVO>>;
  },

  stopAutoPush() {
    return client.post('/platform/alibaba1688-selection-pools/auto-push/stop') as Promise<ApiResponse<Alibaba1688SelectionPoolAutoPushStatusVO>>;
  },

  autoPushLogs(params?: { page?: number; size?: number }) {
    return client.get('/platform/alibaba1688-selection-pools/auto-push/logs', { params }) as Promise<
      ApiResponse<SpringPage<Alibaba1688SelectionPoolAutoPushLogVO>>
    >;
  },
};
