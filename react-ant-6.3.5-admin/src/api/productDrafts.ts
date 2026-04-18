import client from '@/api/client';
import type {
  ApiResponse,
  ProductDraftDetailVO,
  ProductDraftImportPayload,
  ProductDraftListItem,
  ProductDraftPushResponse,
  ProductDraftUpdatePayload,
  SpringPage,
} from '@/types/api';

export interface ProductDraftListParams {
  q?: string;
  sourcePlatform?: string;
  targetShopId?: string;
  pushedToCollection?: boolean;
  showDeleted?: boolean;
  page?: number;
  size?: number;
}

export const productDraftsApi = {
  list(params: ProductDraftListParams) {
    return client.get('/platform/product-drafts', {
      params,
    }) as Promise<ApiResponse<SpringPage<ProductDraftListItem>>>;
  },

  get(id: number) {
    return client.get(`/platform/product-drafts/${id}`) as Promise<ApiResponse<ProductDraftDetailVO>>;
  },

  importDraft(payload: ProductDraftImportPayload) {
    return client.post('/platform/product-drafts/import', payload) as Promise<ApiResponse<ProductDraftDetailVO>>;
  },

  update(id: number, payload: ProductDraftUpdatePayload) {
    return client.put(`/platform/product-drafts/${id}`, payload) as Promise<ApiResponse<ProductDraftDetailVO>>;
  },

  delete(id: number) {
    return client.delete(`/platform/product-drafts/${id}`) as Promise<ApiResponse<null>>;
  },

  pushToCollection(id: number) {
    return client.post(`/platform/product-drafts/${id}/push-to-collection`) as Promise<
      ApiResponse<ProductDraftPushResponse>
    >;
  },
};
