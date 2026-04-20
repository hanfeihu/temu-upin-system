import client from '@/api/client';
import type {
  AiVariantPublishDraftResponse,
  AiVariantPublishGenerateDraftRequest,
  AiVariantPublishRawRequest,
  AiVariantPublishRecordDetail,
  AiVariantPublishRecordSummary,
  ApiResponse,
} from '@/types/api';

const AI_VARIANT_PUBLISH_TIMEOUT = 10 * 60 * 1000;

export const aiVariantPublishApi = {
  generateDraftFromProductCollection(payload: AiVariantPublishGenerateDraftRequest) {
    return client.post('/platform/ai-variant-publish/draft/from-product-collection', payload, {
      timeout: AI_VARIANT_PUBLISH_TIMEOUT,
    }) as Promise<ApiResponse<AiVariantPublishDraftResponse>>;
  },

  publishRaw(payload: AiVariantPublishRawRequest) {
    return client.post('/platform/ai-variant-publish/raw', payload, {
      timeout: AI_VARIANT_PUBLISH_TIMEOUT,
    }) as Promise<ApiResponse<AiVariantPublishRecordDetail>>;
  },

  listRecords(params?: { shopRecordId?: number; size?: number }) {
    return client.get('/platform/ai-variant-publish/records', {
      params,
    }) as Promise<ApiResponse<AiVariantPublishRecordSummary[]>>;
  },

  getRecord(id: number) {
    return client.get(`/platform/ai-variant-publish/records/${id}`) as Promise<ApiResponse<AiVariantPublishRecordDetail>>;
  },
};
