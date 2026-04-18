import { App, Button, Card, Form, Input, Modal, Space, Switch, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { temuAppsApi } from '@/api/temuApps';
import type { TemuAppVO } from '@/types/api';

const TemuAppsPage = () => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<TemuAppVO[]>([]);
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState({
    appName: '',
    appKey: '',
    appSecret: '',
    enabled: true,
  });

  async function reload() {
    setLoading(true);
    try {
      const res = await temuAppsApi.list({ enabled: true });
      setRows(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void reload();
  }, []);

  function openCreate() {
    setEditingId(null);
    setForm({
      appName: '',
      appKey: '',
      appSecret: '',
      enabled: true,
    });
    setEditOpen(true);
  }

  function openEdit(record: TemuAppVO) {
    setEditingId(record.id);
    setForm({
      appName: record.appName || '',
      appKey: record.appKey || '',
      appSecret: '',
      enabled: !!record.enabled,
    });
    setEditOpen(true);
  }

  async function save() {
    if (!form.appName.trim()) return message.error('请输入应用名称');
    if (!form.appKey.trim()) return message.error('请输入 App Key');
    if (!editingId && !form.appSecret.trim()) return message.error('请输入 App Secret');

    setSaving(true);
    try {
      const payload = {
        appName: form.appName.trim(),
        appKey: form.appKey.trim(),
        appSecret: form.appSecret.trim() || undefined,
        enabled: !!form.enabled,
      };

      if (editingId) await temuAppsApi.update(editingId, payload);
      else await temuAppsApi.create(payload);

      message.success('已保存');
      setEditOpen(false);
      await reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  function deleteApp(record: TemuAppVO) {
    Modal.confirm({
      title: '删除应用？',
      content: '删除后不可恢复。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await temuAppsApi.delete(record.id);
          message.success('已删除');
          await reload();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        }
      },
    });
  }

  const columns: ColumnsType<TemuAppVO> = [
    { title: '应用名称', dataIndex: 'appName', key: 'appName' },
    { title: 'App Key', dataIndex: 'appKey', key: 'appKey', width: 240 },
    { title: 'App Secret', dataIndex: 'appSecretMasked', key: 'appSecretMasked', width: 180 },
    {
      title: '启用',
      key: 'enabled',
      width: 90,
      render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? '启用' : '禁用'}</Tag>,
    },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
    { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 170 },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button size="small" danger onClick={() => deleteApp(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space>
          <Button type="primary" onClick={openCreate}>
            新增应用
          </Button>
          <Button onClick={() => void reload()} loading={loading}>
            刷新
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<TemuAppVO> rowKey="id" columns={columns} dataSource={rows} loading={loading} pagination={false} />
      </Card>

      <Modal open={editOpen} title={editingId ? '编辑应用' : '新增应用'} confirmLoading={saving} width={760} onOk={() => void save()} onCancel={() => setEditOpen(false)}>
        <Form layout="vertical">
          <Form.Item label="应用名称" required>
            <Input value={form.appName} onChange={(e) => setForm((current) => ({ ...current, appName: e.target.value }))} placeholder="例如：TEMU-自研-应用A" />
          </Form.Item>
          <Form.Item label="App Key" required>
            <Input value={form.appKey} onChange={(e) => setForm((current) => ({ ...current, appKey: e.target.value }))} placeholder="TEMU App Key" />
          </Form.Item>
          <Form.Item label="App Secret" required={!editingId}>
            <Input.Password value={form.appSecret} onChange={(e) => setForm((current) => ({ ...current, appSecret: e.target.value }))} placeholder="新增必填；编辑留空表示不修改" autoComplete="new-password" />
          </Form.Item>
          <Form.Item label="启用">
            <Switch checked={form.enabled} onChange={(checked) => setForm((current) => ({ ...current, enabled: checked }))} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default TemuAppsPage;
