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
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { temuAppsApi } from '@/api/temuApps';
import { temuShopsApi } from '@/api/temuShops';
import type { TemuAppVO, TemuShopFreightTemplateOption, TemuShopPayload, TemuShopVO } from '@/types/api';

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
  orderToken: '',
  dianxiaomiCookie: '',
  dianxiaomiShopId: '',
  appId: null,
  orderAppId: null,
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

const buildFreightTemplateOptions = (
  items: TemuShopFreightTemplateOption[],
  currentTemplateId?: string | null,
) => {
  const merged = new Map<string, TemuShopFreightTemplateOption>();
  const normalizedCurrentId = currentTemplateId?.trim();

  if (normalizedCurrentId) {
    merged.set(normalizedCurrentId, {
      freightTemplateId: normalizedCurrentId,
      templateName: null,
    });
  }

  items.forEach((item) => {
    const freightTemplateId = item.freightTemplateId?.trim();
    if (!freightTemplateId) {
      return;
    }
    const current = merged.get(freightTemplateId);
    merged.set(freightTemplateId, {
      freightTemplateId,
      templateName: item.templateName?.trim() || current?.templateName || null,
    });
  });

  return Array.from(merged.values());
};

const freightTemplateLabel = (item: TemuShopFreightTemplateOption) => {
  if (item.templateName?.trim()) {
    return `${item.templateName.trim()}（${item.freightTemplateId}）`;
  }
  return item.freightTemplateId;
};

