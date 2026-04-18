import { App, Button, Card, Descriptions, Divider, Drawer, Input, Space, Table, Tabs, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { temuPublishSuccessCasesApi } from '@/api/temuPublishSuccessCases';
import type { SuccessCaseDetailVO, SuccessCaseRowVO } from '@/types/api';

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

const PublishSuccessCasesPage = () => {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [spuId, setSpuId] = useState('');
  const [temuCatid, setTemuCatid] = useState('');
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<SuccessCaseRowVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<SuccessCaseDetailVO | null>(null);
  const [rawOpen, setRawOpen] = useState(false);
  const [raw, setRaw] = useState<{ requestJson: string; responseRaw: string }>({ requestJson: '', responseRaw: '' });

  async function fetchList(nextPage = page, nextPageSize = pageSize) {
    setLoading(true);
    try {
      const params: Record<string, unknown> = {
        page: nextPage,
        pageSize: nextPageSize,
      };
      if (String(spuId || '').trim()) params.spuId = String(spuId).trim();
      if (String(temuCatid || '').trim()) params.temuCatid = String(temuCatid).trim();

      const res = await temuPublishSuccessCasesApi.list(params);
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
      if (!res.data.content?.length) message.info('暂无成功案例');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  async function openDetail(record: SuccessCaseRowVO) {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetail(null);
    try {
      const res = await temuPublishSuccessCasesApi.get(record.id);
      setDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  async function openRaw(record: SuccessCaseRowVO) {
    try {
      const res = await temuPublishSuccessCasesApi.get(record.id);
      setRaw({
        requestJson: res.data.requestJson || '',
        responseRaw: res.data.responseRaw || '',
      });
      setRawOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    }
  }

  const columns: ColumnsType<SuccessCaseRowVO> = [
    { title: 'caseId', dataIndex: 'id', key: 'id', width: 90 },
    { title: 'runId', dataIndex: 'publishRunId', key: 'publishRunId', width: 100 },
    { title: 'spuId', dataIndex: 'spuId', key: 'spuId', width: 90 },
    { title: 'goodsId', dataIndex: 'goodsId', key: 'goodsId', width: 170 },
    { title: '商品标题', dataIndex: 'productName', key: 'productName', width: 280, ellipsis: true },
    {
      title: '类目链路',
      key: 'temuCatid',
      width: 300,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{record.temuCatid || '-'}</Typography.Text>
          <Typography.Text type="secondary">{record.temuCatname || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '时间',
      key: 'time',
      width: 250,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>发布: {formatTime(record.publishedAt) || '-'}</Typography.Text>
          <Typography.Text>归档: {formatTime(record.createdAt) || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 240,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void openDetail(record)}>
            查看详情
          </Button>
          <Button size="small" type="primary" ghost onClick={() => void openRaw(record)}>
            请求/响应
          </Button>
          {record.publishRunId ? (
            <Button size="small" onClick={() => navigate(`/platform/publish-logs?spuId=${record.spuId}`)}>
              打开 run
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Input value={spuId} onChange={(e) => setSpuId(e.target.value)} placeholder="按 spuId 查询" style={{ width: 180 }} allowClear onPressEnter={() => {
            setPage(1);
            void fetchList(1, pageSize);
          }} />
          <Input value={temuCatid} onChange={(e) => setTemuCatid(e.target.value)} placeholder="按类目链路查询，如 27011,28946" style={{ width: 320 }} allowClear onPressEnter={() => {
            setPage(1);
            void fetchList(1, pageSize);
          }} />
          <Button type="primary" loading={loading} onClick={() => {
            setPage(1);
            void fetchList(1, pageSize);
          }}>
            查询
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<SuccessCaseRowVO>
          rowKey="id"
          columns={columns}
          dataSource={rows}
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
            void fetchList(nextPage, nextPageSize);
          }}
        />
      </Card>

      <Drawer
        open={detailOpen}
        title={detail ? `成功案例 #${detail.id} / spuId=${detail.spuId}` : '成功案例详情'}
        width={860}
        onClose={() => setDetailOpen(false)}
      >
        {detailLoading ? (
          <Typography.Text>加载中...</Typography.Text>
        ) : detail ? (
          <>
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="caseId">{detail.id}</Descriptions.Item>
              <Descriptions.Item label="publishRunId">{detail.publishRunId || '-'}</Descriptions.Item>
              <Descriptions.Item label="spuId">{detail.spuId}</Descriptions.Item>
              <Descriptions.Item label="productId">{detail.productId || '-'}</Descriptions.Item>
              <Descriptions.Item label="商品标题">{detail.productName || '-'}</Descriptions.Item>
              <Descriptions.Item label="TEMU 类目链路">
                <div>{detail.temuCatid || '-'}</div>
                <div>{detail.temuCatname || '-'}</div>
              </Descriptions.Item>
              <Descriptions.Item label="goodsId">{detail.goodsId || '-'}</Descriptions.Item>
              <Descriptions.Item label="发布时间">{formatTime(detail.publishedAt) || '-'}</Descriptions.Item>
              <Descriptions.Item label="归档时间">{formatTime(detail.createdAt) || '-'}</Descriptions.Item>
            </Descriptions>

            <Divider />

            <Tabs
              items={[
                {
                  key: 'request',
                  label: 'AddGloGoodsRequest',
                  children: <Typography.Paragraph style={{ whiteSpace: 'pre-wrap' }}>{prettyPrint(detail.requestJson)}</Typography.Paragraph>,
                },
                {
                  key: 'response',
                  label: '返回值 Raw',
                  children: <Typography.Paragraph style={{ whiteSpace: 'pre-wrap' }}>{prettyPrint(detail.responseRaw)}</Typography.Paragraph>,
                },
              ]}
            />
          </>
        ) : null}
      </Drawer>

      <Drawer open={rawOpen} title="请求 / 响应" width={960} onClose={() => setRawOpen(false)}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <div>
            <Typography.Text strong>AddGloGoodsRequest</Typography.Text>
            <Typography.Paragraph style={{ marginTop: 8, whiteSpace: 'pre-wrap' }}>{prettyPrint(raw.requestJson)}</Typography.Paragraph>
          </div>
          <div>
            <Typography.Text strong>响应 Raw</Typography.Text>
            <Typography.Paragraph style={{ marginTop: 8, whiteSpace: 'pre-wrap' }}>{prettyPrint(raw.responseRaw)}</Typography.Paragraph>
          </div>
        </Space>
      </Drawer>
    </Space>
  );
};

export default PublishSuccessCasesPage;
