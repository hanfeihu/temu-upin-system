import { BarChartOutlined, EyeOutlined, FilterOutlined, RobotOutlined } from '@ant-design/icons';
import { App, Button, Card, Checkbox, Descriptions, Image, Input, InputNumber, Modal, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useMemo, useState, type Key, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { alibaba1688DetailRecordsApi } from '@/api/alibaba1688DetailRecords';
import { alibabaImageProxyConfigApi } from '@/api/alibabaImageProxyConfig';
import { alibaba1688SelectionPoolFilterCategoriesApi } from '@/api/alibaba1688SelectionPoolFilterCategories';
import { alibaba1688SelectionPoolsApi } from '@/api/alibaba1688SelectionPools';
import { temuShopsApi } from '@/api/temuShops';
import type {
  Alibaba1688DetailRecordVO,
  AlibabaImageProxyConfigVO,
  Alibaba1688SelectionPoolCategoryOption,
  Alibaba1688SelectionPoolAiReportWorkerConfigVO,
  Alibaba1688SelectionPoolAiReportWorkerStatusVO,
  Alibaba1688SelectionPoolAutoPushLogVO,
  Alibaba1688SelectionPoolAutoPushStatusVO,
  Alibaba1688SelectionPoolDetailVO,
  Alibaba1688SelectionPoolReportVO,
  Alibaba1688SelectionPoolSkuVO,
  Alibaba1688SelectionPoolVO,
  TemuShopVO,
} from '@/types/api';
import { buildAlibabaImageProxyUrl, mapAlibabaImageProxyUrls } from '@/utils/alibabaImageProxy';
import { formatDateTime, prettyJson } from '@/utils/format';
import './Alibaba1688SelectionPoolsPage.css';

interface Filters {
  keyword: string;
  category: string;
  status: string;
  aiSelectionDecision: string;
  liquidStatus: 'ALL' | 'YES' | 'NO';
  fragileStatus: 'ALL' | 'YES' | 'NO';
  siteExceptionStatus: 'ALL' | 'BLOCKED' | 'NORMAL';
  pushedStatus: 'UNPUSHED' | 'PUSHED' | 'ALL';
  detailRecordId: string;
  skuPriceMin: string;
  skuPriceMax: string;
  moqMin: string;
  moqMax: string;
  startBatchQtyMin: string;
  startBatchQtyMax: string;
  aiMaxDimensionCmMin: string;
  aiMaxDimensionCmMax: string;
  aiMaxWeightGMin: string;
  aiMaxWeightGMax: string;
}

interface ImportFilters {
  keyword: string;
  detailRecordId: string;
}

const initialFilters: Filters = {
  keyword: '',
  category: '',
  status: '',
  aiSelectionDecision: '',
  liquidStatus: 'ALL',
  fragileStatus: 'ALL',
  siteExceptionStatus: 'NORMAL',
  pushedStatus: 'UNPUSHED',
  detailRecordId: '',
  skuPriceMin: '',
  skuPriceMax: '',
  moqMin: '',
  moqMax: '',
  startBatchQtyMin: '',
  startBatchQtyMax: '',
  aiMaxDimensionCmMin: '',
  aiMaxDimensionCmMax: '',
  aiMaxWeightGMin: '',
  aiMaxWeightGMax: '',
};

const initialImportFilters: ImportFilters = {
  keyword: '',
  detailRecordId: '',
};

const PUSH_TARGET_SHOP_STORAGE_KEY = 'alibaba1688SelectionPool.pushTargetShopId';

const statusColorMap: Record<string, string> = {
  NEW: 'gold',
  PENDING_SELECTION: 'gold',
  SHORTLISTED: 'green',
  REJECTED: 'red',
  PARTIAL_PROGRESS: 'processing',
  COMPLETED: 'blue',
  ONLINE: 'green',
  QUOTED: 'blue',
  IN_CAMPAIGN: 'purple',
  PROMOTED: 'magenta',
  LOW: 'orange',
  OUT: 'red',
  NORMAL: 'green',
  UNKNOWN: 'default',
  READY: 'green',
  NOT_STARTED: 'default',
  SUITABLE: 'green',
  REVIEW: 'gold',
  UNSUITABLE: 'red',
};

function statusTag(value?: string | null) {
  const text = value || '-';
  return <Tag color={statusColorMap[text] || 'blue'}>{text}</Tag>;
}

function normalizeArray<T>(...values: Array<T[] | null | undefined>): T[] {
  return values.find((value) => Array.isArray(value)) || [];
}

function toNumber(value: string) {
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function toPushedParam(value: Filters['pushedStatus']) {
  if (value === 'PUSHED') {
    return true;
  }
  if (value === 'UNPUSHED') {
    return false;
  }
  return undefined;
}

function toBooleanFilterParam(value: 'ALL' | 'YES' | 'NO') {
  if (value === 'YES') {
    return true;
  }
  if (value === 'NO') {
    return false;
  }
  return undefined;
}

function toSiteExceptionParam(value: Filters['siteExceptionStatus']) {
  if (value === 'BLOCKED') {
    return true;
  }
  if (value === 'NORMAL') {
    return false;
  }
  return undefined;
}

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  return Number(value).toFixed(2);
}

function toFiniteNumber(value?: number | null) {
  if (value === null || value === undefined) {
    return null;
  }
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function calculateWeightFee(maxWeightG?: number | null) {
  const weightG = toFiniteNumber(maxWeightG);
  if (weightG === null) {
    return null;
  }
  return (Math.max(weightG, 30) / 1000) * 69 + 6;
}

function calculateEstimatedCost(record: {
  baseFreightSnapshot?: number | null;
  maxPrice?: number | null;
  aiMaxWeightG?: number | null;
}) {
  const baseFreight = toFiniteNumber(record.baseFreightSnapshot);
  const maxPrice = toFiniteNumber(record.maxPrice);
  const weightFee = calculateWeightFee(record.aiMaxWeightG);
  if (baseFreight === null || maxPrice === null || weightFee === null) {
    return null;
  }
  return baseFreight + maxPrice + weightFee;
}

function calculateMinimumSupplyPrice(record: {
  baseFreightSnapshot?: number | null;
  maxPrice?: number | null;
  aiMaxWeightG?: number | null;
}) {
  const estimatedCost = calculateEstimatedCost(record);
  return estimatedCost === null ? null : estimatedCost + 10;
}

function readCachedPushTargetShopId() {
  try {
    return window.localStorage.getItem(PUSH_TARGET_SHOP_STORAGE_KEY) || undefined;
  } catch {
    return undefined;
  }
}

function cachePushTargetShopId(shopId: string) {
  try {
    window.localStorage.setItem(PUSH_TARGET_SHOP_STORAGE_KEY, shopId);
  } catch {
    // Ignore storage failures; the current push still uses the selected shop.
  }
}

function formatWeightG(value?: number | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return '-';
  }
  if (Number.isInteger(numeric)) {
    return `${numeric} g`;
  }
  return `${numeric.toFixed(2)} g`;
}

function formatDimensionCm(value?: number | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return '-';
  }
  if (Number.isInteger(numeric)) {
    return `${numeric} cm`;
  }
  return `${numeric.toFixed(2)} cm`;
}

function formatPriceRange(minPrice?: number | null, maxPrice?: number | null) {
  if (minPrice === null || minPrice === undefined) {
    return maxPrice === null || maxPrice === undefined ? '-' : formatMoney(maxPrice);
  }
  if (maxPrice === null || maxPrice === undefined) {
    return formatMoney(minPrice);
  }
  if (Number(minPrice) === Number(maxPrice)) {
    return formatMoney(minPrice);
  }
  return `${formatMoney(minPrice)} ~ ${formatMoney(maxPrice)}`;
}

function formatDateOnly(value: string | number[] | null | undefined) {
  const dateTime = formatDateTime(value);
  if (!dateTime || dateTime === '-') {
    return '-';
  }
  return dateTime.split(' ')[0] || dateTime;
}

function formatMetricValue(value?: number | null, suffix = '') {
  if (value === null || value === undefined) {
    return '-';
  }
  return `${value}${suffix}`;
}

function formatBooleanText(value?: boolean | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  return value ? '是' : '否';
}

function formatMs(value?: number | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  return `${value} ms`;
}

function pickNumber(...values: Array<number | null | undefined>) {
  return values.find((value) => typeof value === 'number' && Number.isFinite(value)) ?? null;
}

function isNoTaskMessage(message?: string | null) {
  const text = String(message || '').trim();
  if (!text) {
    return false;
  }
  return [
    '无任务可执行',
    '当前无待执行任务',
    '当前没有待执行任务',
    '暂无待执行任务',
    '没有待执行任务',
    '无待执行任务',
  ].some((keyword) => text.includes(keyword));
}

function formatWorkerRunningText(value?: boolean | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  return value ? '运行中' : '已停止';
}

function resolveWorkerThreadCount(
  config?: Alibaba1688SelectionPoolAiReportWorkerConfigVO | null,
  status?: Alibaba1688SelectionPoolAiReportWorkerStatusVO | null,
) {
  return pickNumber(
    config?.threadCount,
    config?.configuredThreads,
    status?.configuredThreadCount,
    status?.configuredThreads,
    status?.threadCount,
  );
}

function resolveWorkerPollIntervalMs(
  config?: Alibaba1688SelectionPoolAiReportWorkerConfigVO | null,
  status?: Alibaba1688SelectionPoolAiReportWorkerStatusVO | null,
) {
  return pickNumber(
    config?.pollIntervalMs,
    config?.pollMs,
    status?.pollIntervalMs,
    status?.pollMs,
  );
}

function resolveWorkerLastCheckedAt(status?: Alibaba1688SelectionPoolAiReportWorkerStatusVO | null) {
  return status?.lastCheckedAt ?? status?.lastCheckAt ?? status?.lastScanAt ?? null;
}

function resolveWorkerProcessingCount(status?: Alibaba1688SelectionPoolAiReportWorkerStatusVO | null) {
  return pickNumber(status?.processingCount, status?.runningCount);
}

function resolveWorkerCompletedCount(status?: Alibaba1688SelectionPoolAiReportWorkerStatusVO | null) {
  return pickNumber(status?.completedCount, status?.readyCount);
}

function resolveWorkerActiveThreadCount(status?: Alibaba1688SelectionPoolAiReportWorkerStatusVO | null) {
  return pickNumber(status?.activeThreads, status?.activeThreadCount);
}

function formatWorkerEventStatus(status?: string | null) {
  const value = status || 'UNKNOWN';
  if (value === 'RUNNING') {
    return <Tag color="processing">运行中</Tag>;
  }
  if (value === 'SUCCESS') {
    return <Tag color="green">成功</Tag>;
  }
  if (value === 'FAILED') {
    return <Tag color="red">失败</Tag>;
  }
  return <Tag>{value}</Tag>;
}

function formatDurationMs(value?: number | null) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) {
    return '-';
  }
  const durationMs = Math.max(0, Number(value));
  if (durationMs < 1000) {
    return `${Math.round(durationMs)} ms`;
  }
  const seconds = durationMs / 1000;
  if (seconds < 60) {
    return `${seconds >= 10 ? seconds.toFixed(1) : seconds.toFixed(2)} s`;
  }
  const minutes = Math.floor(seconds / 60);
  const remainSeconds = Math.round(seconds % 60);
  return `${minutes}m ${remainSeconds}s`;
}

