import { App, Button, Card, Drawer, Form, Image, Input, InputNumber, Modal, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { temuAttrAiFillApi } from '@/api/temuAttrAiFill';
import type { TemuAttrAiFillTaskDetailVO, TemuAttrAiFillTaskVO } from '@/types/api';
import { formatDateTime, prettyJson } from '@/utils/format';

interface Filters {
  q: string;
  status?: number;
}

const initialFilters: Filters = {
  q: '',
  status: undefined,
};

const TemuAttrAiFillPage = () => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<TemuAttrAiFillTaskVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [spuId, setSpuId] = useState<number | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<TemuAttrAiFillTaskDetailVO | null>(null);

  async function load(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true);
    try {
      const res = await temuAttrAiFillApi.listTasks({
        q: nextFilters.q.trim() || undefined,
        status: nextFilters.status,
        page: nextPage - 1,
        size: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(1, 20, initialFilters);
  }, []);

  async function openDetail(record: TemuAttrAiFillTaskVO) {
    setDetailLoading(true);
    setDetailOpen(true);
    try {
      const res = await temuAttrAiFillApi.getTask(record.id);
      setDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  async function createTask() {
    if (!spuId) {
      message.error('请输入 SPU ID');
      return;
    }
    setCreating(true);
    try {
      await temuAttrAiFillApi.createTask(spuId);
      message.success('任务已创建');
      setCreateOpen(false);
      setSpuId(null);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建失败');
    } finally {
      setCreating(false);
    }
  }

  async function runTask(record: TemuAttrAiFillTaskVO) {
    try {
      await temuAttrAiFillApi.runTask(record.id);
      message.success('任务已执行');
      await load();
      if (detail?.id === record.id) {
        await openDetail(record);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '执行失败');
    }
  }

  const columns: ColumnsType<TemuAttrAiFillTaskVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '商品',
      key: 'product',
      width: 320,
      render: (_, record) => (
        <Space size={12}>
          {record.productMainImage ? <Image width={56} height={56} src={record.productMainImage} /> : null}
          <Space direction="vertical" size={2}>
            <Typography.Text ellipsis style={{ maxWidth: 220 }}>
              {record.productName || '-'}
            </Typography.Text>
            <Typography.Text type="secondary">SPU {record.spuId ?? '-'}</Typography.Text>
          </Space>
        </Space>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 120,
      render: (_, record) => <Tag color={record.status === 2 ? 'green' : record.status === 3 ? 'red' : 'processing'}>{record.status ?? '-'}</Tag>,
    },
    {
      title: '结果摘要',
      key: 'resultSummary',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text ellipsis style={{ maxWidth: 320 }}>
            {record.resultSummary || '-'}
          </Typography.Text>
          {record.errorMsg ? <Typography.Text type="danger" ellipsis style={{ maxWidth: 320 }}>{record.errorMsg}</Typography.Text> : null}
        </Space>
      ),
    },
    {
      title: '时间',
      key: 'times',
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
      width: 180,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          <Button size="small" type="primary" ghost onClick={() => void runTask(record)}>
            运行
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Input value={filters.q} onChange={(e) => setFilters((current) => ({ ...current, q: e.target.value }))} placeholder="按商品名 / SPU 搜索" allowClear style={{ width: 260 }} />
          <InputNumber value={filters.status} onChange={(value) => setFilters((current) => ({ ...current, status: value ?? undefined }))} placeholder="状态" style={{ width: 140 }} />
          <Button type="primary" loading={loading} onClick={() => {
            setPage(1);
            void load(1, pageSize, filters);
          }}>
            查询
          </Button>
          <Button onClick={() => {
            setFilters(initialFilters);
            setPage(1);
            setPageSize(20);
            void load(1, 20, initialFilters);
          }}>
            重置
          </Button>
          <Button type="primary" ghost onClick={() => setCreateOpen(true)}>
            新建任务
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<TemuAttrAiFillTaskVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1100 }}
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
        open={createOpen}
        title="新建 AI 填充任务"
        confirmLoading={creating}
        onOk={() => void createTask()}
        onCancel={() => setCreateOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="SPU ID" required>
            <InputNumber value={spuId} onChange={(value) => setSpuId(value)} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer open={detailOpen} title="任务详情" width={860} onClose={() => setDetailOpen(false)} loading={detailLoading}>
        {detail ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card size="small">
              <Space direction="vertical" size={4}>
                <span>任务ID: {detail.taskId || '-'}</span>
                <span>SPU: {detail.spuId ?? '-'}</span>
                <span>状态: {detail.status ?? '-'}</span>
                <span>类目: {detail.leafCatId ?? '-'}</span>
                <span>创建时间: {formatDateTime(detail.createdAt)}</span>
              </Space>
            </Card>
            <Card size="small" title="结果摘要">
              <Typography.Paragraph>{detail.resultSummary || detail.errorMsg || '-'}</Typography.Paragraph>
            </Card>
            <Card size="small" title="Prompt">
              <pre style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{prettyJson(detail.promptText)}</pre>
            </Card>
            <Card size="small" title="响应原文">
              <pre style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{prettyJson(detail.responseRaw)}</pre>
            </Card>
            <Card size="small" title="解析结果">
              <pre style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{prettyJson(detail.resultJson || detail.parsedJson)}</pre>
            </Card>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
};

export default TemuAttrAiFillPage;
