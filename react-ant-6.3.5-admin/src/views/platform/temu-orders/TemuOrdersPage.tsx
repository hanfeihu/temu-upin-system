import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Popover,
  Spin,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState, type ReactNode } from 'react';
import type { Dayjs } from 'dayjs';
import { syncApi } from '@/api/sync';
import { temuOrdersApi } from '@/api/temuOrders';
import { temuShopsApi } from '@/api/temuShops';
import type { TemuOrderDetailVO, TemuOrderLogisticsRefreshPayload, TemuOrderLogisticsVO, TemuOrderVO, TemuShopVO } from '@/types/api';
import { formatDateTime, formatPrice, formatTimestampMinute, prettyJson } from '@/utils/format';
import { loadStoredNumericShopFilter, resolveStoredShopFilter, saveStoredShopFilter } from '@/utils/shopFilter';
import './TemuOrdersPage.css';

const SHOP_FILTER_STORAGE_KEY = 'temu-orders';

const orderStatusText = (value?: number | null) => {
  switch (value) {
    case 1:
      return '待处理';
    case 2:
      return '待发货';
    case 3:
      return '已取消';
    case 4:
      return '已发货';
    case 5:
      return '已签收';
    case 41:
      return '部分发货';
    case 51:
      return '部分签收';
    default:
      return value ?? '-';
  }
};

const matchColor = (value?: string | null) => {
  switch (value) {
    case 'MATCHED':
      return 'green';
    case 'MATCHED_MULTI':
      return 'blue';
    case 'AMBIGUOUS':
      return 'orange';
    case 'EMPTY':
      return 'default';
    default:
      return 'red';
  }
};

const matchText = (value?: string | null) => {
  switch (value) {
    case 'MATCHED':
      return '已匹配';
    case 'MATCHED_MULTI':
      return '同 SPU 多 SKU';
    case 'AMBIGUOUS':
      return '多商品冲突';
    case 'EMPTY':
      return '无 SKU';
    case 'UNMATCHED':
      return '未匹配';
    default:
      return value || '-';
  }
};

const formatDecimalPrice = (value?: number | null) => {
  if (value === null || value === undefined) {
    return '-';
  }
  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }
  return `¥${numeric.toFixed(2)}`;
};

const formatPercent = (value?: number | null) => {
  if (value === null || value === undefined) {
    return '-';
  }
  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return '-';
  }
  return `${numeric.toFixed(2)}%`;
};

const formatMatchedLogisticsFee = (value?: number | null) => (
  value === null || value === undefined ? '-' : formatPrice(value, 1)
);

const formatPurchasePrice = (value?: number | null) => (
  value === null || value === undefined ? '未配置' : formatPrice(value)
);

const centsToYuan = (value?: number | null) => {
  if (value === null || value === undefined) {
    return null;
  }
  return Number((value / 100).toFixed(2));
};

const yuanToCents = (value?: number | null) => {
  if (value === null || value === undefined) {
    return null;
  }
  return Math.round(value * 100);
};

const parseProductSkuId = (value?: string | null) => {
  if (!value) {
    return null;
  }
  const numeric = Number(value);
  return Number.isInteger(numeric) && numeric > 0 ? numeric : null;
};

type ProfitTone = 'success' | 'danger' | 'warning';

type ProfitSummary = {
  text: string;
  tone: ProfitTone;
};

const calculateProfitCents = (record?: Pick<TemuOrderVO, 'matchedSupplyPrice' | 'matchedFirstLegLogisticsFee' | 'purchasePrice' | 'quantity'> | null) => {
  if (!record) {
    return null;
  }

  const { matchedSupplyPrice, matchedFirstLegLogisticsFee, purchasePrice, quantity } = record;
  if (
    matchedSupplyPrice === null
    || matchedSupplyPrice === undefined
    || matchedFirstLegLogisticsFee === null
    || matchedFirstLegLogisticsFee === undefined
    || purchasePrice === null
    || purchasePrice === undefined
    || quantity === null
    || quantity === undefined
  ) {
    return null;
  }

  const supplyPriceCents = Math.round(Number(matchedSupplyPrice) * 100);
  const firstLegFeeCents = Math.round(Number(matchedFirstLegLogisticsFee) * 100);
  const orderQuantity = Number(quantity);
  if (!Number.isFinite(supplyPriceCents) || !Number.isFinite(firstLegFeeCents) || !Number.isFinite(orderQuantity)) {
    return null;
  }

  return (supplyPriceCents - (firstLegFeeCents + purchasePrice)) * orderQuantity;
};

const summarizeProfit = (record?: Pick<TemuOrderVO, 'matchedSupplyPrice' | 'matchedFirstLegLogisticsFee' | 'purchasePrice' | 'quantity'> | null): ProfitSummary => {
  const profitCents = calculateProfitCents(record);
  if (profitCents === null) {
    return { text: '未配置', tone: 'warning' };
  }
  if (profitCents > 0) {
    return { text: formatPrice(profitCents), tone: 'success' };
  }
  if (profitCents < 0) {
    return { text: formatPrice(profitCents), tone: 'danger' };
  }
  return { text: formatPrice(profitCents), tone: 'warning' };
};

type FeeDetailItem = {
  fee_kind_name?: string | null;
  fee_kind_code?: string | null;
  amount?: string | number | null;
  currency_amount?: string | number | null;
  note?: string | null;
  occur_date?: string | null;
};

