import { App, Button, Card, Descriptions, Drawer, Image, Input, InputNumber, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { syncApi } from '@/api/sync';
import { temuShopsApi } from '@/api/temuShops';
import type { SyncGoodsDetailVO, SyncGoodsListItemVO, SyncGoodsPropertyVO, SyncGoodsSkuVO, TemuShopVO } from '@/types/api';
import { formatDateTime, formatPrice, prettyJson } from '@/utils/format';
import { getToken } from '@/utils/request';

const SyncGoodsPage = () => {
  const { message } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: string; label: string }>>([]);
  const [shopId, setShopId] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [skcSiteStatus, setSkcSiteStatus] = useState<number>();
  const [minSupplierPrice, setMinSupplierPrice] = useState<number>();
  const [maxSupplierPrice, setMaxSupplierPrice] = useState<number>();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<SyncGoodsListItemVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<SyncGoodsDetailVO | null>(null);
  const [repairStatus, setRepairStatus] = useState<Record<string, unknown> | null>(null);
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.DEV ? '/api' : '');

  async function loadShops() {
    const res = await temuShopsApi.list({ enabled: true });
    const data = Array.isArray(res.data) ? res.data : [];
    const options = data
      .filter((item: TemuShopVO) => item.shopId && item.shopName)
      .map((item: TemuShopVO) => ({ value: item.shopId, label: item.shopName }));
    setShops(options);
    if (!shopId && options[0]) {
      setShopId(options[0].value);
      await Promise.all([load(1, 20, options[0].value), loadRepairStatus(options[0].value)]);
    }
  }

  async function load(nextPage = page, nextPageSize = pageSize, nextShopId = shopId) {
    if (!nextShopId) {
      return;
    }
    setLoading(true);
    try {
      const res = await syncApi.getGoodsList({
        shopId: nextShopId,
        keyword: keyword.trim() || undefined,
        skcSiteStatus,
        minSupplierPrice,
        maxSupplierPrice,
        page: nextPage,
        pageSize: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载商品失败');
    } finally {
      setLoading(false);
    }
  }

  async function loadRepairStatus(targetShopId = shopId) {
    if (!targetShopId) {
      return;
    }
    try {
      const res = await syncApi.getLatestRepairGoodsDetailsStatus(targetShopId);
      setRepairStatus(res.data || null);
    } catch {
      setRepairStatus(null);
    }
  }

  useEffect(() => {
    void loadShops();
  }, []);

  async function openDetail(record: SyncGoodsListItemVO) {
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const res = await syncApi.getGoodsDetail(record.id);
      setDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  async function repairDetails() {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }
    try {
      await syncApi.repairGoodsDetails(shopId);
      message.success('已发起修复详情任务');
      await loadRepairStatus(shopId);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发起修复失败');
    }
  }

  async function exportCsv() {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }
    try {
      const params = new URLSearchParams();
      params.set('shopId', shopId);
      if (keyword.trim()) params.set('keyword', keyword.trim());
      if (skcSiteStatus !== undefined) params.set('skcSiteStatus', String(skcSiteStatus));
      if (minSupplierPrice !== undefined) params.set('minSupplierPrice', String(minSupplierPrice));
      if (maxSupplierPrice !== undefined) params.set('maxSupplierPrice', String(maxSupplierPrice));

      const response = await fetch(`${apiBaseUrl}/sync/goods/export?${params.toString()}`, {
        headers: {
          Authorization: `Bearer ${getToken()}`,
        },
      });
      if (!response.ok) {
        throw new Error(`导出失败: ${response.status}`);
      }
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `temu-sync-goods-${shopId}.csv`;
      anchor.click();
      window.URL.revokeObjectURL(url);
      message.success('导出已开始');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '导出失败');
    }
  }

  const columns: ColumnsType<SyncGoodsListItemVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '商品',
      key: 'product',
      render: (_, record) => (
        <Space size={12}>
          {record.mainImageUrl ? <Image width={64} height={64} src={record.mainImageUrl} /> : null}
          <Space direction="vertical" size={2}>
            <Typography.Text ellipsis style={{ maxWidth: 260 }}>
              {record.productName || '-'}
            </Typography.Text>
            <Typography.Text type="secondary">
              Product {record.productId ?? '-'} / SKC {record.productSkcId ?? '-'}
            </Typography.Text>
            <Typography.Text type="secondary">{record.extCode || '-'}</Typography.Text>
          </Space>
        </Space>
      ),
    },
    {
      title: '类目',
      key: 'leafCatName',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <span>{record.leafCatName || '-'}</span>
          <Typography.Text type="secondary">{record.leafCatId ?? '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      key: 'meta',
      width: 140,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Tag>{record.skcSiteStatus ?? '-'}</Tag>
          <Tag color={record.matchJitMode ? 'green' : 'default'}>{record.matchJitMode ? 'JIT' : '普通'}</Tag>
        </Space>
      ),
    },
    { title: 'SKU 数', dataIndex: 'skuCount', key: 'skuCount', width: 90 },
    {
      title: '同步时间',
      dataIndex: 'syncedAt',
      key: 'syncedAt',
      width: 180,
      render: (value: SyncGoodsListItemVO['syncedAt']) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_, record) => (
        <Button size="small" onClick={() => void openDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  const skuColumns: ColumnsType<SyncGoodsSkuVO> = [
    { title: 'SKU', dataIndex: 'productSkuId', key: 'productSkuId', width: 120 },
    { title: '货号', dataIndex: 'extCode', key: 'extCode', width: 140 },
    {
      title: '规格',
      key: 'specList',
      render: (_, record) => record.specList?.map((item) => `${item.parentSpecName || item.parentSpecId}:${item.specName || item.specId}`).join(' / ') || '-',
    },
    {
      title: '供货价',
      key: 'supplierPrice',
      width: 120,
      render: (_, record) => formatPrice(record.price?.supplierPrice),
    },
    { title: '库存', dataIndex: 'virtualStock', key: 'virtualStock', width: 100 },
  ];

  const propertyColumns: ColumnsType<SyncGoodsPropertyVO> = [
    { title: 'PID', dataIndex: 'pid', key: 'pid', width: 100 },
    { title: '属性名', dataIndex: 'propName', key: 'propName', width: 180 },
    { title: 'VID', dataIndex: 'vid', key: 'vid', width: 100 },
    { title: '属性值', dataIndex: 'propValue', key: 'propValue' },
    { title: '单位', dataIndex: 'valueUnit', key: 'valueUnit', width: 120 },
  ];

  const summaryDescription = useMemo(() => {
    if (!repairStatus) {
      return '未获取到修复状态';
    }
    return prettyJson(repairStatus);
  }, [repairStatus]);

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Select
            value={shopId}
            onChange={(value) => {
              setShopId(value);
              setPage(1);
              void Promise.all([load(1, pageSize, value), loadRepairStatus(value)]);
            }}
            placeholder="店铺"
            style={{ width: 220 }}
            options={shops}
          />
          <Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="关键词 / 产品ID / 货号" allowClear style={{ width: 220 }} />
          <Select value={skcSiteStatus} onChange={setSkcSiteStatus} allowClear placeholder="站点状态" style={{ width: 140 }} options={[{ value: 0, label: '待发布' }, { value: 1, label: '已上架' }, { value: 2, label: '已下架' }]} />
          <InputNumber value={minSupplierPrice} onChange={(value) => setMinSupplierPrice(value ?? undefined)} placeholder="最低供货价(分)" style={{ width: 160 }} />
          <InputNumber value={maxSupplierPrice} onChange={(value) => setMaxSupplierPrice(value ?? undefined)} placeholder="最高供货价(分)" style={{ width: 160 }} />
          <Button type="primary" loading={loading} onClick={() => {
            setPage(1);
            void load(1, pageSize, shopId);
          }}>
            查询
          </Button>
          <Button onClick={() => {
            setKeyword('');
            setSkcSiteStatus(undefined);
            setMinSupplierPrice(undefined);
            setMaxSupplierPrice(undefined);
            setPage(1);
            void load(1, 20, shopId);
          }}>
            重置
          </Button>
          <Button onClick={() => void repairDetails()}>修复详情</Button>
          <Button type="primary" ghost onClick={() => void exportCsv()}>
            导出 CSV
          </Button>
        </Space>
      </Card>

      <Card title="修复状态">
        <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{summaryDescription}</Typography.Paragraph>
      </Card>

      <Card>
        <Table<SyncGoodsListItemVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1200 }}
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
            void load(nextPage, nextPageSize, shopId);
          }}
        />
      </Card>

      <Drawer open={detailOpen} width={980} title="TEMU 商品详情" onClose={() => setDetailOpen(false)} loading={detailLoading}>
        {detail ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card size="small">
              <Descriptions column={2} size="small">
                <Descriptions.Item label="商品ID">{detail.productId ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="SKC ID">{detail.productSkcId ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="货号">{detail.extCode || '-'}</Descriptions.Item>
                <Descriptions.Item label="类目">{detail.leafCatName || '-'}</Descriptions.Item>
                <Descriptions.Item label="运费模板">{detail.freightTemplateId || '-'}</Descriptions.Item>
                <Descriptions.Item label="发货时限">{detail.shipmentLimitSecond ?? '-'}</Descriptions.Item>
              </Descriptions>
            </Card>
            <Card size="small" title="SKU 列表">
              <Table<SyncGoodsSkuVO> rowKey="id" columns={skuColumns} dataSource={detail.skuList || []} pagination={false} />
            </Card>
            <Card size="small" title="属性列表">
              <Table<SyncGoodsPropertyVO> rowKey={(record) => `${record.pid}-${record.vid}`} columns={propertyColumns} dataSource={detail.propertyList || []} pagination={false} />
            </Card>
            <Card size="small" title="原始类目 JSON">
              <pre style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{prettyJson(detail.categoriesJson)}</pre>
            </Card>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
};

export default SyncGoodsPage;
