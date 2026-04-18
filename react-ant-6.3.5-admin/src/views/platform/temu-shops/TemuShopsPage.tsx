import {
  Alert,
  App,
  Button,
  Card,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { temuAppsApi } from '@/api/temuApps';
import { temuShopsApi } from '@/api/temuShops';
import type { TemuAppVO, TemuShopPayload, TemuShopVO } from '@/types/api';

const SHOP_DEFAULTS = {
  siteId: 100,
  warehouseId: 'WH-03304781516934009',
  defaultStock: 1000,
  maxStock: 10842,
  originRegion1ShortName: 'CN',
  originRegion2Id: 43000000000016,
  freightTemplateId: 'HFT-14851213328261424009',
  shipmentLimitSecond: 777600,
};

const initialEditForm: TemuShopPayload & { id: number | null } = {
  id: null,
  shopName: '',
  shopId: '',
  token: '',
  appId: null,
  enabled: true,
  siteId: SHOP_DEFAULTS.siteId,
  warehouseId: SHOP_DEFAULTS.warehouseId,
  defaultStock: SHOP_DEFAULTS.defaultStock,
  maxStock: SHOP_DEFAULTS.maxStock,
  originRegion1ShortName: SHOP_DEFAULTS.originRegion1ShortName,
  originRegion2Id: SHOP_DEFAULTS.originRegion2Id,
  freightTemplateId: SHOP_DEFAULTS.freightTemplateId,
  shipmentLimitSecond: SHOP_DEFAULTS.shipmentLimitSecond,
};

const TemuShopsPage = () => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<TemuShopVO[]>([]);
  const [appsLoading, setAppsLoading] = useState(false);
  const [apps, setApps] = useState<TemuAppVO[]>([]);
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editForm, setEditForm] = useState(initialEditForm);

  async function loadApps() {
    setAppsLoading(true);
    try {
      const res = await temuAppsApi.list({ enabled: true });
      setApps(Array.isArray(res.data) ? res.data : []);
    } finally {
      setAppsLoading(false);
    }
  }

  async function reload() {
    setLoading(true);
    try {
      const res = await temuShopsApi.list();
      setRows(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadApps();
    void reload();
  }, []);

  function updateForm<K extends keyof typeof editForm>(key: K, value: (typeof editForm)[K]) {
    setEditForm((current) => ({
      ...current,
      [key]: value,
    }));
  }

  function openCreate() {
    setEditForm(initialEditForm);
    setEditOpen(true);
  }

  function openEdit(record: TemuShopVO) {
    setEditForm({
      id: record.id,
      shopName: record.shopName || '',
      shopId: record.shopId || '',
      token: '',
      appId: record.appId,
      enabled: !!record.enabled,
      siteId: record.siteId ?? null,
      warehouseId: record.warehouseId || '',
      defaultStock: record.defaultStock ?? null,
      maxStock: record.maxStock ?? null,
      originRegion1ShortName: record.originRegion1ShortName || '',
      originRegion2Id: record.originRegion2Id ?? null,
      freightTemplateId: record.freightTemplateId || '',
      shipmentLimitSecond: record.shipmentLimitSecond ?? null,
    });
    setEditOpen(true);
  }

  function validateForm() {
    if (!editForm.shopName.trim()) return '店铺名称不能为空';
    if (!editForm.shopId.trim()) return '店铺ID不能为空';
    if (!editForm.id && !editForm.token?.trim()) return 'TOKEN 不能为空';
    if (!editForm.appId) return '应用不能为空';
    if (!editForm.siteId) return '站点 ID 不能为空';
    if (!editForm.warehouseId.trim()) return '仓库 ID 不能为空';
    if (!editForm.defaultStock) return '默认库存不能为空';
    if (!editForm.maxStock) return '最大库存不能为空';
    if (!editForm.originRegion1ShortName.trim()) return '产地区域1简称不能为空';
    if (!editForm.originRegion2Id) return '产地区域2 ID 不能为空';
    if (!editForm.freightTemplateId.trim()) return '运费模板 ID 不能为空';
    if (!editForm.shipmentLimitSecond) return '发货时限秒数不能为空';
    return '';
  }

  async function save() {
    const validationMessage = validateForm();
    if (validationMessage) {
      message.error(validationMessage);
      return;
    }

    setSaving(true);
    try {
      const payload: TemuShopPayload = {
        shopName: editForm.shopName.trim(),
        shopId: editForm.shopId.trim(),
        token: editForm.token?.trim() || undefined,
        appId: editForm.appId,
        enabled: !!editForm.enabled,
        siteId: editForm.siteId,
        warehouseId: editForm.warehouseId.trim(),
        defaultStock: editForm.defaultStock,
        maxStock: editForm.maxStock,
        originRegion1ShortName: editForm.originRegion1ShortName.trim(),
        originRegion2Id: editForm.originRegion2Id,
        freightTemplateId: editForm.freightTemplateId.trim(),
        shipmentLimitSecond: editForm.shipmentLimitSecond,
      };

      if (editForm.id) {
        await temuShopsApi.update(editForm.id, payload);
        message.success('店铺已更新');
      } else {
        await temuShopsApi.create(payload);
        message.success('店铺已创建');
      }

      setEditOpen(false);
      await reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  function deleteShop(record: TemuShopVO) {
    Modal.confirm({
      title: '删除店铺？',
      content: `确认删除店铺“${record.shopName || record.shopId}”吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await temuShopsApi.delete(record.id);
          message.success('已删除');
          await reload();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        }
      },
    });
  }

  const columns: ColumnsType<TemuShopVO> = [
    {
      title: '店铺',
      key: 'shopName',
      width: 260,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{record.shopName || '-'}</Typography.Text>
          <Typography.Text type="secondary">{record.shopId || '-'}</Typography.Text>
        </Space>
      ),
    },
    { title: '应用', dataIndex: 'appName', key: 'appName', width: 220 },
    {
      title: '发布配置',
      key: 'publishConfig',
      width: 520,
      render: (_, record) => (
        <Space size={[6, 6]} wrap>
          <Tag>站点 {record.siteId ?? '-'}</Tag>
          <Tag>仓库 {record.warehouseId || '-'}</Tag>
          <Tag>
            库存 {record.defaultStock ?? '-'} / {record.maxStock ?? '-'}
          </Tag>
          <Tag>
            产地 {record.originRegion1ShortName || '-'} / {record.originRegion2Id ?? '-'}
          </Tag>
          <Tag>运费模板 {record.freightTemplateId || '-'}</Tag>
          <Tag>发货时限 {record.shipmentLimitSecond ?? '-'}s</Tag>
        </Space>
      ),
    },
    {
      title: 'TOKEN',
      dataIndex: 'tokenMasked',
      key: 'tokenMasked',
      width: 180,
      render: (value: string | null) => value || '-',
    },
    {
      title: '启用',
      key: 'enabled',
      width: 90,
      render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? '启用' : '禁用'}</Tag>,
    },
    { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 170 },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button size="small" danger onClick={() => deleteShop(record)}>
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
        message="平台配置已并入店铺配置"
        description="发布站点、仓库、库存、产地、运费模板和发货时限现在都跟随店铺保存，发布商品时会按商品绑定店铺读取。"
      />

      <Card>
        <Space>
          <Button type="primary" onClick={openCreate}>
            新增店铺
          </Button>
          <Button onClick={() => void reload()} loading={loading}>
            刷新
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<TemuShopVO>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={false}
          scroll={{ x: 1600 }}
        />
      </Card>

      <Modal
        open={editOpen}
        title={editForm.id ? '编辑店铺' : '新增店铺'}
        confirmLoading={saving}
        width={920}
        onOk={() => {
          void save();
        }}
        onCancel={() => setEditOpen(false)}
      >
        <Form layout="vertical">
          <Typography.Title level={5}>店铺凭证</Typography.Title>
          <Form.Item label="店铺名称" required>
            <Input value={editForm.shopName} onChange={(event) => updateForm('shopName', event.target.value)} />
          </Form.Item>
          <Form.Item label="店铺ID" required>
            <Input value={editForm.shopId} onChange={(event) => updateForm('shopId', event.target.value)} />
          </Form.Item>
          <Form.Item label="TOKEN" required={!editForm.id}>
            <Input.Password
              value={editForm.token}
              onChange={(event) => updateForm('token', event.target.value)}
              placeholder="新增必填；编辑留空表示不修改"
              autoComplete="new-password"
            />
          </Form.Item>
          <Form.Item label="应用" required>
            <Select
              value={editForm.appId}
              onChange={(value) => updateForm('appId', value)}
              options={apps.map((item) => ({
                value: item.id,
                label: `${item.appName}（${item.appKey}）`,
              }))}
              loading={appsLoading}
              showSearch
              optionFilterProp="label"
              placeholder="请选择应用"
            />
          </Form.Item>
          <Form.Item label="启用">
            <Switch checked={editForm.enabled} onChange={(checked) => updateForm('enabled', checked)} />
          </Form.Item>

          <Divider />

          <Typography.Title level={5}>店铺发布配置</Typography.Title>
          <Form.Item label="站点 ID" required>
            <InputNumber
              value={editForm.siteId}
              min={1}
              precision={0}
              style={{ width: '100%' }}
              onChange={(value) => updateForm('siteId', value ?? null)}
            />
          </Form.Item>
          <Form.Item label="仓库 ID" required>
            <Input value={editForm.warehouseId} onChange={(event) => updateForm('warehouseId', event.target.value)} />
          </Form.Item>
          <Form.Item label="默认库存" required>
            <InputNumber
              value={editForm.defaultStock}
              min={1}
              precision={0}
              style={{ width: '100%' }}
              onChange={(value) => updateForm('defaultStock', value ?? null)}
            />
          </Form.Item>
          <Form.Item label="最大库存" required>
            <InputNumber
              value={editForm.maxStock}
              min={1}
              precision={0}
              style={{ width: '100%' }}
              onChange={(value) => updateForm('maxStock', value ?? null)}
            />
          </Form.Item>
          <Form.Item label="产地区域1简称" required>
            <Input
              value={editForm.originRegion1ShortName}
              onChange={(event) => updateForm('originRegion1ShortName', event.target.value)}
            />
          </Form.Item>
          <Form.Item label="产地区域2 ID" required>
            <InputNumber
              value={editForm.originRegion2Id}
              min={1}
              precision={0}
              style={{ width: '100%' }}
              onChange={(value) => updateForm('originRegion2Id', value ?? null)}
            />
          </Form.Item>
          <Form.Item label="运费模板 ID" required>
            <Input
              value={editForm.freightTemplateId}
              onChange={(event) => updateForm('freightTemplateId', event.target.value)}
            />
          </Form.Item>
          <Form.Item label="发货时限秒数" required>
            <InputNumber
              value={editForm.shipmentLimitSecond}
              min={1}
              precision={0}
              style={{ width: '100%' }}
              onChange={(value) => updateForm('shipmentLimitSecond', value ?? null)}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default TemuShopsPage;
