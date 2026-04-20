import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Empty,
  Modal,
  Progress,
  Radio,
  Row,
  Select,
  Skeleton,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useMemo, useRef, useState } from 'react';
import { syncApi } from '@/api/sync';
import { temuShopsApi } from '@/api/temuShops';
import type {
  SyncGoodsRepairFailedPageVO,
  SyncGoodsRepairJobVO,
  SyncTaskLogVO,
  SyncTaskProgressVO,
  SyncTaskVO,
  TemuShopVO,
} from '@/types/api';
import { formatDateTime } from '@/utils/format';
import { loadStoredShopFilter, saveStoredShopFilter } from '@/utils/shopFilter';
import './SyncTasksPage.css';

interface ShopOption {
  value: string;
  label: string;
  shopId: string;
  shopName: string;
}

const SHOP_FILTER_STORAGE_KEY = 'sync-tasks';

const syncTypeMap: Record<string, string> = {
  GOODS: '商品信息',
  LIFECYCLE: '生命周期',
  PRICE: '供货价格',
  FREIGHT: '运费模板',
  WAREHOUSE: '发货仓库',
  PRICE_REVIEW: '核价单',
  PRICE_ADJUST: '调价单',
  ACTIVITY: '营销活动',
};

const syncGroups = [
  {
    title: '商品数据',
    items: [
      { value: 'GOODS', label: '商品信息' },
      { value: 'LIFECYCLE', label: '生命周期' },
      { value: 'PRICE', label: '供货价格' },
    ],
  },
  {
    title: '物流配置',
    items: [
      { value: 'FREIGHT', label: '运费模板' },
      { value: 'WAREHOUSE', label: '发货仓库' },
    ],
  },
  {
    title: '价格与活动',
    items: [
      { value: 'PRICE_REVIEW', label: '核价单' },
      { value: 'PRICE_ADJUST', label: '调价单' },
      { value: 'ACTIVITY', label: '营销活动' },
    ],
  },
];

const allSyncTypes = Object.entries(syncTypeMap).map(([value, label]) => ({ value, label }));

const statusMap: Record<string, string> = {
  PENDING: '等待执行',
  DOWNLOADING: '下载中',
  DOWNLOADED: '下载完成',
  DOWNLOAD_FAILED: '下载失败',
  PERSISTING: '入库中',
  PERSIST_FAILED: '入库失败',
  SUCCEEDED: '已完成',
  SUCCESS: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
  RUNNING: '执行中',
};

const statusColorMap: Record<string, string> = {
  PENDING: 'default',
  DOWNLOADING: 'processing',
  DOWNLOADED: 'cyan',
  DOWNLOAD_FAILED: 'red',
  PERSISTING: 'processing',
  PERSIST_FAILED: 'red',
  SUCCEEDED: 'green',
  SUCCESS: 'green',
  FAILED: 'red',
  CANCELLED: 'orange',
  RUNNING: 'processing',
};

function typeLabel(value?: string | null) {
  return syncTypeMap[value || ''] || value || '-';
}

function statusLabel(value?: string | null) {
  return statusMap[value || ''] || value || '-';
}

function statusColor(value?: string | null) {
  return statusColorMap[value || ''] || 'default';
}

function phaseLabel(value?: string | null) {
  return (
    {
      DOWNLOAD: '下载',
      PERSIST: '入库',
      GENERAL: '通用',
    }[value || ''] || value || '-'
  );
}

function logLevelColor(value?: string | null) {
  return (
    {
      INFO: 'blue',
      WARN: 'orange',
      ERROR: 'red',
    }[value || ''] || 'default'
  );
}

function syncScopeLabel(scope?: string | null) {
  if (!scope) {
    return '-';
  }
  if (scope === 'LAST_WEEK') {
    return '最近一周';
  }
  if (scope === 'LAST_YEAR') {
    return '最近一年';
  }
  const matched = String(scope).match(/^LAST_(\d+)_DAYS$/);
  if (matched) {
    return `最近${matched[1]}天`;
  }
  return scope;
}

function repairStatusLabel(status?: string | null) {
  return (
    {
      PENDING: '等待执行',
      RUNNING: '执行中',
      SUCCEEDED: '已完成',
      FAILED: '部分失败',
      IDLE: '空闲',
    }[status || ''] || status || '-'
  );
}

function isRunning(status?: string | null) {
  return ['PENDING', 'DOWNLOADING', 'DOWNLOADED', 'PERSISTING', 'RUNNING'].includes(status || '');
}

function canRetry(status?: string | null) {
  return ['DOWNLOAD_FAILED', 'PERSIST_FAILED', 'FAILED'].includes(status || '');
}

