export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface CurrentUserVO {
  username: string;
  displayName: string;
  expiresAt: number | null;
}

export interface ProductCollectionRow {
  id: number;
  productId: string;
  productName: string;
  sourcePlatform: string;
  collectionStatus: number | null;
  execStatus: number | null;
  execResult: string | null;
  lastPublishRunId: number | null;
  targetShopIds: string[];
  targetShopNames: string[];
  productMainImage: string | null;
  temuCatid: string | null;
  temuCatname: string | null;
  temuPublished: boolean | null;
  temuGoodsId: string | null;
  minPrice: number | null;
  maxPrice: number | null;
  ocrStatus: number | null;
  carouselImageCount: number | null;
  detailImageCount: number | null;
  skuCount: number | null;
  moq: number | null;
  moqText: string | null;
  netWeight: number | null;
  packagingWeight: number | null;
  deleted: boolean | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface ProductCollectionDetailVO {
  id: number;
  productId: string;
  productName: string;
  sourcePlatform: string;
  temuCatid: string | null;
  temuCatname: string | null;
  temuAttributes: string | null;
  targetShopIds: string[];
  targetShopNames: string[];
}

export interface TemuCategoryOption {
  leafId: string | number;
  leafName: string;
  pathIds?: string;
  pathNames?: string;
  pathText?: string;
}

export interface TemuCategorySummary {
  temuCatid: string;
  temuCatname: string;
}

export interface PublishRunVO {
  id: number;
  spuId: number;
  status: string;
  goodsId: string | null;
  requestJson: string | null;
  responseRaw: string | null;
  error: string | null;
  startedAt: string | number[] | null;
  finishedAt: string | number[] | null;
}

export interface PublishLogVO {
  id: number;
  runId: number;
  stage: string;
  level: string;
  message: string | null;
  dataJson: string | null;
  createdAt: string | number[] | null;
}

export interface TemuAppVO {
  id: number;
  enabled: boolean;
  appName: string;
  appKey: string;
  appSecretMasked: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface TemuShopVO {
  id: number;
  enabled: boolean;
  shopName: string;
  shopId: string;
  tokenMasked: string | null;
  appId: number;
  appName: string;
  siteId: number;
  warehouseId: string;
  defaultStock: number;
  maxStock: number;
  originRegion1ShortName: string;
  originRegion2Id: number;
  freightTemplateId: string;
  shipmentLimitSecond: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface TemuShopPayload {
  shopName: string;
  shopId: string;
  token?: string;
  appId: number | null;
  enabled: boolean;
  siteId: number | null;
  warehouseId: string;
  defaultStock: number | null;
  maxStock: number | null;
  originRegion1ShortName: string;
  originRegion2Id: number | null;
  freightTemplateId: string;
  shipmentLimitSecond: number | null;
}

export interface ProductDraftListItem {
  id: number;
  sourcePlatform: string;
  productId: string;
  productName: string;
  productCategory: string | null;
  originalCategory: string | null;
  productMainImage: string | null;
  productUrl: string | null;
  sourceUrl: string | null;
  monthlySales: string | null;
  reviewCount: number | null;
  companyName: string | null;
  targetShopIds: string[];
  targetShopNames: string[];
  pushedToCollection: boolean;
  pushedCollectionId: number | null;
  pushedAt: string | null;
  pushMessage: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface ProductDraftDetailVO extends ProductDraftListItem {
  originalHtml: string | null;
  extractedJson: string | null;
  parserSnapshotJson: string | null;
}

export interface ProductDraftImportPayload {
  html: string;
  extractedJson?: string;
  targetShopIds?: string[];
}

export interface ProductDraftUpdatePayload {
  productName: string;
  productCategory: string;
  originalCategory: string;
  productMainImage: string;
  productUrl: string;
  sourceUrl: string;
  monthlySales: string;
  reviewCount: number | null;
  companyName: string;
}

export interface ProductDraftPushResponse {
  draftId: number;
  collectionId: number;
  productId: string;
  message: string;
}