function selectionDecisionTag(value?: string | null) {
  if (!value) {
    return <Tag>未分析</Tag>;
  }
  if (value === 'SUITABLE') {
    return <Tag color="green">适合</Tag>;
  }
  if (value === 'UNSUITABLE') {
    return <Tag color="red">不适合</Tag>;
  }
  return <Tag color="gold">需复核</Tag>;
}

function aiRiskTag(label: string, value?: boolean | null) {
  if (value === null || value === undefined) {
    return <Tag>{label}:未判定</Tag>;
  }
  return <Tag color={value ? 'red' : 'green'}>{label}:{value ? '是' : '否'}</Tag>;
}

function renderJsonBlock(value?: string | null) {
  const text = prettyJson(value) || value || '-';
  return <pre className="alibaba1688-selection-pool-pre">{text}</pre>;
}

function getDisplayMainImage(
  record: { productMainImage?: string | null; mainImageSnapshot?: string | null },
  config?: AlibabaImageProxyConfigVO | null,
) {
  return buildAlibabaImageProxyUrl(record.productMainImage, config)
    || buildAlibabaImageProxyUrl(record.mainImageSnapshot, config)
    || record.productMainImage
    || record.mainImageSnapshot
    || null;
}

function getDisplaySkuImage(
  record: { image?: string | null; skuMainImage?: string | null; skuImage?: string | null },
  config?: AlibabaImageProxyConfigVO | null,
) {
  return buildAlibabaImageProxyUrl(record.image, config)
    || buildAlibabaImageProxyUrl(record.skuMainImage, config)
    || buildAlibabaImageProxyUrl(record.skuImage, config)
    || record.image
    || record.skuMainImage
    || record.skuImage
    || null;
}

