import { Alert, App, Button, Card, Drawer, Form, Image, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { Key } from 'react';
import { useEffect, useState } from 'react';
import { syncApi } from '@/api/sync';
import { temuOrdersApi } from '@/api/temuOrders';
import { temuShopsApi } from '@/api/temuShops';
import type { PriceAdjustOrderVO, PriceAdjustSkuVO, TemuShopVO } from '@/types/api';
import { formatDateTime, formatPrice } from '@/utils/format';
import { loadStoredShopFilter, resolveStoredShopFilter, saveStoredShopFilter } from '@/utils/shopFilter';

const statusMap: Record<number, string> = {
  0: '待核价',
  1: '待供应商确认',
  2: '调价成功',
  3: '调价失败',
};

const reviewTextMap: Record<string, string> = {
  PENDING: '待处理',
  APPROVE: '已同意',
  APPROVED: '已同意',
  REJECT: '已拒绝',
  REJECTED: '已拒绝',
};

function summarizePurchasePrice(skus?: PriceAdjustSkuVO[] | null) {
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
    return `采购价 ${formatPrice(prices[0])}`;
  }

  return `采购价 ${formatPrice(Math.min(...prices))} 起`;
}

function formatLogisticsFee(value?: number | null) {
  return value === null || value === undefined ? '-' : formatPrice(value, 1);
}

function calculateProfitCents(newSupplyPrice: number | string | null | undefined, sku?: PriceAdjustSkuVO | null) {
  const nextSupplyPrice = parsePriceValue(newSupplyPrice);
  if (
    nextSupplyPrice === null
    || !sku
    || sku.purchasePrice === null
    || sku.purchasePrice === undefined
    || sku.firstLegLogisticsFee === null
    || sku.firstLegLogisticsFee === undefined
  ) {
    return null;
  }

  return Math.round(nextSupplyPrice) - (sku.purchasePrice + Math.round(Number(sku.firstLegLogisticsFee) * 100) + 200);
}

function summarizeProfit(newSupplyPrice: number | string | null | undefined, skus?: PriceAdjustSkuVO[] | null) {
  const rows = skus || [];
  if (!rows.length) {
    return '利润：缺少数据';
  }

  const profits: number[] = [];
  for (const sku of rows) {
    const profit = calculateProfitCents(newSupplyPrice, sku);
    if (profit === null) {
      return '利润：缺少数据';
    }
    profits.push(profit);
  }

  const uniqueProfits = Array.from(new Set(profits));
  if (uniqueProfits.length === 1) {
    return `利润 ${formatPrice(uniqueProfits[0])}`;
  }

  return `利润 ${formatPrice(Math.min(...uniqueProfits))} ~ ${formatPrice(Math.max(...uniqueProfits))}`;
}

type QuickApproveResultState = 'idle' | 'running' | 'success' | 'error' | 'skipped';

interface QuickApproveResult {
  state: QuickApproveResultState;
  message?: string;
}

interface QuickApproveItem {
  id: number;
  priceOrderSn: string | null;
  productName: string | null;
  reviewAction: string | null;
  primarySku: PriceAdjustSkuVO | null;
  skuCount: number;
  currentSupplyPrice: number | null;
  currentSupplyPriceText: string;
  newSupplyPrice: number | null;
  priceGap: number | null;
  priceChange: number | null;
  purchasePriceText: string;
  disabledReason: string | null;
}

interface RetryApproveSuggestion {
  mode: 'retry' | 'resolved';
  triggerMessage: string;
  sourceOrderSns: string[];
  sourceRecords: PriceAdjustOrderVO[];
  candidates: PriceAdjustOrderVO[];
}

interface StaleOrderHint {
  message: string;
  relatedOrderSns: string[];
}

const SHOP_FILTER_STORAGE_KEY = 'sync-price-adjust';

const RELATED_PRICE_ORDER_SN_PATTERN = /需和调价单([A-Z0-9]+).*一起确认是否调价/i;
const REFRESH_RETRY_PATTERN = /非待确认状态|刷新页面重试/i;

function getAdjustSkus(record: PriceAdjustOrderVO) {
  return record.skuInfoList || record.skuList || [];
}

function parsePriceValue(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const numeric = typeof value === 'number' ? value : Number(value);
  return Number.isNaN(numeric) ? null : numeric;
}

