import { App, Button, Card, Empty, Input, Select, Space, Spin, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { syncApi } from '@/api/sync';
import { temuShopsApi } from '@/api/temuShops';
import type { SyncConfigItem, TemuShopVO } from '@/types/api';

const SyncConfigPage = () => {
  const { message } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: string; label: string }>>([]);
  const [shopId, setShopId] = useState<string>();
  const [loadingShops, setLoadingShops] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [rows, setRows] = useState<SyncConfigItem[]>([]);

  async function loadShops() {
    setLoadingShops(true);
    try {
      const res = await temuShopsApi.list({ enabled: true });
      const data = Array.isArray(res.data) ? res.data : [];
      const options = data
        .filter((item: TemuShopVO) => item.shopId && item.shopName)
        .map((item: TemuShopVO) => ({ value: item.shopId, label: item.shopName }));
      setShops(options);
      if (!shopId && options[0]) {
        setShopId(options[0].value);
        await loadConfigs(options[0].value);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载店铺失败');
    } finally {
      setLoadingShops(false);
    }
  }

  async function loadConfigs(targetShopId = shopId) {
    if (!targetShopId) {
      return;
    }

    setLoading(true);
    try {
      const res = await syncApi.getConfigs(targetShopId);
      setRows(Array.isArray(res.data.configs) ? res.data.configs : []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载配置失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadShops();
  }, []);

  function updateValue(id: number, value: string) {
    setRows((current) => current.map((item) => (item.id === id ? { ...item, configValue: value } : item)));
  }

  async function save() {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }
    setSaving(true);
    try {
      await syncApi.saveConfigs({
        shopId,
        configs: rows.map((item) => ({
          configKey: item.configKey,
          configValue: item.configValue,
        })),
      });
      message.success('同步配置已保存');
      await loadConfigs(shopId);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  const columns: ColumnsType<SyncConfigItem> = [
    {
      title: '配置项',
      key: 'configKey',
      width: 260,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{record.configKey}</Typography.Text>
          <Typography.Text type="secondary">{record.configDesc || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '配置值',
      key: 'configValue',
      render: (_, record) => (
        <Input.TextArea rows={2} value={record.configValue || ''} onChange={(e) => updateValue(record.id, e.target.value)} />
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Select
            value={shopId}
            onChange={(value) => {
              setShopId(value);
              void loadConfigs(value);
            }}
            loading={loadingShops}
            placeholder="选择店铺"
            style={{ width: 260 }}
            options={shops}
          />
          <Button loading={loading} onClick={() => void loadConfigs()}>
            刷新
          </Button>
          <Button type="primary" loading={saving} onClick={() => void save()}>
            保存配置
          </Button>
        </Space>
      </Card>

      <Card>
        {loading ? (
          <Spin />
        ) : rows.length ? (
          <Table<SyncConfigItem> rowKey="id" columns={columns} dataSource={rows} pagination={false} />
        ) : (
          <Empty description="选择店铺后加载同步配置" />
        )}
      </Card>
    </Space>
  );
};

export default SyncConfigPage;
