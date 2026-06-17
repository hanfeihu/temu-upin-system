import client from '@/api/client';
import type {
  ApiResponse,
  DeleteSizeFilteredImagesResultVO,
  OcrImageTranslateWorkerConfigPayload,
  OcrImageTranslateWorkerConfigVO,
  OcrImageTranslateWorkerLogVO,
  OcrImageTranslateWorkerStatusVO,
  OcrSizeFilterConfigPayload,
  OcrSizeFilterConfigVO,
  OcrTaskPayload,
  OcrTaskVO,
  SpringPage,
  WordVO,
} from '@/types/api';

export const ocrApi = {
  listTasks(params: {
    spuId?: number;
    productId?: string;
    imageType?: number;
    execStatus?: number;
    filtered?: boolean;
    containsChinese?: boolean;
    translateStatus?: string;
    imageWidthMin?: number;
    imageWidthMax?: number;
    imageHeightMin?: number;
    imageHeightMax?: number;
    page?: number;
    size?: number;
  }) {
    return client.get('/platform/ocr-tasks', { params }) as Promise<ApiResponse<SpringPage<OcrTaskVO>>>;
  },

  statsTasks(params: {
    spuId?: number;
    productId?: string;
    imageType?: number;
    filtered?: boolean;
    containsChinese?: boolean;
    translateStatus?: string;
    imageWidthMin?: number;
    imageWidthMax?: number;
    imageHeightMin?: number;
    imageHeightMax?: number;
  }) {
    return client.get('/platform/ocr-tasks/stats', { params }) as Promise<ApiResponse<Record<string, number>>>;
  },

  createTask(payload: OcrTaskPayload) {
    return client.post('/platform/ocr-tasks', payload) as Promise<ApiResponse<OcrTaskVO>>;
  },

  updateTask(id: number, payload: OcrTaskPayload) {
    return client.put(`/platform/ocr-tasks/${id}`, payload) as Promise<ApiResponse<OcrTaskVO>>;
  },

  deleteTask(id: number) {
    return client.delete(`/platform/ocr-tasks/${id}`) as Promise<ApiResponse<null>>;
  },

  deleteSizeFilteredImages() {
    return client.post('/platform/ocr-tasks/delete-size-filtered-images') as Promise<ApiResponse<DeleteSizeFilteredImagesResultVO>>;
  },

  listSizeFilterConfigs() {
    return client.get('/platform/ocr-size-filter-configs') as Promise<ApiResponse<OcrSizeFilterConfigVO[]>>;
  },

  createSizeFilterConfig(payload: OcrSizeFilterConfigPayload) {
    return client.post('/platform/ocr-size-filter-configs', payload) as Promise<ApiResponse<OcrSizeFilterConfigVO>>;
  },

  updateSizeFilterConfig(id: number, payload: OcrSizeFilterConfigPayload) {
    return client.put(`/platform/ocr-size-filter-configs/${id}`, payload) as Promise<ApiResponse<OcrSizeFilterConfigVO>>;
  },

  deleteSizeFilterConfig(id: number) {
    return client.delete(`/platform/ocr-size-filter-configs/${id}`) as Promise<ApiResponse<null>>;
  },

  imageTranslateWorkerConfig() {
    return client.get('/platform/ocr-tasks/image-translate-worker/config') as Promise<ApiResponse<OcrImageTranslateWorkerConfigVO>>;
  },

  updateImageTranslateWorkerConfig(payload: OcrImageTranslateWorkerConfigPayload) {
    return client.put('/platform/ocr-tasks/image-translate-worker/config', payload) as Promise<ApiResponse<OcrImageTranslateWorkerStatusVO>>;
  },

  imageTranslateWorkerStatus() {
    return client.get('/platform/ocr-tasks/image-translate-worker/status') as Promise<ApiResponse<OcrImageTranslateWorkerStatusVO>>;
  },

  startImageTranslateWorker() {
    return client.post('/platform/ocr-tasks/image-translate-worker/start') as Promise<ApiResponse<OcrImageTranslateWorkerStatusVO>>;
  },

  stopImageTranslateWorker() {
    return client.post('/platform/ocr-tasks/image-translate-worker/stop') as Promise<ApiResponse<OcrImageTranslateWorkerStatusVO>>;
  },

  retryImageTranslateWorkerTask(ocrTaskId: number) {
    return client.post(`/platform/ocr-tasks/image-translate-worker/retry/${ocrTaskId}`) as Promise<ApiResponse<OcrImageTranslateWorkerStatusVO>>;
  },

  imageTranslateWorkerLogs(params?: { page?: number; size?: number }) {
    return client.get('/platform/ocr-tasks/image-translate-worker/logs', { params }) as Promise<
      ApiResponse<SpringPage<OcrImageTranslateWorkerLogVO>>
    >;
  },

  listFilterWords(params?: { q?: string; page?: number; size?: number }) {
    return client.get('/platform/ocr-filter-words', { params }) as Promise<ApiResponse<SpringPage<WordVO>>>;
  },

  createFilterWord(word: string) {
    return client.post('/platform/ocr-filter-words', { word }) as Promise<ApiResponse<WordVO>>;
  },

  updateFilterWord(id: number, word: string) {
    return client.put(`/platform/ocr-filter-words/${id}`, { word }) as Promise<ApiResponse<WordVO>>;
  },

  deleteFilterWord(id: number) {
    return client.delete(`/platform/ocr-filter-words/${id}`) as Promise<ApiResponse<null>>;
  },

  listTitleFilterWords(params?: { q?: string; page?: number; size?: number }) {
    return client.get('/platform/title-filter-words', { params }) as Promise<ApiResponse<SpringPage<WordVO>>>;
  },

  createTitleFilterWord(word: string) {
    return client.post('/platform/title-filter-words', { word }) as Promise<ApiResponse<WordVO>>;
  },

  updateTitleFilterWord(id: number, word: string) {
    return client.put(`/platform/title-filter-words/${id}`, { word }) as Promise<ApiResponse<WordVO>>;
  },

  deleteTitleFilterWord(id: number) {
    return client.delete(`/platform/title-filter-words/${id}`) as Promise<ApiResponse<null>>;
  },
};
