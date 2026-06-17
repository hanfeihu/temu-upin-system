import { App, Button, Card, Descriptions, Drawer, Form, Image, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { Key } from 'react';
import { useEffect, useState } from 'react';
import { syncApi } from '@/api/sync';
import { temuShopsApi } from '@/api/temuShops';
import type {
  PriceReviewBatchPayload,
  PriceReviewLowPriceRejectWorkerConfigVO,
  PriceReviewLowPriceRejectWorkerStatusVO,
  PriceReviewOrderVO,
  PriceReviewSkuVO,
  TemuShopVO,
} from '@/types/api';
import { formatDateTime, formatPrice, safeJsonParse } from '@/utils/format';
import { loadStoredShopFilter, resolveStoredShopFilter, saveStoredShopFilter } from '@/utils/shopFilter';

interface EditableReviewOrder extends PriceReviewOrderVO {
  skuList: Array<PriceReviewSkuVO & { editNewPrice?: number | null }>;
}

const orderStatusMap: Record<number, string> = {
  1: '待处理',
  2: '处理中',
  3: '已完成',
  4: '已关闭',
};

const reviewColorMap: Record<string, string> = {
  APPROVE: 'green',
  APPROVED: 'green',
  REJECT: 'red',
  REJECTED: 'red',
  COMPLETED: 'default',
  PENDING: 'processing',
};

const REJECT_REASON_TYPE_OPTIONS = [
  { value: 0, label: '材质' },
  { value: 1, label: '功能' },
  { value: 2, label: '其他' },
  { value: 3, label: '品类' },
  { value: 4, label: '外观' },
  { value: 5, label: '版型' },
  { value: 6, label: '图案' },
  { value: 7, label: '规格尺寸' },
  { value: 8, label: '品牌' },
];
const DEFAULT_REJECT_REASON = '价格太低';

function summarizePurchasePrice(skus?: PriceReviewSkuVO[] | null) {
  const prices = Array.from(
    new Set(
      (skus || [])
        .map((item) => item.purchasePrice)
        .filter((item): item is number => item !== null && item !== undefined),
    ),
  );

  if (!prices.length) {
    return '采购价未配置';
  }

  if (prices.length === 1) {
    return `采购价（${formatPrice(prices[0])}）`;
  }

  return `采购价（${formatPrice(Math.min(...prices))} 起）`;
}

function formatCollectedPriceSource(source?: string | null) {
  const normalized = String(source || '').trim().toUpperCase();
  if (!normalized) {
    return '未知来源';
  }
  if (normalized === 'TEMU') {
    return 'temu';
  }
  if (normalized === '1688') {
    return '1688';
  }
  return source || '未知来源';
}

function summarizeCollectedPrice(skus?: PriceReviewSkuVO[] | null) {
  const matched = (skus || [])
    .filter((item): item is PriceReviewSkuVO & { collectedPrice: number; collectedPriceSource: string | null } => item.collectedPrice !== null && item.collectedPrice !== undefined)
    .map((item) => ({
      price: item.collectedPrice,
      source: formatCollectedPriceSource(item.collectedPriceSource),
    }));

  if (!matched.length) {
    return '采集价格未匹配';
  }

  const uniquePairs = Array.from(new Set(matched.map((item) => `${item.source}:${item.price}`)));
  if (uniquePairs.length === 1) {
    const [source, priceText] = uniquePairs[0].split(':');
    return `采集价（${formatPrice(Number(priceText))}, ${source}）`;
  }

  const minPrice = Math.min(...matched.map((item) => item.price));
  const sourceSet = Array.from(new Set(matched.map((item) => item.source)));
  return `采集价（${formatPrice(minPrice)} 起, ${sourceSet.length === 1 ? sourceSet[0] : '多来源'}）`;
}

function summarizeFirstCollectedPrice(skus?: PriceReviewSkuVO[] | null) {
  const firstSku = (skus || [])[0];
  if (!firstSku || firstSku.collectedPrice === null || firstSku.collectedPrice === undefined) {
    return '采集价格未匹配';
  }
  return `采集价（${formatPrice(firstSku.collectedPrice)}, ${formatCollectedPriceSource(firstSku.collectedPriceSource)}）`;
}

function formatYuanAmount(value?: number | string | null) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return String(value);
  }
  return `¥${numberValue.toFixed(2)}`;
}

function formatWeightG(value?: number | string | null) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return `${value} g`;
  }
  if (numberValue >= 1000) {
    return `${(numberValue / 1000).toFixed(2)} kg (${numberValue.toFixed(0)} g)`;
  }
  return `${numberValue.toFixed(0)} g`;
}

function formatMaybePercent(value?: string | null) {
  if (!value) {
    return '-';
  }
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return value;
  }
  if (numberValue > 0 && numberValue <= 1) {
    return `${(numberValue * 100).toFixed(2)}%`;
  }
  return `${numberValue.toFixed(2)}%`;
}

