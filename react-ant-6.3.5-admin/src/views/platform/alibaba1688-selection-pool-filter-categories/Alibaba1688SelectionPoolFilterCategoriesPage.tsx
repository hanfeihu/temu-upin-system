import { Alert, App, Button, Card, Form, Input, Modal, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { alibaba1688SelectionPoolFilterCategoriesApi } from '@/api/alibaba1688SelectionPoolFilterCategories';
import type {
  Alibaba1688SelectionPoolFilterCategoryPayload,
  Alibaba1688SelectionPoolFilterCategoryVO,
} from '@/types/api';
import { formatDateTime } from '@/utils/format';

interface Filters {
  keyword: string;
  enabled?: boolean;
}

const initialFilters: Filters = {
  keyword: '',
  enabled: undefined,
};

const initialFormValues: Alibaba1688SelectionPoolFilterCategoryPayload = {
  categoryName: '',
  enabled: true,
  remark: '',
};

function buildPayload(values: Alibaba1688SelectionPoolFilterCategoryPayload): Alibaba1688SelectionPoolFilterCategoryPayload {
  const categoryName = String(values.categoryName || '').trim();
  const remark = String(values.remark || '').trim();

  return {
    categoryName,
    enabled: Boolean(values.enabled),
    remark: remark || undefined,
  };
}

const Alibaba1688SelectionPoolFilterCategoriesPage = () => {
  const { message } = App.useApp();
  const [form] = Form.useForm<Alibaba1688SelectionPoolFilterCategoryPayload>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [rows, setRows] = useState<Alibaba1688SelectionPoolFilterCategoryVO[]>([]);
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<Alibaba1688SelectionPoolFilterCategoryVO | null>(null);
  const [actioningId, setActioningId] = useState<number | null>(null);

  async function load() {
    setLoading(true);
    try {
      const res = await alibaba1688SelectionPoolFilterCategoriesApi.list();
      setRows(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 1688 过滤类目配置失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  function openCreateModal() {
    setEditingRecord(null);
    form.setFieldsValue(initialFormValues);
    setModalOpen(true);
  }

  function openEditModal(record: Alibaba1688SelectionPoolFilterCategoryVO) {
    setEditingRecord(record);
    form.setFieldsValue({
      categoryName: record.categoryName || '',
      enabled: Boolean(record.enabled),
      remark: record.remark || '',
    });
    setModalOpen(true);
  }

  async function handleSave() {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const payload = buildPayload(values);
      if (editingRecord) {
        await alibaba1688SelectionPoolFilterCategoriesApi.update(editingRecord.id, payload);
        message.success('过滤类目配置已更新');
      } else {
        await alibaba1688SelectionPoolFilterCategoriesApi.create(payload);
        message.success('过滤类目配置已新增');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存过滤类目配置失败');
    } finally {
      setSaving(false);
    }
  }

  async function handleToggle(record: Alibaba1688SelectionPoolFilterCategoryVO) {
    setActioningId(record.id);
    try {
      await alibaba1688SelectionPoolFilterCategoriesApi.update(record.id, {
        categoryName: String(record.categoryName || '').trim(),
        enabled: !record.enabled,
        remark: record.remark || undefined,
      });
      message.success(record.enabled ? '已停用该过滤类目' : '已启用该过滤类目');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '切换状态失败');
    } finally {
      setActioningId(null);
    }
  }

  function handleDelete(record: Alibaba1688SelectionPoolFilterCategoryVO) {
    Modal.confirm({
      title: '删除过滤类目？',
      content: `确认删除 “${record.categoryName || `ID ${record.id}`}” 吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        setActioningId(record.id);
        try {
          await alibaba1688SelectionPoolFilterCategoriesApi.remove(record.id);
          message.success('过滤类目配置已删除');
          await load();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除过滤类目失败');
          throw error;
        } finally {
          setActioningId(null);
        }
      },
    });
  }

  function updateFilter<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((current) => ({
      ...current,
      [key]: value,
    }));
  }

  const filteredRows = useMemo(() => {
    const keyword = filters.keyword.trim().toLowerCase();
    return rows.filter((item) => {
      const matchesKeyword = !keyword
        || String(item.categoryName || '').toLowerCase().includes(keyword)
        || String(item.source || '').toLowerCase().includes(keyword)
        || String(item.remark || '').toLowerCase().includes(keyword);
      const matchesEnabled = filters.enabled === undefined || Boolean(item.enabled) === filters.enabled;
      return matchesKeyword && matchesEnabled;
    });
  }, [filters, rows]);

  const summary = useMemo(() => {
    const enabledCount = rows.filter((item) => item.enabled).length;
    return {
      total: rows.length,
      enabled: enabledCount,
      disabled: rows.length - enabledCount,
    };
  }, [rows]);

  const columns: ColumnsType<Alibaba1688SelectionPoolFilterCategoryVO> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 88,
    },
    {
      title: '过滤类目',
      key: 'category',
      width: 360,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong copyable={Boolean(record.categoryName)}>
            {record.categoryName || '-'}
          </Typography.Text>
          <Typography.Text type="secondary">
            来源：{record.source || '-'}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      width: 260,
      render: (value: string | null) => (
        <Typography.Paragraph ellipsis={{ rows: 2, tooltip: value || '' }} style={{ marginBottom: 0 }}>
          {value || '-'}
        </Typography.Paragraph>
      ),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 120,
      render: (value: boolean) => (
        <Tag color={value ? 'green' : 'default'}>
          {value ? '启用中' : '已停用'}
        </Tag>
      ),
    },
    {
      title: '更新时间',
      key: 'updatedAt',
      width: 180,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{formatDateTime(record.updatedAt)}</Typography.Text>
          <Typography.Text type="secondary">{formatDateTime(record.createdAt)}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space wrap>
          <Button size="small" onClick={() => openEditModal(record)}>
            编辑
          </Button>
          <Button
            size="small"
            type={record.enabled ? 'default' : 'primary'}
            loading={actioningId === record.id}
            onClick={() => void handleToggle(record)}
          >
            {record.enabled ? '停用' : '启用'}
          </Button>
          <Button
            size="small"
            danger
            loading={actioningId === record.id}
            onClick={() => handleDelete(record)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message="1688过滤类目配置"
        description="这里维护 1688 选品池需要屏蔽的完整类目字符串。启用中的类目会自动从选品池列表和类目下拉中排除，也可以在选品池操作列里一键加入。"
      />

      <Card>
        <Space wrap>
          <Input
            value={filters.keyword}
            onChange={(event) => updateFilter('keyword', event.target.value)}
            allowClear
            placeholder="搜索类目 / 来源 / 备注"
            style={{ width: 320 }}
          />
          <Select
            value={filters.enabled}
            onChange={(value) => updateFilter('enabled', value)}
            allowClear
            placeholder="启用状态"
            style={{ width: 140 }}
            options={[
              { label: '仅启用', value: true },
              { label: '仅停用', value: false },
            ]}
          />
          <Button loading={loading} onClick={() => void load()}>
            刷新
          </Button>
          <Button type="primary" onClick={openCreateModal}>
            新增过滤类目
          </Button>
          <Tag color="blue">共 {summary.total} 条</Tag>
          <Tag color="green">启用 {summary.enabled}</Tag>
          <Tag>停用 {summary.disabled}</Tag>
        </Space>
      </Card>

      <Card>
        <Table<Alibaba1688SelectionPoolFilterCategoryVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={filteredRows}
          scroll={{ x: 1120 }}
          pagination={{
            pageSize: 20,
            showSizeChanger: true,
            showTotal: (currentTotal) => `共 ${currentTotal} 条`,
          }}
        />
      </Card>

      <Modal
        open={modalOpen}
        title={editingRecord ? '编辑过滤类目' : '新增过滤类目'}
        confirmLoading={saving}
        onOk={() => void handleSave()}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={initialFormValues}
        >
          <Form.Item
            label="完整类目"
            name="categoryName"
            rules={[{ required: true, message: '请输入完整类目' }]}
            extra="请直接填写选品池里展示的完整类目字符串，例如：个护家清 > 头发护理 > 洗发水。"
          >
            <Input
              allowClear
              placeholder="个护家清 > 头发护理 > 洗发水"
            />
          </Form.Item>
          <Form.Item label="启用状态" name="enabled" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea
              rows={4}
              allowClear
              placeholder="可选，记录过滤原因、来源或后续处理说明"
            />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default Alibaba1688SelectionPoolFilterCategoriesPage;
