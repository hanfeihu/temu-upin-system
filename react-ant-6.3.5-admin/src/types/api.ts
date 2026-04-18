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
  temuOptimizedTitleEn?: string | null;
  temuOptimizedTitleZh?: string | null;
  temuCategoryKeywords?: string | null;
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

export interface TemuAppPayload {
  appName: string;
  appKey: string;
  appSecret?: string;
  enabled: boolean;
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

export interface PriceReviewSkuVO {
  id: number;
  productSkuId: number | null;
  newPrice: number | null;
  imageUrl: string | null;
  extCode: string | null;
  specInfo: string | null;
  currentSupplyPrice: number | null;
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

export interface PriceAdjustSkuVO {
  id: number;
  productSkuId: number | null;
  price: number | null;
  spec: string | null;
  imageUrl: string | null;
  extCode: string | null;
  specInfo: string | null;
  currentSupplyPrice: number | null;
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

export interface OcrTaskVO {
  id: number;
  spuId: number;
  productId: string | null;
  imageType: number;
  imageUrl: string;
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
  execStatus?: number;
  execResult?: string;
  failReason?: string;
  executorPublicIp?: string;
  filtered?: boolean;
  containsChinese?: boolean;
}

export interface WordVO {
  id: number;
  word: string;
  createdAt: string | number[] | null;
  updatedAt: string | number[] | null;
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
