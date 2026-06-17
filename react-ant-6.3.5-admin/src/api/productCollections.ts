import client from '@/api/client';
import type {
  ApiResponse,
  ProductCollectionDetailVO,
  ProductCollectionRow,
  ProductCollectionTemuSkuVO,
  SpringPage,
  TemuAttrRuleVO,
  TemuCategoryOption,
  TemuCategorySummary,
  TemuTitleOptimizationResponseVO,
} from '@/types/api';

export interface ProductCollectionListParams {
  id?: number;
  q?: string;
  skuIdKeyword?: string;
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

interface TemuSkuImageUploadResponse {
  originalUrl?: string;
  imageUrl?: string;
  storedUrl?: string;
  sku?: ProductCollectionTemuSkuVO;
}

export interface ProductCollectionUpdatePayload {
  productName?: string;
  collectionStatus?: number;
  collectCount?: number;
  deleted?: boolean;
  companyName?: string;
  companyLocation?: string;
  shippingLocation?: string;
  productCategory?: string;
  originalCategory?: string;
  temuCatid?: string;
  temuCatname?: string;
  temuOptimizedTitleEn?: string;
  temuOptimizedTitleZh?: string;
  temuCategoryKeywords?: string;
  productMainImage?: string;
  productUrl?: string;
  carouselVideo?: string;
  annualSales?: string;
  monthlySales?: string;
  monthlyConsignment?: string;
  moq?: number;
  moqText?: string;
  minPrice?: number;
  maxPrice?: number;
  baseFreight?: number;
  netWeight?: number;
  packagingWeight?: number;
  packagingLength?: number;
  packagingWidth?: number;
  packagingHeight?: number;
  hasSevereInventory?: boolean;
  attributesData?: string;
  skuData?: string;
  skuModel?: string;
  carouselImages?: string;
  carouselThumbImages?: string;
  detailImages?: string;
  customMadeSpecs?: string;
  shippingServicesInfo?: string;
  priceSteps?: string;
  originalContent?: string;
  originalHtml?: string;
  temuAttributes?: string;
  targetShopIds?: string[];
}

export interface ProductCollectionSplitGroupPayload {
  name: string;
  skuRowIds: number[];
}

export interface ProductCollectionSplitPayload {
  groups: ProductCollectionSplitGroupPayload[];
}

export interface ProductCollectionSplitResult {
  sourceSpuId?: number;
  sourceProductName?: string;
  splitCount?: number;
  products?: Array<{
    id?: number;
    productId?: string;
    productName?: string;
    skuCount?: number;
  }>;
}

export interface ProductCollectionTranslateImagePayload {
  imageUrl: string;
  sourceLanguage?: string;
  targetLang?: string;
  provider?: string;
  containDetail?: boolean;
  uploadToOss?: boolean;
  scene?: string;
}

export interface ProductCollectionTranslateImageResult {
  originalUrl?: string;
  uploadedUrl?: string;
  taskId?: string;
  translatedUrl?: string;
  storedUrl?: string;
}

export interface ProductCollectionTranslateAllPayload {
  provider?: string;
}

export interface ProductCollectionBatchImageResult {
  changed?: boolean;
  totalImages?: number;
  changes?: Array<Record<string, unknown>>;
  skuImageChanged?: number;
  provider?: string;
  totalFields?: number;
  uniqueImages?: number;
  successCount?: number;
  failedCount?: number;
  skuChanged?: number;
}

export interface ProductCollectionFuseImagesPayload {
  imageUrls: string[];
  prompt?: string;
  negativePrompt?: string;
  width?: number;
  height?: number;
  strength?: number;
  model?: string;
  seed?: number;
}

export interface ProductCollectionFuseImagesResult {
  imageUrl?: string;
  finishReason?: string;
  seed?: number;
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

  update(id: number, payload: ProductCollectionUpdatePayload) {
    return client.put(`/platform/product-collections/${id}`, payload) as Promise<ApiResponse<unknown>>;
  },

