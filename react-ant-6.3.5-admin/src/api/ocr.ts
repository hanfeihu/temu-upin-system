import client from '@/api/client';
import type { ApiResponse, OcrTaskPayload, OcrTaskVO, SpringPage, WordVO } from '@/types/api';

export const ocrApi = {
  listTasks(params: {
    spuId?: number;
    productId?: string;
    imageType?: number;
    execStatus?: number;
    filtered?: boolean;
    containsChinese?: boolean;
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

  listFilterWords() {
    return client.get('/platform/ocr-filter-words') as Promise<ApiResponse<WordVO[]>>;
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

  listTitleFilterWords() {
    return client.get('/platform/title-filter-words') as Promise<ApiResponse<WordVO[]>>;
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
