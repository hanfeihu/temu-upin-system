import { App, Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { aiChannelsApi } from '@/api/aiChannels';
import type { AIChannelVO } from '@/types/api';

const AIChannelsPage = () => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<AIChannelVO[]>([]);
  const [q, setQ] = useState('');
  const [enabledFilter, setEnabledFilter] = useState<boolean | undefined>();
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testingId, setTestingId] = useState<number | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState({
    name: '',
    platform: '',
    model: '',
    apiKey: '',
    apiSecret: '',
    baseUrl: '',
    enabled: true,
    description: '',
    sortOrder: 0,
  });

  const requiresSecret = (platform: string) => ['volcengine', 'jimeng_i2i', 'jimeng_t2i'].includes(String(platform || '').toLowerCase());
  const isChannelOk = (channel: AIChannelVO) => channel.hasApiKey && (!requiresSecret(channel.platform) || channel.hasApiSecret);
  const credentialHint = (channel: AIChannelVO) => {
    if (!channel.hasApiKey) return '缺少Key';
    if (requiresSecret(channel.platform) && !channel.hasApiSecret) return '缺少Secret';
    return '凭证OK';
  };

  async function reload() {
    setLoading(true);
    try {
      const res = await aiChannelsApi.listAll();
      const list = Array.isArray(res.data) ? res.data : [];
      setRows(list);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void reload();
  }, []);

  const filteredRows = [...rows]
    .filter((item) => {
      const keyword = q.trim().toLowerCase();
      if (!keyword) return true;
      return `${item.name || ''} ${item.platform || ''} ${item.model || ''}`.toLowerCase().includes(keyword);
    })
    .filter((item) => (enabledFilter === undefined ? true : !!item.enabled === enabledFilter))
    .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0) || left.id - right.id);

  async function toggleEnabled(record: AIChannelVO, checked: boolean) {
    try {
      await aiChannelsApi.update(record.id, { enabled: checked });
      setRows((current) => current.map((item) => (item.id === record.id ? { ...item, enabled: checked } : item)));
      message.success(checked ? '已启用' : '已禁用');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '更新失败');
    }
  }

  async function updateSort(record: AIChannelVO, value: number | null) {
    const next = Number(value ?? 0);
    try {
      await aiChannelsApi.update(record.id, { sortOrder: next });
      setRows((current) => current.map((item) => (item.id === record.id ? { ...item, sortOrder: next } : item)));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '更新失败');
    }
  }

  async function testChannel(record: AIChannelVO) {
    setTestingId(record.id);
    try {
      const res = await aiChannelsApi.test(record.id);
      message.success(res.data?.success ? '测试成功' : res.data?.message || '测试未通过');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '测试失败');
    } finally {
      setTestingId(null);
    }
  }

  function openCreate() {
    setEditingId(null);
    setForm({
      name: '',
      platform: '',
      model: '',
      apiKey: '',
      apiSecret: '',
      baseUrl: '',
      enabled: true,
      description: '',
      sortOrder: 0,
    });
    setEditOpen(true);
  }

  function openEdit(record: AIChannelVO) {
    setEditingId(record.id);
    setForm({
      name: record.name || '',
      platform: record.platform || '',
      model: record.model || '',
      apiKey: '',
      apiSecret: '',
      baseUrl: record.baseUrl || '',
      enabled: !!record.enabled,
      description: record.description || '',
      sortOrder: record.sortOrder ?? 0,
    });
    setEditOpen(true);
  }

  async function save() {
    if (!form.name.trim()) return message.error('请输入渠道名称');
    if (!form.platform.trim()) return message.error('请输入平台');
    if (!form.model.trim()) return message.error('请输入模型');

    setSaving(true);
    try {
      const payload = {
        name: form.name.trim(),
        platform: form.platform.trim(),
        model: form.model.trim(),
        apiKey: form.apiKey.trim() || undefined,
        apiSecret: form.apiSecret.trim() || undefined,
        baseUrl: form.baseUrl.trim() || undefined,
        enabled: !!form.enabled,
        description: form.description || '',
        sortOrder: Number(form.sortOrder ?? 0),
      };

      if (editingId) await aiChannelsApi.update(editingId, payload);
      else await aiChannelsApi.create(payload);

      message.success('已保存');
      setEditOpen(false);
      await reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  async function removeChannel(record: AIChannelVO) {
    try {
      await aiChannelsApi.remove(record.id);
      message.success('已删除');
      await reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  }

  const columns: ColumnsType<AIChannelVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '渠道',
      key: 'name',
      width: 220,
      render: (_, record) => (
        <Space>
          <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? '启用' : '禁用'}</Tag>
          <span>{record.name || '-'}</span>
        </Space>
      ),
    },
    { title: '平台', dataIndex: 'platform', key: 'platform', width: 160 },
    { title: '模型', dataIndex: 'model', key: 'model', width: 220 },
    {
      title: '凭证状态',
      key: 'credential',
      width: 120,
      render: (_, record) => <Tag color={isChannelOk(record) ? 'green' : 'red'}>{credentialHint(record)}</Tag>,
    },
    {
      title: '启用',
      key: 'enabled',
      width: 90,
      render: (_, record) => <Switch checked={!!record.enabled} onChange={(checked) => void toggleEnabled(record, checked)} />,
    },
    {
      title: '排序',
      key: 'sort',
      width: 130,
      render: (_, record) => (
        <InputNumber value={record.sortOrder ?? 0} min={0} max={999} style={{ width: 110 }} onChange={(value) => void updateSort(record, value)} />
      ),
    },
    { title: 'Base URL', dataIndex: 'baseUrl', key: 'baseUrl', ellipsis: true },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button size="small" loading={testingId === record.id} onClick={() => void testChannel(record)}>
            测试
          </Button>
          <Popconfirm title="确定删除该渠道？" okText="删除" cancelText="取消" onConfirm={() => void removeChannel(record)}>
            <Button size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="搜索渠道/平台/模型" allowClear style={{ width: 260 }} />
          <Select
            value={enabledFilter}
            onChange={setEnabledFilter}
            allowClear
            style={{ width: 120 }}
            placeholder="状态"
            options={[
              { label: '启用', value: true },
              { label: '禁用', value: false },
            ]}
          />
          <Button onClick={() => void reload()} loading={loading}>
            刷新
          </Button>
          <Button type="primary" onClick={openCreate}>
            新增渠道
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<AIChannelVO> rowKey="id" columns={columns} dataSource={filteredRows} loading={loading} pagination={false} scroll={{ x: 1320 }} />
      </Card>

      <Modal open={editOpen} title={editingId ? '编辑渠道' : '新增渠道'} confirmLoading={saving} width={760} onOk={() => void save()} onCancel={() => setEditOpen(false)}>
        <Form layout="vertical">
          <Form.Item label="渠道名称" required>
            <Input value={form.name} onChange={(e) => setForm((current) => ({ ...current, name: e.target.value }))} placeholder="例如：稳定扩图渠道" />
          </Form.Item>
          <Form.Item label="平台" required>
            <Input value={form.platform} onChange={(e) => setForm((current) => ({ ...current, platform: e.target.value }))} placeholder="例如：stability / volcengine" />
          </Form.Item>
          <Form.Item label="模型" required>
            <Input value={form.model} onChange={(e) => setForm((current) => ({ ...current, model: e.target.value }))} placeholder="例如：sd3" />
          </Form.Item>
          <Form.Item label="API Key">
            <Input.Password value={form.apiKey} onChange={(e) => setForm((current) => ({ ...current, apiKey: e.target.value }))} placeholder="为空则不更新" />
          </Form.Item>
          <Form.Item label="API Secret">
            <Input.Password value={form.apiSecret} onChange={(e) => setForm((current) => ({ ...current, apiSecret: e.target.value }))} placeholder="为空则不更新" />
          </Form.Item>
          <Form.Item label="Base URL">
            <Input value={form.baseUrl} onChange={(e) => setForm((current) => ({ ...current, baseUrl: e.target.value }))} placeholder="例如：https://api.stability.ai" />
          </Form.Item>
          <Form.Item label="排序">
            <InputNumber value={form.sortOrder} min={0} max={999} style={{ width: '100%' }} onChange={(value) => setForm((current) => ({ ...current, sortOrder: Number(value ?? 0) }))} />
          </Form.Item>
          <Form.Item label="启用">
            <Switch checked={form.enabled} onChange={(checked) => setForm((current) => ({ ...current, enabled: checked }))} />
          </Form.Item>
          <Form.Item label="描述">
            <Input.TextArea value={form.description} onChange={(e) => setForm((current) => ({ ...current, description: e.target.value }))} rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default AIChannelsPage;