function buildQuickApproveItem(record: PriceAdjustOrderVO): QuickApproveItem {
  const skus = getAdjustSkus(record);
  const primarySku = skus[0] || null;
  const currentPriceList = Array.from(
    new Set(
      skus
        .map((item) => parsePriceValue(item.currentSupplyPrice ?? item.price))
        .filter((item): item is number => item !== null),
    ),
  );
  const newSupplyPrice = parsePriceValue(record.newSupplyPrice);
  const hasMultiCurrentPrices = currentPriceList.length > 1;
  const currentSupplyPrice = hasMultiCurrentPrices ? null : (currentPriceList[0] ?? null);
  const currentSupplyPriceText = !currentPriceList.length
    ? '-'
    : hasMultiCurrentPrices
      ? `${formatPrice(Math.min(...currentPriceList))} ~ ${formatPrice(Math.max(...currentPriceList))}`
      : formatPrice(currentPriceList[0]);

  let disabledReason: string | null = null;
  if (record.reviewAction && record.reviewAction !== 'PENDING') {
    disabledReason = `当前状态为${reviewTextMap[record.reviewAction] || record.reviewAction}`;
  } else if (hasMultiCurrentPrices) {
    disabledReason = '多个 SKU 当前供货价不一致';
  } else if (currentSupplyPrice === null || newSupplyPrice === null) {
    disabledReason = '价格数据不完整';
  }

  const priceGap = currentSupplyPrice !== null && newSupplyPrice !== null ? Math.abs(currentSupplyPrice - newSupplyPrice) : null;
  const priceChange = currentSupplyPrice !== null && newSupplyPrice !== null ? currentSupplyPrice - newSupplyPrice : null;

  return {
    id: record.id,
    priceOrderSn: record.priceOrderSn,
    productName: record.productName,
    reviewAction: record.reviewAction,
    primarySku,
    skuCount: skus.length,
    currentSupplyPrice,
    currentSupplyPriceText,
    newSupplyPrice,
    priceGap,
    priceChange,
    purchasePriceText: summarizePurchasePrice(skus),
    disabledReason,
  };
}

function extractRelatedPriceOrderSn(message?: string | null) {
  if (!message) {
    return null;
  }
  const matched = message.match(RELATED_PRICE_ORDER_SN_PATTERN);
  return matched?.[1] || null;
}

function isRefreshRetryError(message?: string | null) {
  return !!message && REFRESH_RETRY_PATTERN.test(message);
}

