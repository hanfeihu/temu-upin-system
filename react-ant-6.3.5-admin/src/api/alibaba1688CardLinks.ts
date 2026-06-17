import client from '@/api/client';
import type { Alibaba1688CardLinkVO, ApiResponse, SpringPage } from '@/types/api';

export const alibaba1688CardLinksApi = {
  list(params: {
    keyword?: string;
    type?: string;
    status?: number;
    page?: number;
    size?: number;
  }) {
    return client.get('/platform/alibaba1688-card-links', {
      params,
    }) as Promise<ApiResponse<SpringPage<Alibaba1688CardLinkVO>>>;
  },

  updateStatus(id: number, status: number) {
    return client.put(`/platform/alibaba1688-card-links/${id}/status`, { status }) as Promise<ApiResponse<Alibaba1688CardLinkVO>>;
  },
};
