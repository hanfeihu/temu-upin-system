import client from '@/api/client';
import type {
  ApiResponse,
  ProductCollectionDetailVO,
  ProductCollectionRow,
  SpringPage,
  TemuCategoryOption,
  TemuCategorySummary,
  TemuTitleOptimizationResponseVO,
} from '@/types/api';

export interface ProductCollectionListParams {
  q?: string;
  sourcePlatform?: string;
  targetShopId?: string;
  collectionStatus?: number;
  showDeleted?: boolean;
  temuCatid?: string;
  moqMin?: number;
  moqMax?: number;
  carouselImageCountMin?: number;
  carouselImageCountMax?: number;
  detailImageCountMin?: number;
  detailImageCountMax?: number;
  skuCountMin?: number;
  skuCountMax?: number;
  page?: number;
  size?: number;
}

interface MatchCategoryResponse {
  options: TemuCategoryOption[];
}

interface PublishToTemuResponse {
  runId?: number;
  goodsId?: string;
}

export const productCollectionsApi = {
  list(params: ProductCollectionListParams) {
    return client.get('/platform/product-collections', {
      params,
    }) as Promise<ApiResponse<SpringPage<ProductCollectionRow>>>;
  },

  listTemuCategories(params?: { showDeleted?: boolean }) {
    return client.get('/platform/product-collections/temu-categories', {
      params,
    }) as Promise<ApiResponse<TemuCategorySummary[]>>;
  },

  get(id: number) {
    return client.get(`/platform/product-collections/${id}`) as Promise<ApiResponse<ProductCollectionDetailVO>>;
  },

  update(id: number, payload: { productName: string }) {
    return client.put(`/platform/product-collections/${id}`, payload) as Promise<ApiResponse<unknown>>;
  },

  delete(id: number) {
    return client.delete(`/platform/product-collections/${id}`) as Promise<ApiResponse<null>>;
  },

  matchTemuCategory(id: number) {
    return client.post(`/platform/product-collections/${id}/temu-category/match`) as Promise<
      ApiResponse<MatchCategoryResponse>
    >;
  },

  saveTemuCategory(id: number, payload: { temuCatid: string; temuCatname: string }) {
    return client.post(`/platform/product-collections/${id}/temu-category/save`, payload) as Promise<
      ApiResponse<null>
    >;
  },

  generateTemuTitleOptimization(id: number) {
    return client.post(`/platform/product-collections/${id}/temu-title-optimizer/generate`) as Promise<
      ApiResponse<TemuTitleOptimizationResponseVO>
    >;
  },

  publishToTemu(id: number) {
    return client.post(`/platform/product-collections/${id}/temu/publish`) as Promise<
      ApiResponse<PublishToTemuResponse>
    >;
  },

  requeuePostImportTask(id: number, force = false) {
    return client.post(`/platform/product-collections/${id}/post-import/requeue?force=${force ? 'true' : 'false'}`) as Promise<
      ApiResponse<null>
    >;
  },
};
