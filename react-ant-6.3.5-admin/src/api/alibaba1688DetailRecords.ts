import client from '@/api/client';
import type {
  Alibaba1688DetailRecordDetailVO,
  Alibaba1688DetailRecordVO,
  ApiResponse,
  SpringPage,
} from '@/types/api';

export const alibaba1688DetailRecordsApi = {
  list(params: {
    keyword?: string;
    credentialId?: number;
    recordId?: number;
    status?: string;
    excludeImported?: boolean;
    page?: number;
    size?: number;
  }) {
    return client.get('/platform/alibaba1688-detail-records', { params }) as Promise<ApiResponse<SpringPage<Alibaba1688DetailRecordVO>>>;
  },

  detail(id: number) {
    return client.get(`/platform/alibaba1688-detail-records/${id}`) as Promise<ApiResponse<Alibaba1688DetailRecordDetailVO>>;
  },
};
