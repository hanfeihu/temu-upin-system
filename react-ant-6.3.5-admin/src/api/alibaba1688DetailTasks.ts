import client from '@/api/client';
import type {
  Alibaba1688DetailTaskCreatePayload,
  Alibaba1688DetailTaskCreateResultVO,
  Alibaba1688DetailTaskImportFromCardLinksPayload,
  Alibaba1688DetailTaskImportFromCardLinksResultVO,
  Alibaba1688DetailTaskVO,
  ApiResponse,
  SpringPage,
} from '@/types/api';

export const alibaba1688DetailTasksApi = {
  list(params: {
    keyword?: string;
    credentialId?: number;
    status?: string;
    page?: number;
    size?: number;
  }) {
    return client.get('/platform/alibaba1688-detail-tasks', { params }) as Promise<ApiResponse<SpringPage<Alibaba1688DetailTaskVO>>>;
  },

  createDialog(payload: Alibaba1688DetailTaskCreatePayload) {
    return client.post('/platform/alibaba1688-detail-tasks/dialog-create', payload) as Promise<ApiResponse<Alibaba1688DetailTaskCreateResultVO>>;
  },

  importFromCardLinks(payload: Alibaba1688DetailTaskImportFromCardLinksPayload) {
    return client.post('/platform/alibaba1688-detail-tasks/import-from-card-links', payload) as Promise<
      ApiResponse<Alibaba1688DetailTaskImportFromCardLinksResultVO>
    >;
  },

  retry(id: number) {
    return client.post(`/platform/alibaba1688-detail-tasks/${id}/retry`) as Promise<ApiResponse<Alibaba1688DetailTaskVO>>;
  },

  cancel(id: number) {
    return client.post(`/platform/alibaba1688-detail-tasks/${id}/cancel`) as Promise<ApiResponse<Alibaba1688DetailTaskVO>>;
  },
};
