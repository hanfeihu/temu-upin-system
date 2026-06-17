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

export interface TemuSitePublishExceptionVO {
  id: number;
  skc: string;
  offerId: string | null;
  productCollectionId: number | null;
  temuSpuId: string | null;
  temuGoodsId: string | null;
  goodsNo: string | null;
  siteName: string | null;
  statusText: string | null;
  productTitle: string | null;
  categoryText: string | null;
  declaredPrice: number | null;
  reasonText: string | null;
  reasonType: string | null;
  operatorName: string | null;
  createdTimeText: string | null;
  priceConfirmTimeText: string | null;
  joinedSiteTimeText: string | null;
  sourceUrl: string | null;
  sourceTitle: string | null;
  sourceCollectedAt: string | number[] | null;
  sourceFileName: string | null;
  rowText: string | null;
  detailText: string | null;
  active: boolean | null;
  remark: string | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface TemuSitePublishExceptionImportResult {
  totalRecords: number;
  imported: number;
  created: number;
  updated: number;
  skipped: number;
  skippedReasons: string[];
}

export interface SupplierProductSubmissionVO {
  id: number;
  supplierName: string;
  supplierPhone: string | null;
  supplierAddress: string | null;
  productName: string;
  supplyPrice: number | null;
  weightG: number | null;
  lengthCm: number | null;
  widthCm: number | null;
  heightCm: number | null;
  imageUrls: string[];
  status: string;
  remark: string | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface SupplierProductSubmissionPayload {
  supplierName: string;
  supplierPhone?: string;
  supplierAddress?: string;
  productName: string;
  supplyPrice?: number;
  weightG?: number;
  lengthCm?: number;
  widthCm?: number;
  heightCm?: number;
  imageUrls: string[];
  status?: string;
  remark?: string;
}

export interface SupplierProductPackageVO {
  id: number;
  submissionId: number;
  supplierName: string | null;
  productName: string | null;
  remark: string | null;
  supplyPrice: number | null;
  weightG: number | null;
  lengthCm: number | null;
  widthCm: number | null;
  heightCm: number | null;
  sourceImageUrls: string[];
  aiTitle: string | null;
  aiTitleZh: string | null;
  generatedImageUrls: string[];
  status: string;
  lastError: string | null;
  productCollectionId: number | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface UploadImageResult {
  url: string;
  filename: string;
  originalName: string;
  size: string;
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
  chineseImageCount: number | null;
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
  createdAt: string | number[] | null;
  createdBy: number | null;
  deleted: boolean | null;
  updatedAt: string | number[] | null;
  updatedBy: number | null;
  version: number | null;
  annualSales: string | null;
  attributesData: string | null;
  carouselImages: string | null;
  collectCount: number | null;
  collectionStatus: number | null;
  execStatus: number | null;
  execResult: string | null;
  lastPublishRunId: number | null;
  collectionTime: string | number[] | null;
  companyLocation: string | null;
  companyName: string | null;
  productId: string;
  detailImages: string | null;
  hasSevereInventory: boolean | null;
  maxPrice: number | null;
  minPrice: number | null;
  monthlyConsignment: string | null;
  monthlySales: string | null;
  moq: number | null;
  netWeight: number | null;
  originalCategory: string | null;
  originalContent: string | null;
  packagingDimensions: string | null;
  packagingHeight: number | null;
  packagingLength: number | null;
  packagingWeight: number | null;
  packagingWidth: number | null;
  productCategory: string | null;
  productMainImage: string | null;
  productName: string;
  productUrl: string | null;
  ratingScore: number | null;
  repeatCustomerRate: number | null;
  reviewCount: number | null;
  serviceScore: number | null;
  shippingLocation: string | null;
  skuData: string | null;
  skuModel: string | null;
  sourcePlatform: string;
  temuCatid: string | null;
  temuCatname: string | null;
  temuAttributes: string | null;
  temuOptimizedTitleEn: string | null;
  temuOptimizedTitleZh: string | null;
  temuCategoryKeywords: string | null;
  temuPublished: boolean | null;
  temuGoodsId: string | null;
  temuPublishedAt: string | number[] | null;
  lastPublishStatus: string | null;
  lastPublishGoodsId: string | null;
  lastPublishRequestJson: string | null;
  lastPublishResponseRaw: string | null;
  lastPublishError: string | null;
  lastPublishStartedAt: string | number[] | null;
  lastPublishFinishedAt: string | number[] | null;
  carouselThumbImages: string | null;
  carouselVideo: string | null;
  baseFreight: number | null;
  customMadeSpecs: string | null;
  shippingServicesInfo: string | null;
  moqText: string | null;
  priceSteps: string | null;
  alibabaProductId: string | null;
  originalHtml: string | null;
  targetShopIds: string[];
  targetShopNames: string[];
  temuSkus: ProductCollectionTemuSkuVO[];
  skuPropsExt: ProductCollectionSkuPropVO[];
  skuRows: ProductCollectionSkuRowVO[];
}

export interface ProductCollectionSkuPropValueVO {
  id: number;
  value: string | null;
  image: string | null;
  sort: number | null;
}

export interface ProductCollectionSkuPropVO {
  id: number;
  fid: number | null;
  name: string | null;
  sort: number | null;
  values: ProductCollectionSkuPropValueVO[];
}

export interface ProductCollectionSkuRowVO {
  id: number;
  skuId: string | null;
  specKey: string | null;
  specJson: string | null;
  stock: number | null;
  price: number | null;
  image: string | null;
}

export interface ProductCollectionTemuSkuVO {
  id: number | null;
  temuSkuId: string | null;
  originSkuId: string | null;
  specKey: string | null;
  specJson: string | null;
  image: string | null;
  originPrice: number | null;
  supplyPrice: number | null;
  weightG: number | null;
  lengthCm: number | null;
  widthCm: number | null;
  heightCm: number | null;
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
  appType: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface TemuAppPayload {
  appName: string;
  appKey: string;
  appSecret?: string;
  appType?: string;
  enabled: boolean;
}

export interface TemuShopVO {
  id: number;
  enabled: boolean;
  shopName: string;
  shopId: string;
  productTokenMasked: string | null;
  orderTokenMasked: string | null;
  productAppId: number | null;
  productAppName: string | null;
  orderAppId: number | null;
  orderAppName: string | null;
  dianxiaomiCookieMasked: string | null;
  dianxiaomiShopId: string | null;
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
  orderToken?: string;
  dianxiaomiCookie?: string;
  dianxiaomiShopId?: string;
  appId: number | null;
  orderAppId?: number | null;
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

export interface AiVariantPublishRawRequest {
  shopRecordId: number | null;
  sourceType: string;
  sourceBizType?: string | null;
  sourceBizId?: number | null;
  sourceBizName?: string | null;
  sourceNote: string;
  requestPayload: string;
}

export interface AiVariantPublishGenerateDraftRequest {
  shopRecordId: number | null;
  sourceSpuId: number | null;
}

export interface AiVariantPublishSkuPreview {
  skcExtCode: string | null;
  skuExtCode: string | null;
  thumbUrl: string | null;
  supplierPriceText: string | null;
  siteSupplierPriceText: string | null;
  specNames: string[];
}

export interface AiVariantPublishPreview {
  productName: string | null;
  mainImageUrl: string | null;
  carouselImageUrls: string[];
  detailImageUrls: string[];
  skcCount: number;
  skuCount: number;
  skuList: AiVariantPublishSkuPreview[];
}

export interface AiVariantPublishRecordSummary {
  id: number;
  shopRecordId: number;
  shopId: string | null;
  shopName: string | null;
  sourceType: string | null;
  sourceBizType: string | null;
  sourceBizId: number | null;
  sourceBizName: string | null;
  sourceNote: string | null;
  status: string | null;
  goodsId: string | null;
  errorMessage: string | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
  preview: AiVariantPublishPreview | null;
}

export interface AiVariantPublishRecordDetail {
  id: number;
  shopRecordId: number;
  shopId: string | null;
  shopName: string | null;
  sourceType: string | null;
  sourceBizType: string | null;
  sourceBizId: number | null;
  sourceBizName: string | null;
  sourceNote: string | null;
  status: string | null;
  goodsId: string | null;
  errorMessage: string | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
  requestPayload: string | null;
  responsePayload: string | null;
  responseSuccess: boolean | null;
  responseErrorCode: number | null;
  responseErrorMsg: string | null;
  requestId: string | null;
  preview: AiVariantPublishPreview | null;
}

export interface AiVariantPublishDraftResponse {
  shopRecordId: number;
  shopId: string | null;
  shopName: string | null;
  sourceSpuId: number;
  sourceProductName: string | null;
  sourceProductMainImage: string | null;
  sourceType: string | null;
  sourceBizType: string | null;
  sourceBizId: number | null;
  sourceBizName: string | null;
  sourceNote: string | null;
  requestPayload: string;
  warnings: string[];
  preview: AiVariantPublishPreview | null;
}

export interface TemuShopFreightTemplateOption {
  freightTemplateId: string;
  templateName: string | null;
}

export interface TemuOrderLogisticsVO {
  providerCode: string | null;
  providerName: string | null;
  referenceNo: string | null;
  shippingMethodNo: string | null;
  trackingNumber: string | null;
  destinationCountry: string | null;
  trackStatus: string | null;
  trackStatusName: string | null;
  grossWeight: number | null;
  volumeWeight: number | null;
  chargeWeight: number | null;
  firstLegLogisticsFee: number | null;
  trackDetailsJson: string | null;
  orderFeeDetailJson: string | null;
  orderWeightInfoJson: string | null;
  lastSyncedAt: string | number[] | null;
}

export interface TemuOrderVO {
  id: number;
  shopRecordId: number;
  shopId: string;
  shopName: string | null;
  orderSn: string;
  parentOrderSn: string | null;
  dianxiaomiPackageNumber: string | null;
  goodsId: string | null;
  goodsName: string | null;
  spec: string | null;
  thumbUrl: string | null;
  quantity: number | null;
  orderStatus: number | null;
  parentOrderStatus: number | null;
  orderPaymentType: string | null;
  orderTimeMs: number | null;
  updateTimeMs: number | null;
  matchedSpuId: number | null;
  matchedTemuSkuId: string | null;
  matchedOriginSkuId: string | null;
  matchedSkuSpecName: string | null;
  matchedProductName: string | null;
  matchedSupplyPrice: number | null;
  purchasePrice: number | null;
  matchedFirstLegLogisticsFee: number | null;
  salesQuantity: number | null;
  aftersaleQuantity: number | null;
  aftersaleRate: number | null;
  signedQuantity: number | null;
  signedAftersaleQuantity: number | null;
  matchStatus: string | null;
  matchMessage: string | null;
  logisticsTrackingNumber: string | null;
  logisticsTrackStatusName: string | null;
  firstLegLogisticsFee: number | null;
  chargeWeight: number | null;
  orderFeeDetailJson: string | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface TemuOrderDetailVO extends TemuOrderVO {
  inventoryDeductionWarehouseId: string | null;
  inventoryDeductionWarehouseName: string | null;
  earliestTimeGetShippingDocumentMs: number | null;
  expectShipLatestTimeMs: number | null;
  regionId: number | null;
  siteId: number | null;
  productSkusJson: string | null;
  rawJson: string | null;
  logistics: TemuOrderLogisticsVO | null;
}

export interface TemuOrderSyncPayload {
  shopRecordId?: number | null;
  fullSync?: boolean;
  hoursBack?: number | null;
  pageSize?: number | null;
  maxPages?: number | null;
}

export interface TemuOrderSyncResultVO {
  shopRecordId: number;
  shopId: string;
  shopName: string | null;
  success: boolean;
  totalCount: number;
  createdCount: number;
  updatedCount: number;
  matchedCount: number;
  logisticsRefreshedCount: number;
  message: string | null;
}

export interface TemuOrderDashboardDailyValueVO {
  date: string;
  value: number;
}

export interface TemuOrderDashboardDailyCompareValueVO {
  date: string;
  signedValue: number;
  aftersaleValue: number;
}

export interface TemuOrderDashboardWindowVO {
  startDate: string;
  endDate: string;
  days: number;
  label: string;
}

export interface TemuOrderDashboardTotalsVO {
  recentChildOrderCount: number;
  recentQuantity: number;
  historicalSignedParentCount: number;
  historicalAftersaleParentCount: number;
  todayProfit: number;
  todaySalesAmount: number;
  todayQuantity: number;
  todayDistinctSkuCount: number;
  todayParentOrderCount: number;
}

export interface TemuOrderDashboardResponseVO {
  shop: {
    shopRecordId: number;
    shopId: string;
    shopName: string;
  };
  recent30Window: TemuOrderDashboardWindowVO;
  historicalWindow: TemuOrderDashboardWindowVO;
  totals: TemuOrderDashboardTotalsVO;
  recentOrderCountSeries: TemuOrderDashboardDailyValueVO[];
  recentQuantitySeries: TemuOrderDashboardDailyValueVO[];
  recentDistinctSkuSeries: TemuOrderDashboardDailyValueVO[];
  historicalSignedAftersaleSeries: TemuOrderDashboardDailyCompareValueVO[];
}

export interface ProductDashboardDailyValueVO {
  date: string;
  value: number;
}

export interface ProductDashboardWindowVO {
  startDate: string;
  endDate: string;
  days: number;
  label: string;
}

export interface ProductDashboardTotalsVO {
  totalCollectedCount: number;
  totalPublishedCount: number;
  totalAddedSiteCount: number;
  recentPublishedCount: number;
  recentAddedSiteCount: number;
  todayPublishedCount: number;
  todayAddedSiteCount: number;
}

export interface ProductDashboardResponseVO {
  recent30Window: ProductDashboardWindowVO;
  totals: ProductDashboardTotalsVO;
  recentPublishSuccessSeries: ProductDashboardDailyValueVO[];
  recentPriceReviewApproveSeries: ProductDashboardDailyValueVO[];
  recentPriceReviewRejectSeries: ProductDashboardDailyValueVO[];
  recentPublishedProductPriceReviewApprovedSeries: ProductDashboardDailyValueVO[];
  recentPublishedProductPriceReviewRejectedSeries: ProductDashboardDailyValueVO[];
  recentPublishedProductPriceReviewUnreviewedSeries: ProductDashboardDailyValueVO[];
  recentPublishedProductAddedSiteSeries: ProductDashboardDailyValueVO[];
}

export interface TemuOrderLogisticsRefreshPayload {
  providerCode?: string;
  referenceNo?: string;
  shippingMethodNo?: string;
  trackingNumber?: string;
}

export interface TemuOrderAftersaleVO {
  id: number;
  shopRecordId: number;
  shopId: string;
  shopName: string | null;
  parentAfterSalesSn: string;
  parentOrderSn: string | null;
  afterSalesStatusGroup: number | null;
  afterSalesStatusGroupName: string | null;
  parentAfterSalesStatus: number | null;
  parentAfterSalesStatusName: string | null;
  afterSalesType: number | null;
  afterSalesTypeName: string | null;
  createAtMs: number | null;
  updateAtMs: number | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface TemuOrderAftersaleSyncPayload {
  shopRecordId?: number | null;
  fullSync?: boolean;
  hoursBack?: number | null;
}

export interface TemuOrderAftersaleSyncResultVO {
  shopRecordId: number;
  shopId: string;
  shopName: string | null;
  success: boolean;
  totalCount: number;
  createdCount: number;
  updatedCount: number;
  message: string | null;
}

export interface LogisticsProviderConfigVO {
  id: number;
  providerCode: string;
  providerName: string;
  enabled: boolean;
  baseUrl: string | null;
  appTokenMasked: string | null;
  appKeyMasked: string | null;
  connectTimeoutMs: number | null;
  readTimeoutMs: number | null;
  extraConfigJson: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface LogisticsProviderConfigPayload {
  providerCode: string;
  providerName: string;
  enabled: boolean;
  baseUrl?: string;
  appToken?: string;
  appKey?: string;
  connectTimeoutMs?: number | null;
  readTimeoutMs?: number | null;
  extraConfigJson?: string;
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

export interface TemuAutoPublishRunVO {
  id: number;
  spuId: number;
  status: string;
  action: string;
  publishRunId: number | null;
  eligibilityJson: string | null;
  summary: string | null;
  error: string | null;
  startedAt: string | number[] | null;
  finishedAt: string | number[] | null;
}

export interface WorkerStatusVO {
  enabled: boolean;
  running: boolean;
  pollMs: number;
  lastTickAt?: string | number[] | null;
  lastClaimAt?: string | number[] | null;
  lastError?: string | null;
}

export interface Alibaba1688SelectionPoolAiReportWorkerConfigVO {
  id?: number | null;
  configName?: string | null;
  threadCount: number | null;
  configuredThreads?: number | null;
  enabled?: boolean | null;
  pollIntervalMs: number | null;
  pollMs?: number | null;
  remark?: string | null;
  createdAt?: string | number[] | null;
  updatedAt?: string | number[] | null;
}

export interface Alibaba1688SelectionPoolAiReportWorkerConfigPayload {
  threadCount: number;
}

export interface Alibaba1688SelectionPoolAiReportWorkerStatusVO {
  running: boolean | null;
  configuredThreads: number | null;
  configuredThreadCount?: number | null;
  threadCount?: number | null;
  pollIntervalMs: number | null;
  pollMs?: number | null;
  pendingCount: number | null;
  processingCount: number | null;
  runningCount?: number | null;
  completedCount: number | null;
  readyCount?: number | null;
  failedCount: number | null;
  activeThreads: number | null;
  activeThreadCount?: number | null;
  aliveThreadCount?: number | null;
  hasStuckThreads: boolean | null;
  stuckThreadCount?: number | null;
  lastCheckedAt: string | number[] | null;
  lastCheckAt?: string | number[] | null;
  lastScanAt?: string | number[] | null;
  lastWorkAt?: string | number[] | null;
  lastErrorAt?: string | number[] | null;
  lastError: string | null;
  threads?: Array<{
    workerIndex?: number | null;
    threadName?: string | null;
    alive?: boolean | null;
    working?: boolean | null;
    stuck?: boolean | null;
    currentPoolId?: number | null;
    currentOfferId?: string | null;
    lastHeartbeatAt?: string | number[] | null;
    currentTaskStartedAt?: string | number[] | null;
    lastFinishedAt?: string | number[] | null;
    successCount?: number | null;
    failureCount?: number | null;
    lastError?: string | null;
  }> | null;
  recentTaskEvents?: Array<{
    sequence?: number | null;
    workerIndex?: number | null;
    threadName?: string | null;
    poolId?: number | null;
    offerId?: string | null;
    status?: string | null;
    startedAt?: string | number[] | null;
    finishedAt?: string | number[] | null;
    durationMs?: number | null;
    error?: string | null;
  }> | null;
}

export interface Alibaba1688SelectionPoolAutoPushConfigVO {
  id?: number | null;
  configName?: string | null;
  enabled?: boolean | null;
  targetShopIds?: string[] | null;
  targetShopNames?: string[] | null;
  batchSize?: number | null;
  pollMs?: number | null;
  forceCreate?: boolean | null;
  remark?: string | null;
  createdAt?: string | number[] | null;
  updatedAt?: string | number[] | null;
}

export interface Alibaba1688SelectionPoolAutoPushConfigPayload {
  configName?: string | null;
  targetShopIds?: string[] | null;
  batchSize?: number | null;
  pollMs?: number | null;
  forceCreate?: boolean | null;
  remark?: string | null;
}

export interface Alibaba1688SelectionPoolAutoPushStatusVO {
  running?: boolean | null;
  enabled?: boolean | null;
  batchSize?: number | null;
  pollMs?: number | null;
  pendingCount?: number | null;
  successCount?: number | null;
  failureCount?: number | null;
  skippedCount?: number | null;
  lastPoolId?: number | null;
  lastOfferId?: string | null;
  startedAt?: string | number[] | null;
  stoppedAt?: string | number[] | null;
  lastScanAt?: string | number[] | null;
  lastWorkAt?: string | number[] | null;
  lastErrorAt?: string | number[] | null;
  lastError?: string | null;
}

export interface Alibaba1688SelectionPoolAutoPushLogVO {
  id: number;
  poolId?: number | null;
  offerId?: string | null;
  productCollectionId?: number | null;
  status?: string | null;
  targetShopIds?: string[] | null;
  targetShopNames?: string[] | null;
  message?: string | null;
  errorMessage?: string | null;
  startedAt?: string | number[] | null;
  finishedAt?: string | number[] | null;
  durationMs?: number | null;
  createdAt?: string | number[] | null;
}

export interface TemuAutoPublishSampleVO {
  runId?: number;
  spuId?: number;
  status?: string;
  action?: string;
  publishRunId?: number | null;
  eligibilityJson?: string | null;
  summary?: string | null;
  error?: string | null;
  startedAt?: string | number[] | null;
  finishedAt?: string | number[] | null;
}

export interface PostImportRunVO {
  id: number;
  spuId: number;
  status: string;
  summary: string | null;
  error: string | null;
  sampleJson: string | null;
  startedAt: string | number[] | null;
  finishedAt: string | number[] | null;
}

export interface SuccessCaseRowVO {
  id: number;
  publishRunId: number | null;
  spuId: number;
  productId: string | null;
  productName: string | null;
  temuCatid: string | null;
  temuCatname: string | null;
  goodsId: string | null;
  publishedAt: string | number[] | null;
  createdAt: string | number[] | null;
}

export interface SuccessCaseDetailVO extends SuccessCaseRowVO {
  requestJson: string | null;
  responseRaw: string | null;
}

export interface BizLogVO {
  id: number;
  bizName: string;
  content: string;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface BizLogPayload {
  bizName: string;
  content: string;
}

export interface AIChannelVO {
  id: number;
  name: string;
  platform: string;
  model: string;
  baseUrl: string | null;
  enabled: boolean;
  hasApiKey: boolean;
  hasApiSecret: boolean;
  description: string | null;
  sortOrder: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface AIChannelPayload {
  name?: string;
  platform?: string;
  model?: string;
  apiKey?: string;
  apiSecret?: string;
  baseUrl?: string;
  enabled?: boolean;
  description?: string;
  sortOrder?: number;
}

export interface AIChannelTestResponseVO {
  channelId: number;
  channelName: string;
  platform: string;
  model: string;
  success: boolean;
  imageUrl?: string | null;
  message?: string | null;
  durationMs?: number | null;
}

export interface AIChannelBusinessConfigVO {
  id: number;
  businessName: string;
  businessCode: string;
  channelId: number;
  channelName: string | null;
  channelModel: string | null;
  channelBaseUrl: string | null;
  enabled: boolean;
  description: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface AIChannelBusinessConfigPayload {
  businessName?: string;
  businessCode?: string;
  channelId?: number;
  enabled?: boolean;
  description?: string;
}

export interface SyncConfigItem {
  id: number;
  shopId: string;
  configKey: string;
  configValue: string;
  configDesc: string | null;
}

export interface SyncConfigResponse {
  shopId: string;
  configs: SyncConfigItem[];
}

export interface SyncTaskLogVO {
  phase: string | null;
  level: string | null;
  message: string | null;
  createdAt: string | number[] | null;
}

export interface SyncTaskVO {
  id: number;
  shopId: string;
  syncType: string;
  syncScope: string | null;
  triggerType: string | null;
  status: string | null;
  currentPhase: string | null;
  downloadTotal: number | null;
  downloadCompleted: number | null;
  downloadFailed: number | null;
  persistTotal: number | null;
  persistCompleted: number | null;
  persistFailed: number | null;
  totalBatches: number | null;
  persistedBatches: number | null;
  failedBatchIndex: number | null;
  lastErrorMsg: string | null;
  retryCount: number | null;
  startedAt: string | number[] | null;
  finishedAt: string | number[] | null;
  createdAt: string | number[] | null;
  updatedAt?: string | number[] | null;
  logs?: SyncTaskLogVO[] | null;
}

export interface SyncTaskProgressVO {
  taskId: number;
  status: string | null;
  currentPhase: string | null;
  downloadTotal: number | null;
  downloadCompleted: number | null;
  persistTotal: number | null;
  persistCompleted: number | null;
  totalBatches: number | null;
  persistedBatches: number | null;
  latestLogs: SyncTaskLogVO[];
}

export interface SyncTaskCreatePayload {
  shopId: string;
  syncTypes: string[];
  goodsSyncMode?: string;
  priceAdjustSyncMode?: string;
}

export interface SyncGoodsRepairFailedPageVO {
  page: number | null;
  errorMessage?: string | null;
  error?: string | null;
}

export interface SyncGoodsRepairJobVO {
  jobId: string | null;
  status: string | null;
  shopId: string | null;
  pageSize: number | null;
  concurrency: number | null;
  totalPages: number | null;
  submittedPages: number | null;
  completedPages: number | null;
  runningPages: number[] | null;
  scanned: number | null;
  repaired: number | null;
  skipped: number | null;
  failedPages: SyncGoodsRepairFailedPageVO[] | null;
  lastProgressAt: string | number[] | null;
  message: string | null;
}

export interface SyncGoodsSiteItemVO {
  siteId: number | null;
  siteName: string | null;
}

export interface SyncGoodsPropertyVO {
  pid: number | null;
  propName: string | null;
  vid: number | null;
  propValue: string | null;
  valueUnit: string | null;
}

export interface SyncGoodsSitePriceVO {
  siteId: number | null;
  supplierPrice: number | null;
  priceReviewStatus: number | null;
}

export interface SyncGoodsSkuPriceVO {
  supplierPrice: number | null;
  currencyType: string | null;
  sitePrices: SyncGoodsSitePriceVO[];
}

export interface SyncGoodsSkuSpecVO {
  specId: number | null;
  specName: string | null;
  parentSpecId: number | null;
  parentSpecName: string | null;
}

export interface SyncGoodsSkuVO {
  id: number;
  productSkuId: number | null;
  extCode: string | null;
  imageUrl: string | null;
  virtualStock: number | null;
  weightMg: number | null;
  lengthMm: number | null;
  widthMm: number | null;
  heightMm: number | null;
  isSensitive: boolean | null;
  isFragile: boolean | null;
  shippingMode: number | null;
  specList: SyncGoodsSkuSpecVO[];
  price: SyncGoodsSkuPriceVO | null;
}

export interface SyncGoodsPriceChangeVO {
  id: number;
  productSkuId: number | null;
  imageUrl: string | null;
  siteId: number | null;
  siteName: string | null;
  oldSupplierPrice: number | null;
  newSupplierPrice: number | null;
  changedAt: string | number[] | null;
}

export interface SyncGoodsListItemVO {
  id: number;
  shopId: string;
  productId: number | null;
  productSkcId: number | null;
  productName: string | null;
  extCode: string | null;
  mainImageUrl: string | null;
  skcSiteStatus: number | null;
  leafCatName: string | null;
  leafCatId: number | null;
  matchJitMode: boolean | null;
  selectStatus: number | null;
  skuCount: number | null;
  site100MinSupplierPrice: number | null;
  site100MaxSupplierPrice: number | null;
  activityBlacklisted: boolean | null;
  syncedAt: string | number[] | null;
}

export interface SyncGoodsDetailVO extends SyncGoodsListItemVO {
  categoriesJson: string | null;
  isSupportPersonalization: boolean | null;
  matchSkcJitMode: boolean | null;
  freightTemplateId: string | null;
  shipmentLimitSecond: number | null;
  temuCreatedAt: number | null;
  applyJitStatus: number | null;
  suggestCloseJit: boolean | null;
  skuList: SyncGoodsSkuVO[];
  siteList: SyncGoodsSiteItemVO[];
  propertyList: SyncGoodsPropertyVO[];
  priceChangeList: SyncGoodsPriceChangeVO[];
}

export interface ShopSkuItemVO {
  id: number;
  shopId: string;
  productId: number | null;
  productName: string | null;
  productSkcId: number | null;
  productSkuId: number | null;
  skuExtCode: string | null;
  skuSpecName: string | null;
  mainImageUrl: string | null;
  virtualStock: number | null;
  purchasePrice: number | null;
  referenceSupplierPrice: number | null;
  usSiteSupplierPrice: number | null;
}

export interface ShopSkuWarehouseVO {
  siteId: number | null;
  siteName: string | null;
  warehouseId: string;
  warehouseName: string | null;
  managementType: string | null;
  defaultWarehouse: boolean;
}

export interface BatchZeroShopSkuVirtualStockResultVO {
  matchedCount: number;
  updatedCount: number;
  alreadyZeroCount: number;
  failedCount: number;
  messages: string[];
}

export interface PriceReviewSkuVO {
  id: number;
  productSkuId: number | null;
  newPrice: number | null;
  imageUrl: string | null;
  extCode: string | null;
  specInfo: string | null;
  currentSupplyPrice: number | null;
  purchasePrice: number | null;
  collectedPrice: number | null;
  collectedPriceSource: string | null;
  collectedProductCollectionId: number | null;
  collectedProductId: string | null;
  collectedProductName: string | null;
  collectedProductUrl: string | null;
  collectedSkuId: string | null;
  collectedSkuSpec: string | null;
  collectedBaseFreight: number | string | null;
  collectedMaxWeightG: number | string | null;
  collectedPublishedAt1688: string | number[] | null;
  collectedPushedAt: string | number[] | null;
  collectedCompanyName: string | null;
  collectedCompanyLocation: string | null;
  collectedShippingLocation: string | null;
  collectedSelectionPoolId: number | null;
  collectedMerchantRepeatCustomerRate: string | null;
  collectedMerchantServiceScore: string | null;
  collectedMerchantOnTimeDeliveryRate: string | null;
  collectedMerchantShopPositiveRate: string | null;
  collectedMerchantPowerSeller: boolean | null;
  collectedMerchantSettledYears: string | null;
  collectedMerchantMainBusiness: string | null;
}

export interface PriceReviewOrderVO {
  id: number;
  shopId: string;
  orderId: number | null;
  orderStatus: number | null;
  supplyPrice: number | null;
  priceCurrency: string | null;
  suggestSupplyPrice: number | null;
  suggestPriceCurrency: string | null;
  canBargain: boolean | null;
  siteIdsJson: string | null;
  siteNamesJson: string | null;
  reviewAction: string | null;
  reviewAt: string | number[] | null;
  rejectReasonsJson: string | null;
  syncedAt: string | number[] | null;
  skuList: PriceReviewSkuVO[];
}

export interface PriceReviewRejectReasonComponentPayload {
  reason: string;
  type: number;
}

export interface PriceReviewRejectPricePayload {
  orderId: number;
  productSkuId: number;
  newPrice: number;
}

export interface PriceReviewBatchPayload {
  shopId: string;
  orderIds: number[];
  action: 'APPROVE' | 'REJECT';
  bargainReasonList?: Array<{
    componentList?: PriceReviewRejectReasonComponentPayload[];
    externalLinkList?: string[];
  }>;
  rejectPrices?: PriceReviewRejectPricePayload[];
}

export interface PriceReviewLocalCompletePayload {
  shopId: string;
  orderIds: number[];
}

export interface PriceReviewLowPriceRejectWorkerConfigVO {
  id?: number | null;
  configName?: string | null;
  enabled?: boolean | null;
  maxSuggestSupplyPrice: number | null;
  pollMs: number | null;
  batchSize: number | null;
  reasonType: number | null;
  reasonText: string | null;
  createdAt?: string | number[] | null;
  updatedAt?: string | number[] | null;
}

export interface PriceReviewLowPriceRejectWorkerConfigPayload {
  configName?: string;
  maxSuggestSupplyPrice: number;
  pollMs: number;
  batchSize: number;
  reasonType: number;
  reasonText: string;
}

export interface PriceReviewLowPriceRejectWorkerStatusVO {
  running: boolean | null;
  enabled: boolean | null;
  maxSuggestSupplyPrice: number | null;
  pollMs: number | null;
  batchSize: number | null;
  reasonType: number | null;
  reasonText: string | null;
  successCount: number | null;
  failureCount: number | null;
  skippedCount: number | null;
  lastOrderId: number | null;
  lastShopId: string | null;
  startedAt: string | number[] | null;
  stoppedAt: string | number[] | null;
  lastScanAt: string | number[] | null;
  lastWorkAt: string | number[] | null;
  lastErrorAt: string | number[] | null;
  lastError: string | null;
}

export interface PriceAdjustSkuVO {
  id: number;
  productSkuId: number | null;
  price: number | null;
  spec: string | null;
  imageUrl: string | null;
  extCode: string | null;
  specInfo: string | null;
  currentSupplyPrice: number | null;
  purchasePrice: number | null;
  salesQuantity: number | null;
  aftersaleQuantity: number | null;
  signedQuantity: number | null;
  firstLegLogisticsFee: number | null;
  logisticsRefreshOrderId: number | null;
}

export interface PriceAdjustOrderVO {
  id: number;
  shopId: string;
  priceOrderSn: string | null;
  skcId: number | null;
  productName: string | null;
  priceType: number | null;
  source: string | null;
  adjustReason: string | null;
  newSupplyPrice: string | null;
  priceCurrency: string | null;
  rejectReason: string | null;
  trafficLowExpose: boolean | null;
  status: number | null;
  siteNamesJson: string | null;
  siteNameList: string[] | null;
  reviewAction: string | null;
  reviewAt: string | number[] | null;
  syncedAt: string | number[] | null;
  skuList: PriceAdjustSkuVO[];
  skuInfoList: PriceAdjustSkuVO[];
}

export interface PriceAdjustBatchPayload {
  shopId: string;
  orderIds: number[];
  action: 'APPROVE' | 'REJECT';
  rejectReason?: string;
}

export interface ActivityThematicVO {
  id: number;
  activityThematicId: number | null;
  activityThematicName: string | null;
  enrollSource: number | null;
  enrollStartAt: number | null;
  enrollDeadLine: number | null;
  startTime: number | null;
  endTime: number | null;
  durationDays: number | null;
  salePromotionLabel: string | null;
  benefitLabelsJson: string | null;
  sitesJson: string | null;
}

export interface ActivityVO {
  id: number;
  shopId: string;
  activityType: number | null;
  activityName: string | null;
  activityContent: string | null;
  activityLabelTag: number | null;
  sessionAssignType: number | null;
  benefitLabelsJson: string | null;
  thematicList: ActivityThematicVO[];
}

export interface ActivitySessionVO {
  id: number;
  sessionId: number | null;
  sessionName: string | null;
  sessionStatus: number | null;
  siteId: number | null;
  siteName: string | null;
  startTime: number | null;
  endTime: number | null;
  durationDays: number | null;
  startDateStr: string | null;
  endDateStr: string | null;
}

export interface ActivityRequirementVO {
  checkStatus: number | null;
  checkStatusDesc: string | null;
  requirementCode: number | null;
  requirementType: string | null;
  requirementDesc: string | null;
}

export interface ActivitySiteVO {
  siteId: number | null;
  siteName: string | null;
}

export interface ActivityDetailVO {
  canEnroll: boolean | null;
  activityInfo: {
    benefitLabelName?: string[] | null;
    activityContent?: string | null;
    sessionAssignType?: number | null;
    activityName?: string | null;
    activityLabelTag?: number | null;
    activityType?: number | null;
  } | null;
  thematicInfo: {
    benefitLabelName?: string[] | null;
    durationDays?: number | null;
    enrollSource?: number | null;
    salePromotionLabel?: string | null;
    enrollDeadLine?: number | null;
    activityLabelTag?: number | null;
    enrollStartAt?: number | null;
    startTime?: number | null;
    endTime?: number | null;
    activityThematicName?: string | null;
    activityThematicId?: number | null;
    sites?: ActivitySiteVO[] | null;
  } | null;
  requirements: ActivityRequirementVO[];
  mallAptitude: ActivityRequirementVO[];
}

export interface ActivitySitePriceVO {
  siteId: number | null;
  siteName: string | null;
  dailyPrice: number | null;
  suggestActivityPrice: number | null;
  activityPrice: number | null;
  activityDiscount: number | null;
}

export interface ActivityMatchedSkuVO {
  skuId: number | null;
  dailyPrice: number | null;
  activityPrice: number | null;
  suggestActivityPrice: number | null;
  currency: string | null;
  sitePriceList: ActivitySitePriceVO[];
  extCode: string | null;
  specInfo: string | null;
  imageUrl: string | null;
}

export interface ActivityMatchedSkcVO {
  skcId: number | null;
  dailyPrice: number | null;
  activityPrice: number | null;
  suggestActivityPrice: number | null;
  currency: string | null;
  sitePriceList: ActivitySitePriceVO[];
  skuList: ActivityMatchedSkuVO[];
}

export interface ActivityMatchedProductVO {
  productId: number;
  productName: string | null;
  currency: string | null;
  isApparel: number | null;
  targetActivityStock: number | null;
  suggestActivityStock: number | null;
  enrollSessionIdList: number[] | null;
  sites: ActivitySiteVO[];
  skcList: ActivityMatchedSkcVO[];
  mainImageUrl: string | null;
  extCode: string | null;
}

export interface ActivityMatchResponseVO {
  searchScrollContext: string | null;
  hasMore: boolean | null;
  matchList: ActivityMatchedProductVO[];
}

export interface ActivityRecommendedProductVO {
  productId: number;
  goodsId: number | null;
  productName: string | null;
  mainImageUrl: string | null;
  extCode: string | null;
  currentSupplyPrice: number | null;
  suggestActivityPrice: number | null;
  minPurchasePrice: number | null;
  maxPurchasePrice: number | null;
  estimatedProfit: number | null;
  estimatedProfitRate: number | null;
  salesQuantity: number | null;
  listedDays: number | null;
  activityStock: number | null;
  decision: string | null;
  reason: string | null;
  matchedProduct: ActivityMatchedProductVO;
}

export interface ActivityRecommendationResponseVO {
  list: ActivityRecommendedProductVO[];
  localCandidateCount: number | null;
  matchedCount: number | null;
}

export interface ActivitySessionQueryResponseVO {
  siteIds: number[];
  list: ActivitySessionVO[];
  productCanEnrollSessionMap: Record<string, ActivitySessionVO[]>;
}

export interface ActivityEnrollPriceVO {
  id: number;
  level: string | null;
  skcId: number | null;
  skuId: number | null;
  siteId: number | null;
  siteName: string | null;
  dailyPrice: number | null;
  activityPrice: number | null;
  activityDiscount: number | null;
  currency: string | null;
}

export interface ActivityEnrollmentVO {
  id: number;
  shopId: string;
  enrollId: number | null;
  productId: number | null;
  goodsId: number | null;
  activityType: number | null;
  activityTypeName: string | null;
  activityThematicId: number | null;
  activityThematicName: string | null;
  enrollStatus: number | null;
  enrollTime: number | null;
  activityStock: number | null;
  remainingActivityStock: number | null;
  currency: string | null;
  soldStatus: number | null;
  sessionStartTime: number | null;
  sessionEndTime: number | null;
  assignSessionsJson: string | null;
  syncedAt: string | number[] | null;
  priceList: ActivityEnrollPriceVO[];
}

export interface TemuAttrRuleVO {
  id: number;
  ruleType: 'GENERAL' | 'FIXED_CATEGORY';
  leafCatId: string | null;
  attrName: string;
  fillMode: 'FORCE_EMPTY' | 'FIXED_VALUE' | 'SKIP';
  fixedValue: string | null;
  enabled: boolean;
  sortOrder: number;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface TemuAttrRulePayload {
  ruleType: 'GENERAL' | 'FIXED_CATEGORY';
  leafCatId?: string;
  attrName: string;
  fillMode: 'FORCE_EMPTY' | 'FIXED_VALUE' | 'SKIP';
  fixedValue?: string;
  enabled?: boolean;
  sortOrder?: number;
}

export interface ParentSpecMappingVO {
  id: number;
  sourceFieldName: string;
  normalizedSourceField: string;
  targetParentSpecName: string;
  enabled: boolean;
  notes: string | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface ParentSpecMappingPayload {
  sourceFieldName: string;
  targetParentSpecName: string;
  enabled?: boolean;
  notes?: string;
}

export interface TemuAttrAiFillTaskVO {
  id: number;
  taskId: string | null;
  spuId: number | null;
  productName: string | null;
  productMainImage: string | null;
  status: number | null;
  leafCatId: number | null;
  resultSummary: string | null;
  errorMsg: string | null;
  startedAt: string | number[] | null;
  finishedAt: string | number[] | null;
  createdAt: string | number[] | null;
}

export interface TemuAttrAiFillTaskDetailVO extends TemuAttrAiFillTaskVO {
  updatedAt: string | number[] | null;
  templateRaw: string | null;
  promptText: string | null;
  responseRaw: string | null;
  parsedJson: string | null;
  resultJson: string | null;
  ruleActions: string | null;
}

export interface TemuTitleOptimizationResponseVO {
  spuId: number | null;
  sourceTitle: string | null;
  optimizedTitleEn: string | null;
  optimizedTitleZh: string | null;
  categoryKeywords: string | null;
  attemptCount: number | null;
  matchedKeyword: string | null;
  categoryMatched: boolean | null;
  matchedTemuCatid: string | null;
  matchedTemuCatname: string | null;
  errorMsg: string | null;
  failedKeywords: string[] | null;
}

export interface ImageTranslateRecordVO {
  id: number;
  spuId: number;
  provider: string;
  status: string;
  fieldRefs: string | null;
  sourceUrl: string;
  translatedUrl: string | null;
  errorMsg: string | null;
  startedAt: string | number[] | null;
  endedAt: string | number[] | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface Alibaba1688CardLinkVO {
  id: number;
  type: string | null;
  offerId: string;
  detailUrl: string | null;
  cardHref: string | null;
  renderKey: string | null;
  cardIndex: string | null;
  offerIdSource: string | null;
  cardClass: string | null;
  status: number | null;
  rawPayload: string | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface Alibaba1688AuthSessionVO {
  id: number;
  sessionName: string;
  accountNick: string | null;
  memberId: string | null;
  homeUrl: string | null;
  remark: string | null;
  enabled: boolean;
  status: string;
  lastError: string | null;
  hasStorageState: boolean;
  storageStateUpdatedAt: string | number[] | null;
  lastVerifiedAt: string | number[] | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface Alibaba1688AuthSessionOptionVO {
  id: number;
  sessionName: string;
  accountNick: string | null;
  memberId: string | null;
  status: string | null;
}

export interface Alibaba1688AuthSessionPayload {
  sessionName: string;
  remark?: string | null;
  enabled?: boolean;
}

export interface AlibabaImageProxyConfigVO {
  id: number | null;
  configName: string;
  enabled: boolean;
  proxyBaseUrl: string | null;
  imageProxyPath: string | null;
  allowedHostsText: string | null;
  remark: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface AlibabaImageProxyConfigPayload {
  configName: string;
  enabled: boolean;
  proxyBaseUrl?: string;
  imageProxyPath?: string;
  allowedHostsText?: string;
  remark?: string;
}

export interface Alibaba1688DetailTaskVO {
  id: number;
  credentialId: number;
  credentialName: string | null;
  offerId: string;
  detailUrl: string;
  sourceType: string | null;
  status: string;
  attemptCount: number | null;
  workerName: string | null;
  lastError: string | null;
  detailRecordId: number | null;
  startedAt: string | number[] | null;
  finishedAt: string | number[] | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface Alibaba1688DetailTaskCreatePayload {
  credentialId: number;
  rawInput: string;
  forceRefresh?: boolean;
}

export interface Alibaba1688DetailTaskCreateResultVO {
  parsedUrlCount: number;
  createdCount: number;
  skippedExistingRecordCount: number;
  skippedActiveTaskCount: number;
  invalidCount: number;
  createdItems: Alibaba1688DetailTaskVO[];
}

export interface Alibaba1688DetailTaskImportFromCardLinksPayload {
  credentialId: number;
  cardLinkIds?: number[];
  allMatching?: boolean;
  keyword?: string | null;
  type?: string | null;
  status?: number | null;
  forceRefresh?: boolean;
}

export interface Alibaba1688DetailTaskImportFromCardLinksResultVO {
  selectedCount: number;
  availableCount: number;
  createdCount: number;
  skippedExistingRecordCount: number;
  skippedActiveTaskCount: number;
  skippedUnavailableCount: number;
  processedCount: number;
  createdItems: Alibaba1688DetailTaskVO[];
}

export interface Alibaba1688DetailRecordVO {
  id: number;
  offerId: string;
  detailUrl: string | null;
  canonicalUrl: string | null;
  productName: string | null;
  companyName: string | null;
  productMainImage: string | null;
  minPrice: number | null;
  maxPrice: number | null;
  repeatCustomerRate?: number | null;
  serviceScore?: number | null;
  onTimeDeliveryRate?: number | null;
  shopPositiveRate?: number | null;
  powerSeller?: boolean | null;
  settledYearsText?: string | null;
  mainBusiness?: string | null;
  sourcePlatform: string | null;
  status: string;
  lastError: string | null;
  lastTaskId: number | null;
  lastCredentialId: number | null;
  credentialName: string | null;
  lastCollectedAt: string | number[] | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface Alibaba1688DetailRecordDetailVO extends Alibaba1688DetailRecordVO {
  rawHtml: string | null;
  extractedJson: string | null;
  parsedJson: string | null;
}

export interface Alibaba1688SelectionPoolVO {
  id: number;
  detailRecordId: number | null;
  offerId: string | null;
  detailUrl: string | null;
  canonicalUrl: string | null;
  sourcePlatform: string | null;
  productTitleSnapshot?: string | null;
  productName: string | null;
  mainImageSnapshot?: string | null;
  companyName: string | null;
  companyNameSnapshot?: string | null;
  productMainImage: string | null;
  repeatCustomerRateSnapshot?: number | null;
  serviceScoreSnapshot?: number | null;
  onTimeDeliveryRateSnapshot?: number | null;
  shopPositiveRateSnapshot?: number | null;
  powerSellerSnapshot?: boolean | null;
  settledYearsTextSnapshot?: string | null;
  mainBusinessSnapshot?: string | null;
  categorySnapshot?: string | null;
  baseFreightSnapshot?: number | null;
  minPrice?: number | null;
  maxPrice?: number | null;
  moqSnapshot?: number | null;
  startBatchQtySnapshot?: number | null;
  monthlySalesSnapshot?: string | null;
  overallScore?: number | null;
  targetShopIds?: string[] | null;
  targetShopNames?: string[] | null;
  tags?: string[] | null;
  poolStatus?: string | null;
  status: string | null;
  note: string | null;
  skuCount: number | null;
  reportCount: number | null;
  temuCompeteScore?: number | null;
  aiSelectionScore?: number | null;
  aiSelectionDecision?: string | null;
  aiContainsLiquid?: boolean | null;
  aiContainsBattery?: boolean | null;
  aiFragile?: boolean | null;
  aiPotentialBrandInfringement?: boolean | null;
  aiMaxWeightG?: number | null;
  aiMaxDimensionCm?: number | null;
  aiMaxDimensionSource?: string | null;
  aiDimensionEvidence?: string | null;
  aiDetectedMoq?: number | null;
  aiDetectedSalesVolume?: number | null;
  aiDetectedSalesText?: string | null;
  pushedProductCollectionId?: number | null;
  pushStatus?: string | null;
  pushedAt?: string | number[] | null;
  pushError?: string | null;
  temuSiteExceptionBlocked?: boolean | null;
  temuSiteExceptionReason?: string | null;
  latestReportStatus: string | null;
  latestReportAt: string | number[] | null;
  publishedAt1688?: string | number[] | null;
  detailLastCollectedAt?: string | number[] | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface Alibaba1688SelectionPoolCategoryOption {
  value: string;
  label: string;
  count: number;
}

export interface Alibaba1688SelectionPoolFilterCategoryVO {
  id: number;
  categoryName: string | null;
  source?: string | null;
  enabled: boolean;
  remark: string | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface Alibaba1688SelectionPoolFilterCategoryPayload {
  categoryName: string;
  source?: string | null;
  enabled: boolean;
  remark?: string | null;
}

export interface Alibaba1688SelectionPoolSkuVO {
  id: number;
  poolId?: number | null;
  sourceSkuId?: string | null;
  skuId: string | null;
  skuSpecText?: string | null;
  specKey: string | null;
  specJson: string | null;
  skuPriceSnapshot?: number | null;
  price: number | null;
  pageStockSnapshot?: number | null;
  manualStockQty?: number | null;
  stockCheckedBy?: string | null;
  stockCheckedAt?: string | number[] | null;
  weightValue?: number | null;
  weightSource?: string | null;
  dimensionValue?: number | null;
  dimensionSource?: string | null;
  dimensionEvidence?: string | null;
  estimatedPurchasePrice?: number | null;
  estimatedFirstLegFee?: number | null;
  estimatedTemuPrice?: number | null;
  estimatedUnitProfit?: number | null;
  temuFinalPrice?: number | null;
  selectionStatus?: string | null;
  isPrimarySku?: boolean | null;
  stockRiskLevel?: string | null;
  remark?: string | null;
  image: string | null;
  skuMainImage?: string | null;
  skuImage?: string | null;
  stock: number | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface Alibaba1688SelectionPoolReportVO {
  id: number;
  poolId?: number | null;
  status: string | null;
  reportType: string | null;
  reportTitle?: string | null;
  title: string | null;
  reportSummary?: string | null;
  summary: string | null;
  reportContent?: string | null;
  dataJson: string | null;
  reportJson: string | null;
  contentJson: string | null;
  versionNo?: number | null;
  sourceType?: string | null;
  modelName?: string | null;
  score?: number | null;
  note: string | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface Alibaba1688SelectionPoolDetailVO extends Alibaba1688SelectionPoolVO {
  repeatCustomerRateSnapshot?: number | null;
  serviceScoreSnapshot?: number | null;
  onTimeDeliveryRateSnapshot?: number | null;
  shopPositiveRateSnapshot?: number | null;
  powerSellerSnapshot?: boolean | null;
  settledYearsTextSnapshot?: string | null;
  mainBusinessSnapshot?: string | null;
  shippingLocationSnapshot?: string | null;
  categorySnapshot?: string | null;
  publishedAt1688?: string | number[] | null;
  carouselImageUrls?: string[] | null;
  detailImageUrls?: string[] | null;
  moqTextSnapshot?: string | null;
  startBatchQtySnapshot?: number | null;
  salesTrendSnapshotJson?: string | null;
  priceStepsSnapshotJson?: string | null;
  scoreDetailJson?: string | null;
  selectedReason?: string | null;
  rejectReason?: string | null;
  firstSeenAt?: string | number[] | null;
  assistantExtraJson?: string | null;
  temuCompeteAnalysisStatus?: string | null;
  temuCompeteAnalysisSummary?: string | null;
  temuCompeteReportTitle?: string | null;
  temuCompeteReportContent?: string | null;
  temuCompeteReportJson?: string | null;
  temuCompeteAnalysisAt?: string | number[] | null;
  aiSelectionAnalysisStatus?: string | null;
  aiSelectionAnalysisSummary?: string | null;
  aiSelectionReportTitle?: string | null;
  aiSelectionReportContent?: string | null;
  aiSelectionReportJson?: string | null;
  aiSelectionDecision?: string | null;
  aiContainsLiquid?: boolean | null;
  aiContainsBattery?: boolean | null;
  aiFragile?: boolean | null;
  aiPotentialBrandInfringement?: boolean | null;
  aiMaxWeightG?: number | null;
  aiMaxDimensionCm?: number | null;
  aiMaxDimensionSource?: string | null;
  aiDimensionEvidence?: string | null;
  aiDetectedMoq?: number | null;
  aiDetectedSalesVolume?: number | null;
  aiDetectedSalesText?: string | null;
  aiSelectionAnalysisAt?: string | number[] | null;
  mainJson?: string | null;
  rawJson?: string | null;
  skus?: Alibaba1688SelectionPoolSkuVO[] | null;
  skuRows: Alibaba1688SelectionPoolSkuVO[] | null;
  skuList?: Alibaba1688SelectionPoolSkuVO[] | null;
  reports: Alibaba1688SelectionPoolReportVO[] | null;
  reportList?: Alibaba1688SelectionPoolReportVO[] | null;
}

export interface Alibaba1688SelectionPoolImportFromDetailPayload {
  detailRecordId: number;
  forceRefresh?: boolean | null;
  targetShopIds?: string[] | null;
  tags?: string[] | null;
  selectedReason?: string | null;
  remark?: string | null;
  note?: string | null;
}

export interface Alibaba1688SelectionPoolBatchImportPayload {
  detailRecordIds?: number[] | null;
  allMatching?: boolean | null;
  keyword?: string | null;
  detailRecordId?: number | null;
  note?: string | null;
}

export interface Alibaba1688SelectionPoolBatchImportFailure {
  detailRecordId?: number | null;
  message?: string | null;
}

export interface Alibaba1688SelectionPoolBatchImportResult {
  total: number;
  successCount: number;
  createdCount: number;
  refreshedCount: number;
  skippedCount: number;
  failedCount: number;
  failures: Alibaba1688SelectionPoolBatchImportFailure[] | null;
}

export interface Alibaba1688SelectionPoolPushPayload {
  forceCreate?: boolean | null;
  targetShopIds?: string[] | null;
}

export interface Alibaba1688SelectionPoolPushResult {
  poolId: number;
  offerId: string | null;
  productCollectionId: number;
  productId: string | null;
  productName: string | null;
  created: boolean;
  existing: boolean;
  pushStatus: string | null;
}

export interface OcrTaskVO {
  id: number;
  spuId: number;
  productId: string | null;
  imageType: number;
  imageUrl: string;
  imageWidth: number | null;
  imageHeight: number | null;
  imageMd5: string | null;
  translateStatus: string | null;
  translatedImageUrl: string | null;
  execStatus: number;
  execResult: string | null;
  failReason: string | null;
  executorPublicIp: string | null;
  taskStartedAt: string | number[] | null;
  taskFinishedAt: string | number[] | null;
  filtered: boolean;
  containsChinese: boolean | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface OcrTaskPayload {
  spuId: number;
  productId?: string;
  imageType: number;
  imageUrl: string;
  imageWidth?: number;
  imageHeight?: number;
  imageMd5?: string;
  translateStatus?: string;
  translatedImageUrl?: string;
  execStatus?: number;
  execResult?: string;
  failReason?: string;
  executorPublicIp?: string;
  filtered?: boolean;
  containsChinese?: boolean;
}

export interface OcrSizeFilterConfigVO {
  id: number;
  imageWidth: number;
  imageHeight: number;
  enabled: boolean;
  remark: string | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface OcrSizeFilterConfigPayload {
  imageWidth: number;
  imageHeight: number;
  enabled?: boolean;
  remark?: string;
}

export interface DeleteSizeFilteredImagesResultVO {
  matchedTaskCount: number;
  deletedTaskCount: number;
  removedProductImageCount: number;
  affectedProductCount: number;
}

export interface OcrImageTranslateWorkerConfigVO {
  id?: number | null;
  configName?: string | null;
  enabled?: boolean | null;
  maxChineseImageCount?: number | null;
  batchSize?: number | null;
  pollMs?: number | null;
  provider?: string | null;
  model?: string | null;
  quality?: string | null;
  remark?: string | null;
  createdAt?: string | number[] | null;
  updatedAt?: string | number[] | null;
}

export interface OcrImageTranslateWorkerConfigPayload {
  configName?: string | null;
  maxChineseImageCount?: number | null;
  batchSize?: number | null;
  pollMs?: number | null;
  provider?: string | null;
  model?: string | null;
  quality?: string | null;
  remark?: string | null;
}

export interface OcrImageTranslateWorkerStatusVO {
  running?: boolean | null;
  enabled?: boolean | null;
  maxChineseImageCount?: number | null;
  batchSize?: number | null;
  pollMs?: number | null;
  provider?: string | null;
  model?: string | null;
  pendingProductCount?: number | null;
  successCount?: number | null;
  failureCount?: number | null;
  skippedCount?: number | null;
  lastSpuId?: number | null;
  lastOcrTaskId?: number | null;
  startedAt?: string | number[] | null;
  stoppedAt?: string | number[] | null;
  lastScanAt?: string | number[] | null;
  lastWorkAt?: string | number[] | null;
  lastErrorAt?: string | number[] | null;
  lastError?: string | null;
}

export interface OcrImageTranslateWorkerLogVO {
  id: number;
  spuId: number;
  productId?: string | null;
  ocrTaskId?: number | null;
  imageType?: number | null;
  sourceField?: string | null;
  sourceIndex?: number | null;
  status?: string | null;
  model?: string | null;
  originalUrl?: string | null;
  translatedUrl?: string | null;
  temuUrl?: string | null;
  message?: string | null;
  errorMessage?: string | null;
  startedAt?: string | number[] | null;
  finishedAt?: string | number[] | null;
  durationMs?: number | null;
  createdAt?: string | number[] | null;
}

export interface WordVO {
  id: number;
  word: string;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface TemuForbiddenWordRuleVO {
  id: number;
  word: string;
  replacement?: string | null;
  fieldScope?: string | null;
  enabled: boolean;
  remark?: string | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface TemuForbiddenWordRulePayload {
  word: string;
  replacement?: string | null;
  fieldScope?: string | null;
  enabled?: boolean | null;
  remark?: string | null;
}

export interface ParserTestRunVO {
  runId: string | null;
  parserType: string | null;
  success: boolean;
  errorMessage: string | null;
  createdAt: string | null;
  html: string | null;
  result: Record<string, unknown> | null;
}

export interface DianxiaomiPackageFeeDetailItemVO {
  feeKindCode: string | null;
  feeKindName: string | null;
  amount: number | null;
  currencyAmount: number | null;
  currencyCode: string | null;
  currencyName: string | null;
  currencyRate: number | null;
  note: string | null;
  occurDate: string | null;
  billDate: string | null;
  createDate: string | null;
}

export interface DianxiaomiPackageFeeVO {
  id: number;
  dianxiaomiPackageNumber: string;
  totalFee: number | null;
  packingFee: number | null;
  totalFeeWithPacking: number | null;
  errorMessage: string | null;
  feeDetailItems: DianxiaomiPackageFeeDetailItemVO[];
  lastQueriedAt: string | number[] | null;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
}

export interface DianxiaomiPackageFeeSummaryVO {
  totalCount: number;
  successCount: number;
  failedCount: number;
  totalFee: number | null;
  packingFee: number | null;
  totalFeeWithPacking: number | null;
}

export interface DianxiaomiPackageFeeImportFailureVO {
  dianxiaomiPackageNumber: string;
  message: string;
}

export interface DianxiaomiPackageFeeImportResultVO {
  inputCount: number;
  acceptedCount: number;
  createdCount: number;
  updatedCount: number;
  successCount: number;
  failedCount: number;
  failures: DianxiaomiPackageFeeImportFailureVO[];
}
