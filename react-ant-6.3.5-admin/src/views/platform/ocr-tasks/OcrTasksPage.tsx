import {
  App,
  Button,
  Card,
  Checkbox,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { ocrApi } from '@/api/ocr';
import type { OcrTaskPayload, OcrTaskVO } from '@/types/api';
import { formatDateTime } from '@/utils/format';

interface Filters {
  spuId?: number;
  productId: string;
  imageType?: number;
  execStatus?: number;
  filtered?: boolean;
  containsChinese?: boolean;
}

const initialFilters: Filters = {
  spuId: undefined,
  productId: '',
  imageType: undefined,
  execStatus: undefined,
  filtered: undefined,
  containsChinese: undefined,
};

const initialForm: OcrTaskPayload & { id: number | null } = {
  id: null,
  spuId: 0,
  productId: '',
  imageType: 1,
  imageUrl: '',
  execStatus: 0,
  execResult: '',
  failReason: '',
  executorPublicIp: '',
  filtered: false,
  containsChinese: undefined,
};

const OcrTasksPage = () => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<OcrTaskVO[]>([]);
  const [stats, setStats] = useState<Record<string, number>>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(initialForm);

  async function load(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true);
    try {
      const [listRes, statsRes] = await Promise.all([
        ocrApi.listTasks({
          spuId: nextFilters.spuId,
          productId: nextFilters.productId.trim() || undefined,
          imageType: nextFilters.imageType,
          execStatus: nextFilters.execStatus,
          filtered: nextFilters.filtered,
          containsChinese: nextFilters.containsChinese,
          page: nextPage - 1,
          size: nextPageSize,
        }),
        ocrApi.statsTasks({
          spuId: nextFilters.spuId,
          productId: nextFilters.productId.trim() || undefined,
          imageType: nextFilters.imageType,
          filtered: nextFilters.filtered,
          containsChinese: nextFilters.containsChinese,
        }),
      ]);

      setRows(Array.isArray(listRes.data.content) ? listRes.data.content : []);
      setTotal(Number(listRes.data.totalElements || 0));
      setStats(statsRes.data || {});
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(1, 20, initialFilters);
  }, []);

  function updateForm<K extends keyof typeof form>(key: K, value: (typeof form)[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function openCreate() {
    setForm(initialForm);
    setEditOpen(true);
  }

  function openEdit(record: OcrTaskVO) {
    setForm({
      id: record.id,
      spuId: record.spuId,
      productId: record.productId || '',
      imageType: record.imageType,
      imageUrl: record.imageUrl,
      execStatus: record.execStatus,
      execResult: record.execResult || '',
      failReason: record.failReason || '',
      executorPublicIp: record.executorPublicIp || '',
      filtered: record.filtered,
      containsChinese: record.containsChinese ?? undefined,
    });
    setEditOpen(true);
  }

  async function save() {
    if (!form.spuId) {
      message.error('SPU ID 不能为空');
      return;
    }
    if (!form.imageUrl.trim()) {
      message.error('图片 URL 不能为空');
      return;
    }

    const payload: OcrTaskPayload = {
      spuId: form.spuId,
      productId: form.productId?.trim() || undefined,
      imageType: form.imageType,
      imageUrl: form.imageUrl.trim(),
      execStatus: form.execStatus,
      execResult: form.execResult?.trim() || undefined,
      failReason: form.failReason?.trim() || undefined,
      executorPublicIp: form.executorPublicIp?.trim() || undefined,
      filtered: !!form.filtered,
      containsChinese: form.containsChinese,
    };

    setSaving(true);
    try {
      if (form.id) {
        await ocrApi.updateTask(form.id, payload);
        message.success('任务已更新');
      } else {
        await ocrApi.createTask(payload);
        message.success('任务已创建');
      }
      setEditOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  function remove(record: OcrTaskVO) {
    Modal.confirm({
      title: '删除任务？',
      content: `确认删除 OCR 任务 #${record.id} 吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await ocrApi.deleteTask(record.id);
          message.success('已删除');
          await load();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        }
      },
    });
  }

  const columns: ColumnsType<OcrTaskVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '商品',
      key: 'product',
      width: 180,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <span>SPU {record.spuId}</span>
          <Typography.Text type="secondary">{record.productId || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '图片',
      key: 'image',
      width: 120,
      render: (_, record) => <Image width={64} height={64} src={record.imageUrl} />,
    },
    {
      title: '类型 / 状态',
      key: 'meta',
      width: 180,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Tag>{record.imageType}</Tag>
          <Tag color={record.execStatus === 2 ? 'green' : record.execStatus === 3 ? 'red' : 'processing'}>{record.execStatus}</Tag>
        </Space>
      ),
    },
    {
      title: '结果',
      key: 'result',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text ellipsis style={{ maxWidth: 320 }}>
            {record.execResult || '-'}
          </Typography.Text>
          {record.failReason ? (
            <Typography.Text type="danger" ellipsis style={{ maxWidth: 320 }}>
              {record.failReason}
            </Typography.Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: '标记',
      key: 'flags',
      width: 160,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Tag color={record.filtered ? 'orange' : 'default'}>{record.filtered ? '已过滤' : '未过滤'}</Tag>
          <Tag color={record.containsChinese ? 'blue' : 'default'}>{record.containsChinese ? '含中文' : '无中文'}</Tag>
        </Space>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      render: (value: OcrTaskVO['updatedAt']) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button size="small" danger onClick={() => remove(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <InputNumber value={filters.spuId} onChange={(value) => setFilters((current) => ({ ...current, spuId: value ?? undefined }))} placeholder="SPU ID" style={{ width: 140 }} />
          <Input value={filters.productId} onChange={(e) => setFilters((current) => ({ ...current, productId: e.target.value }))} placeholder="商品ID" allowClear style={{ width: 180 }} />
          <Select value={filters.imageType} onChange={(value) => setFilters((current) => ({ ...current, imageType: value }))} allowClear placeholder="图片类型" style={{ width: 140 }} options={[{ value: 1, label: '轮播图' }, { value: 2, label: '详情图' }, { value: 3, label: 'SKU 图' }]} />
          <Select value={filters.execStatus} onChange={(value) => setFilters((current) => ({ ...current, execStatus: value }))} allowClear placeholder="执行状态" style={{ width: 140 }} options={[{ value: 0, label: '待执行' }, { value: 1, label: '运行中' }, { value: 2, label: '成功' }, { value: 3, label: '失败' }]} />
          <Select value={filters.filtered} onChange={(value) => setFilters((current) => ({ ...current, filtered: value }))} allowClear placeholder="过滤状态" style={{ width: 140 }} options={[{ value: true, label: '已过滤' }, { value: false, label: '未过滤' }]} />
          <Select value={filters.containsChinese} onChange={(value) => setFilters((current) => ({ ...current, containsChinese: value }))} allowClear placeholder="中文识别" style={{ width: 140 }} options={[{ value: true, label: '含中文' }, { value: false, label: '无中文' }]} />
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
          <Button type="primary" ghost onClick={openCreate}>
            新建任务
          </Button>
        </Space>
      </Card>

      <Space size={16} wrap>
        <Card size="small">
          <Statistic title="总任务" value={stats.total || 0} />
        </Card>
        <Card size="small">
          <Statistic title="成功" value={stats.success || 0} />
        </Card>
        <Card size="small">
          <Statistic title="失败" value={stats.failed || 0} />
        </Card>
        <Card size="small">
          <Statistic title="待处理" value={stats.pending || 0} />
        </Card>
      </Space>

      <Card>
        <Table<OcrTaskVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1400 }}
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
        open={editOpen}
        title={form.id ? '编辑 OCR 任务' : '新建 OCR 任务'}
        width={760}
        confirmLoading={saving}
        onOk={() => void save()}
        onCancel={() => setEditOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="SPU ID" required>
            <InputNumber value={form.spuId} onChange={(value) => updateForm('spuId', value ?? 0)} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="商品ID">
            <Input value={form.productId} onChange={(e) => updateForm('productId', e.target.value)} />
          </Form.Item>
          <Form.Item label="图片类型" required>
            <Select value={form.imageType} onChange={(value) => updateForm('imageType', value)} options={[{ value: 1, label: '轮播图' }, { value: 2, label: '详情图' }, { value: 3, label: 'SKU 图' }]} />
          </Form.Item>
          <Form.Item label="图片 URL" required>
            <Input.TextArea rows={3} value={form.imageUrl} onChange={(e) => updateForm('imageUrl', e.target.value)} />
          </Form.Item>
          <Form.Item label="执行状态">
            <Select value={form.execStatus} onChange={(value) => updateForm('execStatus', value)} options={[{ value: 0, label: '待执行' }, { value: 1, label: '运行中' }, { value: 2, label: '成功' }, { value: 3, label: '失败' }]} />
          </Form.Item>
          <Form.Item label="OCR 结果">
            <Input.TextArea rows={4} value={form.execResult} onChange={(e) => updateForm('execResult', e.target.value)} />
          </Form.Item>
          <Form.Item label="失败原因">
            <Input.TextArea rows={3} value={form.failReason} onChange={(e) => updateForm('failReason', e.target.value)} />
          </Form.Item>
          <Form.Item label="执行器 IP">
            <Input value={form.executorPublicIp} onChange={(e) => updateForm('executorPublicIp', e.target.value)} />
          </Form.Item>
          <Form.Item label="标记">
            <Space>
              <Checkbox checked={!!form.filtered} onChange={(e) => updateForm('filtered', e.target.checked)}>
                已过滤
              </Checkbox>
              <Checkbox checked={form.containsChinese === true} onChange={(e) => updateForm('containsChinese', e.target.checked ? true : false)}>
                含中文
              </Checkbox>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default OcrTasksPage;
