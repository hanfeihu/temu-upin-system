import client from '@/api/client';
import type { ApiResponse, ImageTranslateRecordVO, SpringPage } from '@/types/api';

export const imageTranslateRecordsApi = {
  list(params: {
    spuId?: number;
    provider?: string;
    sourceUrl?: string;
    status?: string;
    page?: number;
    size?: number;
  }) {
    return client.get('/platform/image-translate-records', { params }) as Promise<ApiResponse<SpringPage<ImageTranslateRecordVO>>>;
  },
};