const appLabel = (record?: TemuAppVO | null) => {
  if (!record) {
    return '-';
  }
  return `${record.appName}（${record.appKey}）`;
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
  const [freightTemplatesLoading, setFreightTemplatesLoading] = useState(false);
  const [freightTemplateOptions, setFreightTemplateOptions] = useState<TemuShopFreightTemplateOption[]>(
    buildFreightTemplateOptions(
      [
        {
          freightTemplateId: SHOP_DEFAULTS.freightTemplateId,
          templateName: null,
        },
      ],
      SHOP_DEFAULTS.freightTemplateId,
    ),
  );

  const productApps = useMemo(
    () => apps.filter((item) => !item.appType || item.appType === 'PRODUCT'),
    [apps],
  );
  const orderApps = useMemo(
    () => apps.filter((item) => item.appType === 'ORDER'),
    [apps],
  );
  const freightTemplateSelectOptions = useMemo(
    () =>
      freightTemplateOptions.map((item) => ({
        value: item.freightTemplateId,
        label: freightTemplateLabel(item),
      })),
    [freightTemplateOptions],
  );

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
    setEditForm((current) => ({ ...current, [key]: value }));
  }

  async function loadFreightTemplates(shopRecordId: number, currentTemplateId?: string | null) {
    setFreightTemplatesLoading(true);
    try {
      const res = await temuShopsApi.listFreightTemplates(shopRecordId);
      setFreightTemplateOptions(buildFreightTemplateOptions(Array.isArray(res.data) ? res.data : [], currentTemplateId));
    } catch (error) {
      setFreightTemplateOptions(buildFreightTemplateOptions([], currentTemplateId));
      message.error(error instanceof Error ? error.message : '运费模板加载失败');
    } finally {
      setFreightTemplatesLoading(false);
    }
  }

  function openCreate() {
    setEditForm(initialEditForm);
    setFreightTemplateOptions(buildFreightTemplateOptions([], SHOP_DEFAULTS.freightTemplateId));
    setEditOpen(true);
  }

  function openEdit(record: TemuShopVO) {
    const nextForm = {
      id: record.id,
      shopName: record.shopName || '',
      shopId: record.shopId || '',
      token: '',
      orderToken: '',
      dianxiaomiCookie: '',
      dianxiaomiShopId: record.dianxiaomiShopId || '',
      appId: record.productAppId,
      orderAppId: record.orderAppId ?? null,
      enabled: !!record.enabled,
      siteId: record.siteId ?? null,
      warehouseId: record.warehouseId || '',
      defaultStock: record.defaultStock ?? null,
      maxStock: record.maxStock ?? null,
      originRegion1ShortName: record.originRegion1ShortName || '',
      originRegion2Id: record.originRegion2Id ?? null,
      freightTemplateId: record.freightTemplateId || '',
      shipmentLimitSecond: record.shipmentLimitSecond ?? null,
    };
    setEditForm(nextForm);
    setFreightTemplateOptions(buildFreightTemplateOptions([], nextForm.freightTemplateId));
    setEditOpen(true);
    void loadFreightTemplates(record.id, nextForm.freightTemplateId);
  }

  function validateForm() {
    if (!editForm.shopName.trim()) return '店铺名称不能为空';
    if (!editForm.shopId.trim()) return '店铺ID不能为空';
    if (!editForm.id && !editForm.token?.trim()) return '产品 TOKEN 不能为空';
    if (!editForm.appId) return '产品应用不能为空';
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
        orderToken: editForm.orderToken?.trim() || undefined,
        dianxiaomiCookie: editForm.dianxiaomiCookie?.trim() || undefined,
        dianxiaomiShopId: editForm.dianxiaomiShopId?.trim() || undefined,
        appId: editForm.appId,
        orderAppId: editForm.orderAppId ?? undefined,
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
      key: 'shop',
      width: 240,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{record.shopName || '-'}</Typography.Text>
          <Typography.Text type="secondary">{record.shopId || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '产品凭证',
      key: 'productCredential',
      width: 260,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{record.productAppName || '-'}</Typography.Text>
          <Typography.Text type="secondary">{record.productTokenMasked || '未设置'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '订单凭证',
      key: 'orderCredential',
      width: 260,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{record.orderAppName || '沿用产品应用'}</Typography.Text>
          <Typography.Text type="secondary">{record.orderTokenMasked || '沿用产品 TOKEN'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '店小秘',
      key: 'dianxiaomi',
      width: 180,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{record.dianxiaomiCookieMasked ? '已配置 Cookie' : '未配置'}</Typography.Text>
          <Typography.Text type="secondary">店铺ID：{record.dianxiaomiShopId || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '发布配置',
      key: 'publishConfig',
      width: 420,
      render: (_, record) => (
        <Space size={[6, 6]} wrap>
          <Tag>站点 {record.siteId ?? '-'}</Tag>
          <Tag>仓库 {record.warehouseId || '-'}</Tag>
          <Tag>
            库存 {record.defaultStock ?? '-'} / {record.maxStock ?? '-'}
          </Tag>
          <Tag>模板 {record.freightTemplateId || '-'}</Tag>
        </Space>
      ),
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
        message="发布与订单凭证已分离"
        description="产品上架继续使用产品应用与产品 TOKEN；订单同步可单独配置订单应用与订单 TOKEN，不填时会自动回退到产品凭证。店小秘 Cookie 用于按 PO 自动查询包裹号并联动浩远物流。"
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
          scroll={{ x: 1500 }}
        />
      </Card>

      <Modal
        open={editOpen}
        title={editForm.id ? '编辑店铺' : '新增店铺'}
        confirmLoading={saving}
        width={920}
        onOk={() => void save()}
        onCancel={() => setEditOpen(false)}
      >
        <Form layout="vertical">
          <Typography.Title level={5}>基础信息</Typography.Title>
          <Form.Item label="店铺名称" required>
            <Input value={editForm.shopName} onChange={(event) => updateForm('shopName', event.target.value)} />
          </Form.Item>
          <Form.Item label="店铺ID" required>
            <Input value={editForm.shopId} onChange={(event) => updateForm('shopId', event.target.value)} />
          </Form.Item>

          <Divider />

          <Typography.Title level={5}>产品凭证</Typography.Title>
          <Form.Item label="产品应用" required>
            <Select
              value={editForm.appId}
              onChange={(value) => updateForm('appId', value)}
              options={productApps.map((item) => ({ value: item.id, label: appLabel(item) }))}
              loading={appsLoading}
              showSearch
              optionFilterProp="label"
              placeholder="请选择产品应用"
            />
          </Form.Item>
          <Form.Item label="产品 TOKEN" required={!editForm.id}>
            <Input.Password
              value={editForm.token}
              onChange={(event) => updateForm('token', event.target.value)}
              placeholder="新增必填；编辑留空表示不修改"
              autoComplete="new-password"
            />
          </Form.Item>

          <Divider />

          <Typography.Title level={5}>订单凭证</Typography.Title>
          <Form.Item label="订单应用">
            <Select
              allowClear
              value={editForm.orderAppId ?? undefined}
              onChange={(value) => updateForm('orderAppId', value ?? null)}
              options={orderApps.map((item) => ({ value: item.id, label: appLabel(item) }))}
              loading={appsLoading}
              showSearch
              optionFilterProp="label"
              placeholder="不选则沿用产品应用"
            />
          </Form.Item>
          <Form.Item label="订单 TOKEN">
            <Input.Password
              value={editForm.orderToken}
              onChange={(event) => updateForm('orderToken', event.target.value)}
              placeholder="留空则沿用产品 TOKEN"
              autoComplete="new-password"
            />
          </Form.Item>

          <Divider />

          <Typography.Title level={5}>店小秘配置</Typography.Title>
          <Form.Item label="店小秘 Cookie">
            <Input.TextArea
              rows={5}
              value={editForm.dianxiaomiCookie}
              onChange={(event) => updateForm('dianxiaomiCookie', event.target.value)}
              placeholder="登录店小秘后，将整段 cookie 粘贴到这里；留空则不启用 PO -> 店小秘单号自动查询"
              autoComplete="off"
            />
          </Form.Item>
          <Form.Item label="店小秘店铺ID" extra="配置后会按这个店小秘 shopId 拉取订单；为空则跳过店小秘订单同步。">
            <Input
              value={editForm.dianxiaomiShopId}
              onChange={(event) => updateForm('dianxiaomiShopId', event.target.value)}
              placeholder="例如：8244765"
              autoComplete="off"
            />
          </Form.Item>

          <Divider />

          <Typography.Title level={5}>发布配置</Typography.Title>
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
            <Input value={editForm.originRegion1ShortName} onChange={(event) => updateForm('originRegion1ShortName', event.target.value)} />
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
          <Form.Item
            label="运费模板"
            required
            extra={editForm.id ? '模板列表按当前店铺凭证从 TEMU 实时查询。' : '新增完成后再编辑，可从 TEMU 拉取该店铺的模板列表。'}
          >
            <div style={{ display: 'flex', gap: 8 }}>
              <Select
                style={{ flex: 1 }}
                value={editForm.freightTemplateId || undefined}
                onChange={(value) => updateForm('freightTemplateId', value)}
                options={freightTemplateSelectOptions}
                loading={freightTemplatesLoading}
                showSearch
                optionFilterProp="label"
                placeholder={editForm.id ? '请选择运费模板' : '新增后可从 TEMU 拉取'}
                notFoundContent={editForm.id ? '暂无可选运费模板' : '请先保存店铺'}
              />
              <Button
                onClick={() => editForm.id && void loadFreightTemplates(editForm.id, editForm.freightTemplateId)}
                loading={freightTemplatesLoading}
                disabled={!editForm.id}
              >
                刷新模板
              </Button>
            </div>
          </Form.Item>
          <Form.Item label="发货时限（秒）" required>
            <InputNumber
              value={editForm.shipmentLimitSecond}
              min={1}
              precision={0}
              style={{ width: '100%' }}
              onChange={(value) => updateForm('shipmentLimitSecond', value ?? null)}
            />
          </Form.Item>
          <Form.Item label="启用状态">
            <Select
              value={editForm.enabled ? 'enabled' : 'disabled'}
              onChange={(value) => updateForm('enabled', value === 'enabled')}
              options={[
                { value: 'enabled', label: '启用' },
                { value: 'disabled', label: '禁用' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default TemuShopsPage;