function toFiniteNumber(value?: number | string | null) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function calculateEstimatedFirstLegYuan(maxWeightG?: number | string | null) {
  const weightG = toFiniteNumber(maxWeightG);
  if (weightG === null) {
    return null;
  }
  return (Math.max(weightG, 30) / 1000) * 69 + 6;
}

function calculateCollectedCostSummary(skus?: PriceReviewSkuVO[] | null, suggestSupplyPrice?: number | null) {
  const firstMatched = (skus || []).find((item) => item.collectedPrice !== null && item.collectedPrice !== undefined);
  if (!firstMatched || firstMatched.collectedPrice === null || firstMatched.collectedPrice === undefined) {
    return null;
  }

  const baseFreight = toFiniteNumber(firstMatched.collectedBaseFreight);
  const firstLeg = calculateEstimatedFirstLegYuan(firstMatched.collectedMaxWeightG);
  const baseFreightCents = baseFreight === null ? null : Math.round(baseFreight * 100);
  const purchaseCents = Number(firstMatched.collectedPrice || 0) + (baseFreightCents === null ? 0 : baseFreightCents);
  const firstLegCents = firstLeg === null ? null : Math.round(firstLeg * 100);
  const profitCents = suggestSupplyPrice !== null
    && suggestSupplyPrice !== undefined
    && firstLegCents !== null
    ? Number(suggestSupplyPrice) - purchaseCents - firstLegCents
    : null;

  const profitType: 'danger' | 'success' | 'warning' | undefined =
    profitCents === null ? undefined : profitCents < 0 ? 'danger' : profitCents > 0 ? 'success' : 'warning';

  return {
    baseFreightCents,
    purchaseCents,
    firstLegCents,
    profitCents,
    profitType,
  };
}

function summarizeCollectedCostRows(skus?: PriceReviewSkuVO[] | null, suggestSupplyPrice?: number | null) {
  const summary = calculateCollectedCostSummary(skus, suggestSupplyPrice);
  if (!summary) {
    return null;
  }
  return {
    baseFreightText: summary.baseFreightCents === null ? '-' : formatPrice(summary.baseFreightCents),
    purchaseText: formatPrice(summary.purchaseCents),
    firstLegText: summary.firstLegCents === null ? '-' : formatPrice(summary.firstLegCents),
    profitText: summary.profitCents === null ? '-' : formatPrice(summary.profitCents),
    profitType: summary.profitType,
  };
}

const SHOP_FILTER_STORAGE_KEY = 'sync-price-review';
const DEFAULT_LOW_PRICE_REJECT_THRESHOLD = 20;
const PROFIT_APPROVE_THRESHOLD_CENTS = 1000;
const DEFAULT_LOW_PRICE_REJECT_WORKER_CONFIG = {
  maxSuggestSupplyPriceYuan: 20,
  pollMinutes: 5,
  batchSize: 20,
  reasonType: 2,
  reasonText: DEFAULT_REJECT_REASON,
};