function canCancel(status?: string | null) {
  return ['PENDING', 'DOWNLOADING', 'DOWNLOADED', 'PERSISTING', 'RUNNING'].includes(status || '');
}

function toMillis(value?: string | number[] | null) {
  if (!value) {
    return 0;
  }
  if (typeof value === 'string') {
    return new Date(value).getTime();
  }
  if (Array.isArray(value) && value.length >= 6) {
    return new Date(value[0], value[1] - 1, value[2], value[3], value[4], value[5]).getTime();
  }
  return 0;
}

function calcDuration(record: Pick<SyncTaskVO, 'startedAt' | 'finishedAt' | 'status'>) {
  if (!record.startedAt || !record.finishedAt) {
    return isRunning(record.status) ? '进行中' : '-';
  }
  const ms = toMillis(record.finishedAt) - toMillis(record.startedAt);
  if (ms < 1000) {
    return `${ms}ms`;
  }
  if (ms < 60000) {
    return `${(ms / 1000).toFixed(1)}秒`;
  }
  return `${Math.floor(ms / 60000)}分${Math.round((ms % 60000) / 1000)}秒`;
}

function calcPercent(record: {
  currentPhase?: string | null;
  downloadTotal?: number | null;
  downloadCompleted?: number | null;
  persistTotal?: number | null;
  persistCompleted?: number | null;
}) {
  if (record.currentPhase === 'PERSIST' && record.persistTotal) {
    return Math.min(100, Math.round(((record.persistCompleted || 0) / record.persistTotal) * 100));
  }
  if (record.downloadTotal) {
    return Math.min(100, Math.round(((record.downloadCompleted || 0) / record.downloadTotal) * 100));
  }
  return 0;
}

function calcSinglePercent(completed?: number | null, total?: number | null) {
  if (!total || total <= 0) {
    return 0;
  }
  return Math.min(100, Math.round(((completed || 0) / total) * 100));
}

function formatRunningPages(runningPages?: number[] | null) {
  if (!runningPages?.length) {
    return '-';
  }
  if (runningPages.length <= 8) {
    return runningPages.join('、');
  }
  return `${runningPages.slice(0, 8).join('、')} 等 ${runningPages.length} 页`;
}

function formatFailedPages(failedPages?: SyncGoodsRepairFailedPageVO[] | null) {
  if (!failedPages?.length) {
    return '-';
  }
  return failedPages
    .map((item) => `第 ${item.page ?? '-'} 页：${item.errorMessage || item.error || '-'}`)
    .join('；');
}

function mergeLogs(existingLogs: SyncTaskLogVO[] | null | undefined, latestLogs: SyncTaskLogVO[] | null | undefined) {
  const next = Array.isArray(existingLogs) ? [...existingLogs] : [];
  if (!Array.isArray(latestLogs) || !latestLogs.length) {
    return next;
  }
  const seen = new Set(next.map((item) => `${item.createdAt || ''}-${item.level || ''}-${item.message || ''}`));
  latestLogs.forEach((item) => {
    const key = `${item.createdAt || ''}-${item.level || ''}-${item.message || ''}`;
    if (!seen.has(key)) {
      seen.add(key);
      next.push(item);
    }
  });
  return next;
}

function applyProgressToDetail(detail: SyncTaskVO, progress: SyncTaskProgressVO): SyncTaskVO {
  return {
    ...detail,
    status: progress.status,
    currentPhase: progress.currentPhase,
    downloadTotal: progress.downloadTotal,
    downloadCompleted: progress.downloadCompleted,
    persistTotal: progress.persistTotal,
    persistCompleted: progress.persistCompleted,
    totalBatches: progress.totalBatches,
    persistedBatches: progress.persistedBatches,
    logs: mergeLogs(detail.logs, progress.latestLogs),
  };
}