  splitProduct(id: number, payload: ProductCollectionSplitPayload) {
    return client.post(`/platform/product-collections/${id}/split`, payload, { timeout: 240000 }) as Promise<
      ApiResponse<ProductCollectionSplitResult>
    >;
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

  getTemuCategoryAttributes(id: number) {
    return client.post(`/platform/product-collections/${id}/temu-category/attributes`) as Promise<ApiResponse<string>>;
  },

  saveTemuAttributes(id: number, temuAttributes: Record<string, unknown>) {
    return client.post(`/platform/product-collections/${id}/temu-attributes/save`, { temuAttributes }) as Promise<
      ApiResponse<null>
    >;
  },

  aiFillTemuAttributes(id: number) {
    return client.post(`/platform/product-collections/${id}/temu-attributes/ai-fill`, null, {
      timeout: 240000,
    }) as Promise<ApiResponse<Record<string, unknown>>>;
  },

  listTemuSkus(id: number) {
    return client.get(`/platform/product-collections/${id}/temu/skus`) as Promise<
      ApiResponse<ProductCollectionTemuSkuVO[]>
    >;
  },

  initTemuSkus(id: number, force = false) {
    return client.post(`/platform/product-collections/${id}/temu/skus/init`, { force }, { timeout: 240000 }) as Promise<
      ApiResponse<ProductCollectionTemuSkuVO[]>
    >;
  },

  saveTemuSkus(id: number, skus: ProductCollectionTemuSkuVO[]) {
    return client.post(`/platform/product-collections/${id}/temu/skus/save`, { skus }, { timeout: 240000 }) as Promise<
      ApiResponse<ProductCollectionTemuSkuVO[]>
    >;
  },

  uploadTemuSkuImage(id: number, imageUrl: string) {
    return client.post(`/platform/product-collections/${id}/temu/skus/image/upload`, { imageUrl }, { timeout: 240000 }) as Promise<
      ApiResponse<TemuSkuImageUploadResponse>
    >;
  },

  uploadTemuSkuImageFile(id: number, skuId: number, file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return client.post(`/platform/product-collections/${id}/temu/skus/${skuId}/image/file`, formData, {
      timeout: 240000,
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    }) as Promise<ApiResponse<TemuSkuImageUploadResponse>>;
  },

  listTemuAttrRules(params: { enabled?: boolean; leafCatId?: string } = {}) {
    return client.get('/platform/temu-attr-rules', { params }) as Promise<ApiResponse<TemuAttrRuleVO[]>>;
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

  replaceImagesToKwcdn(id: number) {
    return client.post(`/platform/product-collections/${id}/temu/images/kwcdn-replace`, null, {
      timeout: 600000,
    }) as Promise<ApiResponse<ProductCollectionBatchImageResult>>;
  },

  normalizeAllImagesTo800(id: number) {
    return client.post(`/platform/product-collections/${id}/temu/images/normalize-800`, null, {
      timeout: 600000,
    }) as Promise<ApiResponse<ProductCollectionBatchImageResult>>;
  },

  translateAllImages(id: number, payload: ProductCollectionTranslateAllPayload = {}) {
    return client.post(`/platform/product-collections/${id}/images/translate-all`, payload, {
      timeout: 600000,
    }) as Promise<ApiResponse<ProductCollectionBatchImageResult>>;
  },

  translateImage(id: number, payload: ProductCollectionTranslateImagePayload) {
    return client.post(`/platform/product-collections/${id}/image/translate`, payload, {
      timeout: 240000,
    }) as Promise<ApiResponse<ProductCollectionTranslateImageResult>>;
  },

  fuseImages(id: number, payload: ProductCollectionFuseImagesPayload) {
    return client.post(`/platform/product-collections/${id}/images/fuse`, payload, {
      timeout: 600000,
    }) as Promise<ApiResponse<ProductCollectionFuseImagesResult>>;
  },

  requeuePostImportTask(id: number, force = false) {
    return client.post(`/platform/product-collections/${id}/post-import/requeue?force=${force ? 'true' : 'false'}`) as Promise<
      ApiResponse<null>
    >;
  },
};