const SyncPriceReviewPage = () => {
  const { message } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: string; label: string }>>([]);
  const [shopId, setShopId] = useState<string | undefined>(() => loadStoredShopFilter(SHOP_FILTER_STORAGE_KEY));
  const [orderStatus, setOrderStatus] = useState<number>(1);
  const [reviewAction, setReviewAction] = useState<string>('PENDING');
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<PriceReviewOrderVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<PriceReviewOrderVO | null>(null);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [reviewing, setReviewing] = useState(false);
  const [reviewMode, setReviewMode] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [reviewTargets, setReviewTargets] = useState<EditableReviewOrder[]>([]);
  const [reasonType, setReasonType] = useState<number>(2);
  const [reasonText, setReasonText] = useState(DEFAULT_REJECT_REASON);
  const [externalLinksText, setExternalLinksText] = useState('');
  const [lowPriceRejectOpen, setLowPriceRejectOpen] = useState(false);
  const [lowPriceRejectThreshold, setLowPriceRejectThreshold] = useState<number>(DEFAULT_LOW_PRICE_REJECT_THRESHOLD);
  const [collectedSourceSku, setCollectedSourceSku] = useState<PriceReviewSkuVO | null>(null);
  const [workerConfig, setWorkerConfig] = useState(DEFAULT_LOW_PRICE_REJECT_WORKER_CONFIG);
  const [workerStatus, setWorkerStatus] = useState<PriceReviewLowPriceRejectWorkerStatusVO | null>(null);
  const [workerLoading, setWorkerLoading] = useState(false);

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
    await load(1, 20, nextShopId, orderStatus, reviewAction);
  }

  function applyWorkerConfig(config?: PriceReviewLowPriceRejectWorkerConfigVO | null) {
    if (!config) {
      return;
    }
    setWorkerConfig({
      maxSuggestSupplyPriceYuan: Number(config.maxSuggestSupplyPrice ?? 2000) / 100,
      pollMinutes: Math.max(Number(config.pollMs ?? 300000) / 60000, 0.17),
      batchSize: Number(config.batchSize ?? 20),
      reasonType: Number(config.reasonType ?? 2),
      reasonText: config.reasonText || DEFAULT_REJECT_REASON,
    });
  }

  async function loadWorker() {
    try {
      const [configRes, statusRes] = await Promise.all([
        syncApi.getPriceReviewLowPriceRejectWorkerConfig(),
        syncApi.getPriceReviewLowPriceRejectWorkerStatus(),
      ]);
      applyWorkerConfig(configRes.data);
      setWorkerStatus(statusRes.data || null);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载低价自动拒绝配置失败');
    }
  }

  async function load(nextPage = page, nextPageSize = pageSize, nextShopId = shopId, nextOrderStatus = orderStatus, nextReviewAction = reviewAction) {
    if (!nextShopId) {
      return;
    }
    setLoading(true);
    try {
      const res = await syncApi.getPriceReviewList({
        shopId: nextShopId,
        orderStatus: nextOrderStatus,
        reviewAction: nextReviewAction,
        page: nextPage,
        pageSize: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
      setSelectedRowKeys((current) => current.filter((key) => (res.data.content || []).some((row) => row.id === key)));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadShops();
    void loadWorker();
  }, []);

  function formatSites(value: string | null) {
    const list = safeJsonParse<string[]>(value, []);
    return list.length ? list.join(' / ') : '-';
  }

  function reviewText(value?: string | null) {
    return (
      {
        APPROVE: '已同意',
        APPROVED: '已同意',
        REJECT: '已拒绝',
        REJECTED: '已拒绝',
        COMPLETED: '已完成',
        PENDING: '待处理',
      }[value || 'PENDING'] || value || '待处理'
    );
  }

  async function openDetail(record: PriceReviewOrderVO) {
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const res = await syncApi.getPriceReviewDetail(record.id);
      setDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  async function loadReviewDetails(targets: PriceReviewOrderVO[]) {
    const details = await Promise.all(targets.map(async (item) => (await syncApi.getPriceReviewDetail(item.id)).data));
    return details.map((item) => ({
      ...item,
      skuList: (item.skuList || []).map((sku) => ({
        ...sku,
        editNewPrice: sku.newPrice ?? null,
      })),
    }));
  }

  async function startReview(records: PriceReviewOrderVO[], mode: 'APPROVE' | 'REJECT') {
    if (!records.length) {
      message.warning('请先选择核价单');
      return;
    }
    setReviewMode(mode);
    setReasonText(DEFAULT_REJECT_REASON);
    setExternalLinksText('');
    setReasonType(2);
    if (mode === 'APPROVE') {
      setReviewTargets(records.map((item) => ({ ...item, skuList: item.skuList || [] })));
      setReviewOpen(true);
      return;
    }
    setReviewing(true);
    try {
      const details = await loadReviewDetails(records);
      setReviewTargets(details);
      setReviewOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载拒绝详情失败');
    } finally {
      setReviewing(false);
    }
  }

  async function batchMarkCompleted(records: PriceReviewOrderVO[]) {
    if (!records.length) {
      message.warning('请先选择核价单');
      return;
    }
    if (!shopId) {
      message.warning('请先选择店铺');
      return;
    }
    setReviewing(true);
    try {
      const res = await syncApi.batchLocalCompletePriceReview({
        shopId,
        orderIds: records.map((item) => item.id),
      });
      message.success(res.message || '批量标记已完成成功');
      setSelectedRowKeys([]);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '批量标记已完成失败');
    } finally {
      setReviewing(false);
    }
  }

  function updateSkuPrice(orderId: number, productSkuId: number | null, value: number | null) {
    setReviewTargets((current) =>
      current.map((order) =>
        order.id === orderId
          ? {
              ...order,
              skuList: order.skuList.map((sku) =>
                sku.productSkuId === productSkuId ? { ...sku, editNewPrice: value } : sku,
              ),
            }
          : order,
      ),
    );
  }

  const lowPriceThresholdInFen = Math.round((Number(lowPriceRejectThreshold) || 0) * 100);
  const lowPriceRejectTargets = rows.filter(
    (item) =>
      item.suggestSupplyPrice !== null &&
      item.suggestSupplyPrice !== undefined &&
      item.suggestSupplyPrice <= lowPriceThresholdInFen,
  );
  const profitApproveTargets = rows.filter((item) => {
    const costSummary = calculateCollectedCostSummary(item.skuList || [], item.suggestSupplyPrice);
    return costSummary?.profitCents !== null
      && costSummary?.profitCents !== undefined
      && costSummary.profitCents >= PROFIT_APPROVE_THRESHOLD_CENTS;
  });

  function openLowPriceReject() {
    setLowPriceRejectThreshold(DEFAULT_LOW_PRICE_REJECT_THRESHOLD);
    setLowPriceRejectOpen(true);
  }

  async function submitLowPriceReject() {
    if (!lowPriceRejectTargets.length) {
      message.warning('当前页没有符合条件的核价单');
      return;
    }
    setLowPriceRejectOpen(false);
    await startReview(lowPriceRejectTargets, 'REJECT');
  }

  async function startProfitApprove() {
    if (!profitApproveTargets.length) {
      message.warning('当前页没有总预估利润超过或等于10元的核价单');
      return;
    }
    await startReview(profitApproveTargets, 'APPROVE');
  }

  async function saveWorkerConfig() {
    setWorkerLoading(true);
    try {
      const res = await syncApi.updatePriceReviewLowPriceRejectWorkerConfig({
        configName: '低价自动拒绝',
        maxSuggestSupplyPrice: Math.round(Number(workerConfig.maxSuggestSupplyPriceYuan || 0) * 100),
        pollMs: Math.round(Number(workerConfig.pollMinutes || 5) * 60000),
        batchSize: Math.round(Number(workerConfig.batchSize || 20)),
        reasonType: Math.round(Number(workerConfig.reasonType ?? 2)),
        reasonText: workerConfig.reasonText || DEFAULT_REJECT_REASON,
      });
      setWorkerStatus(res.data || null);
      message.success('低价自动拒绝配置已保存');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存低价自动拒绝配置失败');
    } finally {
      setWorkerLoading(false);
    }
  }

  async function toggleWorker(nextRunning: boolean) {
    setWorkerLoading(true);
    try {
      const res = nextRunning
        ? await syncApi.startPriceReviewLowPriceRejectWorker()
        : await syncApi.stopPriceReviewLowPriceRejectWorker();
      setWorkerStatus(res.data || null);
      message.success(nextRunning ? '低价自动拒绝已启动' : '低价自动拒绝已停止');
    } catch (error) {
      message.error(error instanceof Error ? error.message : nextRunning ? '启动失败' : '停止失败');
    } finally {
      setWorkerLoading(false);
    }
  }

  function buildPayload(): PriceReviewBatchPayload {
    if (!shopId) {
      throw new Error('请选择店铺');
    }

    const payload: PriceReviewBatchPayload = {
      shopId,
      orderIds: reviewTargets.map((item) => item.id),
      action: reviewMode,
    };

    if (reviewMode === 'REJECT') {
      const componentList = reasonText
        .split('\n')
        .map((item) => item.trim())
        .filter(Boolean)
        .map((reason) => ({ reason, type: reasonType }));
      const externalLinkList = externalLinksText
        .split('\n')
        .map((item) => item.trim())
        .filter(Boolean);
      const rejectPrices = reviewTargets.flatMap((item) =>
        item.skuList
          .filter((sku) => sku.editNewPrice !== undefined && sku.editNewPrice !== null && `${sku.editNewPrice}` !== '')
          .map((sku) => ({
            orderId: item.id,
            productSkuId: Number(sku.productSkuId),
            newPrice: Number(sku.editNewPrice),
          })),
      );

      if (!componentList.length && !externalLinkList.length && !rejectPrices.length) {
        throw new Error('拒绝时请至少填写原因、外链或新的申报价');
      }

      if (externalLinkList.length && !componentList.length) {
        throw new Error('填写外部链接时，请至少填写一条拒绝原因');
      }

      if (componentList.length || externalLinkList.length) {
        payload.bargainReasonList = [{ componentList, externalLinkList }];
      }
      payload.rejectPrices = rejectPrices;
    }

    return payload;
  }

  async function submitReview() {
    setReviewing(true);
    try {
      await syncApi.batchReviewPrice(buildPayload());
      message.success(reviewMode === 'APPROVE' ? '审核同意完成' : '审核拒绝完成');
      setReviewOpen(false);
      setSelectedRowKeys([]);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '提交失败');
    } finally {
      setReviewing(false);
    }
  }

  const columns: ColumnsType<PriceReviewOrderVO> = [
    {
      title: '订单信息',
      key: 'orderInfo',
      width: 250,
      render: (_, record) => (
        <Space direction="vertical" size={4}>
          <Typography.Text strong>{record.orderId || '-'}</Typography.Text>
          <Typography.Text type="secondary">{formatSites(record.siteNamesJson)}</Typography.Text>
          <Space size={[6, 6]} wrap>
            <Tag>{orderStatusMap[record.orderStatus || 0] || record.orderStatus || '-'}</Tag>
            <Tag color={reviewColorMap[record.reviewAction || 'PENDING'] || 'processing'}>{reviewText(record.reviewAction)}</Tag>
            <Tag color={record.canBargain ? 'blue' : 'default'}>{record.canBargain ? '可议价' : '不可议价'}</Tag>
          </Space>
          <Typography.Text type="secondary">同步 {formatDateTime(record.syncedAt)}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'SKU 信息',
      key: 'skuInfo',
      render: (_, record) => (
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          {(record.skuList || []).slice(0, 2).map((sku) => (
            <Space key={`${record.id}-${sku.productSkuId}`} size={10} align="start">
              {sku.imageUrl ? <Image width={38} height={38} src={sku.imageUrl} /> : null}
              <Space direction="vertical" size={2}>
                <Typography.Text>SKU {sku.productSkuId || '-'}</Typography.Text>
                <Typography.Text type="secondary">{sku.specInfo || '-'}</Typography.Text>
              </Space>
            </Space>
          ))}
          {(record.skuList || []).length > 2 ? <Typography.Text type="secondary">其余 {(record.skuList || []).length - 2} 个 SKU 请在详情中查看</Typography.Text> : null}
        </Space>
      ),
    },
    {
      title: '价格',
      key: 'priceInfo',
      width: 230,
      render: (_, record) => {
        const costRows = summarizeCollectedCostRows(record.skuList || [], record.suggestSupplyPrice);
        return (
          <Space direction="vertical" size={2} style={{ alignItems: 'center', width: '100%' }}>
            <Typography.Text strong>{formatPrice(record.suggestSupplyPrice)}</Typography.Text>
            <Typography.Text type="secondary">→ {formatPrice(record.supplyPrice)}</Typography.Text>
            <Typography.Text type="secondary">{summarizePurchasePrice(record.skuList || [])}</Typography.Text>
            <Typography.Text type="secondary">{summarizeFirstCollectedPrice(record.skuList || [])}</Typography.Text>
            {costRows ? (
              <Space direction="vertical" size={0} style={{ alignItems: 'center', width: '100%' }}>
                <Typography.Text type="secondary">基础运费 {costRows.baseFreightText}</Typography.Text>
                <Typography.Text type="secondary">总采购价 {costRows.purchaseText}</Typography.Text>
                <Typography.Text type="secondary">总预估头程 {costRows.firstLegText}</Typography.Text>
                <Typography.Text type={costRows.profitType}>总预估利润 {costRows.profitText}</Typography.Text>
              </Space>
            ) : null}
          </Space>
        );
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          <Button size="small" type="link" onClick={() => void startReview([record], 'APPROVE')}>
            同意
          </Button>
          <Button size="small" type="link" danger onClick={() => void startReview([record], 'REJECT')}>
            拒绝
          </Button>
        </Space>
      ),
    },
  ];

  const detailSkuColumns: ColumnsType<PriceReviewSkuVO> = [
    { title: 'SKU', dataIndex: 'productSkuId', key: 'productSkuId', width: 120 },
    { title: '编码', dataIndex: 'extCode', key: 'extCode', width: 120 },
    { title: '规格', dataIndex: 'specInfo', key: 'specInfo' },
    { title: '采购价', key: 'purchasePrice', width: 120, render: (_, record) => record.purchasePrice !== null && record.purchasePrice !== undefined ? formatPrice(record.purchasePrice) : '未配置' },
    {
      title: '采集价格',
      key: 'collectedPrice',
      width: 170,
      render: (_, record) =>
        record.collectedPrice !== null && record.collectedPrice !== undefined ? (
          <Space direction="vertical" size={0}>
            <Button type="link" size="small" style={{ padding: 0 }} onClick={() => setCollectedSourceSku(record)}>
              {formatPrice(record.collectedPrice)}
            </Button>
            <Typography.Text type="secondary">{formatCollectedPriceSource(record.collectedPriceSource)}</Typography.Text>
          </Space>
        ) : (
          '未匹配'
        ),
    },
    { title: '当前供货价', key: 'currentSupplyPrice', width: 140, render: (_, record) => formatPrice(record.currentSupplyPrice) },
    { title: '建议新价', key: 'newPrice', width: 140, render: (_, record) => formatPrice(record.newPrice) },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        title="低价自动拒绝"
        extra={
          <Space>
            <Tag color={workerStatus?.running ? 'green' : 'default'}>
              {workerStatus?.running ? '运行中' : '已停止'}
            </Tag>
            <Button loading={workerLoading} onClick={() => void loadWorker()}>
              刷新状态
            </Button>
            <Button loading={workerLoading} onClick={() => void saveWorkerConfig()}>
              保存配置
            </Button>
            {workerStatus?.running ? (
              <Button danger loading={workerLoading} onClick={() => void toggleWorker(false)}>
                停止
              </Button>
            ) : (
              <Button type="primary" loading={workerLoading} onClick={() => void toggleWorker(true)}>
                启动
              </Button>
            )}
          </Space>
        }
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space wrap align="center">
            <Typography.Text>每</Typography.Text>
            <InputNumber
              min={0.17}
              step={1}
              precision={2}
              value={workerConfig.pollMinutes}
              onChange={(value) => setWorkerConfig((current) => ({ ...current, pollMinutes: Number(value || 5) }))}
              style={{ width: 120 }}
              addonAfter="分钟"
            />
            <Typography.Text>扫描一次，自动拒绝建议价低于等于</Typography.Text>
            <InputNumber
              min={0.01}
              step={1}
              precision={2}
              value={workerConfig.maxSuggestSupplyPriceYuan}
              onChange={(value) => setWorkerConfig((current) => ({ ...current, maxSuggestSupplyPriceYuan: Number(value || 20) }))}
              style={{ width: 140 }}
              addonAfter="元"
            />
            <Typography.Text>的待处理核价单，单次最多</Typography.Text>
            <InputNumber
              min={1}
              max={200}
              step={1}
              precision={0}
              value={workerConfig.batchSize}
              onChange={(value) => setWorkerConfig((current) => ({ ...current, batchSize: Number(value || 20) }))}
              style={{ width: 120 }}
              addonAfter="条"
            />
          </Space>
          <Space wrap align="center">
            <Typography.Text>拒绝类型</Typography.Text>
            <Select
              value={workerConfig.reasonType}
              onChange={(value) => setWorkerConfig((current) => ({ ...current, reasonType: value }))}
              options={REJECT_REASON_TYPE_OPTIONS}
              style={{ width: 150 }}
            />
            <Typography.Text>拒绝原因</Typography.Text>
            <Input
              value={workerConfig.reasonText}
              onChange={(event) => setWorkerConfig((current) => ({ ...current, reasonText: event.target.value }))}
              style={{ width: 260 }}
              placeholder={DEFAULT_REJECT_REASON}
            />
          </Space>
          <Space size={[12, 6]} wrap>
            <Typography.Text type="secondary">成功 {workerStatus?.successCount ?? 0}</Typography.Text>
            <Typography.Text type="secondary">失败 {workerStatus?.failureCount ?? 0}</Typography.Text>
            <Typography.Text type="secondary">最近扫描 {formatDateTime(workerStatus?.lastScanAt)}</Typography.Text>
            <Typography.Text type="secondary">最近处理 {formatDateTime(workerStatus?.lastWorkAt)}</Typography.Text>
            <Typography.Text type="secondary">最近订单 {workerStatus?.lastOrderId || '-'}</Typography.Text>
            {workerStatus?.lastError ? (
              <Typography.Text type="danger">错误 {workerStatus.lastError}</Typography.Text>
            ) : null}
          </Space>
        </Space>
      </Card>

      <Card>
        <Space wrap>
          <Select
            value={shopId}
            onChange={(value) => {
              setShopId(value);
              saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, value);
              setPage(1);
              void load(1, pageSize, value, orderStatus, reviewAction);
            }}
            placeholder="店铺"
            style={{ width: 220 }}
            options={shops}
          />
          <Select value={orderStatus} onChange={setOrderStatus} style={{ width: 150 }} options={[{ value: 1, label: '待处理' }, { value: 2, label: '处理中' }, { value: 3, label: '已完成' }]} />
          <Select value={reviewAction} onChange={setReviewAction} style={{ width: 150 }} options={[{ value: 'PENDING', label: '待处理' }, { value: 'APPROVE', label: '已同意' }, { value: 'REJECT', label: '已拒绝' }, { value: 'COMPLETED', label: '已完成' }]} />
          <Button type="primary" loading={loading} onClick={() => {
            setPage(1);
            void load(1, pageSize, shopId, orderStatus, reviewAction);
          }}>
            查询
          </Button>
          <Button onClick={() => {
            setOrderStatus(1);
            setReviewAction('PENDING');
            setPage(1);
            void load(1, pageSize, shopId, 1, 'PENDING');
          }}>
            重置
          </Button>
          <Button disabled={!selectedRowKeys.length} onClick={() => void startReview(rows.filter((item) => selectedRowKeys.includes(item.id)), 'APPROVE')}>
            批量同意 ({selectedRowKeys.length})
          </Button>
          <Button disabled={!profitApproveTargets.length} loading={reviewing} onClick={() => void startProfitApprove()}>
            利润≥10批量同意 ({profitApproveTargets.length})
          </Button>
          <Button danger disabled={!selectedRowKeys.length} onClick={() => void startReview(rows.filter((item) => selectedRowKeys.includes(item.id)), 'REJECT')}>
            批量拒绝 ({selectedRowKeys.length})
          </Button>
          <Button disabled={!selectedRowKeys.length} loading={reviewing} onClick={() => void batchMarkCompleted(rows.filter((item) => selectedRowKeys.includes(item.id)))}>
            批量标记已完成 ({selectedRowKeys.length})
          </Button>
          <Button danger disabled={!rows.length} onClick={openLowPriceReject}>
            低价批量拒绝
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<PriceReviewOrderVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
          }}
          scroll={{ x: 1280 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
          }}
          onChange={(pagination: TablePaginationConfig) => {
            const nextPage = pagination.current || 1;
            const nextPageSize = pagination.pageSize || 20;
            setPage(nextPage);
            setPageSize(nextPageSize);
            void load(nextPage, nextPageSize, shopId, orderStatus, reviewAction);
          }}
        />
      </Card>

      <Drawer open={detailOpen} width={920} title="核价单详情" onClose={() => setDetailOpen(false)} loading={detailLoading}>
        {detail ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card size="small">
              <Space direction="vertical" size={4}>
                <span>订单ID: {detail.orderId || '-'}</span>
                <span>站点: {formatSites(detail.siteNamesJson)}</span>
                <span>申报价: {formatPrice(detail.supplyPrice)}</span>
                <span>建议价: {formatPrice(detail.suggestSupplyPrice)}</span>
                <span>{summarizePurchasePrice(detail.skuList || [])}</span>
                <span>{summarizeCollectedPrice(detail.skuList || [])}</span>
                <span>处理状态: {reviewText(detail.reviewAction)}</span>
              </Space>
            </Card>
            <Card size="small" title="SKU 列表">
              <Table<PriceReviewSkuVO> rowKey={(record) => `${record.id}-${record.productSkuId}`} columns={detailSkuColumns} dataSource={detail.skuList || []} pagination={false} />
            </Card>
          </Space>
        ) : null}
      </Drawer>

      <Modal
        open={!!collectedSourceSku}
        title="采集价格来源"
        width={820}
        footer={null}
        onCancel={() => setCollectedSourceSku(null)}
      >
        {collectedSourceSku ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="采集价">{formatPrice(collectedSourceSku.collectedPrice)}</Descriptions.Item>
              <Descriptions.Item label="来源">{formatCollectedPriceSource(collectedSourceSku.collectedPriceSource)}</Descriptions.Item>
              <Descriptions.Item label="采集商品ID">{collectedSourceSku.collectedProductCollectionId || '-'}</Descriptions.Item>
              <Descriptions.Item label="选品池ID">{collectedSourceSku.collectedSelectionPoolId || '-'}</Descriptions.Item>
              <Descriptions.Item label="1688商品ID">{collectedSourceSku.collectedProductId || '-'}</Descriptions.Item>
              <Descriptions.Item label="SKU编码">{collectedSourceSku.collectedSkuId || collectedSourceSku.extCode || '-'}</Descriptions.Item>
              <Descriptions.Item label="SKU规格" span={2}>{collectedSourceSku.collectedSkuSpec || collectedSourceSku.specInfo || '-'}</Descriptions.Item>
              <Descriptions.Item label="商品名称" span={2}>
                {collectedSourceSku.collectedProductUrl ? (
                  <Typography.Link href={collectedSourceSku.collectedProductUrl} target="_blank">
                    {collectedSourceSku.collectedProductName || collectedSourceSku.collectedProductUrl}
                  </Typography.Link>
                ) : (
                  collectedSourceSku.collectedProductName || '-'
                )}
              </Descriptions.Item>
              <Descriptions.Item label="基础运费">{formatYuanAmount(collectedSourceSku.collectedBaseFreight)}</Descriptions.Item>
              <Descriptions.Item label="最大重量">{formatWeightG(collectedSourceSku.collectedMaxWeightG)}</Descriptions.Item>
              <Descriptions.Item label="上架日期">{formatDateTime(collectedSourceSku.collectedPublishedAt1688)}</Descriptions.Item>
              <Descriptions.Item label="推送日期">{formatDateTime(collectedSourceSku.collectedPushedAt)}</Descriptions.Item>
              <Descriptions.Item label="商家名称">{collectedSourceSku.collectedCompanyName || '-'}</Descriptions.Item>
              <Descriptions.Item label="商家地区">{collectedSourceSku.collectedCompanyLocation || '-'}</Descriptions.Item>
              <Descriptions.Item label="发货地">{collectedSourceSku.collectedShippingLocation || '-'}</Descriptions.Item>
              <Descriptions.Item label="实力商家">{collectedSourceSku.collectedMerchantPowerSeller === null || collectedSourceSku.collectedMerchantPowerSeller === undefined ? '-' : collectedSourceSku.collectedMerchantPowerSeller ? '是' : '否'}</Descriptions.Item>
              <Descriptions.Item label="复购率">{formatMaybePercent(collectedSourceSku.collectedMerchantRepeatCustomerRate)}</Descriptions.Item>
              <Descriptions.Item label="服务分">{collectedSourceSku.collectedMerchantServiceScore || '-'}</Descriptions.Item>
              <Descriptions.Item label="准时发货率">{formatMaybePercent(collectedSourceSku.collectedMerchantOnTimeDeliveryRate)}</Descriptions.Item>
              <Descriptions.Item label="好评率">{formatMaybePercent(collectedSourceSku.collectedMerchantShopPositiveRate)}</Descriptions.Item>
              <Descriptions.Item label="经营年限">{collectedSourceSku.collectedMerchantSettledYears || '-'}</Descriptions.Item>
              <Descriptions.Item label="主营业务">{collectedSourceSku.collectedMerchantMainBusiness || '-'}</Descriptions.Item>
            </Descriptions>
          </Space>
        ) : null}
      </Modal>

      <Modal
        open={lowPriceRejectOpen}
        title="低价批量拒绝"
        width={900}
        onOk={() => void submitLowPriceReject()}
        onCancel={() => setLowPriceRejectOpen(false)}
        okText={`拒绝当前 ${lowPriceRejectTargets.length} 条`}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Space wrap align="center">
            <Typography.Text>筛选当前页建议价低于等于</Typography.Text>
            <InputNumber
              min={0}
              step={1}
              precision={2}
              value={lowPriceRejectThreshold}
              onChange={(value) => setLowPriceRejectThreshold(typeof value === 'number' ? value : DEFAULT_LOW_PRICE_REJECT_THRESHOLD)}
              style={{ width: 140 }}
              addonAfter="元"
            />
            <Typography.Text type="secondary">当前命中 {lowPriceRejectTargets.length} 条</Typography.Text>
          </Space>
          <Table<PriceReviewOrderVO>
            rowKey="id"
            size="small"
            pagination={false}
            scroll={{ y: 360 }}
            locale={{ emptyText: '当前页没有符合条件的数据' }}
            dataSource={lowPriceRejectTargets}
            columns={[
              {
                title: '订单ID',
                dataIndex: 'orderId',
                key: 'orderId',
                width: 180,
                render: (value, record) => value || record.id,
              },
              {
                title: '建议价',
                dataIndex: 'suggestSupplyPrice',
                key: 'suggestSupplyPrice',
                width: 120,
                render: (value) => formatPrice(value),
              },
              {
                title: '当前申报价',
                dataIndex: 'supplyPrice',
                key: 'supplyPrice',
                width: 120,
                render: (value) => formatPrice(value),
              },
              {
                title: '采购价',
                key: 'purchasePrice',
                width: 150,
                render: (_, record) => summarizePurchasePrice(record.skuList || []),
              },
              {
                title: '采集价',
                key: 'collectedPrice',
                width: 180,
                render: (_, record) => summarizeFirstCollectedPrice(record.skuList || []),
              },
              {
                title: 'SKU',
                key: 'skuInfo',
                render: (_, record) => {
                  const firstSku = (record.skuList || [])[0];
                  if (!firstSku) {
                    return '-';
                  }
                  return (
                    <Space direction="vertical" size={2}>
                      <Typography.Text>SKU {firstSku.productSkuId || '-'}</Typography.Text>
                      <Typography.Text type="secondary">{firstSku.specInfo || '-'}</Typography.Text>
                    </Space>
                  );
                },
              },
            ]}
          />
        </Space>
      </Modal>

      <Modal
        open={reviewOpen}
        title={reviewMode === 'APPROVE' ? '核价单同意' : '核价单拒绝'}
        width={980}
        confirmLoading={reviewing}
        onOk={() => void submitReview()}
        onCancel={() => setReviewOpen(false)}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Typography.Text>本次共处理 {reviewTargets.length} 个订单。</Typography.Text>
          {reviewMode === 'REJECT' ? (
            <>
              <Form layout="vertical">
                <Form.Item label="原因类型">
                  <Select value={reasonType} onChange={setReasonType} options={REJECT_REASON_TYPE_OPTIONS} />
                </Form.Item>
                <Form.Item label="拒绝原因，每行一条">
                  <Input.TextArea rows={4} value={reasonText} onChange={(e) => setReasonText(e.target.value)} />
                </Form.Item>
                <Form.Item label="外部链接，每行一条">
                  <Input.TextArea rows={3} value={externalLinksText} onChange={(e) => setExternalLinksText(e.target.value)} />
                </Form.Item>
              </Form>
              {reviewTargets.map((item) => (
                <Card key={item.id} size="small" title={`订单 ${item.orderId || item.id}`}>
                  <Table
                    rowKey={(record) => `${item.id}-${record.productSkuId}`}
                    columns={[
                      { title: 'SKU', dataIndex: 'productSkuId', key: 'productSkuId', width: 120 },
                      { title: '规格', dataIndex: 'specInfo', key: 'specInfo' },
                      { title: '采购价', key: 'purchasePrice', width: 120, render: (_, record) => record.purchasePrice !== null && record.purchasePrice !== undefined ? formatPrice(record.purchasePrice) : '未配置' },
                      { title: '当前供货价', key: 'currentSupplyPrice', width: 140, render: (_, record) => formatPrice(record.currentSupplyPrice) },
                      {
                        title: '新的申报价(分)',
                        key: 'editNewPrice',
                        width: 180,
                        render: (_, record) => (
                          <InputNumber
                            value={record.editNewPrice}
                            onChange={(value) => updateSkuPrice(item.id, record.productSkuId, value)}
                            style={{ width: '100%' }}
                          />
                        ),
                      },
                    ]}
                    dataSource={item.skuList}
                    pagination={false}
                  />
                </Card>
              ))}
            </>
          ) : (
            <Typography.Paragraph style={{ marginBottom: 0 }}>确认将选中的核价单批量标记为“同意”吗？</Typography.Paragraph>
          )}
        </Space>
      </Modal>
    </Space>
  );
};

export default SyncPriceReviewPage;