const SyncTasksPage = () => {
  const { message } = App.useApp();
  const taskPollTimerRef = useRef<number | null>(null);
  const repairPollTimerRef = useRef<number | null>(null);

  const [shopsLoading, setShopsLoading] = useState(false);
  const [shops, setShops] = useState<ShopOption[]>([]);
  const [shopId, setShopId] = useState<string | undefined>(() => loadStoredShopFilter(SHOP_FILTER_STORAGE_KEY));

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<SyncTaskVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [syncType, setSyncType] = useState<string>();

  const [creating, setCreating] = useState(false);
  const [selectedTypes, setSelectedTypes] = useState<string[]>([]);
  const [goodsSyncMode, setGoodsSyncMode] = useState('LAST_WEEK');
  const [priceAdjustSyncMode, setPriceAdjustSyncMode] = useState('LAST_WEEK');

  const [repairingDetails, setRepairingDetails] = useState(false);
  const [repairResult, setRepairResult] = useState<SyncGoodsRepairJobVO | null>(null);

  const [detailVisible, setDetailVisible] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailData, setDetailData] = useState<SyncTaskVO | null>(null);

  const repairProgressPercent = useMemo(() => {
    const totalPages = repairResult?.totalPages || 0;
    const completedPages = repairResult?.completedPages || 0;
    if (totalPages <= 0) {
      return 0;
    }
    return Math.min(100, Math.round((completedPages / totalPages) * 100));
  }, [repairResult?.completedPages, repairResult?.totalPages]);

  const repairProgressText = useMemo(() => {
    if (!repairResult) {
      return '商品明细回填执行中';
    }
    return `${repairResult.message || '商品明细回填执行中'}（${repairResult.completedPages ?? 0}/${repairResult.totalPages ?? 0} 页）`;
  }, [repairResult]);

  const detailTitle = useMemo(() => {
    if (!detailData) {
      return '任务详情';
    }
    return `同步任务 #${detailData.id} — ${typeLabel(detailData.syncType)}`;
  }, [detailData]);

  function stopTaskPolling() {
    if (taskPollTimerRef.current) {
      window.clearInterval(taskPollTimerRef.current);
      taskPollTimerRef.current = null;
    }
  }

  function stopRepairPolling() {
    if (repairPollTimerRef.current) {
      window.clearInterval(repairPollTimerRef.current);
      repairPollTimerRef.current = null;
    }
  }

  async function fetchList(
    nextPage = page,
    nextPageSize = pageSize,
    nextShopId = shopId,
    nextSyncType = syncType,
  ) {
    if (!nextShopId) {
      setRows([]);
      setTotal(0);
      return;
    }

    setLoading(true);
    try {
      const res = await syncApi.listSyncTasks({
        shopId: nextShopId,
        syncType: nextSyncType || undefined,
        page: nextPage,
        pageSize: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载任务失败');
    } finally {
      setLoading(false);
    }
  }

  async function loadLatestRepairStatus(nextShopId = shopId) {
    if (!nextShopId) {
      setRepairResult(null);
      setRepairingDetails(false);
      stopRepairPolling();
      return;
    }

    try {
      const res = await syncApi.getLatestRepairGoodsDetailsStatus(nextShopId);
      const data = res.data;
      if (!data || data.status === 'IDLE') {
        setRepairResult(null);
        setRepairingDetails(false);
        stopRepairPolling();
        return;
      }

      setRepairResult(data);
      if (data.status === 'PENDING' || data.status === 'RUNNING') {
        setRepairingDetails(true);
        startRepairPolling(data.jobId || '');
      } else {
        setRepairingDetails(false);
        stopRepairPolling();
      }
    } catch {
      // 恢复回填状态失败不阻塞列表
    }
  }

  async function loadShops() {
    setShopsLoading(true);
    try {
      const res = await temuShopsApi.list({ enabled: true });
      const data = Array.isArray(res.data) ? res.data : [];
      const options = data
        .filter((item: TemuShopVO) => item.shopId && item.shopName)
        .map((item: TemuShopVO) => ({
          value: item.shopId,
          label: `${item.shopName}（${item.shopId}）`,
          shopId: item.shopId,
          shopName: item.shopName,
        }));
      setShops(options);

      const firstShopId = options[0]?.value;
      if (!firstShopId) {
        setShopId(undefined);
        saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, undefined);
        setRows([]);
        setTotal(0);
        setRepairResult(null);
        return;
      }

      const nextShopId = shopId && options.some((item) => item.value === shopId) ? shopId : firstShopId;
      setShopId(nextShopId);
      saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, nextShopId);
      await Promise.all([fetchList(1, pageSize, nextShopId, syncType), loadLatestRepairStatus(nextShopId)]);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载店铺失败');
    } finally {
      setShopsLoading(false);
    }
  }

  useEffect(() => {
    void loadShops();
    return () => {
      stopTaskPolling();
      stopRepairPolling();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function reload(nextShopId = shopId, nextSyncType = syncType) {
    setPage(1);
    await Promise.all([fetchList(1, pageSize, nextShopId, nextSyncType), loadLatestRepairStatus(nextShopId)]);
  }

  function startRepairPolling(nextJobId: string) {
    stopRepairPolling();
    if (!nextJobId) {
      return;
    }

    repairPollTimerRef.current = window.setInterval(async () => {
      try {
        const res = await syncApi.getRepairGoodsDetailsStatus(nextJobId);
        if (!res.data) {
          return;
        }
        setRepairResult(res.data);
        if (res.data.status !== 'PENDING' && res.data.status !== 'RUNNING') {
          stopRepairPolling();
          setRepairingDetails(false);
          const failedCount = res.data.failedPages?.length || 0;
          if (res.data.status === 'FAILED') {
            message.warning(`商品明细回填结束，但有 ${failedCount} 个分页失败`);
          } else {
            message.success('商品明细回填完成');
          }
        }
      } catch {
        // 轮询失败等待下次
      }
    }, 2000);
  }

  async function createTasks() {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }
    if (!selectedTypes.length) {
      message.error('请至少选择一种同步类型');
      return;
    }

    setCreating(true);
    try {
      await syncApi.createSyncTasks({
        shopId,
        syncTypes: selectedTypes,
        goodsSyncMode: selectedTypes.includes('GOODS') ? goodsSyncMode : undefined,
        priceAdjustSyncMode: selectedTypes.includes('PRICE_ADJUST') ? priceAdjustSyncMode : undefined,
      });
      message.success('同步任务已创建');
      setSelectedTypes([]);
      setGoodsSyncMode('LAST_WEEK');
      setPriceAdjustSyncMode('LAST_WEEK');
      await reload(shopId, syncType);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建失败');
    } finally {
      setCreating(false);
    }
  }

  function repairGoodsDetails() {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }

    Modal.confirm({
      title: '确认回填商品明细',
      content: '回填会读取当前店铺已同步商品的 raw_json，补齐 SKU、规格、条码、站点和属性明细。执行期间请勿重复点击。',
      okText: '开始回填',
      cancelText: '取消',
      async onOk() {
        setRepairingDetails(true);
        setRepairResult(null);
        try {
          const res = await syncApi.repairGoodsDetails(shopId);
          setRepairResult(res.data || null);
          if (res.data?.status === 'SUCCEEDED') {
            setRepairingDetails(false);
            message.success('商品明细回填完成');
          } else {
            message.success('商品明细回填已启动');
            if (res.data?.jobId) {
              startRepairPolling(res.data.jobId);
            }
          }
        } catch (error) {
          setRepairingDetails(false);
          message.error(error instanceof Error ? error.message : '回填失败');
        }
      },
    });
  }

  function startTaskPolling(taskId: number) {
    stopTaskPolling();
    taskPollTimerRef.current = window.setInterval(async () => {
      try {
        const res = await syncApi.getTaskProgress(taskId);
        const progress = res.data;
        setDetailData((current) => {
          if (!current || current.id !== taskId) {
            return current;
          }
          return applyProgressToDetail(current, progress);
        });

        if (!isRunning(progress.status)) {
          stopTaskPolling();
          await fetchList();
          const fullDetail = await syncApi.getTaskDetail(taskId);
          setDetailData(fullDetail.data);
        }
      } catch {
        // 轮询异常忽略
      }
    }, 3000);
  }

  async function openDetail(record: SyncTaskVO) {
    setDetailVisible(true);
    setDetailLoading(true);
    stopTaskPolling();
    try {
      const res = await syncApi.getTaskDetail(record.id);
      setDetailData(res.data);
      if (isRunning(res.data.status)) {
        startTaskPolling(record.id);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  async function retryTask(taskId: number, mode: 'CONTINUE' | 'FULL') {
    try {
      const res = await syncApi.retryTask(taskId, mode);
      message.success(mode === 'FULL' ? '全量重跑已启动' : '重试已启动');
      await fetchList();
      if (detailData?.id === taskId) {
        setDetailData(res.data);
        if (isRunning(res.data.status)) {
          startTaskPolling(taskId);
        }
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重试失败');
    }
  }

  function cancelTask(taskId: number) {
    Modal.confirm({
      title: '确认取消任务',
      content: '取消后任务将终止。确定继续？',
      okText: '取消任务',
      cancelText: '返回',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await syncApi.cancelTask(taskId);
          message.success('任务已取消');
          await fetchList();
          if (detailData?.id === taskId) {
            setDetailData((current) => (current ? { ...current, status: 'CANCELLED' } : current));
            stopTaskPolling();
          }
        } catch (error) {
          message.error(error instanceof Error ? error.message : '取消失败');
          throw error;
        }
      },
    });
  }

  function clearShopData() {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }

    Modal.confirm({
      title: '确认清空当前店铺同步数据',
      content: '会删除当前店铺同步链路相关本地数据，请确认当前没有任务执行中。',
      okText: '确认清空',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await syncApi.clearShopSyncData(shopId);
          message.success('同步数据已清空');
          await reload(shopId, syncType);
        } catch (error) {
          message.error(error instanceof Error ? error.message : '清空失败');
          throw error;
        }
      },
    });
  }

  const columns: ColumnsType<SyncTaskVO> = [
    {
      title: '同步类型',
      key: 'syncType',
      width: 140,
      render: (_, record) => (
        <Space direction="vertical" size={2} className="sync-task-cell-stack">
          <Typography.Text>{typeLabel(record.syncType)}</Typography.Text>
          {record.syncScope ? (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {syncScopeLabel(record.syncScope)}
            </Typography.Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 120,
      render: (_, record) => <Tag color={statusColor(record.status)}>{statusLabel(record.status)}</Tag>,
    },
    {
      title: '进度',
      key: 'progress',
      width: 240,
      render: (_, record) =>
        isRunning(record.status) ? (
          <div className="sync-task-progress-cell">
            <Progress
              percent={calcPercent(record)}
              size="small"
              status={record.status === 'DOWNLOADING' ? 'active' : 'normal'}
            />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              下载 {record.downloadCompleted ?? 0}/{record.downloadTotal ?? 0}
              {record.persistTotal ? `，入库 ${record.persistCompleted ?? 0}/${record.persistTotal}` : ''}
            </Typography.Text>
          </div>
        ) : record.downloadTotal ? (
          <Space direction="vertical" size={2} className="sync-task-progress-cell">
            <Typography.Text>
              下载 {record.downloadCompleted ?? 0}/{record.downloadTotal ?? 0}
              {record.persistTotal ? `，入库 ${record.persistCompleted ?? 0}/${record.persistTotal}` : ''}
            </Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              批次 {record.persistedBatches ?? 0}/{record.totalBatches ?? 0}，重试 {record.retryCount ?? 0}
            </Typography.Text>
          </Space>
        ) : (
          <Typography.Text type="secondary">-</Typography.Text>
        ),
    },
    {
      title: '错误信息',
      key: 'error',
      render: (_, record) =>
        record.lastErrorMsg ? (
          <Tooltip title={record.lastErrorMsg}>
            <Tag color="red" className="sync-task-error-tag">
              {record.lastErrorMsg}
            </Tag>
          </Tooltip>
        ) : (
          <Typography.Text type="secondary">-</Typography.Text>
        ),
    },
    {
      title: '时间',
      key: 'time',
      width: 170,
      render: (_, record) => (
        <Space direction="vertical" size={2} className="sync-task-cell-stack">
          <Typography.Text className="sync-task-time">
            {formatDateTime(record.startedAt || record.createdAt)}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            耗时 {calcDuration(record)}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 170,
      fixed: 'right',
      render: (_, record) => (
        <Space wrap size={6}>
          <Button size="small" type="link" onClick={() => void openDetail(record)}>
            详情
          </Button>
          {canRetry(record.status) ? (
            <Button size="small" type="link" onClick={() => void retryTask(record.id, 'CONTINUE')}>
              重试
            </Button>
          ) : null}
          {canCancel(record.status) ? (
            <Button size="small" type="link" danger onClick={() => cancelTask(record.id)}>
              取消
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  if (!shopsLoading && !shops.length) {
    return (
      <Card>
        <Empty description="暂无可用店铺，请先配置店铺" />
      </Card>
    );
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }} className="sync-tasks-page">
      <Card bodyStyle={{ paddingBottom: 0 }} className="sync-tasks-section-card sync-tasks-shop-card">
        <Tabs
          activeKey={shopId}
          onChange={(value) => {
            setShopId(value);
            saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, value);
            setPage(1);
            void Promise.all([fetchList(1, pageSize, value, syncType), loadLatestRepairStatus(value)]);
          }}
          items={shops.map((shop) => ({
            key: shop.value,
            label: (
              <div className="sync-shop-tab-label">
                <span className="sync-shop-tab-name">{shop.shopName}</span>
                <span className="sync-shop-tab-id">{shop.shopId}</span>
              </div>
            ),
            children: null,
          }))}
        />
      </Card>

      {shopId ? (
        <>
          <Card
            className="sync-tasks-section-card"
            title="发起数据同步"
            extra={
              <Button type="primary" loading={creating} disabled={selectedTypes.length === 0} onClick={() => void createTasks()}>
                开始同步（{selectedTypes.length}项）
              </Button>
            }
          >
            <Checkbox.Group value={selectedTypes} onChange={(value) => setSelectedTypes(value as string[])}>
              <Row gutter={[16, 12]}>
                {syncGroups.map((group) => (
                  <Col xs={24} md={12} xl={8} key={group.title}>
                    <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                      {group.title}
                    </Typography.Text>
                    <Space direction="vertical" size={6}>
                      {group.items.map((item) => (
                        <Checkbox key={item.value} value={item.value}>
                          {item.label}
                        </Checkbox>
                      ))}
                    </Space>
                  </Col>
                ))}
              </Row>
            </Checkbox.Group>

            {selectedTypes.includes('GOODS') ? (
              <div className="sync-scope-panel">
                <Space direction="vertical" size={10}>
                  <Typography.Text strong>商品同步方案</Typography.Text>
                  <Radio.Group value={goodsSyncMode} onChange={(event) => setGoodsSyncMode(event.target.value)}>
                    <Radio.Button value="LAST_WEEK">最近一周</Radio.Button>
                    <Radio.Button value="LAST_YEAR">最近一年</Radio.Button>
                  </Radio.Group>
                  <Typography.Text type="secondary">
                    最近一周用于增量更新；最近一年仅用于手动初始化，后台会按周拆分并在每周内继续分页拉取。
                  </Typography.Text>
                </Space>
              </div>
            ) : null}

            {selectedTypes.includes('PRICE_ADJUST') ? (
              <div className="sync-scope-panel">
                <Space direction="vertical" size={10}>
                  <Typography.Text strong>调价单同步方案</Typography.Text>
                  <Radio.Group value={priceAdjustSyncMode} onChange={(event) => setPriceAdjustSyncMode(event.target.value)}>
                    <Radio.Button value="LAST_WEEK">最近一周</Radio.Button>
                    <Radio.Button value="LAST_YEAR">最近一年</Radio.Button>
                  </Radio.Group>
                  <Typography.Text type="secondary">
                    手动同步支持最近一周和最近一年；最近一年会按周切分时间窗口分页拉取。自动同步固定读取配置中的调价单自动同步范围，默认最近7天。
                  </Typography.Text>
                </Space>
              </div>
            ) : null}

            <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
              提示：供货价同步依赖商品信息数据，系统会自动按顺序执行。
            </Typography.Text>
          </Card>

          <Card
            className="sync-tasks-section-card"
            title="商品明细回填"
            extra={
              <Button type="primary" ghost loading={repairingDetails} onClick={repairGoodsDetails}>
                回填商品明细
              </Button>
            }
          >
            <Alert
              type="info"
              showIcon
              message="适用于已同步完商品主表但缺失 SKU / 规格 / 条码 / 站点 / 属性明细的场景"
              description="回填会直接读取本地 raw_json，不会再次调用 TEMU 接口。当前后端按页并发执行，每页独立事务提交；执行期间请勿重复点击。"
            />

            {repairingDetails ? (
              <div style={{ marginTop: 16 }}>
                <Progress percent={repairProgressPercent} status="active" />
                <Typography.Text type="secondary" style={{ marginTop: 8, display: 'block' }}>
                  {repairProgressText}
                </Typography.Text>
              </div>
            ) : null}

            {repairResult ? (
              <div className="sync-repair-result">
                <Descriptions column={3} size="small" bordered>
                  <Descriptions.Item label="任务ID">{repairResult.jobId || '-'}</Descriptions.Item>
                  <Descriptions.Item label="当前状态">{repairStatusLabel(repairResult.status)}</Descriptions.Item>
                  <Descriptions.Item label="店铺ID">{repairResult.shopId || '-'}</Descriptions.Item>
                  <Descriptions.Item label="分页大小">{repairResult.pageSize ?? '-'}</Descriptions.Item>
                  <Descriptions.Item label="并发度">{repairResult.concurrency ?? '-'}</Descriptions.Item>
                  <Descriptions.Item label="总页数">{repairResult.totalPages ?? '-'}</Descriptions.Item>
                  <Descriptions.Item label="已提交分页">{repairResult.submittedPages ?? 0}</Descriptions.Item>
                  <Descriptions.Item label="完成分页">{repairResult.completedPages ?? 0}</Descriptions.Item>
                  <Descriptions.Item label="运行中分页">{formatRunningPages(repairResult.runningPages)}</Descriptions.Item>
                  <Descriptions.Item label="扫描商品">{repairResult.scanned ?? 0}</Descriptions.Item>
                  <Descriptions.Item label="成功回填">{repairResult.repaired ?? 0}</Descriptions.Item>
                  <Descriptions.Item label="跳过数量">{repairResult.skipped ?? 0}</Descriptions.Item>
                  <Descriptions.Item label="失败页数">{repairResult.failedPages?.length ?? 0}</Descriptions.Item>
                  <Descriptions.Item label="最近进展" span={2}>
                    {formatDateTime(repairResult.lastProgressAt)}
                  </Descriptions.Item>
                </Descriptions>

                {repairResult.message ? (
                  <Alert style={{ marginTop: 16 }} type="info" showIcon message={repairResult.message} />
                ) : null}

                {repairResult.failedPages?.length ? (
                  <Alert
                    style={{ marginTop: 16 }}
                    type="warning"
                    showIcon
                    message={`有 ${repairResult.failedPages.length} 个分页回填失败`}
                    description={formatFailedPages(repairResult.failedPages)}
                  />
                ) : (
                  <Typography.Text style={{ marginTop: 16, display: 'block' }} type="success">
                    本次回填未发现失败分页，可以直接继续执行供货价同步。
                  </Typography.Text>
                )}
              </div>
            ) : null}
          </Card>

          <Card
            className="sync-tasks-section-card"
            title="同步任务"
            extra={
              <Space wrap>
                <Select
                  value={syncType}
                  allowClear
                  placeholder="全部类型"
                  style={{ width: 160 }}
                  options={allSyncTypes}
                  onChange={(value) => {
                    setSyncType(value);
                    void reload(shopId, value);
                  }}
                />
                <Button
                  onClick={() => {
                    void reload(shopId, syncType);
                  }}
                >
                  刷新
                </Button>
                <Button danger onClick={clearShopData}>
                  清空店铺同步数据
                </Button>
              </Space>
            }
          >
            <Table<SyncTaskVO>
              rowKey="id"
              columns={columns}
              dataSource={rows}
              loading={loading}
              size="middle"
              scroll={{ x: 1100 }}
              tableLayout="fixed"
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
                void fetchList(nextPage, nextPageSize, shopId, syncType);
              }}
            />
          </Card>
        </>
      ) : null}

      <Modal
        open={detailVisible}
        title={detailTitle}
        width={860}
        footer={null}
        onCancel={() => {
          stopTaskPolling();
          setDetailVisible(false);
        }}
      >
        {detailLoading ? (
          <Skeleton active paragraph={{ rows: 10 }} />
        ) : detailData ? (
          <div className="sync-task-detail">
            <div className="sync-task-detail-headnote">
              <Space wrap size={[8, 8]}>
                <Tag color={statusColor(detailData.status)}>{statusLabel(detailData.status)}</Tag>
                <Typography.Text type="secondary">当前阶段：{phaseLabel(detailData.currentPhase)}</Typography.Text>
                <Typography.Text type="secondary">
                  下载 {detailData.downloadCompleted ?? 0}/{detailData.downloadTotal ?? 0}
                </Typography.Text>
                <Typography.Text type="secondary">
                  入库 {detailData.persistCompleted ?? 0}/{detailData.persistTotal ?? 0}
                </Typography.Text>
                <Typography.Text type="secondary">耗时：{calcDuration(detailData)}</Typography.Text>
              </Space>
            </div>

            <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} size="small" bordered>
              <Descriptions.Item label="任务ID">#{detailData.id}</Descriptions.Item>
              <Descriptions.Item label="店铺ID">{detailData.shopId || '-'}</Descriptions.Item>
              <Descriptions.Item label="同步类型">{typeLabel(detailData.syncType)}</Descriptions.Item>
              {detailData.syncScope ? (
                <Descriptions.Item label="同步范围">{syncScopeLabel(detailData.syncScope)}</Descriptions.Item>
              ) : null}
              <Descriptions.Item label="触发方式">{detailData.triggerType === 'MANUAL' ? '手动' : '定时'}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusColor(detailData.status)}>{statusLabel(detailData.status)}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatDateTime(detailData.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="开始时间">{formatDateTime(detailData.startedAt)}</Descriptions.Item>
              <Descriptions.Item label="结束时间">{formatDateTime(detailData.finishedAt)}</Descriptions.Item>
              <Descriptions.Item label="重试次数">{detailData.retryCount || 0}</Descriptions.Item>
            </Descriptions>

            <Row gutter={[16, 16]}>
              <Col xs={24} md={12}>
                <div className={`sync-task-progress-panel${detailData.currentPhase === 'DOWNLOAD' ? ' is-active' : ''}`}>
                  <div className="sync-task-progress-top">
                    <Typography.Text strong>下载进度</Typography.Text>
                    <Tag color={detailData.currentPhase === 'DOWNLOAD' && isRunning(detailData.status) ? 'processing' : 'default'}>
                      {detailData.currentPhase === 'DOWNLOAD' && isRunning(detailData.status) ? '执行中' : '已记录'}
                    </Tag>
                  </div>
                  <div className="sync-task-progress-metric">
                    <span className="sync-task-progress-count">{detailData.downloadCompleted ?? 0}</span>
                    <span className="sync-task-progress-total">/ {detailData.downloadTotal ?? 0} 条</span>
                  </div>
                  <Progress
                    percent={calcSinglePercent(detailData.downloadCompleted, detailData.downloadTotal)}
                    showInfo={false}
                    status={detailData.currentPhase === 'DOWNLOAD' && isRunning(detailData.status) ? 'active' : 'normal'}
                  />
                  <div className="sync-task-progress-meta">
                    <span>失败 {detailData.downloadFailed ?? 0}</span>
                    <span>总量 {detailData.downloadTotal ?? 0}</span>
                  </div>
                </div>
              </Col>

              <Col xs={24} md={12}>
                <div className={`sync-task-progress-panel${detailData.currentPhase === 'PERSIST' ? ' is-active' : ''}`}>
                  <div className="sync-task-progress-top">
                    <Typography.Text strong>入库进度</Typography.Text>
                    <Tag color={detailData.currentPhase === 'PERSIST' && isRunning(detailData.status) ? 'processing' : 'default'}>
                      {detailData.currentPhase === 'PERSIST' && isRunning(detailData.status) ? '执行中' : '已记录'}
                    </Tag>
                  </div>
                  <div className="sync-task-progress-metric">
                    <span className="sync-task-progress-count">{detailData.persistCompleted ?? 0}</span>
                    <span className="sync-task-progress-total">/ {detailData.persistTotal ?? 0} 条</span>
                  </div>
                  <Progress
                    percent={calcSinglePercent(detailData.persistCompleted, detailData.persistTotal)}
                    showInfo={false}
                    status={detailData.currentPhase === 'PERSIST' && isRunning(detailData.status) ? 'active' : 'normal'}
                  />
                  <div className="sync-task-progress-meta">
                    <span>失败 {detailData.persistFailed ?? 0}</span>
                    <span>
                      批次 {detailData.persistedBatches ?? 0}/{detailData.totalBatches ?? 0}
                    </span>
                  </div>
                </div>
              </Col>
            </Row>

            {detailData.lastErrorMsg ? <Alert type="error" showIcon message={detailData.lastErrorMsg} /> : null}

            {canRetry(detailData.status) || canCancel(detailData.status) ? (
              <div className="sync-task-detail-actions">
                <Space wrap>
                  {canRetry(detailData.status) ? (
                    <Button type="primary" onClick={() => void retryTask(detailData.id, 'CONTINUE')}>
                      {detailData.status === 'PERSIST_FAILED' ? '继续入库' : '重新下载'}
                    </Button>
                  ) : null}
                  {canRetry(detailData.status) ? (
                    <Button onClick={() => void retryTask(detailData.id, 'FULL')}>全量重跑</Button>
                  ) : null}
                  {canCancel(detailData.status) ? (
                    <Button danger onClick={() => cancelTask(detailData.id)}>
                      取消任务
                    </Button>
                  ) : null}
                </Space>
              </div>
            ) : null}

            <div className="sync-task-log-panel">
              <div className="sync-task-log-head">
                <Typography.Text strong>执行日志</Typography.Text>
                <Typography.Text type="secondary">{detailData.logs?.length ?? 0} 条</Typography.Text>
              </div>
              <div className="sync-task-log-body">
                {detailData.logs?.length ? (
                  <>
                    {detailData.logs.map((log, index) => (
                      <div key={`${log.createdAt || 'log'}-${index}`} className="sync-task-log-line">
                        <Typography.Text type="secondary" className="sync-task-log-time">
                          {formatDateTime(log.createdAt)}
                        </Typography.Text>
                        <div>
                          <Tag color={logLevelColor(log.level)}>{log.level || '-'}</Tag>
                        </div>
                        <Typography.Text type="secondary" className="sync-task-log-phase">
                          [{phaseLabel(log.phase)}]
                        </Typography.Text>
                        <Typography.Text className="sync-task-log-message">{log.message || '-'}</Typography.Text>
                      </div>
                    ))}
                  </>
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无日志" />
                )}
              </div>
            </div>
          </div>
        ) : null}
      </Modal>
    </Space>
  );
};

export default SyncTasksPage;
