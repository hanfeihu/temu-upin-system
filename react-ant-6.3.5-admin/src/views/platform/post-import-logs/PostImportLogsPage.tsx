import { App, Button, Card, Drawer, Input, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { postImportLogsApi } from '@/api/postImportLogs';
import type { PostImportRunVO, PublishLogVO } from '@/types/api';

function formatTime(value: string | number[] | null | undefined) {
  if (!value) return '';
  if (Array.isArray(value)) {
    const [year, month, day, hour, minute, second] = value;
    const pad = (input: number | undefined) => String(input || 0).padStart(2, '0');
    return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}`;
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

async function writeClipboard(text: string) {
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {}

  try {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
    return true;
  } catch {
    return false;
  }
}

const PostImportLogsPage = () => {
  const { message } = App.useApp();
  const [spuId, setSpuId] = useState('');
  const [status, setStatus] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [runs, setRuns] = useState<PostImportRunVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [logsOpen, setLogsOpen] = useState(false);
  const [logsLoading, setLogsLoading] = useState(false);
  const [logs, setLogs] = useState<PublishLogVO[]>([]);
  const [logsPage, setLogsPage] = useState(1);
  const [logsPageSize, setLogsPageSize] = useState(20);
  const [selectedRun, setSelectedRun] = useState<PostImportRunVO | null>(null);

  async function reload(nextPage = page, nextPageSize = pageSize, nextSpuId = spuId, nextStatus = status) {
    setLoading(true);
    try {
      const normalizedSpuId = String(nextSpuId || '').trim();
      const normalizedStatus = String(nextStatus || '').trim();
      const res = await postImportLogsApi.searchRuns({
        spuId: normalizedSpuId || undefined,
        status: normalizedStatus || undefined,
        page: Math.max(nextPage - 1, 0),
        size: nextPageSize,
      });
      const content = Array.isArray(res.data.content) ? res.data.content : [];
      setRuns(content);
      setTotal(Number(res.data.totalElements || 0));
      if (!content.length) {
        message.info(normalizedSpuId ? '没有找到该 spuId 的自动化记录' : '暂无自动化记录');
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void reload(1, 20, '', undefined);
  }, []);

  async function openLogs(record: PostImportRunVO) {
    setSelectedRun(record);
    setLogsOpen(true);
    setLogs([]);
    setLogsPage(1);
    setLogsPageSize(20);
    setLogsLoading(true);
    try {
      const res = await postImportLogsApi.listLogs(record.id);
      setLogs(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载日志失败');
    } finally {
      setLogsLoading(false);
    }
  }

  async function copySample(record?: PostImportRunVO | null) {
    if (!record?.id) return;
    try {
      const res = await postImportLogsApi.getSample(record.id);
      const sample = res.data?.sampleJson;
      if (!sample) {
        message.error('样本为空（可能该 run 还没结束）');
        return;
      }
      const copied = await writeClipboard(prettyPrint(sample));
      if (copied) message.success('样本已复制到剪贴板');
      else message.error('复制失败');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '获取样本失败');
    }
  }

  const columns: ColumnsType<PostImportRunVO> = [
    { title: 'runId', dataIndex: 'id', key: 'id', width: 90 },
    { title: 'spuId', dataIndex: 'spuId', key: 'spuId', width: 90 },
    {
      title: '状态',
      key: 'status',
      width: 110,
      render: (_, record) => (
        <Tag color={record.status === 'SUCCEEDED' ? 'green' : record.status === 'FAILED' ? 'red' : 'blue'}>
          {record.status || '-'}
        </Tag>
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
      title: '结果',
      key: 'summary',
      render: (_, record) => (
        <Space direction="vertical" size={2} style={{ width: '100%' }}>
          <Typography.Text ellipsis={{ tooltip: record.summary || '' }}>{record.summary || '-'}</Typography.Text>
          {record.error ? <Typography.Text type="danger">{record.error}</Typography.Text> : null}
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void openLogs(record)}>
            查看日志
          </Button>
          <Button size="small" type="primary" ghost onClick={() => void copySample(record)}>
            一键复制样本
          </Button>
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
          {record.dataJson ? <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{prettyPrint(record.dataJson)}</Typography.Paragraph> : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Input
            value={spuId}
            onChange={(e) => setSpuId(e.target.value)}
            placeholder="输入 spuId，例如 107"
            style={{ width: 220 }}
            allowClear
            onPressEnter={() => {
              setPage(1);
              void reload(1, pageSize);
            }}
          />
          <Select
            value={status}
            onChange={setStatus}
            style={{ width: 140 }}
            allowClear
            placeholder="状态"
            options={[
              { label: '成功', value: 'SUCCEEDED' },
              { label: '失败', value: 'FAILED' },
              { label: '进行中', value: 'STARTED' },
            ]}
          />
          <Button
            type="primary"
            loading={loading}
            onClick={() => {
              setPage(1);
              void reload(1, pageSize);
            }}
          >
            查询
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<PostImportRunVO>
          rowKey="id"
          columns={columns}
          dataSource={runs}
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
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
          scroll={{ x: 980 }}
        />
      </Card>

      <Drawer
        open={logsOpen}
        title={selectedRun ? `自动化日志 runId=${selectedRun.id} spuId=${selectedRun.spuId} 状态=${selectedRun.status || '-'}` : '自动化日志'}
        width={900}
        onClose={() => {
          setLogsOpen(false);
          setLogsPage(1);
        }}
      >
        <div style={{ marginBottom: 16 }}>
          <Button disabled={!selectedRun} onClick={() => void copySample(selectedRun)}>
            复制该 run 样本
          </Button>
        </div>

        <Table<PublishLogVO>
          rowKey="id"
          columns={logColumns}
          dataSource={logs}
          loading={logsLoading}
          pagination={{
            current: logsPage,
            pageSize: logsPageSize,
            total: logs.length,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (count) => `共 ${count} 条日志`,
            pageSizeOptions: ['10', '20', '50', '100'],
          }}
          onChange={(pagination: TablePaginationConfig) => {
            setLogsPage(pagination.current || 1);
            setLogsPageSize(pagination.pageSize || 20);
          }}
          size="small"
        />
      </Drawer>
    </Space>
  );
};

export default PostImportLogsPage;
