import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { temuAutoPublishLogsApi } from '@/api/temuAutoPublishLogs';
import type { PublishLogVO, TemuAutoPublishRunVO, TemuAutoPublishSampleVO, WorkerStatusVO } from '@/types/api';

function formatTime(value: string | number[] | null | undefined) {
  if (!value) return '';
  if (Array.isArray(value)) {
    const [year, month, day, hour, minute, second, nanoSecond] = value;
    const pad = (input: number | undefined, size = 2) => String(input || 0).padStart(size, '0');
    const millisecond = nanoSecond == null ? 0 : Math.floor(Number(nanoSecond) / 1e6);
    return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}.${pad(millisecond, 3)}`;
  }
  return String(value);
}

function prettyPrint(value?: string | null) {
  if (!value) return '';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

const TemuAutoPublishLogsPage = () => {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [workerLoading, setWorkerLoading] = useState(false);
  const [worker, setWorker] = useState<WorkerStatusVO | null>(null);
  const [clearing, setClearing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [runs, setRuns] = useState<TemuAutoPublishRunVO[]>([]);
  const [spuId, setSpuId] = useState('');
  const [status, setStatus] = useState<string>();
  const [action, setAction] = useState<string>();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [logsOpen, setLogsOpen] = useState(false);
  const [logsLoading, setLogsLoading] = useState(false);
  const [logs, setLogs] = useState<PublishLogVO[]>([]);
  const [sample, setSample] = useState<TemuAutoPublishSampleVO | null>(null);
  const [selectedRun, setSelectedRun] = useState<TemuAutoPublishRunVO | null>(null);

  async function loadWorkerStatus() {
    setWorkerLoading(true);
    try {
      const res = await temuAutoPublishLogsApi.workerStatus();
      setWorker(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载状态失败');
    } finally {
      setWorkerLoading(false);
    }
  }

  async function reload(nextPage = page, nextPageSize = pageSize) {
    setLoading(true);
    try {
      const res = await temuAutoPublishLogsApi.searchRuns({
        spuId: String(spuId || '').trim() || undefined,
        status: status || undefined,
        action: action || undefined,
        q: String(q || '').trim() || undefined,
        page: Math.max(nextPage - 1, 0),
        size: nextPageSize,
      });
      setRuns(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
      if (!res.data.content?.length) message.info('暂无记录');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadWorkerStatus();
    void reload(1, 20);
  }, []);

  async function openLogs(record: TemuAutoPublishRunVO) {
    setSelectedRun(record);
    setLogsOpen(true);
    setLogs([]);
    setSample(null);
    setLogsLoading(true);
    try {
      const [logsRes, sampleRes] = await Promise.all([
        temuAutoPublishLogsApi.listLogs(record.id),
        temuAutoPublishLogsApi.sample(record.id),
      ]);
      setLogs(Array.isArray(logsRes.data) ? logsRes.data : []);
      setSample(sampleRes.data || null);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载日志失败');
    } finally {
      setLogsLoading(false);
    }
  }

  function jumpPublishRun(record: TemuAutoPublishRunVO) {
    if (!record.publishRunId) return;
    navigate(`/platform/publish-logs?spuId=${record.spuId}`);
  }

  function clearAllLogs() {
    Modal.confirm({
      title: '清空自动发布日志？',
      content: '该操作会清空自动发布 run 和日志数据，且不可恢复。',
      okText: '清空',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        setClearing(true);
        try {
          const res = await temuAutoPublishLogsApi.clearAllLogs();
          message.success(String(res.message || '已清空'));
          await reload(1, pageSize);
        } catch (error) {
          message.error(error instanceof Error ? error.message : '清空失败');
          throw error;
        } finally {
          setClearing(false);
        }
      },
    });
  }

  const columns: ColumnsType<TemuAutoPublishRunVO> = [
    { title: 'runId', dataIndex: 'id', key: 'id', width: 90 },
    { title: 'spuId', dataIndex: 'spuId', key: 'spuId', width: 90 },
    {
      title: '动作',
      key: 'action',
      width: 110,
      render: (_, record) => <Tag color={record.action === 'PUBLISH' ? 'blue' : 'default'}>{record.action || '-'}</Tag>,
    },
    {
      title: '状态',
      key: 'status',
      width: 110,
      render: (_, record) => (
        <Tag
          color={
            record.status === 'SUCCEEDED'
              ? 'green'
              : record.status === 'FAILED'
                ? 'red'
                : record.status === 'SKIPPED'
                  ? 'default'
                  : 'blue'
          }
        >
          {record.status || '-'}
        </Tag>
      ),
    },
    {
      title: 'publishRunId',
      key: 'publishRunId',
      width: 120,
      render: (_, record) => record.publishRunId || '-',
    },
    {
      title: '摘要/错误',
      key: 'summary',
      width: 420,
      render: (_, record) => (
        <Space direction="vertical" size={2} style={{ width: '100%' }}>
          <Typography.Text ellipsis={{ tooltip: record.summary || '' }}>{record.summary || '-'}</Typography.Text>
          {record.error ? <Typography.Text type="danger">{record.error}</Typography.Text> : null}
        </Space>
      ),
    },
    {
      title: '时间',
      key: 'time',
      width: 260,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>start: {formatTime(record.startedAt) || '-'}</Typography.Text>
          <Typography.Text>end: {formatTime(record.finishedAt) || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 210,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void openLogs(record)}>
            查看详细日志
          </Button>
          {record.publishRunId ? (
            <Button size="small" type="primary" ghost onClick={() => jumpPublishRun(record)}>
              打开发布 run
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  const logColumns: ColumnsType<PublishLogVO> = [
    { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 190, render: (value) => formatTime(value) },
    { title: '阶段', dataIndex: 'stage', key: 'stage', width: 120 },
    {
      title: '级别',
      key: 'level',
      width: 90,
      render: (_, record) => (
        <Tag color={record.level === 'ERROR' ? 'red' : record.level === 'WARN' ? 'gold' : 'blue'}>
          {record.level || 'INFO'}
        </Tag>
      ),
    },
    {
      title: '内容',
      key: 'msg',
      render: (_, record) => (
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <Typography.Text>{record.message || '-'}</Typography.Text>
          {record.dataJson ? (
            <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
              {prettyPrint(record.dataJson)}
            </Typography.Paragraph>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space wrap>
            <Button size="small" loading={workerLoading} onClick={() => void loadWorkerStatus()}>
              刷新状态
            </Button>
            {worker ? (
              <>
                <Tag color={worker.enabled ? (worker.running ? 'green' : 'gold') : 'default'}>
                  Worker {worker.enabled ? (worker.running ? 'RUNNING' : 'STOPPED') : 'DISABLED'}
                </Tag>
                <Tag color="blue">pollMs {worker.pollMs}</Tag>
                {worker.lastTickAt ? <Tag>lastTick {formatTime(worker.lastTickAt)}</Tag> : null}
                {worker.lastClaimAt ? <Tag>lastClaim {formatTime(worker.lastClaimAt)}</Tag> : null}
                {worker.lastError ? <Tag color="red">{worker.lastError}</Tag> : null}
              </>
            ) : null}
          </Space>

          <Space wrap>
            <Input value={spuId} onChange={(e) => setSpuId(e.target.value)} placeholder="spuId (可选)" style={{ width: 160 }} allowClear onPressEnter={() => void reload(1, pageSize)} />
            <Select
              value={status}
              onChange={setStatus}
              placeholder="状态"
              style={{ width: 140 }}
              allowClear
              options={[
                { label: 'STARTED', value: 'STARTED' },
                { label: 'SKIPPED', value: 'SKIPPED' },
                { label: 'SUCCEEDED', value: 'SUCCEEDED' },
                { label: 'FAILED', value: 'FAILED' },
              ]}
            />
            <Select
              value={action}
              onChange={setAction}
              placeholder="动作"
              style={{ width: 120 }}
              allowClear
              options={[
                { label: 'SKIP', value: 'SKIP' },
                { label: 'PUBLISH', value: 'PUBLISH' },
              ]}
            />
            <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="关键字(原因/错误)" style={{ width: 220 }} allowClear onPressEnter={() => void reload(1, pageSize)} />
            <Button type="primary" loading={loading} onClick={() => {
              setPage(1);
              void reload(1, pageSize);
            }}>
              查询
            </Button>
            <Button danger loading={clearing} onClick={clearAllLogs}>
              清空数据
            </Button>
          </Space>
        </Space>
      </Card>

      <Card>
        <Table<TemuAutoPublishRunVO>
          rowKey="id"
          columns={columns}
          dataSource={runs}
          loading={loading}
          scroll={{ x: 1200 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
            pageSizeOptions: ['10', '20', '50', '100'],
          }}
          onChange={(pagination: TablePaginationConfig) => {
            const nextPage = pagination.current || 1;
            const nextPageSize = pagination.pageSize || 20;
            setPage(nextPage);
            setPageSize(nextPageSize);
            void reload(nextPage, nextPageSize);
          }}
        />
      </Card>

      <Alert
        type="info"
        showIcon
        message="页面说明"
        description="Worker RUNNING 代表后台线程在跑，lastTick 会持续更新。只有 claim 到候选 spu 才会生成 run 记录。SKIPPED 代表规则不满足。"
      />

      <Drawer
        open={logsOpen}
        title={selectedRun ? `自动发布 runId=${selectedRun.id} spuId=${selectedRun.spuId} 状态=${selectedRun.status || '-'}` : '自动发布日志'}
        width={920}
        onClose={() => setLogsOpen(false)}
      >
        {sample ? (
          <Descriptions size="small" bordered column={2} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="spuId">{sample.spuId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag>{sample.status || '-'}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="动作">
              <Tag>{sample.action || '-'}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="publishRunId">{sample.publishRunId || '-'}</Descriptions.Item>
            <Descriptions.Item label="开始">{formatTime(sample.startedAt) || '-'}</Descriptions.Item>
            <Descriptions.Item label="结束">{formatTime(sample.finishedAt) || '-'}</Descriptions.Item>
            <Descriptions.Item label="摘要" span={2}>
              {sample.summary || '-'}
            </Descriptions.Item>
            {sample.error ? (
              <Descriptions.Item label="错误" span={2}>
                <Typography.Text type="danger">{sample.error}</Typography.Text>
              </Descriptions.Item>
            ) : null}
            {sample.eligibilityJson ? (
              <Descriptions.Item label="规则判定" span={2}>
                <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                  {prettyPrint(sample.eligibilityJson)}
                </Typography.Paragraph>
              </Descriptions.Item>
            ) : null}
          </Descriptions>
        ) : null}

        <Table<PublishLogVO>
          rowKey="id"
          columns={logColumns}
          dataSource={logs}
          loading={logsLoading}
          pagination={false}
          size="small"
        />
      </Drawer>
    </Space>
  );
};

export default TemuAutoPublishLogsPage;
