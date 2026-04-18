import { App, Button, Card, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { temuAttrRulesApi } from '@/api/temuAttrRules';
import type { TemuAttrRulePayload, TemuAttrRuleVO } from '@/types/api';
import { formatDateTime } from '@/utils/format';

const initialForm: TemuAttrRulePayload & { id: number | null } = {
  id: null,
  ruleType: 'GENERAL',
  leafCatId: '',
  attrName: '',
  fillMode: 'SKIP',
  fixedValue: '',
  enabled: true,
  sortOrder: 0,
};

const TemuAttrRulesPage = () => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<TemuAttrRuleVO[]>([]);
  const [enabled, setEnabled] = useState<boolean | undefined>(undefined);
  const [leafCatId, setLeafCatId] = useState('');
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(initialForm);

  async function load(nextEnabled = enabled, nextLeafCatId = leafCatId) {
    setLoading(true);
    try {
      const res = await temuAttrRulesApi.list({
        enabled: nextEnabled,
        leafCatId: nextLeafCatId.trim() || undefined,
      });
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

  function openEdit(record: TemuAttrRuleVO) {
    setForm({
      id: record.id,
      ruleType: record.ruleType,
      leafCatId: record.leafCatId || '',
      attrName: record.attrName,
      fillMode: record.fillMode,
      fixedValue: record.fixedValue || '',
      enabled: record.enabled,
      sortOrder: record.sortOrder,
    });
    setEditOpen(true);
  }

  async function save() {
    if (!form.attrName.trim()) {
      message.error('属性名称不能为空');
      return;
    }
    if (form.ruleType === 'FIXED_CATEGORY' && !form.leafCatId?.trim()) {
      message.error('固定类目规则需要填写叶子类目 ID');
      return;
    }
    if (form.fillMode === 'FIXED_VALUE' && !form.fixedValue?.trim()) {
      message.error('固定值模式需要填写固定值');
      return;
    }

    const payload: TemuAttrRulePayload = {
      ruleType: form.ruleType,
      leafCatId: form.leafCatId?.trim() || undefined,
      attrName: form.attrName.trim(),
      fillMode: form.fillMode,
      fixedValue: form.fillMode === 'FIXED_VALUE' ? form.fixedValue?.trim() || undefined : undefined,
      enabled: !!form.enabled,
      sortOrder: form.sortOrder ?? 0,
    };

    setSaving(true);
    try {
      if (form.id) {
        await temuAttrRulesApi.update(form.id, payload);
        message.success('规则已更新');
      } else {
        await temuAttrRulesApi.create(payload);
        message.success('规则已创建');
      }
      setEditOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  function remove(record: TemuAttrRuleVO) {
    Modal.confirm({
      title: '删除规则？',
      content: `确认删除属性规则“${record.attrName}”吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await temuAttrRulesApi.delete(record.id);
          message.success('已删除');
          await load();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        }
      },
    });
  }

  const columns: ColumnsType<TemuAttrRuleVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '规则信息',
      key: 'rule',
      width: 280,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <span>{record.attrName}</span>
          <Space size={6} wrap>
            <Tag>{record.ruleType}</Tag>
            {record.leafCatId ? <Tag color="blue">类目 {record.leafCatId}</Tag> : null}
          </Space>
        </Space>
      ),
    },
    {
      title: '填充方式',
      key: 'fillMode',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Tag color={record.fillMode === 'FIXED_VALUE' ? 'processing' : 'default'}>{record.fillMode}</Tag>
          {record.fixedValue ? <span>{record.fixedValue}</span> : null}
        </Space>
      ),
    },
    {
      title: '状态',
      key: 'enabled',
      width: 120,
      render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? '启用' : '停用'}</Tag>,
    },
    { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 100 },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      render: (value: TemuAttrRuleVO['updatedAt']) => formatDateTime(value),
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
          <Select
            value={enabled}
            onChange={(value) => setEnabled(value)}
            placeholder="启用状态"
            allowClear
            style={{ width: 160 }}
            options={[
              { value: true, label: '仅启用' },
              { value: false, label: '仅停用' },
            ]}
          />
          <Input
            value={leafCatId}
            onChange={(e) => setLeafCatId(e.target.value)}
            placeholder="叶子类目 ID"
            style={{ width: 200 }}
            allowClear
          />
          <Button type="primary" loading={loading} onClick={() => void load()}>
            查询
          </Button>
          <Button onClick={() => {
            setEnabled(undefined);
            setLeafCatId('');
            void load(undefined, '');
          }}>
            重置
          </Button>
          <Button type="primary" ghost onClick={openCreate}>
            新增规则
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<TemuAttrRuleVO> rowKey="id" loading={loading} columns={columns} dataSource={rows} pagination={false} />
      </Card>

      <Modal
        open={editOpen}
        title={form.id ? '编辑属性规则' : '新增属性规则'}
        confirmLoading={saving}
        width={700}
        onOk={() => void save()}
        onCancel={() => setEditOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="规则类型" required>
            <Select
              value={form.ruleType}
              onChange={(value) => updateForm('ruleType', value)}
              options={[
                { value: 'GENERAL', label: '通用规则' },
                { value: 'FIXED_CATEGORY', label: '固定类目规则' },
              ]}
            />
          </Form.Item>
          <Form.Item label="叶子类目 ID">
            <Input value={form.leafCatId} onChange={(e) => updateForm('leafCatId', e.target.value)} />
          </Form.Item>
          <Form.Item label="属性名称" required>
            <Input value={form.attrName} onChange={(e) => updateForm('attrName', e.target.value)} />
          </Form.Item>
          <Form.Item label="填充方式" required>
            <Select
              value={form.fillMode}
              onChange={(value) => updateForm('fillMode', value)}
              options={[
                { value: 'SKIP', label: '跳过' },
                { value: 'FORCE_EMPTY', label: '强制留空' },
                { value: 'FIXED_VALUE', label: '固定值' },
              ]}
            />
          </Form.Item>
          <Form.Item label="固定值">
            <Input value={form.fixedValue} onChange={(e) => updateForm('fixedValue', e.target.value)} />
          </Form.Item>
          <Form.Item label="排序">
            <InputNumber value={form.sortOrder} onChange={(value) => updateForm('sortOrder', value ?? 0)} min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="启用">
            <Switch checked={!!form.enabled} onChange={(checked) => updateForm('enabled', checked)} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default TemuAttrRulesPage;
