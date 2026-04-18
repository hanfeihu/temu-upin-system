import {
  App,
  Button,
  Card,
  Drawer,
  Input,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { publishLogsApi } from '@/api/publishLogs';
import type { PublishLogVO, PublishRunVO } from '@/types/api';

function formatTime(value: string | number[] | null) {
  if (!value) {
    return '';
  }

  if (Array.isArray(value)) {
    const [year, month, day, hour, minute, second, nanoSecond] = value;
    const pad = (input: number | undefined, size = 2) => String(input || 0).padStart(size, '0');
    const millisecond = nanoSecond == null ? 0 : Math.floor(Number(nanoSecond) / 1e6);
    return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}.${pad(millisecond, 3)}`;
  }

  return String(value);
}

function prettyPrint(text?: string | null) {
  if (!text) {
    return '';
  }

  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

const PublishLogsPage = () => {
  const { message } = App.useApp();
  const [spuId, setSpuId] = useState('');
  const [loading, setLoading] = useState(false);
  const [runs, setRuns] = useState<PublishRunVO[]>([]);
  const [logsOpen, setLogsOpen] = useState(false);
  const [logsLoading, setLogsLoading] = useState(false);
  const [logs, setLogs] = useState<PublishLogVO[]>([]);
  const [selectedRun, setSelectedRun] = useState<PublishRunVO | null>(null);
  const [rawOpen, setRawOpen] = useState(false);
  const [rawRun, setRawRun] = useState<PublishRunVO | null>(null);

  async function reload(nextSpuId = spuId) {
    setLoading(true);
    try {
      const normalized = String(nextSpuId || '').trim();
      if (normalized) {
        localStorage.setItem('temuPublishLogs.spuId', normalized);
      }
      const res = await publishLogsApi.listRuns(normalized);
      setRuns(Array.isArray(res.data) ? res.data : []);
      if (!res.data.length) {
        message.info(normalized ? '没有找到该 spuId 的发布记录' : '暂无发布记录');
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const lastSpuId = String(localStorage.getItem('temuPublishLogs.spuId') || '').trim();
    if (lastSpuId) {
      setSpuId(lastSpuId);
      void reload(lastSpuId);
      return;
    }

    void reload('');
  }, []);

  async function openLogs(record: PublishRunVO) {
    setSelectedRun(record);
    setLogs([]);
    setLogsOpen(true);
    setLogsLoading(true);
    try {
      const res = await publishLogsApi.listLogs(record.id);
      setLogs(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载日志失败');
    } finally {
      setLogsLoading(false);
    }
  }

  const columns: ColumnsType<PublishRunVO> = [
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
      title: 'goodsId',
      key: 'goodsId',
      width: 190,
      render: (_, record) => record.goodsId || '-',
    },
    {
      title: '时间',
      key: 'time',
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
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void openLogs(record)}>
            查看日志
          </Button>
          <Button size="small" type="primary" ghost onClick={() => {
            setRawRun(record);
            setRawOpen(true);
          }}>
            请求/响应
          </Button>
        </Space>
      ),
    },
  ];

  const logColumns: ColumnsType<PublishLogVO> = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 190,
      render: (value) => formatTime(value),
    },
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
      key: 'message',
      render: (_, record) => (
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <Typography.Text>{record.message || '-'}</Typography.Text>
          {record.dataJson ? (
            <Typography.Paragraph
              style={{
                marginBottom: 0,
                padding: 12,
                borderRadius: 6,
                background: '#fafafa',
                whiteSpace: 'pre-wrap',
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
              }}
            >
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
        <Space>
          <Input
            value={spuId}
            onChange={(event) => setSpuId(event.target.value)}
            placeholder="输入 spuId，例如 73"
            style={{ width: 220 }}
            allowClear
            onPressEnter={() => {
              void reload();
            }}
          />
          <Button type="primary" loading={loading} onClick={() => {
            void reload();
          }}>
            查询
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<PublishRunVO>
          rowKey="id"
          columns={columns}
          dataSource={runs}
          loading={loading}
          pagination={false}
          scroll={{ x: 900 }}
        />
      </Card>

      <Drawer
        open={logsOpen}
        title={
          selectedRun ? `发布日志 runId=${selectedRun.id} spuId=${selectedRun.spuId} 状态=${selectedRun.status || '-'}` : '发布日志'
        }
        width={860}
        onClose={() => setLogsOpen(false)}
      >
        <Table<PublishLogVO>
          rowKey="id"
          columns={logColumns}
          dataSource={logs}
          loading={logsLoading}
          pagination={false}
          size="small"
        />
      </Drawer>

      <Drawer open={rawOpen} title="请求 / 响应" width={920} onClose={() => setRawOpen(false)}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <div>
            <Typography.Text strong>请求 JSON</Typography.Text>
            <Typography.Paragraph
              style={{
                marginTop: 8,
                marginBottom: 0,
                padding: 12,
                borderRadius: 6,
                background: '#fafafa',
                whiteSpace: 'pre-wrap',
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
              }}
            >
              {prettyPrint(rawRun?.requestJson)}
            </Typography.Paragraph>
          </div>

          <div>
            <Typography.Text strong>响应 Raw</Typography.Text>
            <Typography.Paragraph
              style={{
                marginTop: 8,
                marginBottom: 0,
                padding: 12,
                borderRadius: 6,
                background: '#fafafa',
                whiteSpace: 'pre-wrap',
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
              }}
            >
              {prettyPrint(rawRun?.responseRaw)}
            </Typography.Paragraph>
          </div>

          {rawRun?.error ? (
            <div>
              <Typography.Text strong>错误</Typography.Text>
              <Typography.Paragraph
                style={{
                  marginTop: 8,
                  marginBottom: 0,
                  padding: 12,
                  borderRadius: 6,
                  background: '#fff2f0',
                  whiteSpace: 'pre-wrap',
                  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
                }}
              >
                {rawRun.error}
              </Typography.Paragraph>
            </div>
          ) : null}
        </Space>
      </Drawer>
    </Space>
  );
};

export default PublishLogsPage;
