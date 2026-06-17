import { App, Button, Card, Form, Input, Modal, Select, Space, Switch, Table, Tag } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { temuForbiddenWordRulesApi } from '@/api/temuForbiddenWordRules';
import type { TemuForbiddenWordRuleVO } from '@/types/api';
import { formatDateTime } from '@/utils/format';

const fieldScopeOptions = [
  { value: 'ALL', label: '全部字段' },
  { value: 'TITLE', label: '标题' },
  { value: 'SKU', label: 'SKU规格' },
  { value: 'PROPERTY', label: '商品属性' },
  { value: 'DETAIL', label: '详情文案' },
  { value: 'OCR', label: '图片OCR' },
];

const fieldScopeLabel = (value?: string | null) => fieldScopeOptions.find((item) => item.value === value)?.label || value || '-';

const TemuForbiddenWordRulesPage = () => {
  const { message, modal } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<TemuForbiddenWordRuleVO[]>([]);
  const [keyword, setKeyword] = useState('');
  const [queryKeyword, setQueryKeyword] = useState('');
  const [enabledFilter, setEnabledFilter] = useState<'ALL' | 'ENABLED' | 'DISABLED'>('ALL');
  const [queryEnabledFilter, setQueryEnabledFilter] = useState<'ALL' | 'ENABLED' | 'DISABLED'>('ALL');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<TemuForbiddenWordRuleVO | null>(null);
  const [form] = Form.useForm();

  const toEnabledParam = (value: typeof enabledFilter) => {
    if (value === 'ENABLED') return true;
    if (value === 'DISABLED') return false;
    return undefined;
  };

  async function load(nextPage = page, nextPageSize = pageSize, nextKeyword = queryKeyword, nextEnabled = queryEnabledFilter) {
    setLoading(true);
    try {
      const res = await temuForbiddenWordRulesApi.list({
        q: nextKeyword.trim() || undefined,
        enabled: toEnabledParam(nextEnabled),
        page: nextPage - 1,
        size: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 TEMU 违禁词失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(1, pageSize, '', 'ALL');
  }, []);

  function openCreate() {
    setEditing(null);
    form.setFieldsValue({
      word: '',
      replacement: '',
      fieldScope: 'ALL',
      enabled: true,
      remark: '',
    });
    setEditOpen(true);
  }

  function openEdit(record: TemuForbiddenWordRuleVO) {
    setEditing(record);
    form.setFieldsValue({
      word: record.word || '',
      replacement: record.replacement || '',
      fieldScope: record.fieldScope || 'ALL',
      enabled: record.enabled !== false,
      remark: record.remark || '',
    });
    setEditOpen(true);
  }

  async function save() {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const payload = {
        word: String(values.word || '').trim(),
        replacement: String(values.replacement || '').trim() || undefined,
        fieldScope: values.fieldScope || 'ALL',
        enabled: values.enabled !== false,
        remark: String(values.remark || '').trim() || undefined,
      };
      if (editing) {
        await temuForbiddenWordRulesApi.update(editing.id, payload);
        message.success('已更新');
      } else {
        await temuForbiddenWordRulesApi.create(payload);
        message.success('已新增');
      }
      setEditOpen(false);
      await load(page, pageSize, queryKeyword, queryEnabledFilter);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  function remove(record: TemuForbiddenWordRuleVO) {
    modal.confirm({
      title: '删除违禁词？',
      content: `确认删除“${record.word}”吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await temuForbiddenWordRulesApi.delete(record.id);
          message.success('已删除');
          await load(page, pageSize, queryKeyword, queryEnabledFilter);
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        }
      },
    });
  }

  const columns: ColumnsType<TemuForbiddenWordRuleVO> = [
    {
      title: '违禁词',
      dataIndex: 'word',
      key: 'word',
      width: 220,
    },
    {
      title: '建议替换',
      dataIndex: 'replacement',
      key: 'replacement',
      width: 220,
      render: (value) => value || '-',
    },
    {
      title: '适用字段',
      dataIndex: 'fieldScope',
      key: 'fieldScope',
      width: 130,
      render: (value) => fieldScopeLabel(value),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 90,
      render: (value) => <Tag color={value === false ? 'default' : 'green'}>{value === false ? '停用' : '启用'}</Tag>,
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      ellipsis: true,
      render: (value) => value || '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      render: (value) => formatDateTime(value),
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
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索违禁词 / 替换词 / 备注"
            style={{ width: 280 }}
            allowClear
            onPressEnter={() => {
              setQueryKeyword(keyword);
              setQueryEnabledFilter(enabledFilter);
              setPage(1);
              void load(1, pageSize, keyword, enabledFilter);
            }}
          />
          <Select
            value={enabledFilter}
            onChange={setEnabledFilter}
            style={{ width: 130 }}
            options={[
              { value: 'ALL', label: '全部状态' },
              { value: 'ENABLED', label: '启用' },
              { value: 'DISABLED', label: '停用' },
            ]}
          />
          <Button loading={loading} onClick={() => void load()}>
            刷新
          </Button>
          <Button
            type="primary"
            loading={loading}
            onClick={() => {
              setQueryKeyword(keyword);
              setQueryEnabledFilter(enabledFilter);
              setPage(1);
              void load(1, pageSize, keyword, enabledFilter);
            }}
          >
            查询
          </Button>
          <Button type="primary" onClick={openCreate}>
            新增违禁词
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<TemuForbiddenWordRuleVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
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
            void load(nextPage, nextPageSize, queryKeyword, queryEnabledFilter);
          }}
        />
      </Card>

      <Modal
        open={editOpen}
        title={editing ? '编辑 TEMU 违禁词' : '新增 TEMU 违禁词'}
        confirmLoading={saving}
        onOk={() => void save()}
        onCancel={() => setEditOpen(false)}
        width={620}
      >
        <Form form={form} layout="vertical">
          <Form.Item label="违禁词" name="word" rules={[{ required: true, message: '请输入违禁词' }]}>
            <Input maxLength={256} placeholder="例如 Nipple" />
          </Form.Item>
          <Form.Item label="建议替换" name="replacement">
            <Input maxLength={256} placeholder="例如 Connector" />
          </Form.Item>
          <Form.Item label="适用字段" name="fieldScope">
            <Select options={fieldScopeOptions} />
          </Form.Item>
          <Form.Item label="启用" name="enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={3} maxLength={1000} placeholder="记录平台提示、替换原因或适用场景" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default TemuForbiddenWordRulesPage;