const parseFeeDetailItems = (value?: string | null): FeeDetailItem[] => {
  if (!value) {
    return [];
  }
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

const resolveFeeAmount = (item: FeeDetailItem) => {
  const candidate = item.amount ?? item.currency_amount;
  const numeric = Number(candidate);
  return Number.isFinite(numeric) ? numeric : null;
};

type DateRangeValue = [Dayjs, Dayjs] | null;
type AgingFilterValue = 'UNSIGNED_OVER_15_DAYS' | 'UNSHIPPED_OVER_8_DAYS' | undefined;
type NoStockProductFilterValue = 'NO_STOCK_PRODUCT' | undefined;
type TrackTimelineItem = {
  key: string;
  time: string;
  title: string;
  description?: string;
};
type TrackPreview = {
  statusName: string;
  trackingNumber: string;
  timelineItems: TrackTimelineItem[];
};
type PackageEditorTarget = Pick<TemuOrderVO, 'id' | 'parentOrderSn' | 'orderSn' | 'shopName' | 'dianxiaomiPackageNumber'>;
type PurchasePriceEditorTarget = {
  orderId: number;
  shopId: string;
  productName: string | null;
  skuSpecName: string | null;
  productSkuId: number;
  matchedTemuSkuId: string | null;
  purchasePrice: number | null;
};

const TRACK_TIME_KEYS = [
  'track_occur_date',
  'trackOccurDate',
  'track_time',
  'trackTime',
  'time',
  'process_time',
  'processTime',
  'event_time',
  'eventTime',
  'occur_time',
  'occurTime',
  'create_time',
  'createTime',
  'created_at',
  'createdAt',
  'operate_time',
  'operateTime',
  'scan_time',
  'scanTime',
];

const TRACK_TITLE_KEYS = [
  'track_description',
  'trackDescription',
  'track_description_en',
  'trackDescriptionEn',
  'track_content',
  'trackContent',
  'content',
  'description',
  'desc',
  'details',
  'detail',
  'message',
  'msg',
  'remark',
  'status_name',
  'statusName',
  'track_status_name',
  'trackStatusName',
  'event',
  'title',
];

const TRACK_EXTRA_KEYS = [
  'track_location',
  'trackLocation',
  'location',
  'address',
  'city',
  'country',
  'node',
  'site',
  'track_status_cnname',
  'trackStatusCnname',
  'track_code',
  'trackCode',
  'operator',
  'staff',
  'track_status',
  'trackStatus',
];

const firstTrackText = (value: unknown): string | undefined => {
  if (value === null || value === undefined) {
    return undefined;
  }
  if (typeof value === 'string') {
    const trimmed = value.trim();
    return trimmed || undefined;
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  return undefined;
};

const pickTrackField = (record: Record<string, unknown>, keys: string[]) => {
  for (const key of keys) {
    const value = firstTrackText(record[key]);
    if (value) {
      return value;
    }
  }
  return undefined;
};

const toTrackObjectList = (value: unknown): Record<string, unknown>[] => {
  if (Array.isArray(value)) {
    return value.filter((item): item is Record<string, unknown> => !!item && typeof item === 'object' && !Array.isArray(item));
  }
  if (!value || typeof value !== 'object') {
    return [];
  }
  const record = value as Record<string, unknown>;
  for (const key of ['details', 'tracks', 'track_list', 'trackList', 'records', 'nodes', 'events', 'list']) {
    const nested = toTrackObjectList(record[key]);
    if (nested.length) {
      return nested;
    }
  }
  return [record];
};

const parseTrackTimelineItems = (trackDetailsJson?: string | null): TrackTimelineItem[] => {
  if (!trackDetailsJson) {
    return [];
  }
  try {
    const parsed = JSON.parse(trackDetailsJson) as unknown;
    return toTrackObjectList(parsed).reduce<TrackTimelineItem[]>((items, item, index) => {
      const time = pickTrackField(item, TRACK_TIME_KEYS);
      const title = pickTrackField(item, TRACK_TITLE_KEYS);
      const extra = TRACK_EXTRA_KEYS
        .map((key) => firstTrackText(item[key]))
        .filter((value, valueIndex, list): value is string => !!value && list.indexOf(value) === valueIndex);
      if (!time && !title && extra.length === 0) {
        return items;
      }
      const description = extra.join(' / ');
      items.push({
        key: `track-${index}`,
        time: formatDateTime(time) || '-',
        title: title || description || `轨迹 ${index + 1}`,
        description: title && description ? description : undefined,
      });
      return items;
    }, []);
  } catch {
    return [];
  }
};

const buildTrackPreview = (
  logistics?: TemuOrderLogisticsVO | null,
  fallbackStatus?: string | null,
  fallbackTrackingNumber?: string | null,
): TrackPreview => ({
  statusName: logistics?.trackStatusName || fallbackStatus || '',
  trackingNumber: logistics?.trackingNumber || fallbackTrackingNumber || '',
  timelineItems: parseTrackTimelineItems(logistics?.trackDetailsJson),
});

const toRangeParams = (range: DateRangeValue) => {
  if (!range || range.length !== 2) {
    return { startMs: undefined, endMs: undefined };
  }
  return {
    startMs: range[0].startOf('day').valueOf(),
    endMs: range[1].endOf('day').valueOf(),
  };
};

const TemuOrdersPage = () => {
  const { RangePicker } = DatePicker;
  const { message } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: number; label: string; shopId: string }>>([]);
  const [shopRecordId, setShopRecordId] = useState<number | undefined>(() => loadStoredNumericShopFilter(SHOP_FILTER_STORAGE_KEY));
  const [keyword, setKeyword] = useState('');
  const [matchedTemuSkuIdLike, setMatchedTemuSkuIdLike] = useState('');
  const [agingFilter, setAgingFilter] = useState<AgingFilterValue>();
  const [cancelState, setCancelState] = useState<'ACTIVE' | 'CANCELLED' | 'ALL'>('ACTIVE');
  const [aftersaleState, setAftersaleState] = useState<'ALL' | 'REFUNDED' | 'NOT_REFUNDED'>('ALL');
  const [orderStatus, setOrderStatus] = useState<number>();
  const [matchStatus, setMatchStatus] = useState<string>();
  const [noStockProductFilter, setNoStockProductFilter] = useState<NoStockProductFilterValue>();
  const [orderTimeRange, setOrderTimeRange] = useState<DateRangeValue>(null);
  const [updateTimeRange, setUpdateTimeRange] = useState<DateRangeValue>(null);
  const [loading, setLoading] = useState(false);
  const [dianxiaomiSyncing, setDianxiaomiSyncing] = useState(false);
  const [refreshingAllLogistics, setRefreshingAllLogistics] = useState(false);
  const [rows, setRows] = useState<TemuOrderVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<TemuOrderDetailVO | null>(null);
  const [logisticsOpen, setLogisticsOpen] = useState(false);
  const [refreshingLogistics, setRefreshingLogistics] = useState(false);
  const [logisticsOrderId, setLogisticsOrderId] = useState<number | null>(null);
  const [trackPopoverOrderId, setTrackPopoverOrderId] = useState<number | null>(null);
  const [trackLoadingOrderId, setTrackLoadingOrderId] = useState<number | null>(null);
  const [trackPreviewCache, setTrackPreviewCache] = useState<Record<number, TrackPreview>>({});
  const [packageEditorTarget, setPackageEditorTarget] = useState<PackageEditorTarget | null>(null);
  const [packageNumberDraft, setPackageNumberDraft] = useState('');
  const [packageSaving, setPackageSaving] = useState(false);
  const [purchasePriceTarget, setPurchasePriceTarget] = useState<PurchasePriceEditorTarget | null>(null);
  const [purchasePriceDraft, setPurchasePriceDraft] = useState<number | null>(null);
  const [purchasePriceSaving, setPurchasePriceSaving] = useState(false);
  const [refreshingSupplyPriceOrderIds, setRefreshingSupplyPriceOrderIds] = useState<Record<number, boolean>>({});
  const [logisticsForm, setLogisticsForm] = useState<TemuOrderLogisticsRefreshPayload>({
    providerCode: 'HAOYUAN',
    referenceNo: '',
    shippingMethodNo: '',
    trackingNumber: '',
  });

  const renderFeeDetailPopover = (
    fee?: number | null,
    feeDetailJson?: string | null,
    triggerLabel?: ReactNode,
  ) => {
    const items = parseFeeDetailItems(feeDetailJson);
    const hasItems = items.length > 0;
    const total = fee ?? items.reduce<number | null>((sum, item) => {
      const amount = resolveFeeAmount(item);
      if (amount === null) {
        return sum;
      }
      return (sum ?? 0) + amount;
    }, null);

    if (!hasItems) {
      return <>{triggerLabel ?? formatDecimalPrice(total)}</>;
    }

    return (
      <Popover
        trigger="click"
        placement="leftTop"
        content={
          <Space direction="vertical" size={8} style={{ minWidth: 260 }}>
            {items.map((item, index) => {
              const amount = resolveFeeAmount(item);
              return (
                <div key={`${item.fee_kind_code || 'fee'}-${index}`}>
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Typography.Text>{item.fee_kind_name || item.fee_kind_code || `费用${index + 1}`}</Typography.Text>
                    <Typography.Text strong>{formatDecimalPrice(amount)}</Typography.Text>
                  </Space>
                  {item.note || item.occur_date ? (
                    <Typography.Text type="secondary">
                      {[item.note, item.occur_date].filter(Boolean).join(' / ')}
                    </Typography.Text>
                  ) : null}
                </div>
              );
            })}
            <Space style={{ width: '100%', justifyContent: 'space-between' }}>
              <Typography.Text strong>合计</Typography.Text>
              <Typography.Text strong>{formatDecimalPrice(total)}</Typography.Text>
            </Space>
          </Space>
        }
      >
        <Typography.Link>{triggerLabel ?? formatDecimalPrice(total)}</Typography.Link>
      </Popover>
    );
  };

  async function loadShops() {
    const res = await temuShopsApi.list({ enabled: true });
    const data = Array.isArray(res.data) ? res.data : [];
    const options = data
      .filter((item: TemuShopVO) => item.id && item.shopName)
      .map((item: TemuShopVO) => ({
        value: item.id,
        label: `${item.shopName}（${item.shopId}）`,
        shopId: item.shopId,
      }));
    setShops(options);
    const nextShopRecordId = resolveStoredShopFilter(options, shopRecordId);
    if (!nextShopRecordId) {
      setShopRecordId(undefined);
      saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, undefined);
      return;
    }
    setShopRecordId(nextShopRecordId);
    saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, nextShopRecordId);
    await load(
      1,
      pageSize,
      nextShopRecordId,
      keyword,
      matchedTemuSkuIdLike,
      agingFilter,
      cancelState,
      aftersaleState,
      orderStatus,
      matchStatus,
      noStockProductFilter,
      orderTimeRange,
      updateTimeRange,
    );
  }

  async function load(
    nextPage = page,
    nextPageSize = pageSize,
    nextShopRecordId = shopRecordId,
    nextKeyword = keyword,
    nextMatchedTemuSkuIdLike = matchedTemuSkuIdLike,
    nextAgingFilter = agingFilter,
    nextCancelState = cancelState,
    nextAftersaleState = aftersaleState,
    nextOrderStatus = orderStatus,
    nextMatchStatus = matchStatus,
    nextNoStockProductFilter = noStockProductFilter,
    nextOrderTimeRange = orderTimeRange,
    nextUpdateTimeRange = updateTimeRange,
  ) {
    if (!nextShopRecordId) {
      return;
    }
    setLoading(true);
    try {
      const orderTimeParams = toRangeParams(nextOrderTimeRange);
      const updateTimeParams = toRangeParams(nextUpdateTimeRange);
      const res = await temuOrdersApi.list({
        shopRecordId: nextShopRecordId,
        keyword: nextKeyword.trim() || undefined,
        matchedTemuSkuIdLike: nextMatchedTemuSkuIdLike.trim() || undefined,
        agingFilter: nextAgingFilter,
        cancelState: nextCancelState === 'ALL' ? undefined : nextCancelState,
        aftersaleState: nextAftersaleState === 'ALL' ? undefined : nextAftersaleState,
        orderStatus: nextOrderStatus,
        matchStatus: nextMatchStatus || undefined,
        noStockProduct: nextNoStockProductFilter === 'NO_STOCK_PRODUCT' ? true : undefined,
        orderTimeStartMs: orderTimeParams.startMs,
        orderTimeEndMs: orderTimeParams.endMs,
        updateTimeStartMs: updateTimeParams.startMs,
        updateTimeEndMs: updateTimeParams.endMs,
        page: nextPage,
        pageSize: nextPageSize,
      });
      const content = Array.isArray(res.data.content) ? [...res.data.content] : [];
      content.sort((left, right) => {
        const diff = (right.orderTimeMs ?? 0) - (left.orderTimeMs ?? 0);
        if (diff !== 0) {
          return diff;
        }
        return (right.id ?? 0) - (left.id ?? 0);
      });
      setRows(content);
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载订单失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadShops();
  }, []);

  async function openDetail(record: TemuOrderVO) {
    setDetailOpen(true);
    await reloadDetail(record.id);
  }

  async function reloadDetail(orderId: number) {
    setDetailLoading(true);
    try {
      const res = await temuOrdersApi.detail(orderId);
      setDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载订单详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  async function reloadOrderContext(targetOrderId?: number | null) {
    await load();
    if (targetOrderId && detailOpen && detail?.id === targetOrderId) {
      await reloadDetail(targetOrderId);
    }
  }

  async function syncDianxiaomiOrders() {
    if (!shopRecordId) {
      message.warning('请先选择店铺');
      return;
    }
    setDianxiaomiSyncing(true);
    try {
      const res = await temuOrdersApi.syncFromDianxiaomi({
        shopRecordId,
        pageSize: 100,
        maxPages: 1000,
      });
      const results = Array.isArray(res.data) ? res.data : [];
      const first = results[0];
      if (!first?.success) {
        message.error(first?.message || '店小秘订单同步失败');
        return;
      }
      message.success(`店小秘订单同步完成：新增 ${first.createdCount || 0}，更新 ${first.updatedCount || 0}`);
      setPage(1);
      await load(1);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '店小秘订单同步失败');
    } finally {
      setDianxiaomiSyncing(false);
    }
  }

  async function refreshAllLogistics() {
    if (!shopRecordId) {
      message.warning('请先选择店铺');
      return;
    }
    setRefreshingAllLogistics(true);
    try {
      const res = await temuOrdersApi.refreshAllLogistics({ shopRecordId });
      message.success(`全部订单物流刷新完成：刷新 ${res.data ?? 0} 个订单`);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '刷新全部订单物流失败');
    } finally {
      setRefreshingAllLogistics(false);
    }
  }

  function openPackageEditor(record: Pick<TemuOrderVO, 'id' | 'parentOrderSn' | 'orderSn' | 'shopName' | 'dianxiaomiPackageNumber'>) {
    setPackageEditorTarget({
      id: record.id,
      parentOrderSn: record.parentOrderSn,
      orderSn: record.orderSn,
      shopName: record.shopName,
      dianxiaomiPackageNumber: record.dianxiaomiPackageNumber,
    });
    setPackageNumberDraft(record.dianxiaomiPackageNumber || '');
  }

  async function submitPackageNumber() {
    if (!packageEditorTarget) {
      return;
    }
    const packageNumber = packageNumberDraft.trim();
    if (!packageNumber) {
      message.warning('请输入店小秘单号');
      return;
    }

    setPackageSaving(true);
    try {
      await temuOrdersApi.updateDianxiaomiPackageNumber(packageEditorTarget.id, { packageNumber });
      message.success('店小秘单号已保存');
      setPackageEditorTarget(null);
      setPackageNumberDraft('');
      await reloadOrderContext(packageEditorTarget.id);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存店小秘单号失败');
    } finally {
      setPackageSaving(false);
    }
  }

  function buildPurchasePriceTarget(record: Pick<TemuOrderVO, 'id' | 'shopId' | 'goodsName' | 'matchedSkuSpecName' | 'matchedTemuSkuId' | 'purchasePrice'>) {
    const productSkuId = parseProductSkuId(record.matchedTemuSkuId);
    if (!productSkuId || !record.shopId) {
      return null;
    }
    return {
      orderId: record.id,
      shopId: record.shopId,
      productName: record.goodsName,
      skuSpecName: record.matchedSkuSpecName,
      productSkuId,
      matchedTemuSkuId: record.matchedTemuSkuId,
      purchasePrice: record.purchasePrice,
    } satisfies PurchasePriceEditorTarget;
  }

  function openPurchasePriceEditor(record: Pick<TemuOrderVO, 'id' | 'shopId' | 'goodsName' | 'matchedSkuSpecName' | 'matchedTemuSkuId' | 'purchasePrice'>) {
    const target = buildPurchasePriceTarget(record);
    if (!target) {
      message.warning('当前订单没有可维护采购价的匹配 SKU');
      return;
    }
    setPurchasePriceTarget(target);
    setPurchasePriceDraft(centsToYuan(record.purchasePrice));
  }

  async function submitPurchasePrice() {
    if (!purchasePriceTarget) {
      return;
    }
    const purchasePrice = yuanToCents(purchasePriceDraft);
    if (purchasePrice === null || purchasePrice === undefined) {
      message.warning('请填写采购价');
      return;
    }

    setPurchasePriceSaving(true);
    try {
      await syncApi.updateShopSkuPurchasePrice(purchasePriceTarget.productSkuId, {
        shopId: purchasePriceTarget.shopId,
        purchasePrice,
      });
      message.success('采购价已保存');
      setPurchasePriceTarget(null);
      setPurchasePriceDraft(null);
      await reloadOrderContext(purchasePriceTarget.orderId);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存采购价失败');
    } finally {
      setPurchasePriceSaving(false);
    }
  }

  async function refreshSupplyPrice(record: Pick<TemuOrderVO, 'id' | 'shopId' | 'matchedTemuSkuId'>) {
    const productSkuId = parseProductSkuId(record.matchedTemuSkuId);
    if (!productSkuId || !record.shopId) {
      message.warning('当前订单没有可刷新的匹配 SKU');
      return;
    }

    setRefreshingSupplyPriceOrderIds((current) => ({ ...current, [record.id]: true }));
    try {
      await syncApi.refreshShopSkuSupplierPrice(productSkuId, { shopId: record.shopId });
      message.success('供货价已同步');
      await reloadOrderContext(record.id);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '刷新供货价失败');
    } finally {
      setRefreshingSupplyPriceOrderIds((current) => {
        const next = { ...current };
        delete next[record.id];
        return next;
      });
    }
  }

  const renderPackageNumberTrigger = (
    record: Pick<TemuOrderVO, 'id' | 'parentOrderSn' | 'orderSn' | 'shopName' | 'dianxiaomiPackageNumber'>,
    options?: { prefix?: string; emptyText?: string },
  ) => {
    const text = record.dianxiaomiPackageNumber
      ? `${options?.prefix ?? ''}${record.dianxiaomiPackageNumber}`
      : options?.emptyText ?? '店小秘单号未回填';
    return (
      <Typography.Link className="temu-orders-inline-edit-link" onClick={() => openPackageEditor(record)}>
        {text}
      </Typography.Link>
    );
  };

  const renderPurchasePriceValue = (
    record: Pick<TemuOrderVO, 'id' | 'shopId' | 'goodsName' | 'matchedSkuSpecName' | 'matchedTemuSkuId' | 'purchasePrice'>,
  ) => {
    const text = formatPurchasePrice(record.purchasePrice);
    return buildPurchasePriceTarget(record) ? (
      <Typography.Link className="temu-orders-inline-action-link" onClick={() => openPurchasePriceEditor(record)}>
        {text}
      </Typography.Link>
    ) : (
      <strong>{text}</strong>
    );
  };

  const renderSupplyPriceValue = (
    record: Pick<TemuOrderVO, 'id' | 'shopId' | 'matchedTemuSkuId' | 'matchedSupplyPrice'>,
  ) => {
    if (record.matchedSupplyPrice !== null && record.matchedSupplyPrice !== undefined) {
      return <strong>{formatDecimalPrice(record.matchedSupplyPrice)}</strong>;
    }
    const productSkuId = parseProductSkuId(record.matchedTemuSkuId);
    if (!productSkuId || !record.shopId) {
      return <strong>未同步</strong>;
    }
    return (
      <span className="temu-orders-inline-action-group">
        <strong>未同步</strong>
        <Button
          type="link"
          size="small"
          className="temu-orders-inline-refresh-btn"
          loading={!!refreshingSupplyPriceOrderIds[record.id]}
          onClick={() => void refreshSupplyPrice(record)}
        >
          刷新
        </Button>
      </span>
    );
  };

  async function ensureTrackPreview(record: TemuOrderVO) {
    if (detail?.id === record.id) {
      setTrackPreviewCache((current) => ({
        ...current,
        [record.id]: buildTrackPreview(detail.logistics, record.logisticsTrackStatusName, record.logisticsTrackingNumber),
      }));
      return;
    }

    if (trackPreviewCache[record.id] || trackLoadingOrderId === record.id) {
      return;
    }

    setTrackLoadingOrderId(record.id);
    try {
      const res = await temuOrdersApi.detail(record.id);
      setTrackPreviewCache((current) => ({
        ...current,
        [record.id]: buildTrackPreview(res.data?.logistics, record.logisticsTrackStatusName, record.logisticsTrackingNumber),
      }));
    } catch (error) {
      setTrackPreviewCache((current) => ({
        ...current,
        [record.id]: buildTrackPreview(undefined, record.logisticsTrackStatusName, record.logisticsTrackingNumber),
      }));
      message.error(error instanceof Error ? error.message : '加载物流轨迹失败');
    } finally {
      setTrackLoadingOrderId((current) => (current === record.id ? null : current));
    }
  }

  const renderTrackPopoverContent = (preview?: TrackPreview, loading?: boolean) => {
    if (loading) {
      return (
        <div className="temu-orders-track-popover-body">
          <div className="temu-orders-track-popover-loading">
            <Spin size="small" />
            <Typography.Text type="secondary">加载物流轨迹中...</Typography.Text>
          </div>
        </div>
      );
    }

    return (
      <div className="temu-orders-track-popover-body">
        <Descriptions column={2} bordered size="small" className="temu-orders-track-summary">
          <Descriptions.Item label="轨迹状态">{preview?.statusName || '-'}</Descriptions.Item>
          <Descriptions.Item label="运单号">{preview?.trackingNumber || '-'}</Descriptions.Item>
        </Descriptions>

        {preview?.timelineItems?.length ? (
          <div className="temu-orders-track-timeline-wrap">
            <Timeline mode="left">
              {preview.timelineItems.map((item) => (
                <Timeline.Item key={item.key} label={item.time}>
                  <Typography.Text strong>{item.title}</Typography.Text>
                  {item.description ? (
                    <div className="temu-orders-track-description">
                      <Typography.Text type="secondary">{item.description}</Typography.Text>
                    </div>
                  ) : null}
                </Timeline.Item>
              ))}
            </Timeline>
          </div>
        ) : (
          <Empty description="暂无物流轨迹" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </div>
    );
  };

  const renderTrackPopoverTrigger = (
    record: TemuOrderVO,
    label?: ReactNode,
    previewOverride?: TrackPreview,
  ) => {
    if (!record.logisticsTrackStatusName && !record.logisticsTrackingNumber && !record.dianxiaomiPackageNumber && !previewOverride) {
      return <Typography.Text type="secondary">-</Typography.Text>;
    }

    const preview = previewOverride ?? trackPreviewCache[record.id];
    const loading = trackLoadingOrderId === record.id;

    return (
      <Popover
        trigger="hover"
        placement="leftTop"
        mouseEnterDelay={0.15}
        overlayClassName="temu-orders-track-popover"
        open={trackPopoverOrderId === record.id}
        onOpenChange={(open) => {
          setTrackPopoverOrderId((current) => (open ? record.id : current === record.id ? null : current));
          if (open) {
            void ensureTrackPreview(record);
          }
        }}
        content={renderTrackPopoverContent(preview, loading)}
      >
        <Typography.Link ellipsis>{label ?? record.logisticsTrackStatusName ?? '查看轨迹'}</Typography.Link>
      </Popover>
    );
  };

  function openLogisticsModal(record: TemuOrderVO) {
    setLogisticsOrderId(record.id);
    setLogisticsForm({
      providerCode: 'HAOYUAN',
      referenceNo:
        detail?.id === record.id
          ? detail?.logistics?.referenceNo || detail?.dianxiaomiPackageNumber || ''
          : record.dianxiaomiPackageNumber || '',
      shippingMethodNo:
        detail?.id === record.id
          ? detail?.dianxiaomiPackageNumber || detail?.logistics?.shippingMethodNo || ''
          : record.dianxiaomiPackageNumber || '',
      trackingNumber: record.logisticsTrackingNumber || '',
    });
    setLogisticsOpen(true);
  }

  async function refreshLogistics() {
    if (!logisticsOrderId) {
      return;
    }
    setRefreshingLogistics(true);
    try {
      await temuOrdersApi.refreshLogistics(logisticsOrderId, logisticsForm);
      message.success('物流信息已刷新');
      setLogisticsOpen(false);
      await load();
      if (detail?.id === logisticsOrderId) {
        const res = await temuOrdersApi.detail(logisticsOrderId);
        setDetail(res.data);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '刷新物流失败');
    } finally {
      setRefreshingLogistics(false);
    }
  }

  const columns: ColumnsType<TemuOrderVO> = [
    {
      title: '订单',
      key: 'order',
      width: 300,
      render: (_, record) => (
        <div className="temu-orders-cell temu-orders-order-cell">
          <Typography.Text strong ellipsis>
            {record.parentOrderSn || '-'}
          </Typography.Text>
          {renderPackageNumberTrigger(record, { prefix: record.dianxiaomiPackageNumber ? '店小秘 ' : undefined })}
          <Typography.Text type="secondary" ellipsis>
            {record.orderSn || '-'}
          </Typography.Text>
          <Typography.Text type="secondary" ellipsis>
            {record.shopName || record.shopId}
          </Typography.Text>
          <Tag>{orderStatusText(record.orderStatus)}</Tag>
        </div>
      ),
    },
    {
      title: '商品',
      key: 'goods',
      width: 420,
      render: (_, record) => (
        <div className="temu-orders-goods">
          {record.thumbUrl ? (
            <Image
              width={48}
              height={48}
              src={record.thumbUrl}
              alt={record.goodsName || '商品预览图'}
              style={{ borderRadius: 6, objectFit: 'cover', cursor: 'pointer' }}
            />
          ) : null}
          <div className="temu-orders-goods-content">
            <Typography.Paragraph className="temu-orders-title" ellipsis={{ rows: 2, tooltip: record.goodsName || '-' }}>
              {record.goodsName || '-'}
            </Typography.Paragraph>
            <Typography.Paragraph className="temu-orders-subtext" ellipsis={{ rows: 2, tooltip: record.spec || '-' }}>
              {record.spec || '-'}
            </Typography.Paragraph>
            <Typography.Text type="secondary">数量 {record.quantity ?? '-'}</Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: '匹配',
      key: 'match',
      width: 360,
      render: (_, record) => {
        const profitSummary = summarizeProfit(record);
        return (
          <div className="temu-orders-cell temu-orders-match-cell">
            <Tag color={matchColor(record.matchStatus)}>{matchText(record.matchStatus)}</Tag>
            <Typography.Paragraph
              className="temu-orders-title temu-orders-match-title"
              ellipsis={{ rows: 2, tooltip: record.matchedSkuSpecName || '-' }}
            >
              {record.matchedSkuSpecName || '-'}
            </Typography.Paragraph>
            <div className="temu-orders-match-meta-row">
              <Typography.Text type="secondary" ellipsis>
                SKUID {record.matchedTemuSkuId || '-'}
              </Typography.Text>
              <Typography.Text type="secondary" ellipsis>
                货品编码 {record.matchedOriginSkuId || '-'}
              </Typography.Text>
            </div>

            <div className="temu-orders-match-values">
              <div className="temu-orders-match-value-row">
                <span className="temu-orders-match-inline">
                  <span className="temu-orders-match-label">供货价</span>
                  {renderSupplyPriceValue(record)}
                </span>
                <span className="temu-orders-match-inline">
                  <span className="temu-orders-match-label">头程费用</span>
                  <strong>{formatMatchedLogisticsFee(record.matchedFirstLegLogisticsFee)}</strong>
                </span>
              </div>
              <div className="temu-orders-match-value-row">
                <span className="temu-orders-match-inline">
                  <span className="temu-orders-match-label">采购价</span>
                  {renderPurchasePriceValue(record)}
                </span>
                <span className="temu-orders-match-inline">
                  <span className="temu-orders-match-label">利润</span>
                  <strong className={`temu-orders-match-profit-text temu-orders-match-profit-text-${profitSummary.tone}`}>
                    {profitSummary.text}
                  </strong>
                </span>
              </div>
            </div>

            <div className="temu-orders-match-stats">
              <Typography.Text type="secondary">
                数量 {record.quantity ?? '-'} · 销量 {record.salesQuantity ?? 0} · 售后 {record.aftersaleQuantity ?? 0}
              </Typography.Text>
              <Typography.Text type="secondary">
                签收 {record.signedQuantity ?? 0} · 签收售后 {record.signedAftersaleQuantity ?? 0} · 售后率 {formatPercent(record.aftersaleRate)}
              </Typography.Text>
            </div>
          </div>
        );
      },
    },
    {
      title: '物流',
      key: 'logistics',
      width: 290,
      render: (_, record) => (
        <div className="temu-orders-cell">
          <Typography.Text ellipsis>{record.dianxiaomiPackageNumber || '-'}</Typography.Text>
          <Typography.Text type="secondary" ellipsis>
            {record.logisticsTrackingNumber ? `运单 ${record.logisticsTrackingNumber}` : '运单号未同步'}
          </Typography.Text>
          <div className="temu-orders-logistics-status-row">
            <Typography.Text type="secondary">轨迹状态</Typography.Text>
            {renderTrackPopoverTrigger(record)}
          </div>
          <Space size={4} wrap>
            <Typography.Text type="secondary">头程</Typography.Text>
            {renderFeeDetailPopover(record.firstLegLogisticsFee, record.orderFeeDetailJson)}
            <Typography.Text type="secondary">/ {record.chargeWeight ? `${record.chargeWeight}kg` : '-'}</Typography.Text>
          </Space>
        </div>
      ),
    },
    {
      title: '订单创建时间',
      key: 'time',
      width: 220,
      render: (_, record) => <Typography.Text>{formatTimestampMinute(record.orderTimeMs)}</Typography.Text>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 168,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          <Button size="small" onClick={() => openLogisticsModal(record)}>
            刷新物流
          </Button>
        </Space>
      ),
    },
  ];

  const detailProfitSummary = summarizeProfit(detail);

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Select
            value={shopRecordId}
            onChange={(value) => {
              setShopRecordId(value);
              saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, value);
              setPage(1);
              void load(
                1,
                pageSize,
                value,
                keyword,
                matchedTemuSkuIdLike,
                agingFilter,
                cancelState,
                aftersaleState,
                orderStatus,
                matchStatus,
                noStockProductFilter,
                orderTimeRange,
                updateTimeRange,
              );
            }}
            options={shops}
            style={{ width: 280 }}
            placeholder="请选择店铺"
            showSearch
            optionFilterProp="label"
          />
          <Segmented
            value={cancelState}
            onChange={(value) => {
              const nextValue = value as 'ACTIVE' | 'CANCELLED' | 'ALL';
              setCancelState(nextValue);
              if (nextValue === 'CANCELLED' && orderStatus !== undefined && orderStatus !== 3) {
                setOrderStatus(undefined);
              }
              if (nextValue === 'ACTIVE' && orderStatus === 3) {
                setOrderStatus(undefined);
              }
              setPage(1);
              void load(
                1,
                pageSize,
                shopRecordId,
                keyword,
                matchedTemuSkuIdLike,
                agingFilter,
                nextValue,
                aftersaleState,
                nextValue === 'CANCELLED' && orderStatus !== undefined && orderStatus !== 3
                  ? undefined
                  : nextValue === 'ACTIVE' && orderStatus === 3
                    ? undefined
                    : orderStatus,
                matchStatus,
                noStockProductFilter,
                orderTimeRange,
                updateTimeRange,
              );
            }}
            options={[
              { label: '未取消', value: 'ACTIVE' },
              { label: '已取消', value: 'CANCELLED' },
              { label: '全部', value: 'ALL' },
            ]}
          />
          <Segmented
            value={aftersaleState}
            onChange={(value) => {
              const nextValue = value as 'ALL' | 'REFUNDED' | 'NOT_REFUNDED';
              setAftersaleState(nextValue);
              setPage(1);
              void load(
                1,
                pageSize,
                shopRecordId,
                keyword,
                matchedTemuSkuIdLike,
                agingFilter,
                cancelState,
                nextValue,
                orderStatus,
                matchStatus,
                noStockProductFilter,
                orderTimeRange,
                updateTimeRange,
              );
            }}
            options={[
              { label: '未售后', value: 'NOT_REFUNDED' },
              { label: '已售后', value: 'REFUNDED' },
              { label: '全部', value: 'ALL' },
            ]}
          />
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="PO号 / 店小秘单号 / 子订单号 / 商品名"
            style={{ width: 240 }}
          />
          <Input
            value={matchedTemuSkuIdLike}
            onChange={(event) => setMatchedTemuSkuIdLike(event.target.value)}
            placeholder="SKUID 模糊筛选"
            style={{ width: 180 }}
          />
          <Select
            allowClear
            value={agingFilter}
            onChange={(value) => setAgingFilter(value)}
            style={{ width: 190 }}
            placeholder="时效筛选"
            options={[
              { value: 'UNSIGNED_OVER_15_DAYS', label: '超过15天未签收' },
              { value: 'UNSHIPPED_OVER_8_DAYS', label: '超过8天未发货' },
            ]}
          />
          <Select
            allowClear
            value={orderStatus}
            onChange={(value) => {
              setOrderStatus(value);
              if (value === 3) {
                setCancelState('CANCELLED');
              } else if (value !== undefined && value !== null) {
                setCancelState('ACTIVE');
              }
            }}
            style={{ width: 160 }}
            placeholder="订单状态"
            options={[
              { value: 1, label: '待处理' },
              { value: 2, label: '待发货' },
              { value: 3, label: '已取消' },
              { value: 4, label: '已发货' },
              { value: 5, label: '已签收' },
              { value: 41, label: '部分发货' },
              { value: 51, label: '部分签收' },
            ]}
          />
          <Select
            allowClear
            value={matchStatus}
            onChange={(value) => setMatchStatus(value)}
            style={{ width: 160 }}
            placeholder="匹配状态"
            options={[
              { value: 'MATCHED', label: '已匹配' },
              { value: 'MATCHED_MULTI', label: '同 SPU 多 SKU' },
              { value: 'UNMATCHED', label: '未匹配' },
              { value: 'AMBIGUOUS', label: '多商品冲突' },
              { value: 'EMPTY', label: '无 SKU' },
            ]}
          />
          <Select
            allowClear
            value={noStockProductFilter}
            onChange={(value) => setNoStockProductFilter(value)}
            style={{ width: 160 }}
            placeholder="库存筛选"
            options={[
              { value: 'NO_STOCK_PRODUCT', label: '无库存产品' },
            ]}
          />
          <Space size={8}>
            <Typography.Text type="secondary">下单时间</Typography.Text>
            <RangePicker
              value={orderTimeRange ?? undefined}
              onChange={(value) => setOrderTimeRange((value as DateRangeValue) ?? null)}
              placeholder={['开始日期', '结束日期']}
            />
          </Space>
          <Space size={8}>
            <Typography.Text type="secondary">更新时间</Typography.Text>
            <RangePicker
              value={updateTimeRange ?? undefined}
              onChange={(value) => setUpdateTimeRange((value as DateRangeValue) ?? null)}
              placeholder={['开始日期', '结束日期']}
            />
          </Space>
          <Button
            type="primary"
            onClick={() => {
              setPage(1);
              void load(1);
            }}
          >
            搜索
          </Button>
          <Button loading={dianxiaomiSyncing} disabled={!shopRecordId} onClick={() => void syncDianxiaomiOrders()}>
            同步店小秘订单
          </Button>
          <Button loading={refreshingAllLogistics} disabled={!shopRecordId} onClick={() => void refreshAllLogistics()}>
            刷新全部订单物流
          </Button>
          <Button
            onClick={() => {
              setKeyword('');
              setMatchedTemuSkuIdLike('');
              setAgingFilter(undefined);
              setCancelState('ACTIVE');
              setAftersaleState('ALL');
              setOrderStatus(undefined);
              setMatchStatus(undefined);
              setNoStockProductFilter(undefined);
              setOrderTimeRange(null);
              setUpdateTimeRange(null);
              setPage(1);
              void load(1, pageSize, shopRecordId, '', '', undefined, 'ACTIVE', 'ALL', undefined, undefined, undefined, null, null);
            }}
          >
            重置
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<TemuOrderVO>
          className="temu-orders-table"
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          tableLayout="fixed"
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
          }}
          scroll={{ x: 1720 }}
          onChange={(pagination: TablePaginationConfig) => {
            const nextPage = pagination.current || 1;
            const nextPageSize = pagination.pageSize || 20;
            setPage(nextPage);
            setPageSize(nextPageSize);
            void load(nextPage, nextPageSize);
          }}
        />
      </Card>

      <Drawer
        open={detailOpen}
        title={detail?.parentOrderSn || '订单详情'}
        width={920}
        onClose={() => setDetailOpen(false)}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Card loading={detailLoading}>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="店铺">{detail?.shopName || detail?.shopId || '-'}</Descriptions.Item>
              <Descriptions.Item label="订单状态">{orderStatusText(detail?.orderStatus)}</Descriptions.Item>
              <Descriptions.Item label="父订单号">{detail?.parentOrderSn || '-'}</Descriptions.Item>
              <Descriptions.Item label="店小秘单号">
                {detail ? renderPackageNumberTrigger(detail, { emptyText: '店小秘单号未回填' }) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="子订单号">{detail?.orderSn || '-'}</Descriptions.Item>
              <Descriptions.Item label="商品">{detail?.goodsName || '-'}</Descriptions.Item>
              <Descriptions.Item label="规格">{detail?.spec || '-'}</Descriptions.Item>
              <Descriptions.Item label="SKU规格名">{detail?.matchedSkuSpecName || '-'}</Descriptions.Item>
              <Descriptions.Item label="货品编码">{detail?.matchedOriginSkuId || '-'}</Descriptions.Item>
              <Descriptions.Item label="匹配结果">
                <Tag color={matchColor(detail?.matchStatus)}>{matchText(detail?.matchStatus)}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="供货价">
                {detail ? renderSupplyPriceValue(detail) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="采购价">
                {detail ? renderPurchasePriceValue(detail) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="匹配头程费用">{formatMatchedLogisticsFee(detail?.matchedFirstLegLogisticsFee)}</Descriptions.Item>
              <Descriptions.Item label="利润">
                <Typography.Text type={detailProfitSummary.tone}>{detailProfitSummary.text}</Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label="下单时间">{formatTimestampMinute(detail?.orderTimeMs)}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatTimestampMinute(detail?.updateTimeMs)}</Descriptions.Item>
            </Descriptions>
          </Card>

          <Card title="物流快照">
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="服务商">{detail?.logistics?.providerName || '-'}</Descriptions.Item>
              <Descriptions.Item label="店小秘查询号">
                {detail?.logistics?.referenceNo || detail?.dianxiaomiPackageNumber || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="浩远服务单号">{detail?.logistics?.shippingMethodNo || '-'}</Descriptions.Item>
              <Descriptions.Item label="运单号">{detail?.logistics?.trackingNumber || '-'}</Descriptions.Item>
              <Descriptions.Item label="轨迹状态">
                {detail
                  ? renderTrackPopoverTrigger(
                      detail,
                      detail?.logistics?.trackStatusName || '查看轨迹',
                      buildTrackPreview(detail.logistics, detail.logistics?.trackStatusName, detail.logistics?.trackingNumber),
                    )
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="头程运费">
                {renderFeeDetailPopover(
                  detail?.logistics?.firstLegLogisticsFee,
                  detail?.logistics?.orderFeeDetailJson,
                  formatDecimalPrice(detail?.logistics?.firstLegLogisticsFee),
                )}
              </Descriptions.Item>
              <Descriptions.Item label="计费重">{detail?.logistics?.chargeWeight ? `${detail.logistics.chargeWeight}kg` : '-'}</Descriptions.Item>
            </Descriptions>
          </Card>

          <Card title="TEMU SKU 原始数据">
            <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{prettyJson(detail?.productSkusJson)}</pre>
          </Card>

          <Card title="订单原始返回">
            <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{prettyJson(detail?.rawJson)}</pre>
          </Card>
        </Space>
      </Drawer>

      <Modal
        open={logisticsOpen}
        title="刷新浩远物流"
        confirmLoading={refreshingLogistics}
        onOk={() => void refreshLogistics()}
        onCancel={() => setLogisticsOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="物流服务商">
            <Input value="HAOYUAN" disabled />
          </Form.Item>
          <Form.Item label="浩远查询号">
            <Input
              value={logisticsForm.shippingMethodNo}
              onChange={(event) => setLogisticsForm((current) => ({ ...current, shippingMethodNo: event.target.value }))}
              placeholder="默认优先使用店小秘单号，可手动覆盖"
            />
          </Form.Item>
          <Form.Item label="参考号">
            <Input
              value={logisticsForm.referenceNo}
              onChange={(event) => setLogisticsForm((current) => ({ ...current, referenceNo: event.target.value }))}
              placeholder="可选，只有拿到真实参考号时才填写"
            />
          </Form.Item>
          <Form.Item label="运单号">
            <Input
              value={logisticsForm.trackingNumber}
              onChange={(event) => setLogisticsForm((current) => ({ ...current, trackingNumber: event.target.value }))}
              placeholder="可留空，存在历史值时会沿用"
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={!!packageEditorTarget}
        title="填写店小秘单号"
        confirmLoading={packageSaving}
        onOk={() => void submitPackageNumber()}
        onCancel={() => {
          if (!packageSaving) {
            setPackageEditorTarget(null);
            setPackageNumberDraft('');
          }
        }}
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Alert
            type="info"
            showIcon
            message={packageEditorTarget?.parentOrderSn ? `父订单 ${packageEditorTarget.parentOrderSn}` : `子订单 ${packageEditorTarget?.orderSn || '-'}`}
            description="手动保存后，会同步回写当前订单和同父单下的物流查询号。"
          />
          <Form layout="vertical">
            <Form.Item label="店小秘单号" required>
              <Input
                value={packageNumberDraft}
                onChange={(event) => setPackageNumberDraft(event.target.value)}
                placeholder="请输入店小秘单号"
              />
            </Form.Item>
          </Form>
        </Space>
      </Modal>

      <Modal
        open={!!purchasePriceTarget}
        title="填写采购价"
        width={720}
        confirmLoading={purchasePriceSaving}
        onOk={() => void submitPurchasePrice()}
        onCancel={() => {
          if (!purchasePriceSaving) {
            setPurchasePriceTarget(null);
            setPurchasePriceDraft(null);
          }
        }}
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Alert
            type="info"
            showIcon
            message={purchasePriceTarget?.productName || '填写当前订单采购价'}
            description="保存后会同步写入“店铺 SKU”里的采购价，逻辑与调价单管理一致。"
          />
          <Table<PurchasePriceEditorTarget>
            className="temu-orders-modal-table"
            rowKey="productSkuId"
            size="small"
            pagination={false}
            dataSource={purchasePriceTarget ? [purchasePriceTarget] : []}
            columns={[
              {
                title: 'SKUID',
                dataIndex: 'matchedTemuSkuId',
                key: 'matchedTemuSkuId',
                width: 160,
                render: (value: string | null) => value || '-',
              },
              {
                title: 'SKU规格',
                dataIndex: 'skuSpecName',
                key: 'skuSpecName',
                render: (value: string | null) => value || '-',
              },
              {
                title: '当前采购价',
                dataIndex: 'purchasePrice',
                key: 'purchasePrice',
                width: 120,
                render: (value: number | null) => (value === null || value === undefined ? '未配置' : formatPrice(value)),
              },
              {
                title: '新采购价',
                key: 'draftPurchasePrice',
                width: 180,
                render: () => (
                  <InputNumber
                    min={0}
                    precision={2}
                    value={purchasePriceDraft ?? undefined}
                    onChange={(value) => setPurchasePriceDraft(value ?? null)}
                    style={{ width: '100%' }}
                    placeholder="输入采购价"
                    addonBefore="¥"
                  />
                ),
              },
            ]}
          />
        </Space>
      </Modal>
    </Space>
  );
};

export default TemuOrdersPage;
