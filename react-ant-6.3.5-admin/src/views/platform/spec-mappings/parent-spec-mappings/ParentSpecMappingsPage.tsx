import { App, Button, Card, Form, Input, Modal, Space, Switch, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { specMappingsApi } from '@/api/specMappings';
import type { ParentSpecMappingPayload, ParentSpecMappingVO } from '@/types/api';
import { formatDateTime } from '@/utils/format';

const initialForm: ParentSpecMappingPayload & { id: number | null } = {
  id: null,
  sourceFieldName: '',
  targetParentSpecName: '',
  enabled: true,
  notes: '',
};

const ParentSpecMappingsPage = () => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<ParentSpecMappingVO[]>([]);
  const [enabled, setEnabled] = useState<boolean | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(initialForm);

  async function load(nextEnabled = enabled) {
    setLoading(true);
    try {
      const res = await specMappingsApi.listParentSpecMappings({ enabled: nextEnabled });
      setRows(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  function updateForm<K extends keyof typeof form>(key: K, value: (typeof form)[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function openCreate() {
    setForm(initialForm);
    setEditOpen(true);
  }

  function openEdit(record: ParentSpecMappingVO) {
    setForm({
      id: record.id,
      sourceFieldName: record.sourceFieldName,
      targetParentSpecName: record.targetParentSpecName,
      enabled: record.enabled,
      notes: record.notes || '',
    });
    setEditOpen(true);
  }

  async function save() {
    if (!form.sourceFieldName.trim()) {
      message.error('来源字段不能为空');
      return;
    }
    if (!form.targetParentSpecName.trim()) {
      message.error('目标父规格不能为空');
      return;
    }

    const payload: ParentSpecMappingPayload = {
      sourceFieldName: form.sourceFieldName.trim(),
      targetParentSpecName: form.targetParentSpecName.trim(),
      enabled: !!form.enabled,
      notes: form.notes?.trim() || undefined,
    };

    setSaving(true);
    try {
      if (form.id) {
        await specMappingsApi.updateParentSpecMapping(form.id, payload);
        message.success('映射已更新');
      } else {
        await specMappingsApi.createParentSpecMapping(payload);
        message.success('映射已创建');
      }
      setEditOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  function remove(record: ParentSpecMappingVO) {
    Modal.confirm({
      title: '删除映射？',
      content: `确认删除来源字段“${record.sourceFieldName}”的映射吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await specMappingsApi.deleteParentSpecMapping(record.id);
          message.success('已删除');
          await load();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        }
      },
    });
  }

  const filteredRows = rows.filter((item) => {
    if (!keyword.trim()) {
      return true;
    }
    const value = keyword.trim().toLowerCase();
    return (
      item.sourceFieldName.toLowerCase().includes(value) ||
      item.targetParentSpecName.toLowerCase().includes(value) ||
      item.normalizedSourceField.toLowerCase().includes(value)
    );
  });

  const columns: ColumnsType<ParentSpecMappingVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '来源字段',
      key: 'sourceFieldName',
      width: 260,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <span>{record.sourceFieldName}</span>
          <Tag>{record.normalizedSourceField}</Tag>
        </Space>
      ),
    },
    { title: '目标父规格', dataIndex: 'targetParentSpecName', key: 'targetParentSpecName', width: 220 },
    {
      title: '备注',
      dataIndex: 'notes',
      key: 'notes',
      render: (value: string | null) => value || '-',
    },
    {
      title: '状态',
      key: 'enabled',
      width: 100,
      render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? '启用' : '停用'}</Tag>,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      render: (value: ParentSpecMappingVO['updatedAt']) => formatDateTime(value),
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
          <Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="搜索来源字段/目标父规格" allowClear style={{ width: 260 }} />
          <Switch checked={enabled === true} onChange={(checked) => setEnabled(checked ? true : undefined)} checkedChildren="仅启用" unCheckedChildren="全部" />
          <Button type="primary" loading={loading} onClick={() => void load()}>
            查询
          </Button>
          <Button onClick={() => {
            setKeyword('');
            setEnabled(undefined);
            void load(undefined);
          }}>
            重置
          </Button>
          <Button type="primary" ghost onClick={openCreate}>
            新增映射
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<ParentSpecMappingVO> rowKey="id" loading={loading} columns={columns} dataSource={filteredRows} pagination={false} />
      </Card>

      <Modal
        open={editOpen}
        title={form.id ? '编辑父规格映射' : '新增父规格映射'}
        confirmLoading={saving}
        width={720}
        onOk={() => void save()}
        onCancel={() => setEditOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="来源字段" required>
            <Input value={form.sourceFieldName} onChange={(e) => updateForm('sourceFieldName', e.target.value)} />
          </Form.Item>
          <Form.Item label="目标父规格" required>
            <Input value={form.targetParentSpecName} onChange={(e) => updateForm('targetParentSpecName', e.target.value)} />
          </Form.Item>
          <Form.Item label="备注">
            <Input.TextArea rows={4} value={form.notes} onChange={(e) => updateForm('notes', e.target.value)} />
          </Form.Item>
          <Form.Item label="启用">
            <Switch checked={!!form.enabled} onChange={(checked) => updateForm('enabled', checked)} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default ParentSpecMappingsPage;