const SyncPriceAdjustPage = () => {
  const { message } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: string; label: string }>>([]);
  const [shopId, setShopId] = useState<string | undefined>(() => loadStoredShopFilter(SHOP_FILTER_STORAGE_KEY));
  const [status, setStatus] = useState<number>(1);
  const [reviewAction, setReviewAction] = useState<string>('PENDING');
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<PriceAdjustOrderVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<PriceAdjustOrderVO | null>(null);
  const [reviewing, setReviewing] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [rejectTargets, setRejectTargets] = useState<PriceAdjustOrderVO[]>([]);
  const [quickApproveOpen, setQuickApproveOpen] = useState(false);
  const [quickApproveSubmitting, setQuickApproveSubmitting] = useState(false);
  const [quickApproveDiffMax, setQuickApproveDiffMax] = useState<number | null>(0.5);
  const [quickApproveNewSupplyMax, setQuickApproveNewSupplyMax] = useState<number | null>(25);
  const [quickApproveRows, setQuickApproveRows] = useState<PriceAdjustOrderVO[]>([]);
  const [quickApproveResults, setQuickApproveResults] = useState<Record<number, QuickApproveResult>>({});
  const [retryApproveSuggestion, setRetryApproveSuggestion] = useState<RetryApproveSuggestion | null>(null);
  const [retryApproveSubmitting, setRetryApproveSubmitting] = useState(false);
  const [staleOrderHints, setStaleOrderHints] = useState<Record<number, StaleOrderHint>>({});
  const [refreshingLogisticsKeys, setRefreshingLogisticsKeys] = useState<Record<string, boolean>>({});

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
    await load(1, 20, nextShopId, status, reviewAction);
  }

  async function load(nextPage = page, nextPageSize = pageSize, nextShopId = shopId, nextStatus = status, nextReviewAction = reviewAction) {
    if (!nextShopId) {
      return [] as PriceAdjustOrderVO[];
    }
    setLoading(true);
    try {
      const res = await syncApi.getPriceAdjustList({
        shopId: nextShopId,
        status: nextStatus,
        reviewAction: nextReviewAction,
        page: nextPage,
        pageSize: nextPageSize,
      });
      const content = Array.isArray(res.data.content) ? res.data.content : [];
      setRows(content);
      setTotal(Number(res.data.totalElements || 0));
      setSelectedRowKeys((current) => current.filter((key) => content.some((row) => row.id === key)));
      return content;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
      return [] as PriceAdjustOrderVO[];
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadShops();
  }, []);

  async function openDetail(record: PriceAdjustOrderVO) {
    setDetailOpen(true);
    await reloadDetail(record.id);
  }

  async function reloadDetail(orderId: number) {
    setDetailLoading(true);
    try {
      const res = await syncApi.getPriceAdjustDetail(orderId);
      setDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  async function refreshSkuLogistics(record: PriceAdjustOrderVO, sku: PriceAdjustSkuVO) {
    const refreshOrderId = sku.logisticsRefreshOrderId;
    if (!refreshOrderId) {
      message.warning('当前没有可刷新的物流订单');
      return;
    }

    const refreshKey = `${record.id}-${sku.productSkuId || refreshOrderId}`;
    setRefreshingLogisticsKeys((current) => ({ ...current, [refreshKey]: true }));
    try {
      await temuOrdersApi.refreshLogistics(refreshOrderId);
      message.success('物流信息已刷新');
      const refreshedRows = await load();
      setQuickApproveRows(refreshedRows);
      if (detailOpen && detail?.id === record.id) {
        await reloadDetail(record.id);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '刷新物流失败');
    } finally {
      setRefreshingLogisticsKeys((current) => {
        const next = { ...current };
        delete next[refreshKey];
        return next;
      });
    }
  }

  async function approve(orderIds: number[]) {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }
    setReviewing(true);
    const sourceRecords = rows.filter((item) => orderIds.includes(item.id));
    try {
      const res = await syncApi.batchReviewAdjust({
        shopId,
        orderIds,
        action: 'APPROVE',
      });
      message.success(res.message || '审核通过完成');
      setSelectedRowKeys([]);
      await load();
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '审核失败';
      const handled = await tryOpenRetryApproveSuggestion(errorMessage, sourceRecords);
      if (!handled) {
        message.error(errorMessage);
      }
    } finally {
      setReviewing(false);
    }
  }

  async function submitReject() {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }
    setReviewing(true);
    try {
      await syncApi.batchReviewAdjust({
        shopId,
        orderIds: rejectTargets.map((item) => item.id),
        action: 'REJECT',
        rejectReason: rejectReason.trim() || undefined,
      });
      message.success('审核拒绝完成');
      setRejectOpen(false);
      setRejectTargets([]);
      setRejectReason('');
      setSelectedRowKeys([]);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '审核失败');
    } finally {
      setReviewing(false);
    }
  }

  async function clearLocalData() {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }
    Modal.confirm({
      title: '删除本地调价单数据？',
      content: '会清空当前店铺同步到本地的调价单记录，请确认。',
      okText: '确认删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await syncApi.clearPriceAdjustLocalData(shopId);
          message.success('本地调价单数据已清空');
          await load();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '清空失败');
          throw error;
        }
      },
    });
  }

  function openQuickApproveModal() {
    if (!rows.length) {
      message.warning('当前页没有可处理的调价单');
      return;
    }
    setQuickApproveRows(rows);
    setQuickApproveResults({});
    setQuickApproveDiffMax(0.5);
    setQuickApproveNewSupplyMax(25);
    setQuickApproveOpen(true);
  }

  async function tryOpenRetryApproveSuggestion(errorMessage: string, sourceRecords: PriceAdjustOrderVO[]) {
    if (!isRefreshRetryError(errorMessage)) {
      return false;
    }

    const refreshedRows = await load();
    setQuickApproveRows(refreshedRows);
    setRetryApproveSuggestion(null);
    if (sourceRecords.length) {
      setSelectedRowKeys((current) => current.filter((key) => !sourceRecords.some((item) => item.id === key)));
      setStaleOrderHints((current) => {
        const next = { ...current };
        for (const record of sourceRecords) {
          delete next[record.id];
        }
        return next;
      });
    }
    message.warning('已刷新列表并同步本地状态，这条调价单已经不是待确认状态');
    return true;
  }

  async function submitRetryApprove() {
    if (!shopId || !retryApproveSuggestion?.candidates.length) {
      return;
    }

    setRetryApproveSubmitting(true);
    try {
      const res = await syncApi.batchReviewAdjust({
        shopId,
        orderIds: retryApproveSuggestion.candidates.map((item) => item.id),
        action: 'APPROVE',
      });
      message.success(res.message || '再次同意完成');
      setStaleOrderHints((current) => {
        const next = { ...current };
        for (const record of retryApproveSuggestion.sourceRecords) {
          next[record.id] = {
            message: `已改用最新关联单 ${retryApproveSuggestion.candidates.map((item) => item.priceOrderSn).filter((item): item is string => !!item).join(' / ')}`,
            relatedOrderSns: retryApproveSuggestion.candidates.map((item) => item.priceOrderSn).filter((item): item is string => !!item),
          };
        }
        return next;
      });
      setRetryApproveSuggestion(null);
      setSelectedRowKeys([]);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '再次同意失败');
    } finally {
      setRetryApproveSubmitting(false);
    }
  }

  async function submitQuickApprove(items: QuickApproveItem[]) {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }
    if (!items.length) {
      message.warning('当前筛选结果没有可批量同意的数据');
      return;
    }

    setQuickApproveSubmitting(true);
    let successCount = 0;
    let failedCount = 0;
    const processedIds = new Set<number>();
    const itemByOrderSn = new Map(
      quickApproveItems
        .filter((item) => item.priceOrderSn)
        .map((item) => [item.priceOrderSn as string, item] as const),
    );

    try {
      for (const item of items) {
        if (processedIds.has(item.id)) {
          continue;
        }

        setQuickApproveResults((current) => ({
          ...current,
          [item.id]: { state: 'running', message: '正在同意' },
        }));
        try {
          await syncApi.batchReviewAdjust({
            shopId,
            orderIds: [item.id],
            action: 'APPROVE',
          });
          successCount += 1;
          processedIds.add(item.id);
          setQuickApproveResults((current) => ({
            ...current,
            [item.id]: { state: 'success', message: '同意成功' },
          }));
        } catch (error) {
          const errorMessage = error instanceof Error ? error.message : '同意失败';
          const relatedOrderSn = extractRelatedPriceOrderSn(errorMessage);
          const relatedItem = relatedOrderSn ? itemByOrderSn.get(relatedOrderSn) : null;
          const canRetryTogether =
            relatedItem &&
            relatedItem.id !== item.id &&
            !processedIds.has(relatedItem.id) &&
            !relatedItem.disabledReason;

          if (canRetryTogether) {
            setQuickApproveResults((current) => ({
              ...current,
              [item.id]: { state: 'running', message: `检测到关联单 ${relatedOrderSn}，改为一起同意` },
              [relatedItem.id]: { state: 'running', message: `与 ${item.priceOrderSn || '-'} 一起同意` },
            }));
            try {
              await syncApi.batchReviewAdjust({
                shopId,
                orderIds: [item.id, relatedItem.id],
                action: 'APPROVE',
              });
              successCount += 2;
              processedIds.add(item.id);
              processedIds.add(relatedItem.id);
              setQuickApproveResults((current) => ({
                ...current,
                [item.id]: { state: 'success', message: `已和 ${relatedOrderSn} 一起同意成功` },
                [relatedItem.id]: { state: 'success', message: `已和 ${item.priceOrderSn || '-'} 一起同意成功` },
              }));
              continue;
            } catch (retryError) {
              const retryMessage = retryError instanceof Error ? retryError.message : '关联调价单一起同意失败';
              const handled = await tryOpenRetryApproveSuggestion(
                retryMessage,
                [item, relatedItem].map((candidate) => quickApproveRows.find((row) => row.id === candidate.id)).filter((row): row is PriceAdjustOrderVO => !!row),
              );
              if (handled) {
                setQuickApproveResults((current) => ({
                  ...current,
                  [item.id]: { state: 'skipped', message: '已刷新本地状态，这条调价单已不是待确认状态' },
                  [relatedItem.id]: { state: 'skipped', message: '已刷新本地状态，这条调价单已不是待确认状态' },
                }));
                processedIds.add(item.id);
                processedIds.add(relatedItem.id);
                continue;
              }
              failedCount += 2;
              processedIds.add(item.id);
              processedIds.add(relatedItem.id);
              setQuickApproveResults((current) => ({
                ...current,
                [item.id]: { state: 'error', message: retryMessage },
                [relatedItem.id]: { state: 'error', message: retryMessage },
              }));
              continue;
            }
          }

          const handled = await tryOpenRetryApproveSuggestion(
            errorMessage,
            quickApproveRows.filter((row) => row.id === item.id),
          );
          if (handled) {
            processedIds.add(item.id);
            setQuickApproveResults((current) => ({
              ...current,
              [item.id]: {
                state: 'skipped',
                message: '已刷新本地状态，这条调价单已不是待确认状态',
              },
            }));
            continue;
          }

          failedCount += 1;
          processedIds.add(item.id);
          setQuickApproveResults((current) => ({
            ...current,
            [item.id]: {
              state: 'error',
              message: errorMessage,
            },
          }));
        }
      }

      if (successCount) {
        message.success(`已完成 ${successCount} 条同意操作`);
      }
      if (failedCount) {
        message.warning(`${failedCount} 条同意失败，请看列表结果`);
      }
      setSelectedRowKeys([]);
      await load();
    } finally {
      setQuickApproveSubmitting(false);
    }
  }

  const columns: ColumnsType<PriceAdjustOrderVO> = [
    {
      title: '调价单',
      key: 'priceOrderSn',
      width: 240,
      render: (_, record) => (
        <Space direction="vertical" size={4}>
          <Typography.Text strong>{record.priceOrderSn || '-'}</Typography.Text>
          <Typography.Text ellipsis style={{ maxWidth: 220 }}>
            {record.productName || '-'}
          </Typography.Text>
          <Typography.Text type="secondary">SKC {record.skcId ?? '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'SKU 信息',
      key: 'skuInfoList',
      render: (_, record) => (
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          {(record.skuInfoList || record.skuList || []).slice(0, 2).map((sku) => (
            <Space key={`${record.id}-${sku.productSkuId}`} size={10}>
              {sku.imageUrl ? <Image width={38} height={38} src={sku.imageUrl} /> : null}
              <Space direction="vertical" size={2}>
                <Typography.Text>SKU {sku.productSkuId || '-'}</Typography.Text>
                <Typography.Text type="secondary">{sku.specInfo || sku.spec || '-'}</Typography.Text>
                <Typography.Text type="secondary">当前供货价 {formatPrice(sku.currentSupplyPrice ?? sku.price)}</Typography.Text>
                <Typography.Text type="secondary">
                  总销量 {sku.salesQuantity ?? 0} / 售后 {sku.aftersaleQuantity ?? 0} / 已签收 {sku.signedQuantity ?? 0}
                </Typography.Text>
                <Space size={8}>
                  <Typography.Text type="secondary">头程费用 {formatLogisticsFee(sku.firstLegLogisticsFee)}</Typography.Text>
                  {sku.firstLegLogisticsFee === null || sku.firstLegLogisticsFee === undefined ? (
                    sku.logisticsRefreshOrderId ? (
                      <Button
                        size="small"
                        type="link"
                        loading={!!refreshingLogisticsKeys[`${record.id}-${sku.productSkuId || sku.logisticsRefreshOrderId}`]}
                        onClick={() => void refreshSkuLogistics(record, sku)}
                      >
                        刷新
                      </Button>
                    ) : null
                  ) : null}
                </Space>
              </Space>
            </Space>
          ))}
        </Space>
      ),
    },
    {
      title: '新供货价',
      key: 'newSupplyPrice',
      width: 140,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{formatPrice(record.newSupplyPrice)}</Typography.Text>
          <Typography.Text type="secondary">{summarizePurchasePrice(record.skuInfoList || record.skuList || [])}</Typography.Text>
          <Typography.Text type="secondary">{summarizeProfit(record.newSupplyPrice, record.skuInfoList || record.skuList || [])}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '站点 / 状态',
      key: 'status',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={4}>
          <Typography.Text>{record.siteNameList?.join(' / ') || '-'}</Typography.Text>
          <Space size={[6, 6]} wrap>
            <Tag>{statusMap[record.status || 0] || record.status || '-'}</Tag>
            <Tag color={record.reviewAction && record.reviewAction !== 'PENDING' ? (record.reviewAction.startsWith('APPRO') ? 'green' : 'red') : 'processing'}>
              {reviewTextMap[record.reviewAction || 'PENDING'] || record.reviewAction || '待处理'}
            </Tag>
            <Tag color={record.trafficLowExpose ? 'orange' : 'default'}>{record.trafficLowExpose ? '低曝光' : '正常曝光'}</Tag>
          </Space>
        </Space>
      ),
    },
    {
      title: '同步时间',
      dataIndex: 'syncedAt',
      key: 'syncedAt',
      width: 180,
      render: (value: PriceAdjustOrderVO['syncedAt']) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          <Button size="small" type="link" onClick={() => void approve([record.id])}>
            通过
          </Button>
          <Button size="small" type="link" danger onClick={() => {
            setRejectTargets([record]);
            setRejectReason(record.rejectReason || '');
            setRejectOpen(true);
          }}>
            拒绝
          </Button>
        </Space>
      ),
    },
  ];

  const detailSkuColumns: ColumnsType<PriceAdjustSkuVO> = [
    { title: 'SKU', dataIndex: 'productSkuId', key: 'productSkuId', width: 120 },
    { title: '编码', dataIndex: 'extCode', key: 'extCode', width: 140 },
    { title: '规格', dataIndex: 'specInfo', key: 'specInfo' },
    { title: '总销量', dataIndex: 'salesQuantity', key: 'salesQuantity', width: 100, render: (value: number | null) => value ?? 0 },
    { title: '售后数量', dataIndex: 'aftersaleQuantity', key: 'aftersaleQuantity', width: 100, render: (value: number | null) => value ?? 0 },
    { title: '已签数量', dataIndex: 'signedQuantity', key: 'signedQuantity', width: 100, render: (value: number | null) => value ?? 0 },
    {
      title: '头程费用',
      dataIndex: 'firstLegLogisticsFee',
      key: 'firstLegLogisticsFee',
      width: 140,
      render: (_, sku) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{formatLogisticsFee(sku.firstLegLogisticsFee)}</Typography.Text>
          {sku.firstLegLogisticsFee === null || sku.firstLegLogisticsFee === undefined ? (
            sku.logisticsRefreshOrderId && detail ? (
              <Button
                size="small"
                type="link"
                loading={!!refreshingLogisticsKeys[`${detail.id}-${sku.productSkuId || sku.logisticsRefreshOrderId}`]}
                onClick={() => void refreshSkuLogistics(detail, sku)}
              >
                刷新物流
              </Button>
            ) : null
          ) : null}
        </Space>
      ),
    },
    { title: '采购价', key: 'purchasePrice', width: 120, render: (_, record) => record.purchasePrice !== null && record.purchasePrice !== undefined ? formatPrice(record.purchasePrice) : '未配置' },
    { title: '当前供货价', key: 'price', width: 140, render: (_, record) => formatPrice(record.currentSupplyPrice ?? record.price) },
  ];

  const quickApproveItems = quickApproveRows.map(buildQuickApproveItem);
  const retryApproveItems = (retryApproveSuggestion?.candidates || []).map(buildQuickApproveItem);
  const quickApproveDiffLimit = quickApproveDiffMax === null || quickApproveDiffMax === undefined ? null : Math.round(quickApproveDiffMax * 100);
  const quickApproveNewSupplyLimit = quickApproveNewSupplyMax === null || quickApproveNewSupplyMax === undefined ? null : Math.round(quickApproveNewSupplyMax * 100);
  const filteredQuickApproveItems = quickApproveItems.filter((item) => {
    if (item.priceGap === null) {
      return false;
    }
    if (quickApproveDiffLimit !== null && item.priceGap > quickApproveDiffLimit) {
      return false;
    }
    if (quickApproveNewSupplyLimit !== null && (item.newSupplyPrice === null || item.newSupplyPrice < quickApproveNewSupplyLimit)) {
      return false;
    }
    return true;
  });
  const actionableQuickApproveItems = filteredQuickApproveItems.filter((item) => !item.disabledReason);

  const quickApproveColumns: ColumnsType<QuickApproveItem> = [
    {
      title: '调价单 / 商品',
      key: 'order',
      width: 250,
      render: (_, record) => (
        <Space direction="vertical" size={4}>
          <Typography.Text strong>{record.priceOrderSn || '-'}</Typography.Text>
          <Typography.Text ellipsis style={{ maxWidth: 220 }}>
            {record.productName || '-'}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'SKU 信息',
      key: 'sku',
      width: 280,
      render: (_, record) => (
        <Space size={10} align="start">
          {record.primarySku?.imageUrl ? <Image width={44} height={44} src={record.primarySku.imageUrl} /> : null}
          <Space direction="vertical" size={2}>
            <Typography.Text>SKU {record.primarySku?.productSkuId || '-'}</Typography.Text>
            <Typography.Text type="secondary">{record.primarySku?.specInfo || record.primarySku?.spec || '-'}</Typography.Text>
            <Typography.Text type="secondary">
              {record.primarySku?.extCode ? `编码 ${record.primarySku.extCode}` : '编码 -'}
              {record.skuCount > 1 ? ` · 共 ${record.skuCount} 个 SKU` : ''}
            </Typography.Text>
            <Typography.Text type="secondary">
              总销量 {record.primarySku?.salesQuantity ?? 0} / 售后 {record.primarySku?.aftersaleQuantity ?? 0} / 已签收 {record.primarySku?.signedQuantity ?? 0}
            </Typography.Text>
            <Typography.Text type="secondary">头程费用 {formatLogisticsFee(record.primarySku?.firstLegLogisticsFee)}</Typography.Text>
          </Space>
        </Space>
      ),
    },
    {
      title: '当前供货价',
      key: 'currentSupplyPrice',
      width: 150,
      render: (_, record) => record.currentSupplyPriceText,
    },
    {
      title: '新供货价',
      key: 'newSupplyPrice',
      width: 140,
      render: (_, record) => formatPrice(record.newSupplyPrice),
    },
    {
      title: '价差',
      key: 'priceGap',
      width: 140,
      render: (_, record) => {
        if (record.priceChange === null) {
          return '-';
        }
        if (record.priceChange > 0) {
          return <Typography.Text type="success">降价 {formatPrice(record.priceChange)}</Typography.Text>;
        }
        if (record.priceChange < 0) {
          return <Typography.Text type="danger">涨价 {formatPrice(Math.abs(record.priceChange))}</Typography.Text>;
        }
        return <Typography.Text>无变化</Typography.Text>;
      },
    },
    {
      title: '采购价',
      key: 'purchasePrice',
      width: 150,
      render: (_, record) => <Typography.Text type={record.purchasePriceText === '采购价未配置' ? 'secondary' : undefined}>{record.purchasePriceText}</Typography.Text>,
    },
    {
      title: '审核状态',
      key: 'reviewAction',
      width: 120,
      render: (_, record) => (
        <Tag color={record.reviewAction && record.reviewAction !== 'PENDING' ? (record.reviewAction.startsWith('APPRO') ? 'green' : 'red') : 'processing'}>
          {reviewTextMap[record.reviewAction || 'PENDING'] || record.reviewAction || '待处理'}
        </Tag>
      ),
    },
    {
      title: '同意结果',
      key: 'result',
      width: 220,
      render: (_, record) => {
        const result = quickApproveResults[record.id];
        if (result?.state === 'running') {
          return (
            <Space direction="vertical" size={2}>
              <Tag color="processing">同意中</Tag>
              <Typography.Text type="secondary">{result.message}</Typography.Text>
            </Space>
          );
        }
        if (result?.state === 'success') {
          return (
            <Space direction="vertical" size={2}>
              <Tag color="success">成功</Tag>
              <Typography.Text type="secondary">{result.message}</Typography.Text>
            </Space>
          );
        }
        if (result?.state === 'error') {
          return (
            <Space direction="vertical" size={2}>
              <Tag color="error">失败</Tag>
              <Typography.Text type="danger">{result.message}</Typography.Text>
            </Space>
          );
        }
        if (result?.state === 'skipped') {
          return (
            <Space direction="vertical" size={2}>
              <Tag>已跳过</Tag>
              <Typography.Text type="secondary">{result.message}</Typography.Text>
            </Space>
          );
        }
        if (record.disabledReason) {
          return (
            <Space direction="vertical" size={2}>
              <Tag>需手动处理</Tag>
              <Typography.Text type="secondary">{record.disabledReason}</Typography.Text>
            </Space>
          );
        }
        return <Typography.Text type="secondary">待批量同意</Typography.Text>;
      },
    },
  ];

  const retryApproveColumns: ColumnsType<QuickApproveItem> = [
    {
      title: '调价单',
      key: 'order',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={4}>
          <Typography.Text strong>{record.priceOrderSn || '-'}</Typography.Text>
          <Typography.Text ellipsis style={{ maxWidth: 200 }}>
            {record.productName || '-'}
          </Typography.Text>
          <Typography.Text type="secondary">SKU {record.primarySku?.productSkuId || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'SKU 规格',
      key: 'spec',
      width: 180,
      render: (_, record) => record.primarySku?.specInfo || record.primarySku?.spec || '-',
    },
    {
      title: '当前供货价',
      key: 'currentSupplyPrice',
      width: 140,
      render: (_, record) => record.currentSupplyPriceText,
    },
    {
      title: '新供货价',
      key: 'newSupplyPrice',
      width: 140,
      render: (_, record) => formatPrice(record.newSupplyPrice),
    },
    {
      title: '状态',
      key: 'status',
      width: 140,
      render: (_, record) => (
        <Tag color={record.reviewAction && record.reviewAction !== 'PENDING' ? 'default' : 'processing'}>
          {reviewTextMap[record.reviewAction || 'PENDING'] || record.reviewAction || '待处理'}
        </Tag>
      ),
    },
  ];

  const visibleRows = rows.filter((record) => !staleOrderHints[record.id] || !retryApproveSuggestion || !retryApproveSuggestion.sourceRecords.some((item) => item.id === record.id));

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Select
            value={shopId}
            onChange={(value) => {
              setShopId(value);
              saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, value);
              setPage(1);
              void load(1, pageSize, value, status, reviewAction);
            }}
            placeholder="店铺"
            style={{ width: 220 }}
            options={shops}
          />
          <Select value={status} onChange={setStatus} style={{ width: 150 }} options={[{ value: 0, label: '待核价' }, { value: 1, label: '待供应商确认' }, { value: 2, label: '调价成功' }, { value: 3, label: '调价失败' }]} />
          <Select value={reviewAction} onChange={setReviewAction} style={{ width: 150 }} options={[{ value: 'PENDING', label: '待处理' }, { value: 'APPROVE', label: '已同意' }, { value: 'REJECT', label: '已拒绝' }]} />
          <Button type="primary" loading={loading} onClick={() => {
            setPage(1);
            void load(1, pageSize, shopId, status, reviewAction);
          }}>
            查询
          </Button>
          <Button onClick={() => {
            setStatus(1);
            setReviewAction('PENDING');
            setPage(1);
            setStaleOrderHints({});
            void load(1, pageSize, shopId, 1, 'PENDING');
          }}>
            重置
          </Button>
          <Button disabled={!selectedRowKeys.length} loading={reviewing} onClick={() => void approve(rows.filter((item) => selectedRowKeys.includes(item.id) && !staleOrderHints[item.id]).map((item) => item.id))}>
            批量通过 ({selectedRowKeys.length})
          </Button>
          <Button onClick={() => openQuickApproveModal()}>
            价差批量同意
          </Button>
          <Button danger disabled={!selectedRowKeys.length} onClick={() => {
            const targets = rows.filter((item) => selectedRowKeys.includes(item.id));
            setRejectTargets(targets);
            setRejectReason('');
            setRejectOpen(true);
          }}>
            批量拒绝 ({selectedRowKeys.length})
          </Button>
          <Button danger onClick={() => void clearLocalData()}>
            删除本地数据
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<PriceAdjustOrderVO>
          rowKey="id"
          loading={loading}
          columns={columns.map((column) => {
            if (column.key === 'status') {
              return {
                ...column,
                render: (_: unknown, record: PriceAdjustOrderVO) => (
                  <Space direction="vertical" size={4}>
                    <Typography.Text>{record.siteNameList?.join(' / ') || '-'}</Typography.Text>
                    <Space size={[6, 6]} wrap>
                      <Tag>{statusMap[record.status || 0] || record.status || '-'}</Tag>
                      <Tag color={record.reviewAction && record.reviewAction !== 'PENDING' ? (record.reviewAction.startsWith('APPRO') ? 'green' : 'red') : 'processing'}>
                        {reviewTextMap[record.reviewAction || 'PENDING'] || record.reviewAction || '待处理'}
                      </Tag>
                      <Tag color={record.trafficLowExpose ? 'orange' : 'default'}>{record.trafficLowExpose ? '低曝光' : '正常曝光'}</Tag>
                      {staleOrderHints[record.id] ? <Tag color="default">已失效</Tag> : null}
                    </Space>
                    {staleOrderHints[record.id] ? <Typography.Text type="secondary">{staleOrderHints[record.id].message}</Typography.Text> : null}
                  </Space>
                ),
              };
            }
            if (column.key === 'actions') {
              return {
                ...column,
                render: (_: unknown, record: PriceAdjustOrderVO) => (
                  <Space>
                    <Button size="small" onClick={() => void openDetail(record)}>
                      详情
                    </Button>
                    <Button size="small" type="link" disabled={!!staleOrderHints[record.id]} onClick={() => void approve([record.id])}>
                      通过
                    </Button>
                    <Button
                      size="small"
                      type="link"
                      danger
                      disabled={!!staleOrderHints[record.id]}
                      onClick={() => {
                        setRejectTargets([record]);
                        setRejectReason(record.rejectReason || '');
                        setRejectOpen(true);
                      }}
                    >
                      拒绝
                    </Button>
                  </Space>
                ),
              };
            }
            return column;
          })}
          dataSource={visibleRows}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
            getCheckboxProps: (record) => ({ disabled: !!staleOrderHints[record.id] }),
          }}
          scroll={{ x: 1360 }}
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
            void load(nextPage, nextPageSize, shopId, status, reviewAction);
          }}
        />
      </Card>

      <Drawer open={detailOpen} width={920} title="调价单详情" onClose={() => setDetailOpen(false)} loading={detailLoading}>
        {detail ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card size="small">
              <Space direction="vertical" size={4}>
                <span>调价单号: {detail.priceOrderSn || '-'}</span>
                <span>商品: {detail.productName || '-'}</span>
                <span>站点: {detail.siteNameList?.join(' / ') || '-'}</span>
                <span>新供货价: {formatPrice(detail.newSupplyPrice)}</span>
                <span>{summarizePurchasePrice(detail.skuInfoList || detail.skuList || [])}</span>
                <span>{summarizeProfit(detail.newSupplyPrice, detail.skuInfoList || detail.skuList || [])}</span>
                <span>拒绝原因: {detail.rejectReason || '-'}</span>
              </Space>
            </Card>
            <Card size="small" title="SKU 列表">
              <Table<PriceAdjustSkuVO> rowKey={(record) => `${record.id}-${record.productSkuId}`} columns={detailSkuColumns} dataSource={detail.skuInfoList || detail.skuList || []} pagination={false} />
            </Card>
          </Space>
        ) : null}
      </Drawer>

      <Modal
        open={rejectOpen}
        title="调价单拒绝"
        confirmLoading={reviewing}
        onOk={() => void submitReject()}
        onCancel={() => setRejectOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label={`本次共 ${rejectTargets.length} 条调价单`}>
            <Typography.Text type="secondary">如需回传原因，可在这里统一填写。</Typography.Text>
          </Form.Item>
          <Form.Item label="拒绝原因">
            <Input.TextArea rows={4} value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} placeholder="可选；如填写将回写到后台" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={quickApproveOpen}
        title="价差批量同意"
        width={1240}
        destroyOnClose
        onCancel={() => {
          if (!quickApproveSubmitting) {
            setQuickApproveOpen(false);
          }
        }}
        footer={[
          <Button key="close" onClick={() => setQuickApproveOpen(false)} disabled={quickApproveSubmitting}>
            关闭
          </Button>,
          <Button
            key="submit"
            type="primary"
            loading={quickApproveSubmitting}
            disabled={!actionableQuickApproveItems.length}
            onClick={() => void submitQuickApprove(actionableQuickApproveItems)}
          >
            批量同意 ({actionableQuickApproveItems.length})
          </Button>,
        ]}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Space wrap>
            <Typography.Text>价差最大值</Typography.Text>
            <InputNumber
              min={0}
              step={0.01}
              precision={2}
              value={quickApproveDiffMax ?? undefined}
              placeholder="不限"
              addonAfter="元"
              onChange={(value) => setQuickApproveDiffMax(typeof value === 'number' ? value : null)}
            />
            <Typography.Text>新供货价不低于</Typography.Text>
            <InputNumber
              min={0}
              step={0.01}
              precision={2}
              value={quickApproveNewSupplyMax ?? undefined}
              placeholder="不限"
              addonAfter="元"
              onChange={(value) => setQuickApproveNewSupplyMax(typeof value === 'number' ? value : null)}
            />
            <Typography.Text type="secondary">按当前页数据筛选，价差 = |当前供货价 - 新供货价|</Typography.Text>
          </Space>

          <Alert
            type="info"
            showIcon
            message={`当前页 ${quickApproveItems.length} 条，筛选命中 ${filteredQuickApproveItems.length} 条，可批量同意 ${actionableQuickApproveItems.length} 条`}
          />

          <Table<QuickApproveItem>
            rowKey="id"
            size="small"
            columns={quickApproveColumns}
            dataSource={filteredQuickApproveItems}
            pagination={false}
            scroll={{ x: 1180, y: 460 }}
            locale={{ emptyText: '当前页没有符合价差条件的数据' }}
          />
        </Space>
      </Modal>

      <Modal
        open={!!retryApproveSuggestion}
        title={retryApproveSuggestion?.mode === 'retry' ? '发现可再次一起同意的调价单' : '调价单已刷新到最新状态'}
        width={960}
        destroyOnClose
        confirmLoading={retryApproveSubmitting}
        onOk={() => void submitRetryApprove()}
        okText={retryApproveSuggestion?.mode === 'retry' ? `再次一起同意 (${retryApproveSuggestion?.candidates.length || 0})` : '知道了'}
        okButtonProps={{ disabled: retryApproveSuggestion?.mode !== 'retry' }}
        onCancel={() => {
          if (!retryApproveSubmitting) {
            setRetryApproveSuggestion(null);
          }
        }}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Alert
            type="warning"
            showIcon
            message={retryApproveSuggestion?.triggerMessage || '调价单状态已变化'}
            description={
              retryApproveSuggestion?.sourceOrderSns.length
                ? `我已按失败单号 ${retryApproveSuggestion.sourceOrderSns.join(' / ')} 刷新列表，并定位到同一批当前可处理的调价单。`
                : '我已刷新列表，并定位到当前可再次处理的调价单。'
            }
          />

          <Card size="small" title="原失败调价单">
            <Space direction="vertical" size={6} style={{ width: '100%' }}>
              {(retryApproveSuggestion?.sourceRecords || []).map((record) => (
                <Space key={record.id} wrap>
                  <Typography.Text strong>{record.priceOrderSn || '-'}</Typography.Text>
                  <Typography.Text type="secondary">SKC {record.skcId ?? '-'}</Typography.Text>
                  <Typography.Text type="secondary">{record.productName || '-'}</Typography.Text>
                </Space>
              ))}
            </Space>
          </Card>

          <Table<QuickApproveItem>
            rowKey="id"
            size="small"
            columns={retryApproveColumns}
            dataSource={retryApproveItems}
            pagination={false}
            scroll={{ x: 820 }}
          />
        </Space>
      </Modal>
    </Space>
  );
};

export default SyncPriceAdjustPage;
