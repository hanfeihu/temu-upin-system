import { DeleteOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tabs,
  Typography,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { syncApi } from '@/api/sync';
import { temuShopsApi } from '@/api/temuShops';
import type {
  ActivityDetailVO,
  ActivityEnrollPriceVO,
  ActivityEnrollmentVO,
  ActivityMatchedProductVO,
  ActivityMatchedSkcVO,
  ActivityMatchedSkuVO,
  ActivityRecommendedProductVO,
  ActivitySessionQueryResponseVO,
  ActivitySessionVO,
  ActivityThematicVO,
  ActivityVO,
  TemuShopVO,
} from '@/types/api';
import { formatDateTime, formatPrice } from '@/utils/format';
import { loadStoredShopFilter, resolveStoredShopFilter, saveStoredShopFilter } from '@/utils/shopFilter';

interface MatchedProductFormVO extends ActivityMatchedProductVO {
  activityStock?: number | null;
  sessionIds: number[];
}

type BatchEnrollErrorData = {
  failList?: Array<Record<string, unknown>>;
  errors?: string[];
};

interface ActivitySubmitBatch {
  id: string;
  label: string;
  shopId: string;
  activityType: number;
  activityName: string;
  activityThematicId?: number;
  productIds: number[];
  createdAt: string;
}

function formatUnixSeconds(value: number | null | undefined) {
  if (!value) {
    return '-';
  }
  const date = new Date(value > 10_000_000_000 ? value : value * 1000);
  const pad = (input: number) => String(input).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function normalizeTimeMillis(value: number | null | undefined) {
  if (!value) {
    return null;
  }
  return value > 10_000_000_000 ? value : value * 1000;
}

function resolveMatchedSuggestPrice(record: ActivityMatchedProductVO | null | undefined) {
  if (!record?.skcList?.length) {
    return null;
  }
  let best: number | null = null;
  for (const skc of record.skcList) {
    best = minPrice(best, skc.suggestActivityPrice ?? skc.activityPrice ?? null);
    for (const sitePrice of skc.sitePriceList || []) {
      best = minPrice(best, sitePrice.suggestActivityPrice ?? sitePrice.activityPrice ?? null);
    }
    for (const sku of skc.skuList || []) {
      best = minPrice(best, sku.suggestActivityPrice ?? sku.activityPrice ?? null);
      for (const sitePrice of sku.sitePriceList || []) {
        best = minPrice(best, sitePrice.suggestActivityPrice ?? sitePrice.activityPrice ?? null);
      }
    }
  }
  return best;
}

function minPrice(left: number | null, right: number | null) {
  if (left === null) return right;
  if (right === null) return left;
  return Math.min(left, right);
}

function sortRecommendedBySuggestPrice(rows: ActivityRecommendedProductVO[]) {
  return [...rows].sort((left, right) => {
    const leftPrice = left.suggestActivityPrice ?? Number.POSITIVE_INFINITY;
    const rightPrice = right.suggestActivityPrice ?? Number.POSITIVE_INFINITY;
    if (leftPrice !== rightPrice) {
      return leftPrice - rightPrice;
    }
    return (left.productId ?? 0) - (right.productId ?? 0);
  });
}

function buildSitePricePayload(items: Array<{ siteId: number | null; activityPrice: number | null; suggestActivityPrice: number | null }>) {
  return items
    .filter((item) => item.siteId !== null && item.siteId !== undefined)
    .map((item) => ({
      siteId: Number(item.siteId),
      activityPrice: item.activityPrice ?? item.suggestActivityPrice ?? undefined,
    }))
    .filter((item) => item.activityPrice !== undefined && item.activityPrice !== null);
}

function isSkcParentPrice(record: ActivityEnrollPriceVO) {
  return record.level === 'SKC' && !record.skuId;
}

function renderPriceValue(record: ActivityEnrollPriceVO, value: number | null | undefined) {
  if (value !== null && value !== undefined) {
    return formatPrice(value);
  }
  return isSkcParentPrice(record) ? <Typography.Text type="secondary">见下方SKU明细</Typography.Text> : '-';
}

const activityTypeOptions = [
  { value: 1, label: '限时秒杀' },
  { value: 5, label: '官方大促' },
  { value: 13, label: '官方大促专题' },
  { value: 14, label: '限时专属资源位' },
  { value: 21, label: '超级秒杀' },
  { value: 27, label: '清仓甩卖' },
  { value: 101, label: '秒杀进阶' },
  { value: 127, label: '清仓进阶' },
];

const enrollStatusOptions = [
  { value: 1, label: '报名中' },
  { value: 2, label: '报名失败' },
  { value: 3, label: '报名成功待分配场次' },
  { value: 4, label: '报名成功已分配场次' },
  { value: 5, label: '报名活动已结束' },
  { value: 6, label: '报名活动已下线' },
];

const SHOP_FILTER_STORAGE_KEY = 'sync-activity';
const SUBMIT_BATCH_STORAGE_KEY = 'sync-activity-submit-batches';

function loadSubmitBatches() {
  try {
    const raw = localStorage.getItem(SUBMIT_BATCH_STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((item) => item?.id && Array.isArray(item.productIds)) as ActivitySubmitBatch[] : [];
  } catch {
    return [];
  }
}

function saveSubmitBatches(batches: ActivitySubmitBatch[]) {
  localStorage.setItem(SUBMIT_BATCH_STORAGE_KEY, JSON.stringify(batches.slice(0, 20)));
}

const SyncActivityPage = () => {
  const { message, modal } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: string; label: string }>>([]);
  const [activeTab, setActiveTab] = useState('recommend');
  const [shopId, setShopId] = useState<string | undefined>(() => loadStoredShopFilter(SHOP_FILTER_STORAGE_KEY));
  const [activityType, setActivityType] = useState<number | undefined>(undefined);
  const [enrollStatus, setEnrollStatus] = useState<number | undefined>(undefined);
  const [enrollmentProductId, setEnrollmentProductId] = useState<number | undefined>(undefined);
  const [submitBatches, setSubmitBatches] = useState<ActivitySubmitBatch[]>(() => loadSubmitBatches());
  const [selectedSubmitBatchId, setSelectedSubmitBatchId] = useState<string | undefined>(undefined);
  const [loadingActivities, setLoadingActivities] = useState(false);
  const [activities, setActivities] = useState<ActivityVO[]>([]);
  const [loadingEnrollments, setLoadingEnrollments] = useState(false);
  const [enrollments, setEnrollments] = useState<ActivityEnrollmentVO[]>([]);
  const [enrollmentPage, setEnrollmentPage] = useState(1);
  const [enrollmentPageSize, setEnrollmentPageSize] = useState(20);
  const [enrollmentTotal, setEnrollmentTotal] = useState(0);
  const [selectedActivityId, setSelectedActivityId] = useState<number>();
  const [selectedThematicId, setSelectedThematicId] = useState<number>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [activityDetail, setActivityDetail] = useState<ActivityDetailVO | null>(null);
  const [matchLoading, setMatchLoading] = useState(false);
  const [matchedProducts, setMatchedProducts] = useState<MatchedProductFormVO[]>([]);
  const [rowCount] = useState<number>(10);
  const [productIdsText, setProductIdsText] = useState('');
  const [sessionLoading, setSessionLoading] = useState(false);
  const [sessionQueryResult, setSessionQueryResult] = useState<ActivitySessionQueryResponseVO | null>(null);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [recommendLoading, setRecommendLoading] = useState(false);
  const [recommendedProducts, setRecommendedProducts] = useState<ActivityRecommendedProductVO[]>([]);
  const [recommendStats, setRecommendStats] = useState<{ localCandidateCount?: number | null; matchedCount?: number | null }>({});
  const [recommendRowCount, setRecommendRowCount] = useState(20);
  const [minListedDays, setMinListedDays] = useState(0);
  const [maxSalesQuantity, setMaxSalesQuantity] = useState<number | undefined>(undefined);
  const [minProfitYuan, setMinProfitYuan] = useState(0);
  const [minProfitRatePercent, setMinProfitRatePercent] = useState(0);
  const [defaultActivityStock, setDefaultActivityStock] = useState(5);
  const [excludeEnrolled, setExcludeEnrolled] = useState(true);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerRecord, setDrawerRecord] = useState<ActivityEnrollmentVO | null>(null);
  const [drawerLoading, setDrawerLoading] = useState(false);
  const [refreshEnrollmentLoading, setRefreshEnrollmentLoading] = useState(false);

  const selectedActivity = useMemo(
    () => activities.find((item) => item.id === selectedActivityId) || null,
    [activities, selectedActivityId],
  );
  const thematicOptions = useMemo(
    () =>
      (selectedActivity?.thematicList || []).map((item: ActivityThematicVO) => ({
        value: Number(item.activityThematicId),
        label: item.activityThematicName || `主题 ${item.activityThematicId}`,
      })),
    [selectedActivity],
  );
  const selectedActivityNeedsTheme = requiresThematicId(selectedActivity?.activityType);
  const shouldShowThematicSelect = selectedActivityNeedsTheme || thematicOptions.length > 0;
  const selectedSubmitBatch = useMemo(
    () => submitBatches.find((item) => item.id === selectedSubmitBatchId) || null,
    [submitBatches, selectedSubmitBatchId],
  );

  function selectActivity(value: number | undefined) {
    setSelectedActivityId(value);
    const target = activities.find((item) => item.id === value);
    setSelectedThematicId(target?.thematicList?.[0]?.activityThematicId || undefined);
    setActivityDetail(null);
    setSessionQueryResult(null);
    setMatchedProducts([]);
    setRecommendedProducts([]);
    setRecommendStats({});
  }

  function resolveActivityStock(product: ActivityMatchedProductVO, fallback?: number | null) {
    const values = [
      fallback ?? null,
      product.suggestActivityStock ?? null,
      product.targetActivityStock ?? null,
      defaultActivityStock,
      1,
    ].filter((value): value is number => value !== null && value !== undefined && value > 0);
    return Math.max(...values);
  }

  function rememberSubmitBatch(products: MatchedProductFormVO[]) {
    if (!shopId || !selectedActivity || !products.length) {
      return null;
    }
    const createdAt = new Date().toISOString();
    const productIds = products.map((item) => item.productId);
    const batch: ActivitySubmitBatch = {
      id: `${Date.now()}-${productIds.length}`,
      label: `${formatDateTime(createdAt)} / ${selectedActivity.activityName || '活动'} / ${productIds.length}个商品`,
      shopId,
      activityType: Number(selectedActivity.activityType),
      activityName: selectedActivity.activityName || '活动',
      activityThematicId: selectedThematicId,
      productIds,
      createdAt,
    };
    const nextBatches = [batch, ...submitBatches.filter((item) => item.id !== batch.id)].slice(0, 20);
    setSubmitBatches(nextBatches);
    saveSubmitBatches(nextBatches);
    setSelectedSubmitBatchId(batch.id);
    return batch;
  }

  async function loadShops() {
    const res = await temuShopsApi.list({ enabled: true });
    const data = Array.isArray(res.data) ? res.data : [];
    const options = data
      .filter((item: TemuShopVO) => item.shopId && item.shopName)
      .map((item: TemuShopVO) => ({ value: item.shopId, label: item.shopName }));
    setShops(options);
    const nextShopId = resolveStoredShopFilter(options, shopId);
    if (!nextShopId) {
      setShopId(undefined);
      saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, undefined);
      return;
    }
    setShopId(nextShopId);
    saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, nextShopId);
    await Promise.all([loadActivities(nextShopId, activityType), loadEnrollments(1, 20, nextShopId, activityType, enrollStatus)]);
  }

  async function loadActivities(nextShopId = shopId, nextActivityType = activityType) {
    if (!nextShopId) return;
    setLoadingActivities(true);
    try {
      const res = await syncApi.getActivityList({ shopId: nextShopId, activityType: nextActivityType });
      setActivities(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载活动失败');
    } finally {
      setLoadingActivities(false);
    }
  }

  async function loadEnrollments(
    nextPage = enrollmentPage,
    nextPageSize = enrollmentPageSize,
    nextShopId = shopId,
    nextActivityType = activityType,
    nextEnrollStatus = enrollStatus,
    nextProductId = enrollmentProductId,
    nextProductIds = selectedSubmitBatch?.productIds,
  ) {
    if (!nextShopId) return;
    setLoadingEnrollments(true);
    try {
      const res = await syncApi.getEnrollmentList({
        shopId: nextShopId,
        activityType: nextActivityType,
        enrollStatus: nextEnrollStatus,
        productId: nextProductId,
        productIds: nextProductId ? undefined : nextProductIds,
        page: nextPage,
        pageSize: nextPageSize,
      });
      setEnrollments(Array.isArray(res.data.content) ? res.data.content : []);
      setEnrollmentTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载报名记录失败');
    } finally {
      setLoadingEnrollments(false);
    }
  }

  useEffect(() => {
    void loadShops();
  }, []);

  async function loadDetail(activity = selectedActivity, thematicId = selectedThematicId) {
    if (!shopId || !activity) {
      message.warning('请先选择活动');
      return;
    }
    if (requiresThematicId(activity.activityType) && !thematicId) {
      message.warning('这个活动类型需要先选择专题 ID。请先同步活动，或者在专题下拉框里选择一个专题。');
      return;
    }
    setDetailLoading(true);
    try {
      const res = await syncApi.getActivityDetail({
        shopId,
        activityType: Number(activity.activityType),
        activityThematicId: thematicId,
      });
      setActivityDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载活动详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  function requiresThematicId(type: number | null | undefined) {
    return type === 13 || type === 14 || type === 101 || type === 127;
  }

  async function refreshEnrollments() {
    if (!shopId) {
      message.warning('请先选择店铺');
      return;
    }
    const productIds = enrollmentProductId ? [enrollmentProductId] : selectedSubmitBatch?.productIds || [];
    if (!productIds.length) {
      message.warning('请先选择最近提交批次，或者输入商品ID');
      return;
    }
    setRefreshEnrollmentLoading(true);
    try {
      const res = await syncApi.refreshActivityEnrollments({
        shopId,
        activityType: selectedSubmitBatch?.activityType ?? activityType,
        activityThematicId: selectedSubmitBatch?.activityThematicId ?? selectedThematicId,
        productIds,
      });
      message.success(res.message || '已刷新报名记录');
      await loadEnrollments(1, enrollmentPageSize, shopId, selectedSubmitBatch?.activityType ?? activityType, enrollStatus, enrollmentProductId, productIds);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '刷新报名记录失败');
    } finally {
      setRefreshEnrollmentLoading(false);
    }
  }

  async function matchProducts() {
    if (!shopId || !selectedActivity) {
      message.warning('请先选择活动');
      return;
    }
    setMatchLoading(true);
    try {
      const productIds = productIdsText
        .split(/[\s,，]+/)
        .map((item) => item.trim())
        .filter(Boolean)
        .map((item) => Number(item))
        .filter((item) => !Number.isNaN(item));
      if (!productIds.length) {
        message.warning('请先输入要手动检查的商品ID，或者先在“活动选品”里推荐商品并放入工作台');
        return;
      }

      const res = await syncApi.matchActivityProducts({
        shopId,
        activityType: Number(selectedActivity.activityType),
        activityThematicId: selectedThematicId,
        rowCount: Math.max(rowCount, productIds.length),
        productIds: productIds.length ? productIds : undefined,
      });
      const data = Array.isArray(res.data.matchList) ? res.data.matchList : [];
      const nextRows = data.map((item) => ({
        ...item,
        activityStock: resolveActivityStock(item),
        sessionIds: [],
      }));
      setMatchedProducts(nextRows);
      void querySessionsForProducts(nextRows);
      message.success(`已匹配 ${data.length} 个商品`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '匹配商品失败');
    } finally {
      setMatchLoading(false);
    }
  }

  async function recommendProducts() {
    if (!shopId || !selectedActivity) {
      message.warning('请先选择活动');
      return;
    }
    setRecommendLoading(true);
    try {
      const res = await syncApi.recommendActivityProducts({
        shopId,
        activityType: Number(selectedActivity.activityType),
        activityThematicId: selectedThematicId,
        rowCount: recommendRowCount,
        minListedDays,
        maxSalesQuantity,
        minProfitCents: Math.round(minProfitYuan * 100),
        minProfitRatePercent,
        defaultActivityStock,
        excludeEnrolled,
      });
      const nextRows = sortRecommendedBySuggestPrice(
        (Array.isArray(res.data.list) ? res.data.list : []).map((item) => ({
          ...item,
          activityStock: resolveActivityStock(item.matchedProduct, item.activityStock),
        })),
      );
      setRecommendedProducts(nextRows);
      setRecommendStats({
        localCandidateCount: res.data.localCandidateCount,
        matchedCount: res.data.matchedCount,
      });
      message.success(`推荐 ${nextRows.length} 个可报名商品`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '推荐商品失败');
    } finally {
      setRecommendLoading(false);
    }
  }

  function useRecommendedProducts(rows = recommendedProducts) {
    if (!rows.length) {
      message.warning('暂无推荐商品');
      return;
    }
    const nextRows = rows.map((item) => ({
      ...item.matchedProduct,
      activityStock: resolveActivityStock(item.matchedProduct, item.activityStock),
      sessionIds: [],
    }));
    setMatchedProducts(nextRows);
    setActiveTab('workbench');
    void querySessionsForProducts(nextRows);
    message.success(`已放入报名工作台 ${rows.length} 个商品`);
  }

  function removeRecommendedProduct(record: ActivityRecommendedProductVO) {
    if (!shopId || !record.productId) {
      return;
    }
    Modal.confirm({
      title: '加入活动黑名单',
      content: '加入后这个商品以后不会再出现在活动推荐和活动匹配里。适合建议活动价太低、已经不值得继续报活动的商品。',
      okText: '确认加入',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        await syncApi.addActivityBlacklist({
          shopId,
          productId: record.productId,
          goodsId: record.goodsId,
          productName: record.productName,
          reason: '建议活动价太低',
        });
        setRecommendedProducts((rows) => rows.filter((item) => item.productId !== record.productId));
        message.success('已加入活动黑名单');
      },
    });
  }

  async function querySessions() {
    await querySessionsForProducts(matchedProducts);
  }

  async function querySessionsForProducts(products: MatchedProductFormVO[]) {
    if (!shopId || !selectedActivity) {
      message.warning('请先选择活动');
      return;
    }
    const sourceProducts = products.length ? products : matchedProducts;
    if (!sourceProducts.length) {
      message.warning('请先把商品放入工作台，或者从活动选品里点“放入工作台”');
      return;
    }
    setSessionLoading(true);
    try {
      const res = await syncApi.queryActivitySessions({
        shopId,
        activityType: Number(selectedActivity.activityType),
        activityThematicId: selectedThematicId,
        productIds: sourceProducts.map((item) => item.productId),
      });
      setSessionQueryResult(res.data);
      setMatchedProducts((current) =>
        (products.length ? sourceProducts : current).map((product) => {
          const firstSession = getPreferredSession(res.data, product.productId);
          return {
            ...product,
            sessionIds: firstSession?.sessionId ? [Number(firstSession.sessionId)] : product.sessionIds,
          };
        }),
      );
      message.success('可报名场次已刷新');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '查询场次失败');
    } finally {
      setSessionLoading(false);
    }
  }

  function getSessionOptions(record: MatchedProductFormVO) {
    const specific = sessionQueryResult?.productCanEnrollSessionMap?.[String(record.productId)] || [];
    const fallback = sessionQueryResult?.list || [];
    const source = specific.length ? specific : fallback;
    const now = Date.now();
    const futureSessions = source.filter((item) => {
      const startMillis = normalizeTimeMillis(item.startTime);
      return startMillis === null || startMillis > now;
    });
    const visibleSessions = futureSessions.length ? futureSessions : source;
    return visibleSessions.map((item: ActivitySessionVO) => ({
      value: Number(item.sessionId),
      label: `${item.sessionName || item.sessionId} / ${item.siteName || '-'} / ${formatUnixSeconds(item.startTime)}`,
    }));
  }

  function getPreferredSession(result: ActivitySessionQueryResponseVO, productId: number) {
    const specific = result.productCanEnrollSessionMap?.[String(productId)] || [];
    const source = specific.length ? specific : result.list || [];
    if (!source.length) {
      return null;
    }
    const now = Date.now();
    const sorted = [...source].sort((left, right) => {
      const leftTime = normalizeTimeMillis(left.startTime) ?? Number.MAX_SAFE_INTEGER;
      const rightTime = normalizeTimeMillis(right.startTime) ?? Number.MAX_SAFE_INTEGER;
      return leftTime - rightTime;
    });
    return sorted.find((item) => {
      const startMillis = normalizeTimeMillis(item.startTime);
      return startMillis === null || startMillis > now;
    }) || sorted[0];
  }

  function updateMatchedProduct(productId: number, patch: Partial<MatchedProductFormVO>) {
    setMatchedProducts((current) => current.map((item) => (item.productId === productId ? { ...item, ...patch } : item)));
  }

  async function submitEnroll() {
    if (!shopId || !selectedActivity) {
      message.warning('请先选择活动');
      return;
    }
    if (!matchedProducts.length) {
      message.warning('请先匹配可报名商品');
      return;
    }
    setSubmitLoading(true);
    try {
      const normalizedProducts = matchedProducts.map((product) => {
        const normalizedStock = resolveActivityStock(product, product.activityStock);
        return {
          ...product,
          activityStock: normalizedStock,
        };
      });
      const adjustedCount = normalizedProducts.filter((item, index) => item.activityStock !== matchedProducts[index]?.activityStock).length;
      if (adjustedCount > 0) {
        setMatchedProducts(normalizedProducts);
        message.info(`已按建议库存自动调整 ${adjustedCount} 个商品`);
      }
      const payload = {
        shopId,
        activityType: Number(selectedActivity.activityType),
        activityThematicId: selectedThematicId,
        productList: normalizedProducts.map((product) => {
          if (!product.activityStock) {
            throw new Error(`商品 ${product.productId} 还没有设置活动库存`);
          }
          return {
            productId: product.productId,
            activityStock: Number(product.activityStock),
            sessionIds: product.sessionIds.length ? product.sessionIds : undefined,
            skcList: (product.skcList || []).map((skc: ActivityMatchedSkcVO) => ({
              skcId: skc.skcId,
              activityPrice: skc.activityPrice ?? skc.suggestActivityPrice ?? undefined,
              siteActivityPriceList: buildSitePricePayload(skc.sitePriceList || []),
              skuList: (skc.skuList || []).map((sku: ActivityMatchedSkuVO) => ({
                skuId: sku.skuId,
                activityPrice: sku.activityPrice ?? sku.suggestActivityPrice ?? undefined,
                siteActivityPriceList: buildSitePricePayload(sku.sitePriceList || []),
              })),
            })),
          };
        }),
      };
      const batch = rememberSubmitBatch(normalizedProducts);
      await syncApi.batchEnroll(payload as Record<string, unknown>);
      message.success('批量报名已提交');
      setActiveTab('enrollments');
      await loadEnrollments(1, enrollmentPageSize, shopId, Number(selectedActivity.activityType), undefined, undefined, batch?.productIds);
    } catch (error) {
      showBatchEnrollError(error);
    } finally {
      setSubmitLoading(false);
    }
  }

  function showBatchEnrollError(error: unknown) {
    const fallbackMessage = error instanceof Error ? error.message : '批量报名失败';
    const data = (error as { data?: BatchEnrollErrorData } | null)?.data;
    const failList = Array.isArray(data?.failList) ? data.failList : [];
    const errors = Array.isArray(data?.errors) ? data.errors : [];
    if (!failList.length && !errors.length) {
      message.error(fallbackMessage);
      return;
    }
    modal.error({
      title: fallbackMessage,
      width: 720,
      content: (
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          {errors.map((item, index) => (
            <Typography.Text key={`error-${index}`} type="danger">
              {item}
            </Typography.Text>
          ))}
          {failList.map((item, index) => (
            <Typography.Text key={`fail-${index}`}>
              商品 {String(item.productId ?? '-')}：{String(item.failMsg ?? item.message ?? '报名失败')}
              {item.failReason !== undefined ? `（原因码 ${String(item.failReason)}）` : ''}
            </Typography.Text>
          ))}
        </Space>
      ),
    });
  }

  async function openEnrollmentDetail(record: ActivityEnrollmentVO) {
    setDrawerOpen(true);
    setDrawerLoading(true);
    try {
      const res = await syncApi.getEnrollmentDetail(record.id);
      setDrawerRecord(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      setDrawerLoading(false);
    }
  }

  const activityColumns: ColumnsType<ActivityVO> = [
    {
      title: '活动',
      key: 'activityName',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{record.activityName || '-'}</Typography.Text>
          <Typography.Text type="secondary">{record.activityContent || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '类型',
      key: 'activityType',
      width: 140,
      render: (_, record) => activityTypeOptions.find((item) => item.value === record.activityType)?.label || record.activityType || '-',
    },
    {
      title: '主题数',
      key: 'thematicList',
      width: 100,
      render: (_, record) => record.thematicList?.length || 0,
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            type={selectedActivityId === record.id ? 'primary' : 'default'}
            onClick={() => selectActivity(record.id)}
          >
            选中
          </Button>
          <Button size="small" onClick={() => {
            selectActivity(record.id);
            const nextThematicId = record.thematicList?.[0]?.activityThematicId || undefined;
            setSelectedThematicId(nextThematicId);
            void loadDetail(record, nextThematicId);
          }}>
            详情
          </Button>
        </Space>
      ),
    },
  ];

  const enrollmentColumns: ColumnsType<ActivityEnrollmentVO> = [
    { title: '报名ID', dataIndex: 'enrollId', key: 'enrollId', width: 140 },
    { title: '商品ID', dataIndex: 'productId', key: 'productId', width: 120 },
    { title: '活动类型', dataIndex: 'activityTypeName', key: 'activityTypeName', width: 140 },
    { title: '主题', dataIndex: 'activityThematicName', key: 'activityThematicName', width: 220 },
    {
      title: '状态',
      key: 'enrollStatus',
      width: 180,
      render: (_, record) => enrollStatusOptions.find((item) => item.value === record.enrollStatus)?.label || record.enrollStatus || '-',
    },
    { title: '活动库存', dataIndex: 'activityStock', key: 'activityStock', width: 110 },
    {
      title: '同步时间',
      dataIndex: 'syncedAt',
      key: 'syncedAt',
      width: 180,
      render: (value: ActivityEnrollmentVO['syncedAt']) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_, record) => (
        <Button size="small" onClick={() => void openEnrollmentDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  const recommendedColumns: ColumnsType<ActivityRecommendedProductVO> = [
    {
      title: '商品',
      key: 'productName',
      width: 360,
      render: (_, record) => (
        <Space size={10}>
          {record.mainImageUrl ? (
            <img src={record.mainImageUrl} alt="" style={{ width: 48, height: 48, objectFit: 'cover', borderRadius: 4 }} />
          ) : null}
          <Space direction="vertical" size={2}>
            <Typography.Text strong ellipsis style={{ maxWidth: 260 }}>
              {record.productName || '-'}
            </Typography.Text>
            <Typography.Text type="secondary">
              SPU {record.productId} / {record.extCode || '-'}
            </Typography.Text>
          </Space>
        </Space>
      ),
    },
    { title: '当前供货价', key: 'currentSupplyPrice', width: 120, render: (_, record) => formatPrice(record.currentSupplyPrice) },
    { title: '建议活动价', key: 'suggestActivityPrice', width: 120, render: (_, record) => formatPrice(record.suggestActivityPrice) },
    { title: '采购价', key: 'purchasePrice', width: 120, render: (_, record) => formatPrice(record.maxPurchasePrice) },
    { title: '预估利润', key: 'estimatedProfit', width: 120, render: (_, record) => formatPrice(record.estimatedProfit) },
    {
      title: '利润率',
      key: 'estimatedProfitRate',
      width: 100,
      render: (_, record) => (record.estimatedProfitRate === null || record.estimatedProfitRate === undefined ? '-' : `${record.estimatedProfitRate.toFixed(1)}%`),
    },
    { title: '销量', dataIndex: 'salesQuantity', key: 'salesQuantity', width: 90 },
    { title: '上架天数', dataIndex: 'listedDays', key: 'listedDays', width: 100 },
    {
      title: '结论',
      key: 'decision',
      width: 110,
      render: (_, record) => {
        const color = record.decision === '可报名' ? 'green' : record.decision === '需确认' ? 'orange' : 'red';
        return <Tag color={color}>{record.decision || '-'}</Tag>;
      },
    },
    {
      title: '原因',
      dataIndex: 'reason',
      key: 'reason',
      width: 260,
      render: (value: string | null) => <Typography.Text type="secondary">{value || '-'}</Typography.Text>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 210,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => useRecommendedProducts([record])}>
            放入工作台
          </Button>
          <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeRecommendedProduct(record)}>
            加入黑名单
          </Button>
        </Space>
      ),
    },
  ];

  const matchedColumns: ColumnsType<MatchedProductFormVO> = [
    {
      title: '商品',
      key: 'productName',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{record.productName || '-'}</Typography.Text>
          <Typography.Text type="secondary">
            Product {record.productId} / {record.extCode || '-'}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '建议活动价',
      key: 'suggestPrice',
      width: 180,
      render: (_, record) => {
        return formatPrice(resolveMatchedSuggestPrice(record));
      },
    },
    {
      title: '活动库存',
      key: 'activityStock',
      width: 140,
      render: (_, record) => (
        <InputNumber
          value={record.activityStock ?? undefined}
          onChange={(value) => updateMatchedProduct(record.productId, { activityStock: value ?? null })}
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: '可报名场次',
      key: 'sessionIds',
      width: 320,
      render: (_, record) => (
        <Select
          value={record.sessionIds?.[0]}
          onChange={(value) => updateMatchedProduct(record.productId, { sessionIds: value ? [Number(value)] : [] })}
          options={getSessionOptions(record)}
          style={{ width: '100%' }}
          allowClear
          placeholder="建议选择最近的未来场次"
        />
      ),
    },
    {
      title: 'SKU / SKC 数',
      key: 'skuCount',
      width: 120,
      render: (_, record) => `${record.skcList?.length || 0} 个 SKC`,
    },
  ];

  return (
    <>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
        {
          key: 'activities',
          label: '活动列表',
          children: (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Card>
                <Space wrap>
                  <Select
                    value={shopId}
                    onChange={(value) => {
                      setShopId(value);
                      saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, value);
                      void Promise.all([loadActivities(value, activityType), loadEnrollments(1, enrollmentPageSize, value, activityType, enrollStatus)]);
                    }}
                    placeholder="店铺"
                    style={{ width: 220 }}
                    options={shops}
                  />
                  <Select value={activityType} onChange={setActivityType} allowClear placeholder="活动类型" style={{ width: 160 }} options={activityTypeOptions} />
                  <Button type="primary" loading={loadingActivities} onClick={() => void loadActivities(shopId, activityType)}>
                    查询活动
                  </Button>
                  <Button onClick={() => {
                    setActivityType(undefined);
                    void loadActivities(shopId, undefined);
                  }}>
                    重置
                  </Button>
                </Space>
              </Card>
              <Card>
                <Table<ActivityVO> rowKey="id" loading={loadingActivities} columns={activityColumns} dataSource={activities} pagination={false} />
              </Card>
            </Space>
          ),
        },
        {
          key: 'enrollments',
          label: '报名记录',
          children: (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Card>
                <Space wrap>
                  <Select value={shopId} onChange={setShopId} placeholder="店铺" style={{ width: 220 }} options={shops} />
                  <Select value={activityType} onChange={setActivityType} allowClear placeholder="活动类型" style={{ width: 160 }} options={activityTypeOptions} />
                  <Select value={enrollStatus} onChange={setEnrollStatus} allowClear placeholder="报名状态" style={{ width: 180 }} options={enrollStatusOptions} />
                  <Select
                    value={selectedSubmitBatchId}
                    onChange={(value) => {
                      const batch = submitBatches.find((item) => item.id === value);
                      setSelectedSubmitBatchId(value);
                      setEnrollmentProductId(undefined);
                      if (batch) {
                        setShopId(batch.shopId);
                        setActivityType(batch.activityType);
                      }
                    }}
                    allowClear
                    placeholder="最近提交批次"
                    style={{ width: 360 }}
                    options={submitBatches.map((item) => ({ value: item.id, label: item.label }))}
                  />
                  <InputNumber
                    value={enrollmentProductId}
                    onChange={(value) => {
                      setEnrollmentProductId(value ? Number(value) : undefined);
                      if (value) {
                        setSelectedSubmitBatchId(undefined);
                      }
                    }}
                    placeholder="商品ID"
                    style={{ width: 160 }}
                  />
                  <Button type="primary" loading={loadingEnrollments} onClick={() => {
                    setEnrollmentPage(1);
                    void loadEnrollments(
                      1,
                      enrollmentPageSize,
                      shopId,
                      selectedSubmitBatch?.activityType ?? activityType,
                      enrollStatus,
                      enrollmentProductId,
                      selectedSubmitBatch?.productIds,
                    );
                  }}>
                    查询记录
                  </Button>
                  <Button loading={refreshEnrollmentLoading} onClick={() => void refreshEnrollments()}>
                    从TEMU刷新
                  </Button>
                </Space>
              </Card>
              <Card>
                <Table<ActivityEnrollmentVO>
                  rowKey="id"
                  loading={loadingEnrollments}
                  columns={enrollmentColumns}
                  dataSource={enrollments}
                  scroll={{ x: 1200 }}
                  pagination={{
                    current: enrollmentPage,
                    pageSize: enrollmentPageSize,
                    total: enrollmentTotal,
                    showSizeChanger: true,
                    showTotal: (count) => `共 ${count} 条`,
                  }}
                  onChange={(pagination: TablePaginationConfig) => {
                    const nextPage = pagination.current || 1;
                    const nextPageSize = pagination.pageSize || 20;
                    setEnrollmentPage(nextPage);
                    setEnrollmentPageSize(nextPageSize);
                    void loadEnrollments(
                      nextPage,
                      nextPageSize,
                      shopId,
                      selectedSubmitBatch?.activityType ?? activityType,
                      enrollStatus,
                      enrollmentProductId,
                      selectedSubmitBatch?.productIds,
                    );
                  }}
                />
              </Card>
            </Space>
          ),
        },
        {
          key: 'recommend',
          label: '活动选品',
          children: (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Card>
                <Space wrap>
                  <Select
                    value={shopId}
                    onChange={(value) => {
                      setShopId(value);
                      saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, value);
                      void loadActivities(value, activityType);
                    }}
                    placeholder="店铺"
                    style={{ width: 220 }}
                    options={shops}
                  />
                  <Select
                    value={selectedActivityId}
                    onChange={(value) => selectActivity(value)}
                    placeholder="活动"
                    style={{ width: 280 }}
                    options={activities.map((item) => ({
                      value: item.id,
                      label: `${item.activityName || '-'} / ${activityTypeOptions.find((option) => option.value === item.activityType)?.label || item.activityType || '-'}`,
                    }))}
                  />
                  {shouldShowThematicSelect ? (
                    <Select
                      value={selectedThematicId}
                      onChange={setSelectedThematicId}
                      allowClear={!selectedActivityNeedsTheme}
                      disabled={selectedActivityNeedsTheme && thematicOptions.length === 0}
                      placeholder={selectedActivityNeedsTheme ? '该活动需要先同步专题' : '主题（可选）'}
                      style={{ width: 240 }}
                      options={thematicOptions}
                    />
                  ) : (
                    <Typography.Text type="secondary">当前活动无需主题</Typography.Text>
                  )}
                  <InputNumber addonBefore="推荐数量" value={recommendRowCount} min={1} max={100} onChange={(value) => setRecommendRowCount(value || 20)} style={{ width: 160 }} />
                  <InputNumber addonBefore="上架天数≥" value={minListedDays} min={0} onChange={(value) => setMinListedDays(value || 0)} style={{ width: 160 }} />
                  <InputNumber addonBefore="销量≤" value={maxSalesQuantity} min={0} onChange={(value) => setMaxSalesQuantity(value === null ? undefined : Number(value))} style={{ width: 140 }} />
                  <InputNumber addonBefore="最低利润¥" value={minProfitYuan} min={0} precision={2} onChange={(value) => setMinProfitYuan(Number(value || 0))} style={{ width: 170 }} />
                  <InputNumber addonBefore="利润率≥" addonAfter="%" value={minProfitRatePercent} min={0} onChange={(value) => setMinProfitRatePercent(value || 0)} style={{ width: 170 }} />
                  <InputNumber addonBefore="默认库存" value={defaultActivityStock} min={1} onChange={(value) => setDefaultActivityStock(value || 5)} style={{ width: 150 }} />
                  <Select
                    value={excludeEnrolled ? 1 : 0}
                    onChange={(value) => setExcludeEnrolled(value === 1)}
                    style={{ width: 150 }}
                    options={[
                      { value: 1, label: '排除已报名' },
                      { value: 0, label: '包含已报名' },
                    ]}
                  />
                  <Button type="primary" loading={recommendLoading} onClick={() => void recommendProducts()}>
                    推荐选品
                  </Button>
                  <Button onClick={() => useRecommendedProducts()}>
                    全部放入工作台
                  </Button>
                </Space>
              </Card>
              <Card
                title={`推荐结果 ${recommendedProducts.length} 条`}
                extra={
                  <Typography.Text type="secondary">
                    本地候选 {recommendStats.localCandidateCount ?? '-'} / TEMU匹配 {recommendStats.matchedCount ?? '-'}
                  </Typography.Text>
                }
              >
                <Table<ActivityRecommendedProductVO>
                  rowKey="productId"
                  loading={recommendLoading}
                  columns={recommendedColumns}
                  dataSource={recommendedProducts}
                  scroll={{ x: 1600 }}
                  pagination={{ pageSize: 20, showSizeChanger: true }}
                />
              </Card>
            </Space>
          ),
        },
        {
          key: 'workbench',
          label: '报名工作台',
          children: (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Card>
                <Form layout="inline">
                  <Form.Item label="活动">
                    <Select
                      value={selectedActivityId}
                      onChange={(value) => selectActivity(value)}
                      style={{ width: 280 }}
                      options={activities.map((item) => ({
                        value: item.id,
                        label: `${item.activityName || '-'} / ${activityTypeOptions.find((option) => option.value === item.activityType)?.label || item.activityType || '-'}`,
                      }))}
                    />
                  </Form.Item>
                  {shouldShowThematicSelect ? (
                    <Form.Item label="主题">
                      <Select
                        value={selectedThematicId}
                        onChange={setSelectedThematicId}
                        allowClear={!selectedActivityNeedsTheme}
                        disabled={selectedActivityNeedsTheme && thematicOptions.length === 0}
                        style={{ width: 240 }}
                        options={thematicOptions}
                        placeholder={selectedActivityNeedsTheme ? '该活动需要先同步专题' : '主题（可选）'}
                      />
                    </Form.Item>
                  ) : (
                    <Form.Item label="主题">
                      <Typography.Text type="secondary">当前活动无需主题</Typography.Text>
                    </Form.Item>
                  )}
                  <Form.Item label="手动检查商品">
                    <Input
                      value={productIdsText}
                      onChange={(e) => setProductIdsText(e.target.value)}
                      placeholder="逗号分隔，仅用于手动检查指定商品"
                      style={{ width: 300 }}
                    />
                  </Form.Item>
                </Form>
                <Space wrap style={{ marginTop: 16 }}>
                  <Button onClick={() => void loadDetail()} loading={detailLoading}>
                    查看活动要求
                  </Button>
                  <Button type="primary" loading={matchLoading} onClick={() => void matchProducts()}>
                    检查指定商品
                  </Button>
                  <Button loading={sessionLoading} onClick={() => void querySessions()}>
                    刷新可报名场次
                  </Button>
                  <Button type="primary" ghost loading={submitLoading} onClick={() => void submitEnroll()}>
                    提交批量报名
                  </Button>
                </Space>
              </Card>

              {activityDetail ? (
                <Card title="活动详情">
                  <Descriptions column={2} size="small">
                    <Descriptions.Item label="活动名称">{activityDetail.activityInfo?.activityName || '-'}</Descriptions.Item>
                    <Descriptions.Item label="可报名">{activityDetail.canEnroll ? '是' : '否'}</Descriptions.Item>
                    <Descriptions.Item label="活动内容">{activityDetail.activityInfo?.activityContent || '-'}</Descriptions.Item>
                    <Descriptions.Item label="主题">{activityDetail.thematicInfo?.activityThematicName || '-'}</Descriptions.Item>
                    <Descriptions.Item label="报名开始">{formatUnixSeconds(activityDetail.thematicInfo?.enrollStartAt ?? null)}</Descriptions.Item>
                    <Descriptions.Item label="报名截止">{formatUnixSeconds(activityDetail.thematicInfo?.enrollDeadLine ?? null)}</Descriptions.Item>
                    <Descriptions.Item label="活动开始">{formatUnixSeconds(activityDetail.thematicInfo?.startTime ?? null)}</Descriptions.Item>
                    <Descriptions.Item label="活动结束">{formatUnixSeconds(activityDetail.thematicInfo?.endTime ?? null)}</Descriptions.Item>
                  </Descriptions>
                </Card>
              ) : null}

              <Card title={`已匹配商品 ${matchedProducts.length} 条`}>
                <Table<MatchedProductFormVO> rowKey="productId" loading={matchLoading} columns={matchedColumns} dataSource={matchedProducts} scroll={{ x: 1300 }} pagination={false} />
              </Card>
            </Space>
          ),
        },
        ]}
      />

      <Drawer open={drawerOpen} width={820} title="报名详情" onClose={() => setDrawerOpen(false)} loading={drawerLoading}>
        {drawerRecord ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card size="small">
              <Descriptions column={2} size="small">
                <Descriptions.Item label="报名ID">{drawerRecord.enrollId || '-'}</Descriptions.Item>
                <Descriptions.Item label="商品ID">{drawerRecord.productId || '-'}</Descriptions.Item>
                <Descriptions.Item label="活动类型">{drawerRecord.activityTypeName || '-'}</Descriptions.Item>
                <Descriptions.Item label="主题">{drawerRecord.activityThematicName || '-'}</Descriptions.Item>
                <Descriptions.Item label="报名状态">
                  {enrollStatusOptions.find((item) => item.value === drawerRecord.enrollStatus)?.label || drawerRecord.enrollStatus || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="活动库存">{drawerRecord.activityStock ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="报名时间">{formatUnixSeconds(drawerRecord.enrollTime)}</Descriptions.Item>
                <Descriptions.Item label="同步时间">{formatDateTime(drawerRecord.syncedAt)}</Descriptions.Item>
              </Descriptions>
            </Card>
            <Card
              size="small"
              title="价格明细"
              extra={<Typography.Text type="secondary">SKC是款式父级，SKU是具体售卖价格</Typography.Text>}
            >
              <Table<ActivityEnrollPriceVO>
                rowKey={(record) => `${record.id}-${record.siteId}-${record.skuId}-${record.skcId}`}
                columns={[
                  {
                    title: '层级',
                    dataIndex: 'level',
                    key: 'level',
                    width: 150,
                    render: (_, record) =>
                      isSkcParentPrice(record) ? (
                        <Tag color="blue">SKC款式父级</Tag>
                      ) : (
                        <Tag color="green">SKU价格明细</Tag>
                      ),
                  },
                  { title: 'SKC', dataIndex: 'skcId', key: 'skcId', width: 100 },
                  {
                    title: 'SKU',
                    dataIndex: 'skuId',
                    key: 'skuId',
                    width: 150,
                    render: (value, record) => value || (isSkcParentPrice(record) ? <Typography.Text type="secondary">父级，无SKU</Typography.Text> : '-'),
                  },
                  {
                    title: '站点',
                    dataIndex: 'siteName',
                    key: 'siteName',
                    width: 140,
                    render: (value, record) => value || (isSkcParentPrice(record) ? <Typography.Text type="secondary">按SKU展开</Typography.Text> : '-'),
                  },
                  { title: '日常价', key: 'dailyPrice', width: 150, render: (_, record) => renderPriceValue(record, record.dailyPrice) },
                  { title: '活动价', key: 'activityPrice', width: 150, render: (_, record) => renderPriceValue(record, record.activityPrice) },
                  {
                    title: '折扣',
                    dataIndex: 'activityDiscount',
                    key: 'activityDiscount',
                    width: 120,
                    render: (value, record) => value ?? (isSkcParentPrice(record) ? <Typography.Text type="secondary">见SKU</Typography.Text> : '-'),
                  },
                ]}
                dataSource={drawerRecord.priceList || []}
                pagination={false}
                scroll={{ x: 960 }}
              />
            </Card>
          </Space>
        ) : null}
      </Drawer>
    </>
  );
};

export default SyncActivityPage;