const Alibaba1688SelectionPoolsPage = () => {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [workerLoading, setWorkerLoading] = useState(false);
  const [workerSaving, setWorkerSaving] = useState(false);
  const [workerAction, setWorkerAction] = useState<'start' | 'stop' | 'retry' | null>(null);
  const [workerConfig, setWorkerConfig] = useState<Alibaba1688SelectionPoolAiReportWorkerConfigVO | null>(null);
  const [workerStatus, setWorkerStatus] = useState<Alibaba1688SelectionPoolAiReportWorkerStatusVO | null>(null);
  const [workerThreadCount, setWorkerThreadCount] = useState<number | null>(null);
  const [workerLogsOpen, setWorkerLogsOpen] = useState(false);
  const [autoPushOpen, setAutoPushOpen] = useState(false);
  const [autoPushLoading, setAutoPushLoading] = useState(false);
  const [autoPushSaving, setAutoPushSaving] = useState(false);
  const [autoPushAction, setAutoPushAction] = useState<'start' | 'stop' | null>(null);
  const [autoPushStatus, setAutoPushStatus] = useState<Alibaba1688SelectionPoolAutoPushStatusVO | null>(null);
  const [autoPushTargetShopIds, setAutoPushTargetShopIds] = useState<string[]>([]);
  const [autoPushBatchSize, setAutoPushBatchSize] = useState<number>(1);
  const [autoPushPollMs, setAutoPushPollMs] = useState<number>(60000);
  const [autoPushForceCreate, setAutoPushForceCreate] = useState(false);
  const [autoPushLogs, setAutoPushLogs] = useState<Alibaba1688SelectionPoolAutoPushLogVO[]>([]);
  const [autoPushLogPage, setAutoPushLogPage] = useState(1);
  const [autoPushLogTotal, setAutoPushLogTotal] = useState(0);
  const [detailLoading, setDetailLoading] = useState(false);
  const [reportingKey, setReportingKey] = useState<string | null>(null);
  const [pushingKey, setPushingKey] = useState<number | null>(null);
  const [filteringCategoryKey, setFilteringCategoryKey] = useState<string | null>(null);
  const [imageProxyConfig, setImageProxyConfig] = useState<AlibabaImageProxyConfigVO | null>(null);
  const [targetShopOptions, setTargetShopOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [loadingTargetShops, setLoadingTargetShops] = useState(false);
  const [rows, setRows] = useState<Alibaba1688SelectionPoolVO[]>([]);
  const [detailRecord, setDetailRecord] = useState<Alibaba1688SelectionPoolDetailVO | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [reportPreview, setReportPreview] = useState<Alibaba1688SelectionPoolReportVO | null>(null);
  const [reportPreviewOpen, setReportPreviewOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [importNote, setImportNote] = useState('');
  const [importing, setImporting] = useState(false);
  const [importListLoading, setImportListLoading] = useState(false);
  const [importRows, setImportRows] = useState<Alibaba1688DetailRecordVO[]>([]);
  const [importSelectedRowKeys, setImportSelectedRowKeys] = useState<Key[]>([]);
  const [importAllMatching, setImportAllMatching] = useState(false);
  const [categoryOptions, setCategoryOptions] = useState<Alibaba1688SelectionPoolCategoryOption[]>([]);
  const [importPage, setImportPage] = useState(1);
  const [importPageSize, setImportPageSize] = useState(10);
  const [importTotal, setImportTotal] = useState(0);
  const [importFilters, setImportFilters] = useState<ImportFilters>(initialImportFilters);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState<Filters>(initialFilters);

  async function load(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true);
    try {
      const res = await alibaba1688SelectionPoolsApi.list({
        keyword: nextFilters.keyword.trim() || undefined,
        category: nextFilters.category.trim() || undefined,
        status: nextFilters.status.trim() || undefined,
        aiSelectionDecision: nextFilters.aiSelectionDecision || undefined,
        detailRecordId: toNumber(nextFilters.detailRecordId),
        skuPriceMin: toNumber(nextFilters.skuPriceMin),
        skuPriceMax: toNumber(nextFilters.skuPriceMax),
        moqMin: toNumber(nextFilters.moqMin),
        moqMax: toNumber(nextFilters.moqMax),
        startBatchQtyMin: toNumber(nextFilters.startBatchQtyMin),
        startBatchQtyMax: toNumber(nextFilters.startBatchQtyMax),
        aiMaxDimensionCmMin: toNumber(nextFilters.aiMaxDimensionCmMin),
        aiMaxDimensionCmMax: toNumber(nextFilters.aiMaxDimensionCmMax),
        aiMaxWeightGMin: toNumber(nextFilters.aiMaxWeightGMin),
        aiMaxWeightGMax: toNumber(nextFilters.aiMaxWeightGMax),
        aiContainsLiquid: toBooleanFilterParam(nextFilters.liquidStatus),
        aiFragile: toBooleanFilterParam(nextFilters.fragileStatus),
        temuSiteExceptionBlocked: toSiteExceptionParam(nextFilters.siteExceptionStatus),
        pushed: toPushedParam(nextFilters.pushedStatus),
        page: nextPage - 1,
        size: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载选品池失败');
    } finally {
      setLoading(false);
    }
  }

  async function loadImageProxyConfig() {
    try {
      const res = await alibabaImageProxyConfigApi.current();
      setImageProxyConfig(res.data || null);
    } catch {
      setImageProxyConfig(null);
    }
  }

  async function loadWorkerPanel() {
    setWorkerLoading(true);
    try {
      const [configResult, statusResult] = await Promise.allSettled([
        alibaba1688SelectionPoolsApi.aiReportWorkerConfig(),
        alibaba1688SelectionPoolsApi.aiReportWorkerStatus(),
      ]);

      let nextConfig: Alibaba1688SelectionPoolAiReportWorkerConfigVO | null = workerConfig;
      let nextStatus: Alibaba1688SelectionPoolAiReportWorkerStatusVO | null = workerStatus;
      const errorMessages: string[] = [];

      if (configResult.status === 'fulfilled') {
        nextConfig = configResult.value.data || null;
        setWorkerConfig(nextConfig);
      } else {
        errorMessages.push(configResult.reason instanceof Error ? configResult.reason.message : '加载 AI 报告 worker 配置失败');
      }

      if (statusResult.status === 'fulfilled') {
        nextStatus = statusResult.value.data || null;
        setWorkerStatus(nextStatus);
      } else {
        errorMessages.push(statusResult.reason instanceof Error ? statusResult.reason.message : '加载 AI 报告 worker 状态失败');
      }

      const nextThreadCount = resolveWorkerThreadCount(nextConfig, nextStatus);
      setWorkerThreadCount(nextThreadCount);

      if (errorMessages.length > 0) {
        message.error(errorMessages[0]);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 AI 报告 worker 失败');
    } finally {
      setWorkerLoading(false);
    }
  }

  async function loadWorkerStatus() {
    setWorkerLoading(true);
    try {
      const res = await alibaba1688SelectionPoolsApi.aiReportWorkerStatus();
      const nextStatus = res.data || null;
      setWorkerStatus(nextStatus);
      if (workerThreadCount === null) {
        setWorkerThreadCount(resolveWorkerThreadCount(workerConfig, nextStatus));
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 AI 报告 worker 状态失败');
    } finally {
      setWorkerLoading(false);
    }
  }

  async function saveWorkerConfig() {
    const nextThreadCount = workerThreadCount;
    if (!nextThreadCount || !Number.isFinite(nextThreadCount) || nextThreadCount <= 0) {
      message.warning('请输入有效的线程数');
      return;
    }

    setWorkerSaving(true);
    try {
      const res = await alibaba1688SelectionPoolsApi.updateAiReportWorkerConfig({
        threadCount: nextThreadCount,
      });
      message.success(res.message || '线程数配置已保存');
      await loadWorkerPanel();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存线程数配置失败');
    } finally {
      setWorkerSaving(false);
    }
  }

  async function toggleWorker(action: 'start' | 'stop') {
    setWorkerAction(action);
    try {
      const res = action === 'start'
        ? await alibaba1688SelectionPoolsApi.startAiReportWorker()
        : await alibaba1688SelectionPoolsApi.stopAiReportWorker();
      const responseMessage = String(res.message || (action === 'start' ? '任务已启动' : '任务已停止'));
      if (isNoTaskMessage(responseMessage)) {
        message.info(responseMessage);
      } else {
        message.success(responseMessage);
      }
      await loadWorkerStatus();
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : action === 'start' ? '启动任务失败' : '停止任务失败';
      if (isNoTaskMessage(errorMessage)) {
        message.info(errorMessage);
        await loadWorkerStatus();
      } else {
        message.error(errorMessage);
      }
    } finally {
      setWorkerAction(null);
    }
  }

  async function retryFailedWorkerTasks() {
    setWorkerAction('retry');
    try {
      const res = await alibaba1688SelectionPoolsApi.retryFailedAiReportWorker();
      if (res.data) {
        setWorkerStatus(res.data);
      }
      message.success(res.message || '失败任务已重新加入待执行队列');
      await loadWorkerStatus();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重试失败任务失败');
    } finally {
      setWorkerAction(null);
    }
  }

  function openWorkerLogs() {
    setWorkerLogsOpen(true);
    void loadWorkerStatus();
  }

  async function loadAutoPushPanel() {
    setAutoPushLoading(true);
    try {
      const [configResult, statusResult, logsResult] = await Promise.allSettled([
        alibaba1688SelectionPoolsApi.autoPushConfig(),
        alibaba1688SelectionPoolsApi.autoPushStatus(),
        alibaba1688SelectionPoolsApi.autoPushLogs({ page: 0, size: 10 }),
      ]);
      if (configResult.status === 'fulfilled') {
        const config = configResult.value.data || null;
        setAutoPushTargetShopIds(config?.targetShopIds || []);
        setAutoPushBatchSize(config?.batchSize || 1);
        setAutoPushPollMs(config?.pollMs || 60000);
        setAutoPushForceCreate(Boolean(config?.forceCreate));
      }
      if (statusResult.status === 'fulfilled') {
        setAutoPushStatus(statusResult.value.data || null);
      }
      if (logsResult.status === 'fulfilled') {
        setAutoPushLogs(Array.isArray(logsResult.value.data.content) ? logsResult.value.data.content : []);
        setAutoPushLogTotal(Number(logsResult.value.data.totalElements || 0));
        setAutoPushLogPage(1);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载自动推送配置失败');
    } finally {
      setAutoPushLoading(false);
    }
  }

  async function loadAutoPushLogs(nextPage = autoPushLogPage) {
    setAutoPushLoading(true);
    try {
      const res = await alibaba1688SelectionPoolsApi.autoPushLogs({ page: nextPage - 1, size: 10 });
      setAutoPushLogs(Array.isArray(res.data.content) ? res.data.content : []);
      setAutoPushLogTotal(Number(res.data.totalElements || 0));
      setAutoPushLogPage(nextPage);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载自动推送记录失败');
    } finally {
      setAutoPushLoading(false);
    }
  }

  async function openAutoPushModal() {
    if (!targetShopOptions.length && !loadingTargetShops) {
      await loadTargetShops();
    }
    setAutoPushOpen(true);
    await loadAutoPushPanel();
  }

  async function saveAutoPushConfig() {
    if (!autoPushTargetShopIds.length) {
      message.warning('请选择自动推送店铺');
      return;
    }
    setAutoPushSaving(true);
    try {
      const res = await alibaba1688SelectionPoolsApi.updateAutoPushConfig({
        targetShopIds: autoPushTargetShopIds,
        batchSize: autoPushBatchSize,
        pollMs: autoPushPollMs,
        forceCreate: autoPushForceCreate,
      });
      if (res.data) {
        setAutoPushStatus(res.data);
      }
      message.success(res.message || '自动推送配置已保存');
      await loadAutoPushPanel();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存自动推送配置失败');
    } finally {
      setAutoPushSaving(false);
    }
  }

  async function toggleAutoPush(action: 'start' | 'stop') {
    setAutoPushAction(action);
    try {
      const res = action === 'start'
        ? await alibaba1688SelectionPoolsApi.startAutoPush()
        : await alibaba1688SelectionPoolsApi.stopAutoPush();
      if (res.data) {
        setAutoPushStatus(res.data);
      }
      message.success(res.message || (action === 'start' ? '自动推送已启动' : '自动推送已停止'));
      await loadAutoPushPanel();
      await load(1, pageSize, filters);
    } catch (error) {
      message.error(error instanceof Error ? error.message : action === 'start' ? '启动自动推送失败' : '停止自动推送失败');
    } finally {
      setAutoPushAction(null);
    }
  }

  async function loadCategoryOptions() {
    try {
      const res = await alibaba1688SelectionPoolsApi.categories();
      setCategoryOptions(Array.isArray(res.data) ? res.data : []);
    } catch {
      setCategoryOptions([]);
    }
  }

  async function loadTargetShops() {
    setLoadingTargetShops(true);
    try {
      const res = await temuShopsApi.list({ enabled: true });
      const shops = Array.isArray(res.data) ? res.data : [];
      const options = shops
        .filter((item: TemuShopVO) => item.shopId && item.shopName)
        .map((item: TemuShopVO) => ({
          value: String(item.shopId),
          label: String(item.shopName),
        }));
      setTargetShopOptions(options);
      return options;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载店铺失败');
      setTargetShopOptions([]);
      return [];
    } finally {
      setLoadingTargetShops(false);
    }
  }

  useEffect(() => {
    void Promise.all([
      load(1, 20, initialFilters),
      loadImageProxyConfig(),
      loadCategoryOptions(),
      loadTargetShops(),
      loadWorkerPanel(),
    ]);
  }, []);

  function updateFilter<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((current) => ({ ...current, [key]: value }));
  }

  function updateImportFilter<K extends keyof ImportFilters>(key: K, value: ImportFilters[K]) {
    setImportFilters((current) => ({ ...current, [key]: value }));
  }

  async function loadImportRows(nextPage = importPage, nextPageSize = importPageSize, nextFilters = importFilters) {
    setImportListLoading(true);
    try {
      const res = await alibaba1688DetailRecordsApi.list({
        keyword: nextFilters.keyword.trim() || undefined,
        recordId: toNumber(nextFilters.detailRecordId),
        status: 'READY',
        excludeImported: true,
        page: nextPage - 1,
        size: nextPageSize,
      });
      setImportRows(Array.isArray(res.data.content) ? res.data.content : []);
      setImportTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情数据失败');
    } finally {
      setImportListLoading(false);
    }
  }

  async function openImportModal() {
    const presetRecordId = filters.detailRecordId.trim();
    const nextFilters = {
      keyword: '',
      detailRecordId: presetRecordId,
    };
    setImportFilters(nextFilters);
    setImportSelectedRowKeys([]);
    setImportAllMatching(false);
    setImportRows([]);
    setImportPage(1);
    setImportPageSize(10);
    setImportTotal(0);
    setImportNote('');
    setImportOpen(true);
    await loadImportRows(1, 10, nextFilters);
  }

  async function loadDetail(id: number) {
    setDetailLoading(true);
    try {
      const res = await alibaba1688SelectionPoolsApi.detail(id);
      setDetailRecord(res.data);
      return res.data;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载选品池详情失败');
      setDetailOpen(false);
      setDetailRecord(null);
      return null;
    } finally {
      setDetailLoading(false);
    }
  }

  async function openDetail(record: Alibaba1688SelectionPoolVO) {
    setDetailRecord(null);
    setDetailOpen(true);
    await loadDetail(record.id);
  }

  async function handleCreateReport(id: number, reportType: 'AI_SELECTION' | 'TEMU_COMPETE') {
    const key = `${id}-${reportType}`;
    setReportingKey(key);
    try {
      await alibaba1688SelectionPoolsApi.createReport(id, { reportType });
      message.success(reportType === 'TEMU_COMPETE' ? '竞品报告已生成' : 'AI选品报告已生成');
      if (detailRecord?.id === id) {
        await loadDetail(id);
      } else {
        await load();
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '生成报告失败');
    } finally {
      setReportingKey(null);
    }
  }

  async function handlePushToProductCollection(record: Alibaba1688SelectionPoolVO) {
    let options = targetShopOptions;
    if (!options.length && !loadingTargetShops) {
      options = await loadTargetShops();
    }
    if (!options.length) {
      message.warning('请先维护可用店铺，再推送到商品库');
      return;
    }

    const cachedShopId = readCachedPushTargetShopId();
    let selectedShopId = record.targetShopIds?.length === 1
      ? record.targetShopIds[0]
      : options.some((option) => option.value === cachedShopId)
        ? cachedShopId
        : undefined;
    modal.confirm({
      title: '选择推送店铺',
      content: (
        <div className="alibaba1688-selection-pool-cell">
          <Typography.Text type="secondary">每个商品只能绑定一个店铺，推送后商品库会使用该店铺发布。</Typography.Text>
          <Select
            defaultValue={selectedShopId}
            options={options}
            loading={loadingTargetShops}
            showSearch
            optionFilterProp="label"
            placeholder="请选择一个店铺"
            onChange={(value) => {
              selectedShopId = value;
            }}
          />
        </div>
      ),
      okText: '推送商品库',
      cancelText: '取消',
      onOk: async () => {
        if (!selectedShopId) {
          message.warning('请选择一个店铺');
          throw new Error('请选择一个店铺');
        }
        setPushingKey(record.id);
        try {
          const res = await alibaba1688SelectionPoolsApi.pushToProductCollection(record.id, {
            targetShopIds: [selectedShopId],
          });
          cachePushTargetShopId(selectedShopId);
          const productCollectionId = res.data?.productCollectionId;
          message.success(res.message || '已推送到商品采集库');
          await load(page, pageSize, filters);
          if (detailRecord?.id === record.id) {
            await loadDetail(record.id);
          }
          if (productCollectionId) {
            const successModal = modal.success({
              title: Boolean(res.data?.existing) ? '商品已在采集库' : '推送成功',
              content: `商品采集库 ID：${productCollectionId}`,
              okText: '查看商品采集详情',
              onOk: () => {
                navigate(`/platform/product-collections/${productCollectionId}`);
              },
            });
            window.setTimeout(() => {
              successModal.destroy();
            }, 3000);
          }
        } catch (error) {
          message.error(error instanceof Error ? error.message : '推送到商品采集库失败');
          throw error;
        } finally {
          setPushingKey(null);
        }
      },
    });
  }

  async function quickAddFilterCategory(record: Alibaba1688SelectionPoolVO) {
    const categoryName = String(record.categorySnapshot || '').trim();
    if (!categoryName) {
      message.warning('当前记录没有类目，无法加入过滤类目');
      return;
    }
    const actionKey = `${record.id}-${categoryName}`;
    setFilteringCategoryKey(actionKey);
    try {
      await alibaba1688SelectionPoolFilterCategoriesApi.quickAdd({
        categoryName,
        source: 'SELECTION_POOL_ROW',
        remark: `来自 1688选品池，poolId=${record.id}${record.offerId ? `，offerId=${record.offerId}` : ''}`,
      });
      message.success(`已将类目加入过滤配置：${categoryName}`);
      setPage(1);
      await Promise.all([
        load(1, pageSize, filters),
        loadCategoryOptions(),
      ]);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加入过滤类目失败');
    } finally {
      setFilteringCategoryKey(null);
    }
  }

  function confirmAddFilterCategory(record: Alibaba1688SelectionPoolVO) {
    const categoryName = String(record.categorySnapshot || '').trim();
    if (!categoryName) {
      message.warning('当前记录没有类目，无法加入过滤类目');
      return;
    }
    modal.confirm({
      title: '加入过滤类目？',
      content: `加入后，类目“${categoryName}”对应的选品池数据会在列表中自动隐藏。`,
      okText: '加入过滤',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        await quickAddFilterCategory(record);
      },
    });
  }

  async function handleImportFromDetail() {
    setImporting(true);
    const selectedIds = importAllMatching
      ? []
      : importSelectedRowKeys
        .map((item) => Number(item))
        .filter((item) => Number.isFinite(item) && item > 0);

    if (!importAllMatching && !selectedIds.length) {
      message.warning(importAllMatching ? '当前筛选下没有可导入的详情数据' : '请先勾选要导入的详情数据');
      setImporting(false);
      return;
    }

    try {
      const response = await alibaba1688SelectionPoolsApi.batchImportFromDetail({
        allMatching: importAllMatching,
        detailRecordIds: importAllMatching ? undefined : selectedIds,
        keyword: importAllMatching ? importFilters.keyword.trim() || undefined : undefined,
        detailRecordId: importAllMatching ? toNumber(importFilters.detailRecordId) : undefined,
        note: importNote.trim() || undefined,
      });
      const result = response.data;
      const successCount = Number(result?.successCount || 0);
      const skippedCount = Number(result?.skippedCount || 0);
      const failedItems = Array.isArray(result?.failures) ? result.failures : [];

      if (successCount > 0 || skippedCount > 0) {
        const parts = [`已导入 ${successCount} 条详情数据`];
        if (skippedCount > 0) {
          parts.push(`跳过 ${skippedCount} 条重复数据`);
        }
        message.success(parts.join('，'));
      }
      if (failedItems.length > 0) {
        Modal.warning({
          title: `导入完成，失败 ${failedItems.length} 条`,
          content: (
            <div className="alibaba1688-selection-pool-cell">
              {failedItems.slice(0, 8).map((item) => (
                <Typography.Text key={item.detailRecordId || item.message || Math.random()} type="danger">
                  详情记录 #{item.detailRecordId || '-'}：{item.message || '导入失败'}
                </Typography.Text>
              ))}
              {failedItems.length > 8 ? (
                <Typography.Text type="secondary">其余失败项请重新筛选后再试。</Typography.Text>
              ) : null}
            </div>
          ),
        });
      }
      if (successCount === 0) {
        if (skippedCount > 0 && failedItems.length === 0) {
          message.info('没有新增数据，已跳过重复数据');
        } else if (failedItems.length === 0) {
          message.info(importAllMatching ? '当前筛选下没有可导入的详情数据' : '没有新增数据');
        }
        return;
      }

      setImportOpen(false);
      setImportRows([]);
      setImportSelectedRowKeys([]);
      setImportAllMatching(false);
      setImportNote('');
      setPage(1);
      await load(1, pageSize, filters);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加入选品池失败');
    } finally {
      setImporting(false);
    }
  }

  const detailSkuRows = useMemo(
    () => normalizeArray(detailRecord?.skuRows, detailRecord?.skuList, detailRecord?.skus),
    [detailRecord],
  );

  const detailReports = useMemo(
    () => normalizeArray(detailRecord?.reports, detailRecord?.reportList),
    [detailRecord],
  );

  const displayCarouselImageUrls = useMemo(
    () => mapAlibabaImageProxyUrls(detailRecord?.carouselImageUrls, imageProxyConfig),
    [detailRecord, imageProxyConfig],
  );

  const displayDetailImageUrls = useMemo(
    () => mapAlibabaImageProxyUrls(detailRecord?.detailImageUrls, imageProxyConfig),
    [detailRecord, imageProxyConfig],
  );

  const importColumns: ColumnsType<Alibaba1688DetailRecordVO> = [
    {
      title: '详情ID',
      dataIndex: 'id',
      key: 'id',
      width: 92,
      render: (value: number) => <Typography.Text copyable={{ text: String(value) }}>{value}</Typography.Text>,
    },
    {
      title: '商品',
      key: 'goods',
      width: 360,
      render: (_, record) => {
        const imageUrl = getDisplayMainImage(record, imageProxyConfig);
        return (
          <div className="alibaba1688-selection-pool-main">
            {imageUrl ? (
              <Image width={48} height={48} src={imageUrl} alt={record.productName || record.offerId} />
            ) : null}
            <div className="alibaba1688-selection-pool-main-content">
              <Typography.Text strong className="alibaba1688-selection-pool-title">
                {record.productName || '-'}
              </Typography.Text>
              <Typography.Text type="secondary">{record.companyName || '-'}</Typography.Text>
              <Space size={6} wrap>
                {statusTag(record.status)}
                <Tag>{record.offerId || '-'}</Tag>
              </Space>
            </div>
          </div>
        );
      },
    },
    {
      title: '价格 / 时间',
      key: 'meta',
      width: 220,
      render: (_, record) => (
        <div className="alibaba1688-selection-pool-cell">
          <Typography.Text>价格：{formatPriceRange(record.minPrice, record.maxPrice)}</Typography.Text>
          <Typography.Text type="secondary">采集：{formatDateOnly(record.lastCollectedAt)}</Typography.Text>
          <Typography.Text type="secondary">凭证：{record.credentialName || '-'}</Typography.Text>
        </div>
      ),
    },
    {
      title: '链接',
      key: 'detailUrl',
      width: 280,
      render: (_, record) => (
        record.detailUrl ? (
          <a href={record.detailUrl} target="_blank" rel="noreferrer" className="alibaba1688-selection-pool-title-link" title={record.detailUrl}>
            {record.detailUrl}
          </a>
        ) : (
          <Typography.Text type="secondary">-</Typography.Text>
        )
      ),
    },
  ];

  const columns: ColumnsType<Alibaba1688SelectionPoolVO> = [
    {
      title: '商品',
      key: 'main',
      width: 360,
      render: (_, record) => {
        const imageUrl = getDisplayMainImage(record, imageProxyConfig);
        return (
          <div className="alibaba1688-selection-pool-main">
            {imageUrl ? (
              <Image width={56} height={56} src={imageUrl} alt={record.productName || String(record.id)} />
            ) : null}
            <div className="alibaba1688-selection-pool-main-content">
              {record.detailUrl ? (
                <a
                  href={record.detailUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="alibaba1688-selection-pool-title-link"
                  title={record.productName || record.detailUrl}
                >
                  {record.productName || '-'}
                </a>
              ) : (
                <Typography.Text strong className="alibaba1688-selection-pool-title">
                  {record.productName || '-'}
                </Typography.Text>
              )}
              <Typography.Text type="secondary">{record.companyName || '-'}</Typography.Text>
              <Typography.Text type="secondary">类目：{record.categorySnapshot || '-'}</Typography.Text>
              <Space size={6} wrap>
                <Tag>{record.sourcePlatform || '1688'}</Tag>
                {statusTag(record.status)}
                {(record.tags || []).slice(0, 3).map((tag) => <Tag key={tag}>{tag}</Tag>)}
              </Space>
            </div>
          </div>
        );
      },
    },
    {
      title: '来源与快照',
      key: 'source',
      width: 320,
      render: (_, record) => (
        <div className="alibaba1688-selection-pool-cell">
          <Typography.Text copyable={{ text: record.offerId || '' }}>offerId：{record.offerId || '-'}</Typography.Text>
          <Typography.Text type="secondary">详情记录：{record.detailRecordId ?? '-'}</Typography.Text>
          <Typography.Text type="secondary">价格区间：{formatPriceRange(record.minPrice, record.maxPrice)}</Typography.Text>
          <Typography.Text type="secondary">MOQ：{record.moqSnapshot ?? '-'}</Typography.Text>
          <Typography.Text type="secondary">起批量：{record.startBatchQtySnapshot ?? '-'}</Typography.Text>
          <Typography.Text type="secondary">月销量：{record.monthlySalesSnapshot || '-'}</Typography.Text>
          <Typography.Text type="secondary">最大重量：{formatWeightG(record.aiMaxWeightG)}</Typography.Text>
          <Typography.Text type={record.aiMaxDimensionCm !== null && record.aiMaxDimensionCm !== undefined && record.aiMaxDimensionCm > 10 ? 'danger' : 'secondary'}>
            最大尺寸：{formatDimensionCm(record.aiMaxDimensionCm)}
            {record.aiMaxDimensionSource ? `（${record.aiMaxDimensionSource}）` : ''}
          </Typography.Text>
          <Typography.Text type="secondary">基础快递费：{formatMoney(record.baseFreightSnapshot)}</Typography.Text>
          <Typography.Text type="secondary">重量费：{formatMoney(calculateWeightFee(record.aiMaxWeightG))}（不足30g按30g，69元/1000g + 6）</Typography.Text>
          <Typography.Text type="secondary">预估成本：{formatMoney(calculateEstimatedCost(record))}</Typography.Text>
          <Typography.Text type="secondary">最低供货价：{formatMoney(calculateMinimumSupplyPrice(record))}</Typography.Text>
        </div>
      ),
    },
    {
      title: '选品结果',
      key: 'result',
      width: 260,
      render: (_, record) => (
        <div className="alibaba1688-selection-pool-cell">
          <Typography.Text>综合评分：{record.overallScore ?? '-'}</Typography.Text>
          <Typography.Text type="secondary">SKU：{record.skuCount ?? 0}</Typography.Text>
          <Typography.Text type="secondary">报告：{record.reportCount ?? 0}</Typography.Text>
          <Space size={4} wrap>
            <Typography.Text type="secondary">AI结论：</Typography.Text>
            {selectionDecisionTag(record.aiSelectionDecision)}
          </Space>
          <Typography.Text type="secondary">AI评分：{record.aiSelectionScore ?? '-'}</Typography.Text>
          <Typography.Text type="secondary">AI销量：{record.aiDetectedSalesText || record.aiDetectedSalesVolume || '-'}</Typography.Text>
          <Typography.Text type="secondary">竞品评分：{record.temuCompeteScore ?? '-'}</Typography.Text>
          <Space size={[4, 4]} wrap>
            {aiRiskTag('液体', record.aiContainsLiquid)}
            {aiRiskTag('带电', record.aiContainsBattery)}
            {aiRiskTag('易碎', record.aiFragile)}
            {aiRiskTag('侵权', record.aiPotentialBrandInfringement)}
            {record.temuSiteExceptionBlocked ? <Tag color="red">加站异常</Tag> : null}
          </Space>
          {record.temuSiteExceptionBlocked ? (
            <Typography.Text type="danger" ellipsis title={record.temuSiteExceptionReason || ''}>
              加站异常：{record.temuSiteExceptionReason || '-'}
            </Typography.Text>
          ) : null}
          {record.pushedProductCollectionId ? (
            <Typography.Link onClick={() => navigate(`/platform/product-collections/${record.pushedProductCollectionId}`)}>
              已入采集库：#{record.pushedProductCollectionId}
            </Typography.Link>
          ) : (
            <Typography.Text type="secondary">采集库：未推送</Typography.Text>
          )}
          <Typography.Text type="secondary">
            目标店铺：{record.targetShopNames?.length ? record.targetShopNames.join('、') : '-'}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '时间',
      key: 'time',
      width: 190,
      render: (_, record) => (
        <div className="alibaba1688-selection-pool-cell">
          <Typography.Text>1688上架：{formatDateOnly(record.publishedAt1688)}</Typography.Text>
          <Typography.Text>更新：{formatDateOnly(record.updatedAt)}</Typography.Text>
          <Typography.Text type="secondary">采集：{formatDateOnly(record.detailLastCollectedAt)}</Typography.Text>
          <Typography.Text type="secondary">最新报告：{formatDateOnly(record.latestReportAt)}</Typography.Text>
        </div>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 240,
      fixed: 'right',
      render: (_, record) => (
        <div className="alibaba1688-selection-pool-actions">
          <Button
            size="small"
            type="primary"
            icon={<EyeOutlined />}
            className="alibaba1688-selection-pool-action-primary"
            onClick={() => void openDetail(record)}
          >
            查看详情
          </Button>
          <Button
            size="small"
            icon={<RobotOutlined />}
            className="alibaba1688-selection-pool-action-button"
            loading={reportingKey === `${record.id}-AI_SELECTION`}
            onClick={() => void handleCreateReport(record.id, 'AI_SELECTION')}
          >
            AI报告
          </Button>
          <Button
            size="small"
            icon={<BarChartOutlined />}
            className="alibaba1688-selection-pool-action-button"
            loading={reportingKey === `${record.id}-TEMU_COMPETE`}
            onClick={() => void handleCreateReport(record.id, 'TEMU_COMPETE')}
          >
            竞品报告
          </Button>
          {record.pushedProductCollectionId ? (
            <Button
              size="small"
              className="alibaba1688-selection-pool-action-button"
              onClick={() => navigate(`/platform/product-collections/${record.pushedProductCollectionId}`)}
            >
              查看采集库
            </Button>
          ) : (
            <Button
              size="small"
              className="alibaba1688-selection-pool-action-button"
              loading={pushingKey === record.id}
              onClick={() => void handlePushToProductCollection(record)}
            >
              推送采集库
            </Button>
          )}
          <Button
            size="small"
            danger
            icon={<FilterOutlined />}
            className="alibaba1688-selection-pool-action-filter"
            loading={filteringCategoryKey === `${record.id}-${String(record.categorySnapshot || '').trim()}`}
            disabled={!String(record.categorySnapshot || '').trim()}
            onClick={() => confirmAddFilterCategory(record)}
          >
            加入过滤类目
          </Button>
        </div>
      ),
    },
  ];

  const skuColumns: ColumnsType<Alibaba1688SelectionPoolSkuVO> = [
    {
      title: 'SKU',
      key: 'sku',
      width: 280,
      render: (_, record) => {
        const imageUrl = getDisplaySkuImage(record, imageProxyConfig);
        return (
          <div className="alibaba1688-selection-pool-main">
            {imageUrl ? <Image width={52} height={52} src={imageUrl} alt={record.skuId || String(record.id)} /> : null}
            <div className="alibaba1688-selection-pool-main-content">
              <Typography.Text strong>{record.specKey || '-'}</Typography.Text>
              <Typography.Text type="secondary">skuId：{record.skuId || '-'}</Typography.Text>
              <Space size={6} wrap>
                {statusTag(record.selectionStatus)}
                {record.isPrimarySku ? <Tag color="blue">主推</Tag> : null}
              </Space>
            </div>
          </div>
        );
      },
    },
    {
      title: '快照',
      key: 'snapshot',
      width: 210,
      render: (_, record) => (
        <div className="alibaba1688-selection-pool-cell">
          <Typography.Text>SKU价：{formatMoney(record.price)}</Typography.Text>
          <Typography.Text type="secondary">页面库存：{record.stock ?? '-'}</Typography.Text>
          <Typography.Text type="secondary">人工库存：{record.manualStockQty ?? '-'}</Typography.Text>
          <Typography.Text type="secondary">重量：{record.weightValue ?? '-'} {record.weightSource ? `(${record.weightSource})` : ''}</Typography.Text>
          <Typography.Text type={record.dimensionValue !== null && record.dimensionValue !== undefined && record.dimensionValue > 10 ? 'danger' : 'secondary'}>
            最大尺寸：{formatDimensionCm(record.dimensionValue)}
            {record.dimensionSource ? `（${record.dimensionSource}）` : ''}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '成本与利润',
      key: 'profit',
      width: 260,
      render: (_, record) => (
        <div className="alibaba1688-selection-pool-cell">
          <Typography.Text>预估采购：{formatMoney(record.estimatedPurchasePrice)}</Typography.Text>
          <Typography.Text type="secondary">预估头程：{formatMoney(record.estimatedFirstLegFee)}</Typography.Text>
          <Typography.Text type="secondary">预估TEMU价：{formatMoney(record.estimatedTemuPrice)}</Typography.Text>
          <Typography.Text type="secondary">TEMU最终给价：{formatMoney(record.temuFinalPrice)}</Typography.Text>
          <Typography.Text type={record.estimatedUnitProfit !== null && record.estimatedUnitProfit !== undefined && record.estimatedUnitProfit < 0 ? 'danger' : undefined}>
            单件利润：{formatMoney(record.estimatedUnitProfit)}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '风险与备注',
      key: 'risk',
      width: 220,
      render: (_, record) => (
        <div className="alibaba1688-selection-pool-cell">
          <Typography.Text>库存风险：{statusTag(record.stockRiskLevel)}</Typography.Text>
          <Typography.Text type="secondary">确认人：{record.stockCheckedBy || '-'}</Typography.Text>
          <Typography.Text type="secondary">确认时间：{formatDateTime(record.stockCheckedAt)}</Typography.Text>
          <Typography.Paragraph className="alibaba1688-selection-pool-remark" ellipsis={{ rows: 2, tooltip: record.remark || '' }}>
            {record.remark || '-'}
          </Typography.Paragraph>
        </div>
      ),
    },
  ];

  const reportColumns: ColumnsType<Alibaba1688SelectionPoolReportVO> = [
    {
      title: '报告',
      key: 'report',
      width: 260,
      render: (_, record) => (
        <div className="alibaba1688-selection-pool-cell">
          <Typography.Text strong>{record.title || `报告 #${record.id}`}</Typography.Text>
          <Typography.Text type="secondary">{record.reportType || '-'}</Typography.Text>
          <Typography.Text type="secondary">版本：{record.versionNo ?? '-'}</Typography.Text>
        </div>
      ),
    },
    {
      title: '状态 / 分数',
      key: 'status',
      width: 150,
      render: (_, record) => (
        <div className="alibaba1688-selection-pool-cell">
          {statusTag(record.status)}
          <Typography.Text type="secondary">分数：{record.score ?? '-'}</Typography.Text>
        </div>
      ),
    },
    {
      title: '摘要',
      dataIndex: 'summary',
      key: 'summary',
      width: 440,
      render: (value: string | null) => (
        <Typography.Paragraph className="alibaba1688-selection-pool-remark" ellipsis={{ rows: 3, tooltip: value || '' }}>
          {value || '-'}
        </Typography.Paragraph>
      ),
    },
    {
      title: '时间',
      key: 'time',
      width: 180,
      render: (_, record) => (
        <div className="alibaba1688-selection-pool-cell">
          <Typography.Text>{formatDateTime(record.createdAt)}</Typography.Text>
          <Typography.Text type="secondary">{record.sourceType || '-'}</Typography.Text>
        </div>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_, record) => (
        <Button
          size="small"
          onClick={() => {
            setReportPreview(record);
            setReportPreviewOpen(true);
          }}
        >
          查看
        </Button>
      ),
    },
  ];

  const detailMainItems: Array<{ label: string; value: ReactNode; span?: number }> = detailRecord
    ? [
        { label: 'ID', value: detailRecord.id },
        { label: '详情记录ID', value: detailRecord.detailRecordId },
        { label: 'offerId', value: detailRecord.offerId || '-' },
        { label: '状态', value: statusTag(detailRecord.status) },
        { label: '商品名称', value: detailRecord.productName || '-', span: 2 },
        { label: '公司名称', value: detailRecord.companyName || '-' },
        { label: '店铺回头率', value: formatMetricValue(detailRecord.repeatCustomerRateSnapshot, '%') },
        { label: '店铺服务分', value: formatMetricValue(detailRecord.serviceScoreSnapshot) },
        { label: '准时发货率', value: formatMetricValue(detailRecord.onTimeDeliveryRateSnapshot, '%') },
        { label: '店铺好评率', value: formatMetricValue(detailRecord.shopPositiveRateSnapshot, '%') },
        { label: '实力商家', value: formatBooleanText(detailRecord.powerSellerSnapshot) },
        { label: '入驻信息', value: detailRecord.settledYearsTextSnapshot || '-' },
        { label: '主营类目', value: detailRecord.mainBusinessSnapshot || '-', span: 2 },
        { label: '类目快照', value: detailRecord.categorySnapshot || '-' },
        { label: '发货地', value: detailRecord.shippingLocationSnapshot || '-' },
        { label: '1688上架时间', value: formatDateTime(detailRecord.publishedAt1688) },
        { label: '基础快递费', value: formatMoney(detailRecord.baseFreightSnapshot) },
        { label: 'MOQ快照', value: detailRecord.moqSnapshot ?? '-' },
        { label: '起批量', value: detailRecord.startBatchQtySnapshot ?? '-' },
        { label: 'MOQ文案', value: detailRecord.moqTextSnapshot || '-', span: 2 },
        { label: '月销量快照', value: detailRecord.monthlySalesSnapshot || '-' },
        { label: 'AI识别 MOQ', value: detailRecord.aiDetectedMoq ?? '-' },
        { label: 'AI识别销量', value: detailRecord.aiDetectedSalesText || detailRecord.aiDetectedSalesVolume || '-' },
        { label: 'AI最大重量', value: formatWeightG(detailRecord.aiMaxWeightG) },
        {
          label: 'AI最大尺寸',
          value: (
            <Typography.Text type={detailRecord.aiMaxDimensionCm !== null && detailRecord.aiMaxDimensionCm !== undefined && detailRecord.aiMaxDimensionCm > 10 ? 'danger' : undefined}>
              {formatDimensionCm(detailRecord.aiMaxDimensionCm)}
              {detailRecord.aiMaxDimensionSource ? `（${detailRecord.aiMaxDimensionSource}）` : ''}
            </Typography.Text>
          ),
        },
        { label: '尺寸证据', value: detailRecord.aiDimensionEvidence || '-', span: 2 },
        { label: '重量费', value: `${formatMoney(calculateWeightFee(detailRecord.aiMaxWeightG))}（不足30g按30g，69元/1000g + 6）` },
        { label: '预估成本', value: formatMoney(calculateEstimatedCost(detailRecord)) },
        { label: '最低供货价', value: formatMoney(calculateMinimumSupplyPrice(detailRecord)) },
        { label: 'AI选品结论', value: selectionDecisionTag(detailRecord.aiSelectionDecision) },
        { label: '液体风险', value: formatBooleanText(detailRecord.aiContainsLiquid) },
        { label: '带电风险', value: formatBooleanText(detailRecord.aiContainsBattery) },
        { label: '易碎风险', value: formatBooleanText(detailRecord.aiFragile) },
        { label: '侵权风险', value: formatBooleanText(detailRecord.aiPotentialBrandInfringement) },
        { label: '综合评分', value: detailRecord.overallScore ?? '-' },
        { label: '目标店铺', value: detailRecord.targetShopNames?.length ? detailRecord.targetShopNames.join('、') : '-' },
        { label: '标签', value: detailRecord.tags?.length ? detailRecord.tags.join('、') : '-' },
        { label: '入池原因', value: detailRecord.selectedReason || '-', span: 2 },
        { label: '淘汰原因', value: detailRecord.rejectReason || '-', span: 2 },
        { label: '备注', value: detailRecord.note || '-', span: 2 },
        {
          label: '详情链接',
          value: detailRecord.detailUrl ? <a href={detailRecord.detailUrl} target="_blank" rel="noreferrer">{detailRecord.detailUrl}</a> : '-',
          span: 2,
        },
        { label: '首次发现', value: formatDateTime(detailRecord.firstSeenAt) },
        { label: '最近采集', value: formatDateTime(detailRecord.detailLastCollectedAt) },
        {
          label: '最新AI报告',
          value: `${detailRecord.aiSelectionAnalysisStatus || '-'} / ${detailRecord.aiSelectionScore ?? '-'} / ${detailRecord.aiSelectionDecision || '-'}`,
        },
        { label: '最新竞品报告', value: `${detailRecord.temuCompeteAnalysisStatus || '-'} / ${detailRecord.temuCompeteScore ?? '-'}` },
      ]
    : [];

  const reportMirrorItems = detailRecord
    ? [
        {
          key: 'ai',
          label: 'AI选品报告',
          children: (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Descriptions bordered size="small" column={2}>
                <Descriptions.Item label="标题" span={2}>
                  {detailRecord.aiSelectionReportTitle || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="状态">
                  {statusTag(detailRecord.aiSelectionAnalysisStatus)}
                </Descriptions.Item>
                <Descriptions.Item label="分数">{detailRecord.aiSelectionScore ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="结论">
                  {selectionDecisionTag(detailRecord.aiSelectionDecision)}
                </Descriptions.Item>
                <Descriptions.Item label="最大重量">
                  {formatWeightG(detailRecord.aiMaxWeightG)}
                </Descriptions.Item>
                <Descriptions.Item label="最大尺寸">
                  <Typography.Text type={detailRecord.aiMaxDimensionCm !== null && detailRecord.aiMaxDimensionCm !== undefined && detailRecord.aiMaxDimensionCm > 10 ? 'danger' : undefined}>
                    {formatDimensionCm(detailRecord.aiMaxDimensionCm)}
                    {detailRecord.aiMaxDimensionSource ? `（${detailRecord.aiMaxDimensionSource}）` : ''}
                  </Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label="尺寸证据" span={2}>
                  {detailRecord.aiDimensionEvidence || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="AI识别MOQ">
                  {detailRecord.aiDetectedMoq ?? '-'}
                </Descriptions.Item>
                <Descriptions.Item label="AI识别销量">
                  {detailRecord.aiDetectedSalesText || detailRecord.aiDetectedSalesVolume || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="时间" span={2}>
                  {formatDateTime(detailRecord.aiSelectionAnalysisAt)}
                </Descriptions.Item>
                <Descriptions.Item label="风险标记" span={2}>
                  <Space size={[4, 4]} wrap>
                    {aiRiskTag('液体', detailRecord.aiContainsLiquid)}
                    {aiRiskTag('带电', detailRecord.aiContainsBattery)}
                    {aiRiskTag('易碎', detailRecord.aiFragile)}
                    {aiRiskTag('侵权', detailRecord.aiPotentialBrandInfringement)}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="摘要" span={2}>
                  {detailRecord.aiSelectionAnalysisSummary || '-'}
                </Descriptions.Item>
              </Descriptions>
              <Card size="small" title="正文 / JSON">
                {detailRecord.aiSelectionReportContent
                  ? renderJsonBlock(detailRecord.aiSelectionReportContent)
                  : renderJsonBlock(detailRecord.aiSelectionReportJson)}
              </Card>
            </Space>
          ),
        },
        {
          key: 'temu',
          label: 'TEMU竞品报告',
          children: (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Descriptions bordered size="small" column={2}>
                <Descriptions.Item label="标题" span={2}>
                  {detailRecord.temuCompeteReportTitle || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="状态">
                  {statusTag(detailRecord.temuCompeteAnalysisStatus)}
                </Descriptions.Item>
                <Descriptions.Item label="分数">{detailRecord.temuCompeteScore ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="时间" span={2}>
                  {formatDateTime(detailRecord.temuCompeteAnalysisAt)}
                </Descriptions.Item>
                <Descriptions.Item label="摘要" span={2}>
                  {detailRecord.temuCompeteAnalysisSummary || '-'}
                </Descriptions.Item>
              </Descriptions>
              <Card size="small" title="正文 / JSON">
                {detailRecord.temuCompeteReportContent
                  ? renderJsonBlock(detailRecord.temuCompeteReportContent)
                  : renderJsonBlock(detailRecord.temuCompeteReportJson)}
              </Card>
            </Space>
          ),
        },
      ]
    : [];

  const workerThreadRows = normalizeArray(workerStatus?.threads).slice().sort((left, right) => {
    const leftIndex = left.workerIndex ?? 0;
    const rightIndex = right.workerIndex ?? 0;
    return leftIndex - rightIndex;
  });

  const workerRecentTaskRows = normalizeArray(workerStatus?.recentTaskEvents);

  const workerThreadColumns: ColumnsType<NonNullable<Alibaba1688SelectionPoolAiReportWorkerStatusVO['threads']>[number]> = [
    {
      title: '线程',
      key: 'thread',
      width: 190,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.threadName || '-'}</Typography.Text>
          <Typography.Text type="secondary">#{record.workerIndex ?? '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      key: 'state',
      width: 110,
      render: (_, record) => {
        if (record.stuck) {
          return <Tag color="red">卡住</Tag>;
        }
        if (record.working) {
          return <Tag color="processing">运行中</Tag>;
        }
        if (record.alive) {
          return <Tag color="green">空闲</Tag>;
        }
        return <Tag>离线</Tag>;
      },
    },
    {
      title: '当前任务',
      key: 'task',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{record.currentPoolId ? `池ID ${record.currentPoolId}` : '-'}</Typography.Text>
          <Typography.Text type="secondary">{record.currentOfferId || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '开始时间',
      dataIndex: 'currentTaskStartedAt',
      width: 170,
      render: (value) => formatDateTime(value),
    },
    {
      title: '最近结束',
      dataIndex: 'lastFinishedAt',
      width: 170,
      render: (value) => formatDateTime(value),
    },
    {
      title: '成功/失败',
      key: 'stats',
      width: 110,
      render: (_, record) => `${record.successCount ?? 0}/${record.failureCount ?? 0}`,
    },
    {
      title: '最近错误',
      dataIndex: 'lastError',
      render: (value) => (
        <Typography.Paragraph style={{ marginBottom: 0 }} ellipsis={{ rows: 2, tooltip: value || '' }}>
          <span style={{ color: value ? '#cf1322' : undefined }}>{value || '-'}</span>
        </Typography.Paragraph>
      ),
    },
  ];

  const workerRecentTaskColumns: ColumnsType<NonNullable<Alibaba1688SelectionPoolAiReportWorkerStatusVO['recentTaskEvents']>[number]> = [
    {
      title: '线程',
      key: 'thread',
      width: 190,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.threadName || '-'}</Typography.Text>
          <Typography.Text type="secondary">#{record.workerIndex ?? '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value) => formatWorkerEventStatus(value),
    },
    {
      title: '任务',
      key: 'task',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{record.poolId ? `池ID ${record.poolId}` : '-'}</Typography.Text>
          <Typography.Text type="secondary">{record.offerId || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      width: 170,
      render: (value) => formatDateTime(value),
    },
    {
      title: '结束时间',
      dataIndex: 'finishedAt',
      width: 170,
      render: (value) => formatDateTime(value),
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      width: 100,
      render: (value) => formatDurationMs(value),
    },
    {
      title: '错误',
      dataIndex: 'error',
      render: (value) => (
        <Typography.Paragraph style={{ marginBottom: 0 }} ellipsis={{ rows: 2, tooltip: value || '' }}>
          <span style={{ color: value ? '#cf1322' : undefined }}>{value || '-'}</span>
        </Typography.Paragraph>
      ),
    },
  ];

  const autoPushLogColumns: ColumnsType<Alibaba1688SelectionPoolAutoPushLogVO> = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (value) => formatDateTime(value),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value) => <Tag color={value === 'SUCCESS' ? 'green' : value === 'FAILED' ? 'red' : 'default'}>{value || '-'}</Tag>,
    },
    {
      title: '选品池',
      dataIndex: 'poolId',
      width: 110,
      render: (value) => value ? <Typography.Link onClick={() => void openDetail({ id: value } as Alibaba1688SelectionPoolVO)}>#{value}</Typography.Link> : '-',
    },
    {
      title: 'Offer',
      dataIndex: 'offerId',
      width: 150,
      render: (value) => value || '-',
    },
    {
      title: '商品库',
      dataIndex: 'productCollectionId',
      width: 120,
      render: (value) => value ? <Typography.Link onClick={() => navigate(`/platform/product-collections/${value}`)}>#{value}</Typography.Link> : '-',
    },
    {
      title: '店铺',
      dataIndex: 'targetShopNames',
      width: 160,
      render: (value) => Array.isArray(value) && value.length ? value.join('、') : '-',
    },
    {
      title: '结果',
      dataIndex: 'message',
      render: (_value, record) => (
        <Typography.Paragraph style={{ marginBottom: 0 }} ellipsis={{ rows: 2, tooltip: record.errorMessage || record.message || '' }}>
          <span style={{ color: record.errorMessage ? '#cf1322' : undefined }}>
            {record.errorMessage || record.message || '-'}
          </span>
        </Typography.Paragraph>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card size="small" title="AI报告 Worker" extra={
        <Space size={8}>
          <Button size="small" onClick={openWorkerLogs}>
            查看日志
          </Button>
          <Button size="small" loading={workerLoading} onClick={() => void loadWorkerStatus()}>
            刷新状态
          </Button>
        </Space>
      }>
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Descriptions size="small" column={4} bordered>
            <Descriptions.Item label="是否运行">
              <Tag color={workerStatus?.running ? 'green' : workerStatus?.running === false ? 'default' : 'gold'}>
                {formatWorkerRunningText(workerStatus?.running)}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="配置线程数">
              {resolveWorkerThreadCount(workerConfig, workerStatus) ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="轮询间隔">
              {formatMs(resolveWorkerPollIntervalMs(workerConfig, workerStatus))}
            </Descriptions.Item>
            <Descriptions.Item label="当前活跃线程数">
              {resolveWorkerActiveThreadCount(workerStatus) ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="待处理数量">
              {workerStatus?.pendingCount ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="处理中数量">
              {resolveWorkerProcessingCount(workerStatus) ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="已完成数量">
              {resolveWorkerCompletedCount(workerStatus) ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="失败数量">
              {workerStatus?.failedCount ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="是否存在卡死线程">
              <Tag color={workerStatus?.hasStuckThreads ? 'red' : workerStatus?.hasStuckThreads === false ? 'green' : 'default'}>
                {formatBooleanText(workerStatus?.hasStuckThreads)}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="最后检查时间" span={3}>
              {formatDateTime(resolveWorkerLastCheckedAt(workerStatus))}
            </Descriptions.Item>
            <Descriptions.Item label="最后错误" span={4}>
              <Typography.Paragraph
                style={{ marginBottom: 0 }}
                ellipsis={{ rows: 2, tooltip: workerStatus?.lastError || '' }}
              >
                <span style={{ color: workerStatus?.lastError ? '#cf1322' : undefined }}>
                  {workerStatus?.lastError || '-'}
                </span>
              </Typography.Paragraph>
            </Descriptions.Item>
          </Descriptions>

          <Space wrap>
            <Space size={8} align="center">
              <Typography.Text>线程数配置</Typography.Text>
              <InputNumber
                value={workerThreadCount ?? undefined}
                onChange={(value) => setWorkerThreadCount(value ?? null)}
                min={1}
                precision={0}
                controls={false}
                placeholder="线程数"
                style={{ width: 120 }}
              />
            </Space>
            <Button type="primary" loading={workerSaving} onClick={() => void saveWorkerConfig()}>
              保存线程数配置
            </Button>
            <Button loading={workerAction === 'start'} onClick={() => void toggleWorker('start')}>
              启动任务
            </Button>
            <Button danger loading={workerAction === 'stop'} onClick={() => void toggleWorker('stop')}>
              停止任务
            </Button>
            <Button
              type="dashed"
              danger
              loading={workerAction === 'retry'}
              disabled={!workerStatus?.failedCount}
              onClick={() => void retryFailedWorkerTasks()}
            >
              重试失败
            </Button>
            <Typography.Text type="secondary">
              启动时若后端提示“无任务可执行”或“当前无待执行任务”，会直接以消息提示，不影响页面继续操作。
            </Typography.Text>
          </Space>
        </Space>
      </Card>

      <Card>
        <Space wrap>
          <Input
            value={filters.keyword}
            onChange={(e) => updateFilter('keyword', e.target.value)}
            allowClear
            placeholder="搜索商品名 / offerId / 公司 / 备注 / 类目"
            style={{ width: 320 }}
            onPressEnter={() => {
              setPage(1);
              void load(1, pageSize, filters);
            }}
          />
          <Select
            value={filters.category}
            onChange={(value) => updateFilter('category', value || '')}
            allowClear
            showSearch
            placeholder="选择类目"
            style={{ width: 260 }}
            options={categoryOptions.map((item) => ({
              value: item.value,
              label: `${item.label} (${item.count})`,
            }))}
            optionFilterProp="label"
          />
          <Input
            value={filters.status}
            onChange={(e) => updateFilter('status', e.target.value)}
            allowClear
            placeholder="状态"
            style={{ width: 180 }}
            onPressEnter={() => {
              setPage(1);
              void load(1, pageSize, filters);
            }}
          />
          <Select
            value={filters.pushedStatus}
            onChange={(value) => updateFilter('pushedStatus', value)}
            style={{ width: 150 }}
            options={[
              { value: 'UNPUSHED', label: '未推送' },
              { value: 'PUSHED', label: '已推送' },
              { value: 'ALL', label: '全部推送状态' },
            ]}
          />
          <Select
            value={filters.aiSelectionDecision || undefined}
            onChange={(value) => updateFilter('aiSelectionDecision', value || '')}
            allowClear
            placeholder="AI结论"
            style={{ width: 140 }}
            options={[
              { value: 'SUITABLE', label: '适合' },
              { value: 'UNSUITABLE', label: '不适合' },
              { value: 'REVIEW', label: '需复核' },
              { value: 'UNANALYZED', label: '未分析' },
            ]}
          />
          <Select
            value={filters.liquidStatus}
            onChange={(value) => updateFilter('liquidStatus', value)}
            style={{ width: 130 }}
            options={[
              { value: 'ALL', label: '全部液体' },
              { value: 'YES', label: '液体：是' },
              { value: 'NO', label: '液体：否' },
            ]}
          />
          <Select
            value={filters.fragileStatus}
            onChange={(value) => updateFilter('fragileStatus', value)}
            style={{ width: 130 }}
            options={[
              { value: 'ALL', label: '全部易碎' },
              { value: 'YES', label: '易碎：是' },
              { value: 'NO', label: '易碎：否' },
            ]}
          />
          <Input
            value={filters.detailRecordId}
            onChange={(e) => updateFilter('detailRecordId', e.target.value)}
            allowClear
            placeholder="详情记录 ID"
            style={{ width: 160 }}
            onPressEnter={() => {
              setPage(1);
              void load(1, pageSize, filters);
            }}
          />
          <InputNumber
            value={filters.skuPriceMin ? Number(filters.skuPriceMin) : undefined}
            onChange={(value) => updateFilter('skuPriceMin', value === null ? '' : String(value))}
            min={0}
            precision={2}
            controls={false}
            placeholder="SKU价最低"
            style={{ width: 140 }}
          />
          <InputNumber
            value={filters.skuPriceMax ? Number(filters.skuPriceMax) : undefined}
            onChange={(value) => updateFilter('skuPriceMax', value === null ? '' : String(value))}
            min={0}
            precision={2}
            controls={false}
            placeholder="SKU价最高"
            style={{ width: 140 }}
          />
          <InputNumber
            value={filters.moqMin ? Number(filters.moqMin) : undefined}
            onChange={(value) => updateFilter('moqMin', value === null ? '' : String(value))}
            min={0}
            precision={0}
            controls={false}
            placeholder="MOQ最低"
            style={{ width: 120 }}
          />
          <InputNumber
            value={filters.moqMax ? Number(filters.moqMax) : undefined}
            onChange={(value) => updateFilter('moqMax', value === null ? '' : String(value))}
            min={0}
            precision={0}
            controls={false}
            placeholder="MOQ最高"
            style={{ width: 120 }}
          />
          <InputNumber
            value={filters.startBatchQtyMin ? Number(filters.startBatchQtyMin) : undefined}
            onChange={(value) => updateFilter('startBatchQtyMin', value === null ? '' : String(value))}
            min={0}
            precision={0}
            controls={false}
            placeholder="起批量最低"
            style={{ width: 130 }}
          />
          <InputNumber
            value={filters.startBatchQtyMax ? Number(filters.startBatchQtyMax) : undefined}
            onChange={(value) => updateFilter('startBatchQtyMax', value === null ? '' : String(value))}
            min={0}
            precision={0}
            controls={false}
            placeholder="起批量最高"
            style={{ width: 130 }}
          />
          <InputNumber
            value={filters.aiMaxDimensionCmMin ? Number(filters.aiMaxDimensionCmMin) : undefined}
            onChange={(value) => updateFilter('aiMaxDimensionCmMin', value === null ? '' : String(value))}
            min={0}
            precision={2}
            controls={false}
            placeholder="最大尺寸最低cm"
            style={{ width: 150 }}
          />
          <InputNumber
            value={filters.aiMaxDimensionCmMax ? Number(filters.aiMaxDimensionCmMax) : undefined}
            onChange={(value) => updateFilter('aiMaxDimensionCmMax', value === null ? '' : String(value))}
            min={0}
            precision={2}
            controls={false}
            placeholder="最大尺寸最高cm"
            style={{ width: 150 }}
          />
          <InputNumber
            value={filters.aiMaxWeightGMin ? Number(filters.aiMaxWeightGMin) : undefined}
            onChange={(value) => updateFilter('aiMaxWeightGMin', value === null ? '' : String(value))}
            min={0}
            precision={2}
            controls={false}
            placeholder="最大重量最低g"
            style={{ width: 145 }}
          />
          <InputNumber
            value={filters.aiMaxWeightGMax ? Number(filters.aiMaxWeightGMax) : undefined}
            onChange={(value) => updateFilter('aiMaxWeightGMax', value === null ? '' : String(value))}
            min={0}
            precision={2}
            controls={false}
            placeholder="最大重量最高g"
            style={{ width: 145 }}
          />
          <Select
            value={filters.siteExceptionStatus}
            onChange={(value) => updateFilter('siteExceptionStatus', value)}
            style={{ width: 150 }}
            options={[
              { value: 'NORMAL', label: '排除加站异常' },
              { value: 'BLOCKED', label: '只看加站异常' },
              { value: 'ALL', label: '全部加站状态' },
            ]}
          />
          <Button
            type="primary"
            loading={loading}
            onClick={() => {
              setPage(1);
              void load(1, pageSize, filters);
            }}
          >
            查询
          </Button>
          <Button
            onClick={() => {
              setFilters(initialFilters);
              setPage(1);
              setPageSize(20);
              void load(1, 20, initialFilters);
            }}
          >
            重置
          </Button>
          <Button
            icon={<FilterOutlined />}
            onClick={() => navigate('/platform/alibaba1688-selection-pool-filter-categories')}
          >
            过滤类目配置
          </Button>
          <Button
            icon={<RobotOutlined />}
            onClick={() => void openAutoPushModal()}
          >
            自动推送
          </Button>
          <Button
            type="dashed"
            onClick={() => void openImportModal()}
          >
            从详情导入
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<Alibaba1688SelectionPoolVO>
          rowKey="id"
          className="alibaba1688-selection-pool-table"
          columns={columns}
          dataSource={rows}
          loading={loading}
          tableLayout="fixed"
          scroll={{ x: 1600 }}
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
            void load(nextPage, nextPageSize, filters);
          }}
        />
      </Card>

      <Modal
        title="AI报告 Worker 日志"
        open={workerLogsOpen}
        onCancel={() => setWorkerLogsOpen(false)}
        footer={[
          <Button key="close" onClick={() => setWorkerLogsOpen(false)}>
            关闭
          </Button>,
        ]}
        width={1440}
        destroyOnClose={false}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Card size="small" title="当前线程情况" bodyStyle={{ padding: 0 }}>
            <Table
              size="small"
              rowKey={(record) => String(record.workerIndex ?? record.threadName ?? 'worker')}
              columns={workerThreadColumns}
              dataSource={workerThreadRows}
              pagination={false}
              loading={workerLoading}
              locale={{ emptyText: '暂无线程状态' }}
              scroll={{ x: 1120 }}
            />
          </Card>

          <Card size="small" title="最近执行记录（仅保留最新 18 条）" bodyStyle={{ padding: 0 }}>
            <Table
              size="small"
              rowKey={(record) => String(record.sequence ?? `${record.threadName ?? 'worker'}-${record.startedAt ?? ''}-${record.status ?? ''}`)}
              columns={workerRecentTaskColumns}
              dataSource={workerRecentTaskRows}
              pagination={false}
              loading={workerLoading}
              locale={{ emptyText: '暂无执行记录' }}
              scroll={{ x: 1160 }}
            />
          </Card>
        </Space>
      </Modal>

      <Modal
        title="1688选品池自动推送"
        open={autoPushOpen}
        onCancel={() => setAutoPushOpen(false)}
        footer={[
          <Button key="refresh" loading={autoPushLoading} onClick={() => void loadAutoPushPanel()}>
            刷新
          </Button>,
          <Button key="save" type="primary" loading={autoPushSaving} onClick={() => void saveAutoPushConfig()}>
            保存配置
          </Button>,
          <Button key="start" loading={autoPushAction === 'start'} onClick={() => void toggleAutoPush('start')}>
            启动
          </Button>,
          <Button key="stop" danger loading={autoPushAction === 'stop'} onClick={() => void toggleAutoPush('stop')}>
            停止
          </Button>,
          <Button key="close" onClick={() => setAutoPushOpen(false)}>
            关闭
          </Button>,
        ]}
        width={1280}
        destroyOnClose={false}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions size="small" column={4} bordered>
            <Descriptions.Item label="运行状态">
              <Tag color={autoPushStatus?.running ? 'green' : 'default'}>
                {autoPushStatus?.running ? '运行中' : '已停止'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="候选数量">
              {autoPushStatus?.pendingCount ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="成功">
              {autoPushStatus?.successCount ?? 0}
            </Descriptions.Item>
            <Descriptions.Item label="失败">
              {autoPushStatus?.failureCount ?? 0}
            </Descriptions.Item>
            <Descriptions.Item label="最后处理">
              {autoPushStatus?.lastPoolId ? `#${autoPushStatus.lastPoolId}` : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="最后检查">
              {formatDateTime(autoPushStatus?.lastScanAt)}
            </Descriptions.Item>
            <Descriptions.Item label="最后工作">
              {formatDateTime(autoPushStatus?.lastWorkAt)}
            </Descriptions.Item>
            <Descriptions.Item label="最后错误">
              <Typography.Text type={autoPushStatus?.lastError ? 'danger' : 'secondary'}>
                {autoPushStatus?.lastError || '-'}
              </Typography.Text>
            </Descriptions.Item>
          </Descriptions>

          <Card size="small" title="配置">
            <Space wrap>
              <Select
                value={autoPushTargetShopIds[0]}
                onChange={(value) => setAutoPushTargetShopIds(value ? [value] : [])}
                options={targetShopOptions}
                loading={loadingTargetShops}
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="选择推送店铺"
                style={{ minWidth: 280 }}
              />
              <InputNumber
                value={autoPushBatchSize}
                onChange={(value) => setAutoPushBatchSize(Number(value || 1))}
                min={1}
                max={20}
                precision={0}
                addonBefore="每轮"
                addonAfter="条"
                style={{ width: 170 }}
              />
              <InputNumber
                value={Math.round(autoPushPollMs / 1000)}
                onChange={(value) => setAutoPushPollMs(Math.max(Number(value || 60), 10) * 1000)}
                min={10}
                precision={0}
                addonBefore="间隔"
                addonAfter="秒"
                style={{ width: 190 }}
              />
              <Checkbox checked={autoPushForceCreate} onChange={(event) => setAutoPushForceCreate(event.target.checked)}>
                强制新建商品库记录
              </Checkbox>
              <Typography.Text type="secondary">
                只推送 AI 结论为 SUITABLE 的未推送数据，顺序与当前选品池列表后端排序一致。
              </Typography.Text>
            </Space>
          </Card>

          <Card size="small" title="推送记录" bodyStyle={{ padding: 0 }}>
            <Table<Alibaba1688SelectionPoolAutoPushLogVO>
              size="small"
              rowKey="id"
              columns={autoPushLogColumns}
              dataSource={autoPushLogs}
              loading={autoPushLoading}
              scroll={{ x: 1060 }}
              pagination={{
                current: autoPushLogPage,
                pageSize: 10,
                total: autoPushLogTotal,
                showSizeChanger: false,
              }}
              onChange={(pagination: TablePaginationConfig) => {
                void loadAutoPushLogs(pagination.current || 1);
              }}
            />
          </Card>
        </Space>
      </Modal>

      <Modal
        title="从详情数据批量导入"
        open={importOpen}
        onCancel={() => setImportOpen(false)}
        onOk={() => void handleImportFromDetail()}
        okText={importAllMatching ? `导入当前筛选 ${importTotal} 条` : `导入已选 ${importSelectedRowKeys.length} 条`}
        confirmLoading={importing}
        okButtonProps={{ disabled: importAllMatching ? importTotal === 0 : importSelectedRowKeys.length === 0 }}
        width={1120}
        destroyOnClose
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space wrap>
            <Input
              value={importFilters.keyword}
              onChange={(e) => updateImportFilter('keyword', e.target.value)}
              allowClear
              placeholder="搜索标题 / offerId / 公司 / 链接"
              style={{ width: 320 }}
              onPressEnter={() => {
                setImportPage(1);
                void loadImportRows(1, importPageSize, importFilters);
              }}
            />
            <Input
              value={importFilters.detailRecordId}
              onChange={(e) => updateImportFilter('detailRecordId', e.target.value)}
              allowClear
              placeholder="详情记录 ID"
              style={{ width: 160 }}
              onPressEnter={() => {
                setImportPage(1);
                void loadImportRows(1, importPageSize, importFilters);
              }}
            />
            <Button
              type="primary"
              loading={importListLoading}
              onClick={() => {
                setImportPage(1);
                void loadImportRows(1, importPageSize, importFilters);
              }}
            >
              查询
            </Button>
            <Button
              onClick={() => {
                setImportFilters(initialImportFilters);
                setImportAllMatching(false);
                setImportSelectedRowKeys([]);
                setImportPage(1);
                setImportPageSize(10);
                void loadImportRows(1, 10, initialImportFilters);
              }}
            >
              重置
            </Button>
            <Checkbox
              checked={importAllMatching}
              onChange={(e) => {
                const checked = e.target.checked;
                setImportAllMatching(checked);
                if (checked) {
                  setImportSelectedRowKeys([]);
                }
              }}
            >
              全部数据
            </Checkbox>
            <Typography.Text type="secondary">
              仅展示 `READY` 且未入池的详情数据。勾选“全部数据”后，会按当前筛选条件导入全部结果。
            </Typography.Text>
          </Space>
          <Table<Alibaba1688DetailRecordVO>
            rowKey="id"
            size="small"
            columns={importColumns}
            dataSource={importRows}
            loading={importListLoading}
            pagination={{
              current: importPage,
              pageSize: importPageSize,
              total: importTotal,
              showSizeChanger: true,
              showTotal: (count) => `共 ${count} 条`,
            }}
            rowSelection={{
              selectedRowKeys: importAllMatching ? [] : importSelectedRowKeys,
              preserveSelectedRowKeys: true,
              getCheckboxProps: () => ({ disabled: importAllMatching }),
              onChange: (selectedRowKeys) => {
                if (!importAllMatching) {
                  setImportSelectedRowKeys(selectedRowKeys);
                }
              },
            }}
            scroll={{ x: 980, y: 420 }}
            onChange={(pagination: TablePaginationConfig) => {
              const nextPage = pagination.current || 1;
              const nextPageSize = pagination.pageSize || 10;
              setImportPage(nextPage);
              setImportPageSize(nextPageSize);
              void loadImportRows(nextPage, nextPageSize, importFilters);
            }}
          />
          <Input.TextArea
            value={importNote}
            onChange={(e) => setImportNote(e.target.value)}
            rows={3}
            allowClear
            placeholder="备注（可选，导入选中的所有记录时统一写入）"
          />
        </Space>
      </Modal>

      <Modal
        title={detailRecord?.productName || '1688 选品池详情'}
        open={detailOpen}
        onCancel={() => {
          setDetailOpen(false);
          setDetailRecord(null);
        }}
        width={1260}
        footer={[
          <Button
            key="close"
            onClick={() => {
              setDetailOpen(false);
              setDetailRecord(null);
            }}
          >
            关闭
          </Button>,
          <Button
            key="temu"
            loading={reportingKey === `${detailRecord?.id}-TEMU_COMPETE`}
            onClick={() => {
              if (detailRecord) {
                void handleCreateReport(detailRecord.id, 'TEMU_COMPETE');
              }
            }}
          >
            生成竞品报告
          </Button>,
          <Button
            key="ai"
            type="primary"
            loading={reportingKey === `${detailRecord?.id}-AI_SELECTION`}
            onClick={() => {
              if (detailRecord) {
                void handleCreateReport(detailRecord.id, 'AI_SELECTION');
              }
            }}
          >
            生成AI报告
          </Button>,
        ]}
        destroyOnClose
      >
        {detailLoading || !detailRecord ? (
          <Typography.Text>加载中...</Typography.Text>
        ) : (
          <Tabs
            items={[
              {
                key: 'main',
                label: '主表',
                children: (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Descriptions bordered size="small" column={2}>
                      {detailMainItems.map((item) => (
                        <Descriptions.Item key={String(item.label)} label={item.label} span={item.span || 1}>
                          {item.value}
                        </Descriptions.Item>
                      ))}
                    </Descriptions>
                    <Card size="small" title="图片素材">
                      <Space direction="vertical" size={12} style={{ width: '100%' }}>
                        <div className="alibaba1688-selection-pool-cell">
                          <Typography.Text strong>{`轮播图（${displayCarouselImageUrls.length}张）`}</Typography.Text>
                          <Image.PreviewGroup>
                            <Space wrap>
                              {displayCarouselImageUrls.map((url, index) => (
                                <Image key={`${url}-${index}`} width={72} height={72} src={url} alt="carousel" />
                              ))}
                            </Space>
                          </Image.PreviewGroup>
                        </div>
                        <div className="alibaba1688-selection-pool-cell">
                          <Typography.Text strong>轮播图链接</Typography.Text>
                          {renderJsonBlock(JSON.stringify(displayCarouselImageUrls, null, 2))}
                        </div>
                        <div className="alibaba1688-selection-pool-cell">
                          <Typography.Text strong>{`详情图（${displayDetailImageUrls.length}张）`}</Typography.Text>
                          <Image.PreviewGroup>
                            <Space wrap>
                              {displayDetailImageUrls.map((url, index) => (
                                <Image key={`${url}-${index}`} width={72} height={72} src={url} alt="detail" />
                              ))}
                            </Space>
                          </Image.PreviewGroup>
                        </div>
                        <div className="alibaba1688-selection-pool-cell">
                          <Typography.Text strong>详情图链接</Typography.Text>
                          {renderJsonBlock(JSON.stringify(displayDetailImageUrls, null, 2))}
                        </div>
                      </Space>
                    </Card>
                    <Card size="small" title="结构化附加信息">
                      <Tabs
                        items={[
                          {
                            key: 'score',
                            label: '评分明细',
                            children: renderJsonBlock(detailRecord.scoreDetailJson),
                          },
                          {
                            key: 'steps',
                            label: '阶梯价',
                            children: renderJsonBlock(detailRecord.priceStepsSnapshotJson),
                          },
                          {
                            key: 'trend',
                            label: '销量曲线',
                            children: renderJsonBlock(detailRecord.salesTrendSnapshotJson),
                          },
                          {
                            key: 'assistant',
                            label: '插件补充',
                            children: renderJsonBlock(detailRecord.assistantExtraJson),
                          },
                        ]}
                      />
                    </Card>
                    <Card size="small" title="主表报告镜像">
                      <Tabs items={reportMirrorItems} />
                    </Card>
                  </Space>
                ),
              },
              {
                key: 'sku',
                label: `SKU（${detailSkuRows.length}）`,
                children: (
                  <Table<Alibaba1688SelectionPoolSkuVO>
                    rowKey="id"
                    columns={skuColumns}
                    dataSource={detailSkuRows}
                    pagination={false}
                    size="small"
                    tableLayout="fixed"
                    scroll={{ x: 1200 }}
                  />
                ),
              },
              {
                key: 'report',
                label: `报告（${detailReports.length}）`,
                children: (
                  <Table<Alibaba1688SelectionPoolReportVO>
                    rowKey="id"
                    columns={reportColumns}
                    dataSource={detailReports}
                    pagination={false}
                    size="small"
                    tableLayout="fixed"
                    scroll={{ x: 1180 }}
                  />
                ),
              },
            ]}
          />
        )}
      </Modal>

      <Modal
        title={reportPreview?.title || `报告 #${reportPreview?.id || ''}`}
        open={reportPreviewOpen}
        onCancel={() => {
          setReportPreviewOpen(false);
          setReportPreview(null);
        }}
        footer={null}
        width={980}
        destroyOnClose
      >
        {reportPreview ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="ID">{reportPreview.id}</Descriptions.Item>
              <Descriptions.Item label="状态">{statusTag(reportPreview.status)}</Descriptions.Item>
              <Descriptions.Item label="类型">{reportPreview.reportType || '-'}</Descriptions.Item>
              <Descriptions.Item label="分数">{reportPreview.score ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="版本">{reportPreview.versionNo ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="来源">{reportPreview.sourceType || '-'}</Descriptions.Item>
              <Descriptions.Item label="摘要" span={2}>
                {reportPreview.summary || '-'}
              </Descriptions.Item>
            </Descriptions>
            {reportPreview.contentJson ? renderJsonBlock(reportPreview.contentJson) : renderJsonBlock(reportPreview.dataJson || reportPreview.reportJson)}
          </Space>
        ) : null}
      </Modal>
    </Space>
  );
};

export default Alibaba1688SelectionPoolsPage;
