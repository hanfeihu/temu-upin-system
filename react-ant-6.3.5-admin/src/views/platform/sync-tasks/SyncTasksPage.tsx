import { App, Button, Card, Drawer, Form, Select, Space, Table, Tag, Typography, Modal, Checkbox } from 'antd';
import type { CheckboxOptionType } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { syncApi } from '@/api/sync';
import { temuShopsApi } from '@/api/temuShops';
import type { SyncTaskProgressVO, SyncTaskVO, TemuShopVO } from '@/types/api';
import { formatDateTime } from '@/utils/format';

const syncTypeOptions: CheckboxOptionType<string>[] = [
  { label: '商品', value: 'GOODS' },
  { label: '核价单', value: 'PRICE_REVIEW' },
  { label: '调价单', value: 'PRICE_ADJUST' },
  { label: '活动', value: 'ACTIVITY' },
];

const SyncTasksPage = () => {
  const { message } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: string; label: string }>>([]);
  const [shopId, setShopId] = useState<string>();
  const [syncType, setSyncType] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<SyncTaskVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [selectedSyncTypes, setSelectedSyncTypes] = useState<string[]>(['GOODS']);
  const [goodsSyncMode, setGoodsSyncMode] = useState('ALL');
  const [priceAdjustSyncMode, setPriceAdjustSyncMode] = useState('INCREMENTAL');
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<SyncTaskVO | null>(null);
  const [progress, setProgress] = useState<SyncTaskProgressVO | null>(null);

  async function loadShops() {
    const res = await temuShopsApi.list({ enabled: true });
    const data = Array.isArray(res.data) ? res.data : [];
    const options = data
      .filter((item: TemuShopVO) => item.shopId && item.shopName)
      .map((item: TemuShopVO) => ({ value: item.shopId, label: item.shopName }));
    setShops(options);
    if (!shopId && options[0]) {
      setShopId(options[0].value);
      await load(1, 20, options[0].value);
    }
  }

  async function load(nextPage = page, nextPageSize = pageSize, nextShopId = shopId, nextSyncType = syncType) {
    if (!nextShopId) {
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

  useEffect(() => {
    void loadShops();
  }, []);

  async function openDetail(record: SyncTaskVO) {
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const [detailRes, progressRes] = await Promise.all([
        syncApi.getTaskDetail(record.id),
        syncApi.getTaskProgress(record.id),
      ]);
      setDetail(detailRes.data);
      setProgress(progressRes.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  async function createTasks() {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }
    if (!selectedSyncTypes.length) {
      message.error('请至少选择一种同步类型');
      return;
    }
    setCreating(true);
    try {
      await syncApi.createSyncTasks({
        shopId,
        syncTypes: selectedSyncTypes,
        goodsSyncMode,
        priceAdjustSyncMode,
      });
      message.success('同步任务已创建');
      setCreateOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建失败');
    } finally {
      setCreating(false);
    }
  }

  async function retryTask(record: SyncTaskVO, mode: 'CONTINUE' | 'FULL') {
    try {
      await syncApi.retryTask(record.id, mode);
      message.success('已发起重试');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重试失败');
    }
  }

  function cancelTask(record: SyncTaskVO) {
    Modal.confirm({
      title: '取消任务？',
      content: `确认取消同步任务 #${record.id} 吗？`,
      okText: '取消任务',
      cancelText: '返回',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await syncApi.cancelTask(record.id);
          message.success('任务已取消');
          await load();
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
      title: '清空同步数据？',
      content: '会清理该店铺同步链路相关本地数据，请确认当前没有任务执行中。',
      okText: '确认清空',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await syncApi.clearShopSyncData(shopId);
          message.success('同步数据已清空');
          await load();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '清空失败');
          throw error;
        }
      },
    });
  }

  const columns: ColumnsType<SyncTaskVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 90 },
    {
      title: '任务类型',
      key: 'syncType',
      width: 160,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <span>{record.syncType}</span>
          <Typography.Text type="secondary">{record.triggerType || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '执行状态',
      key: 'status',
      width: 160,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Tag color={record.status === 'SUCCESS' ? 'green' : record.status === 'FAILED' ? 'red' : 'processing'}>{record.status || '-'}</Tag>
          <Typography.Text type="secondary">{record.currentPhase || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '进度',
      key: 'progress',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <span>
            下载 {record.downloadCompleted ?? 0}/{record.downloadTotal ?? 0}，落库 {record.persistCompleted ?? 0}/{record.persistTotal ?? 0}
          </span>
          <Typography.Text type="secondary">
            批次 {record.persistedBatches ?? 0}/{record.totalBatches ?? 0}，重试 {record.retryCount ?? 0}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '异常',
      key: 'lastErrorMsg',
      render: (_, record) => (
        <Typography.Text ellipsis style={{ maxWidth: 240 }}>
          {record.lastErrorMsg || '-'}
        </Typography.Text>
      ),
    },
    {
      title: '时间',
      key: 'time',
      width: 200,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <span>开始: {formatDateTime(record.startedAt)}</span>
          <span>结束: {formatDateTime(record.finishedAt)}</span>
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      render: (_, record) => (
        <Space wrap>
          <Button size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          <Button size="small" onClick={() => void retryTask(record, 'CONTINUE')}>
            续跑
          </Button>
          <Button size="small" onClick={() => void retryTask(record, 'FULL')}>
            全量重跑
          </Button>
          <Button size="small" danger onClick={() => cancelTask(record)}>
            取消
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Select
            value={shopId}
            onChange={(value) => {
              setShopId(value);
              setPage(1);
              void load(1, pageSize, value, syncType);
            }}
            placeholder="店铺"
            style={{ width: 220 }}
            options={shops}
          />
          <Select
            value={syncType}
            onChange={(value) => setSyncType(value)}
            allowClear
            placeholder="同步类型"
            style={{ width: 180 }}
            options={syncTypeOptions.map((item) => ({ value: String(item.value), label: item.label }))}
          />
          <Button type="primary" loading={loading} onClick={() => {
            setPage(1);
            void load(1, pageSize, shopId, syncType);
          }}>
            查询
          </Button>
          <Button onClick={() => {
            setSyncType(undefined);
            setPage(1);
            void load(1, pageSize, shopId, undefined);
          }}>
            重置
          </Button>
          <Button type="primary" ghost onClick={() => setCreateOpen(true)}>
            新建任务
          </Button>
          <Button danger onClick={clearShopData}>
            清空店铺同步数据
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<SyncTaskVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1500 }}
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
            void load(nextPage, nextPageSize, shopId, syncType);
          }}
        />
      </Card>

      <Modal
        open={createOpen}
        title="新建同步任务"
        confirmLoading={creating}
        onOk={() => void createTasks()}
        onCancel={() => setCreateOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="同步类型" required>
            <Checkbox.Group value={selectedSyncTypes} options={syncTypeOptions} onChange={(value) => setSelectedSyncTypes(value as string[])} />
          </Form.Item>
          <Form.Item label="商品同步模式">
            <Select value={goodsSyncMode} onChange={setGoodsSyncMode} options={[{ value: 'ALL', label: '全量' }, { value: 'INCREMENTAL', label: '增量' }]} />
          </Form.Item>
          <Form.Item label="调价同步模式">
            <Select value={priceAdjustSyncMode} onChange={setPriceAdjustSyncMode} options={[{ value: 'INCREMENTAL', label: '增量' }, { value: 'FULL', label: '全量' }]} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer open={detailOpen} width={860} title="同步任务详情" onClose={() => setDetailOpen(false)} loading={detailLoading}>
        {detail ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card size="small">
              <Space direction="vertical" size={4}>
                <span>任务ID: {detail.id}</span>
                <span>店铺: {detail.shopId}</span>
                <span>类型: {detail.syncType}</span>
                <span>状态: {detail.status || '-'}</span>
                <span>阶段: {detail.currentPhase || '-'}</span>
                <span>开始时间: {formatDateTime(detail.startedAt)}</span>
                <span>结束时间: {formatDateTime(detail.finishedAt)}</span>
              </Space>
            </Card>
            <Card size="small" title="进度概览">
              <Space direction="vertical" size={4}>
                <span>下载: {progress?.downloadCompleted ?? detail.downloadCompleted ?? 0}/{progress?.downloadTotal ?? detail.downloadTotal ?? 0}</span>
                <span>落库: {progress?.persistCompleted ?? detail.persistCompleted ?? 0}/{progress?.persistTotal ?? detail.persistTotal ?? 0}</span>
                <span>批次: {progress?.persistedBatches ?? detail.persistedBatches ?? 0}/{progress?.totalBatches ?? detail.totalBatches ?? 0}</span>
                <span>最后错误: {detail.lastErrorMsg || '-'}</span>
              </Space>
            </Card>
            <Card size="small" title="最近日志">
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                {(progress?.latestLogs || detail.logs || []).map((item, index) => (
                  <Card key={`${item.createdAt}-${index}`} size="small">
                    <Space direction="vertical" size={2}>
                      <Space>
                        <Tag>{item.level || '-'}</Tag>
                        <Tag>{item.phase || '-'}</Tag>
                        <Typography.Text type="secondary">{formatDateTime(item.createdAt)}</Typography.Text>
                      </Space>
                      <Typography.Paragraph style={{ marginBottom: 0 }}>{item.message || '-'}</Typography.Paragraph>
                    </Space>
                  </Card>
                ))}
              </Space>
            </Card>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
};

export default SyncTasksPage;
