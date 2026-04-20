import {
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Image,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { syncApi } from '@/api/sync';
import { temuShopsApi } from '@/api/temuShops';
import type {
  SyncGoodsDetailVO,
  SyncGoodsListItemVO,
  SyncGoodsPriceChangeVO,
  SyncGoodsPropertyVO,
  SyncGoodsSitePriceVO,
  SyncGoodsSkuVO,
  TemuShopVO,
} from '@/types/api';
import { formatDateTime, formatTimestamp, prettyJson } from '@/utils/format';
import { getToken } from '@/utils/request';
import { loadStoredShopFilter, resolveStoredShopFilter, saveStoredShopFilter } from '@/utils/shopFilter';

const SHOP_FILTER_STORAGE_KEY = 'sync-goods';

function boolText(value?: boolean | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  return value ? '是' : '否';
}

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }
  return `¥${(numeric / 100).toFixed(2)}`;
}

function formatPriceRange(minValue?: number | null, maxValue?: number | null) {
  if (minValue === null || minValue === undefined) {
    return '-';
  }
  if (maxValue === null || maxValue === undefined) {
    return formatMoney(minValue);
  }
  const minText = formatMoney(minValue);
  const maxText = formatMoney(maxValue);
  return minText === maxText ? minText : `${minText} ~ ${maxText}`;
}

function formatCategories(raw?: string | null) {
  if (!raw) {
    return '-';
  }
  try {
    const parsed = JSON.parse(raw) as Record<string, { catName?: string }>;
    const names: string[] = [];
    for (let index = 1; index <= 10; index += 1) {
      const item = parsed[`cat${index}`];
      if (item?.catName) {
        names.push(item.catName);
      }
    }
    return names.length ? names.join(' / ') : '-';
  } catch {
    return raw;
  }
}

function formatVolume(record: Pick<SyncGoodsSkuVO, 'lengthMm' | 'widthMm' | 'heightMm'>) {
  const values = [record.lengthMm, record.widthMm, record.heightMm];
  return values.every((item) => item !== null && item !== undefined) ? `${values[0]} x ${values[1]} x ${values[2]} mm` : '-';
}

function skcSiteStatusTag(value?: number | null) {
  return value === 1 ? <Tag color="green">已加站</Tag> : <Tag>未加站</Tag>;
}

function renderSitePrices(sitePrices?: SyncGoodsSitePriceVO[]) {
  if (!sitePrices?.length) {
    return '-';
  }
  return sitePrices
    .map((sitePrice) => `站点${sitePrice.siteId ?? '-'} ${formatMoney(sitePrice.supplierPrice)} / 状态${sitePrice.priceReviewStatus ?? '-'}`)
    .join('\n');
}

const SyncGoodsPage = () => {
  const { message } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: string; label: string }>>([]);
  const [shopId, setShopId] = useState<string | undefined>(() => loadStoredShopFilter(SHOP_FILTER_STORAGE_KEY));
  const [keyword, setKeyword] = useState('');
  const [skcSiteStatus, setSkcSiteStatus] = useState<number | undefined>(1);
  const [minSupplierPrice, setMinSupplierPrice] = useState<number>();
  const [maxSupplierPrice, setMaxSupplierPrice] = useState<number>();
  const [loading, setLoading] = useState(false);
  const [clearing, setClearing] = useState(false);
  const [rows, setRows] = useState<SyncGoodsListItemVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<SyncGoodsDetailVO | null>(null);
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';

  async function loadShops() {
    const res = await temuShopsApi.list({ enabled: true });
    const data = Array.isArray(res.data) ? res.data : [];
    const options = data
      .filter((item: TemuShopVO) => item.shopId && item.shopName)
      .map((item: TemuShopVO) => ({ value: item.shopId, label: `${item.shopName} ${item.shopId}` }));
    setShops(options);
    const nextShopId = resolveStoredShopFilter(options, shopId);
    if (!nextShopId) {
      setShopId(undefined);
      saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, undefined);
      return;
    }
    setShopId(nextShopId);
    saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, nextShopId);
    await load(1, 20, nextShopId, keyword, skcSiteStatus, minSupplierPrice, maxSupplierPrice);
  }

  async function load(
    nextPage = page,
    nextPageSize = pageSize,
    nextShopId = shopId,
    nextKeyword = keyword,
    nextSkcSiteStatus = skcSiteStatus,
    nextMinSupplierPrice = minSupplierPrice,
    nextMaxSupplierPrice = maxSupplierPrice,
  ) {
    if (!nextShopId) {
      return;
    }

    setLoading(true);
    try {
      const res = await syncApi.getGoodsList({
        shopId: nextShopId,
        keyword: nextKeyword.trim() || undefined,
        skcSiteStatus: nextSkcSiteStatus,
        minSupplierPrice: nextMinSupplierPrice,
        maxSupplierPrice: nextMaxSupplierPrice,
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

  useEffect(() => {
    void loadShops();
  }, []);

  async function openDetail(record: SyncGoodsListItemVO) {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetail(null);
    try {
      const res = await syncApi.getGoodsDetail(record.id);
      setDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      setDetailLoading(false);
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

  function clearShopData() {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }
    Modal.confirm({
      title: '确认清空当前店铺同步数据',
      content: '会删除当前店铺已同步的商品、SKU、规格、条码、站点、属性、生命周期、供货价及相关同步数据，仅清理本系统本地数据，不会删除 TEMU 平台商品。',
      okText: '确认清空',
      cancelText: '取消',
      okButtonProps: { danger: true },
      width: 680,
      async onOk() {
        setClearing(true);
        try {
          await syncApi.clearShopSyncData(shopId);
          setDetailOpen(false);
          setDetail(null);
          message.success('同步数据已清空');
          await load(1, pageSize, shopId, keyword, skcSiteStatus, minSupplierPrice, maxSupplierPrice);
          setPage(1);
        } catch (error) {
          message.error(error instanceof Error ? error.message : '清空失败');
          throw error;
        } finally {
          setClearing(false);
        }
      },
    });
  }

  const listColumns: ColumnsType<SyncGoodsListItemVO> = [
    {
      title: '图片',
      dataIndex: 'mainImageUrl',
      key: 'mainImageUrl',
      width: 84,
      render: (value: SyncGoodsListItemVO['mainImageUrl']) =>
        value ? (
          <Image src={value} width={54} height={54} style={{ borderRadius: 8, objectFit: 'cover' }} />
        ) : (
          <Typography.Text type="secondary">-</Typography.Text>
        ),
    },
    {
      title: '商品名称',
      key: 'productName',
      render: (_, record) => (
        <Typography.Link ellipsis onClick={() => void openDetail(record)}>
          {record.productName || '-'}
        </Typography.Link>
      ),
    },
    {
      title: '商品信息',
      key: 'identity',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>商品ID: {record.productId ?? '-'}</Typography.Text>
          <Typography.Text>SKC ID: {record.productSkcId ?? '-'}</Typography.Text>
          <Typography.Text type="secondary">外部编码: {record.extCode || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '供货价',
      key: 'site100SupplierPriceRange',
      width: 140,
      render: (_, record) => formatPriceRange(record.site100MinSupplierPrice, record.site100MaxSupplierPrice),
    },
    {
      title: '加站状态',
      dataIndex: 'skcSiteStatus',
      key: 'skcSiteStatus',
      width: 100,
      render: (value: SyncGoodsListItemVO['skcSiteStatus']) => skcSiteStatusTag(value),
    },
    {
      title: '叶子类目',
      key: 'leafCatName',
      width: 160,
      render: (_, record) => record.leafCatName || '-',
    },
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
      width: 90,
      fixed: 'right',
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => void openDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  const skuColumns: ColumnsType<SyncGoodsSkuVO> = [
    {
      title: 'SKU ID',
      dataIndex: 'productSkuId',
      key: 'productSkuId',
      width: 120,
      render: (value: SyncGoodsSkuVO['productSkuId']) => value ?? '-',
    },
    {
      title: '外部编码',
      dataIndex: 'extCode',
      key: 'extCode',
      width: 120,
      render: (value: SyncGoodsSkuVO['extCode']) => value || '-',
    },
    {
      title: '规格',
      key: 'specList',
      width: 220,
      render: (_, record) =>
        record.specList?.length ? (
          <Space direction="vertical" size={2}>
            {record.specList.map((item) => (
              <Typography.Text key={`${record.id}-${item.specId}-${item.parentSpecId}`}>
                {item.parentSpecName || item.parentSpecId}: {item.specName || item.specId}
              </Typography.Text>
            ))}
          </Space>
        ) : (
          '-'
        ),
    },
    {
      title: '重量',
      dataIndex: 'weightMg',
      key: 'weightMg',
      width: 100,
      render: (value: SyncGoodsSkuVO['weightMg']) => (value !== null && value !== undefined ? `${value} mg` : '-'),
    },
    {
      title: '尺寸',
      key: 'size',
      width: 180,
      render: (_, record) => formatVolume(record),
    },
    {
      title: '库存',
      dataIndex: 'virtualStock',
      key: 'virtualStock',
      width: 80,
      render: (value: SyncGoodsSkuVO['virtualStock']) => value ?? '-',
    },
    {
      title: '敏感 / 易碎',
      key: 'riskFlags',
      width: 110,
      render: (_, record) => `${boolText(record.isSensitive)} / ${boolText(record.isFragile)}`,
    },
    {
      title: '发货模式',
      dataIndex: 'shippingMode',
      key: 'shippingMode',
      width: 90,
      render: (value: SyncGoodsSkuVO['shippingMode']) => value ?? '-',
    },
    {
      title: '币种',
      key: 'currencyType',
      width: 80,
      render: (_, record) => record.price?.currencyType || '-',
    },
    {
      title: '供货价',
      key: 'supplierPrice',
      width: 100,
      render: (_, record) => formatMoney(record.price?.supplierPrice),
    },
    {
      title: '站点价格',
      key: 'sitePrices',
      width: 260,
      render: (_, record) => (
        <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
          {renderSitePrices(record.price?.sitePrices)}
        </Typography.Paragraph>
      ),
    },
  ];

  const propertyColumns: ColumnsType<SyncGoodsPropertyVO> = [
    { title: '属性ID', dataIndex: 'pid', key: 'pid', width: 90, render: (value: SyncGoodsPropertyVO['pid']) => value ?? '-' },
    { title: '属性名', dataIndex: 'propName', key: 'propName', width: 180, render: (value: SyncGoodsPropertyVO['propName']) => value || '-' },
    { title: '值ID', dataIndex: 'vid', key: 'vid', width: 90, render: (value: SyncGoodsPropertyVO['vid']) => value ?? '-' },
    { title: '属性值', dataIndex: 'propValue', key: 'propValue', render: (value: SyncGoodsPropertyVO['propValue']) => value || '-' },
    { title: '单位', dataIndex: 'valueUnit', key: 'valueUnit', width: 90, render: (value: SyncGoodsPropertyVO['valueUnit']) => value || '-' },
  ];

  const priceChangeColumns: ColumnsType<SyncGoodsPriceChangeVO> = [
    {
      title: 'SKU ID',
      dataIndex: 'productSkuId',
      key: 'productSkuId',
      width: 120,
      render: (value: SyncGoodsPriceChangeVO['productSkuId']) => value ?? '-',
    },
    {
      title: '站点',
      key: 'siteName',
      width: 120,
      render: (_, record) => record.siteName || record.siteId || '-',
    },
    {
      title: '原价格',
      dataIndex: 'oldSupplierPrice',
      key: 'oldSupplierPrice',
      width: 110,
      render: (value: SyncGoodsPriceChangeVO['oldSupplierPrice']) => formatMoney(value),
    },
    {
      title: '新价格',
      dataIndex: 'newSupplierPrice',
      key: 'newSupplierPrice',
      width: 110,
      render: (value: SyncGoodsPriceChangeVO['newSupplierPrice']) => formatMoney(value),
    },
    {
      title: '变更时间',
      dataIndex: 'changedAt',
      key: 'changedAt',
      width: 180,
      render: (value: SyncGoodsPriceChangeVO['changedAt']) => formatDateTime(value),
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
              saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, value);
              setPage(1);
              void load(1, pageSize, value, keyword, skcSiteStatus, minSupplierPrice, maxSupplierPrice);
            }}
            placeholder="店铺"
            style={{ width: 220 }}
            options={shops}
          />
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="商品名称 / 商品ID / 商品外部编码 / SKC ID / SKU ID / SKU外部编码"
            allowClear
            style={{ width: 360 }}
          />
          <Select
            value={skcSiteStatus}
            onChange={setSkcSiteStatus}
            allowClear
            placeholder="加站状态"
            style={{ width: 140 }}
            options={[
              { value: 1, label: '已加站' },
              { value: 0, label: '未加站' },
            ]}
          />
          <InputNumber
            value={minSupplierPrice}
            onChange={(value) => setMinSupplierPrice(value ?? undefined)}
            placeholder="最低供货价(分)"
            style={{ width: 150 }}
          />
          <InputNumber
            value={maxSupplierPrice}
            onChange={(value) => setMaxSupplierPrice(value ?? undefined)}
            placeholder="最高供货价(分)"
            style={{ width: 150 }}
          />
          <Button
            type="primary"
            loading={loading}
            onClick={() => {
              setPage(1);
              void load(1, pageSize, shopId, keyword, skcSiteStatus, minSupplierPrice, maxSupplierPrice);
            }}
          >
            查询
          </Button>
          <Button
            onClick={() => {
              setKeyword('');
              setSkcSiteStatus(1);
              setMinSupplierPrice(undefined);
              setMaxSupplierPrice(undefined);
              setPage(1);
              void load(1, 20, shopId, '', 1, undefined, undefined);
            }}
          >
            重置
          </Button>
          <Button type="primary" ghost onClick={() => void exportCsv()}>
            导出SKU
          </Button>
          <Button danger loading={clearing} onClick={() => clearShopData()}>
            清空同步数据
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<SyncGoodsListItemVO>
          rowKey="id"
          loading={loading}
          columns={listColumns}
          dataSource={rows}
          scroll={{ x: 1180 }}
          tableLayout="fixed"
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
            void load(nextPage, nextPageSize, shopId, keyword, skcSiteStatus, minSupplierPrice, maxSupplierPrice);
          }}
        />
      </Card>

      <Drawer
        open={detailOpen}
        width={1040}
        title="TEMU 商品详情"
        onClose={() => setDetailOpen(false)}
      >
        {detail ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card size="small">
              <Space size={16} align="start" wrap>
                {detail.mainImageUrl ? (
                  <Image
                    width={120}
                    height={120}
                    src={detail.mainImageUrl}
                    style={{ objectFit: 'cover', borderRadius: 12 }}
                  />
                ) : null}
                <Space direction="vertical" size={8} style={{ flex: 1, minWidth: 280 }}>
                  <Typography.Title level={5} style={{ margin: 0 }}>
                    {detail.productName || '-'}
                  </Typography.Title>
                  <Space size={[8, 8]} wrap>
                    {skcSiteStatusTag(detail.skcSiteStatus)}
                    <Tag color={detail.matchJitMode ? 'blue' : 'default'}>JIT 匹配: {boolText(detail.matchJitMode)}</Tag>
                    <Tag color={detail.matchSkcJitMode ? 'blue' : 'default'}>SKC JIT: {boolText(detail.matchSkcJitMode)}</Tag>
                    <Tag color={detail.isSupportPersonalization ? 'purple' : 'default'}>
                      支持定制: {boolText(detail.isSupportPersonalization)}
                    </Tag>
                  </Space>
                </Space>
              </Space>
            </Card>

            <Card size="small">
              <Descriptions bordered size="small" column={2}>
                <Descriptions.Item label="商品ID">{detail.productId ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="SKC ID">{detail.productSkcId ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="商品名称" span={2}>
                  {detail.productName || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="外部编码">{detail.extCode || '-'}</Descriptions.Item>
                <Descriptions.Item label="叶子类目">{detail.leafCatName || '-'}</Descriptions.Item>
                <Descriptions.Item label="加站状态">{detail.skcSiteStatus === 1 ? '已加站' : '未加站'}</Descriptions.Item>
                <Descriptions.Item label="支持定制">{boolText(detail.isSupportPersonalization)}</Descriptions.Item>
                <Descriptions.Item label="选品状态">{detail.selectStatus ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="JIT 匹配">{boolText(detail.matchJitMode)}</Descriptions.Item>
                <Descriptions.Item label="SKC JIT 匹配">{boolText(detail.matchSkcJitMode)}</Descriptions.Item>
                <Descriptions.Item label="JIT 申请状态">{detail.applyJitStatus ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="建议关闭 JIT">{boolText(detail.suggestCloseJit)}</Descriptions.Item>
                <Descriptions.Item label="运费模板">{detail.freightTemplateId || '-'}</Descriptions.Item>
                <Descriptions.Item label="发货时限">{detail.shipmentLimitSecond ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="TEMU 创建时间">{formatTimestamp(detail.temuCreatedAt)}</Descriptions.Item>
                <Descriptions.Item label="同步时间">{formatDateTime(detail.syncedAt)}</Descriptions.Item>
                <Descriptions.Item label="类目路径" span={2}>
                  {formatCategories(detail.categoriesJson)}
                </Descriptions.Item>
              </Descriptions>
            </Card>

            <Card size="small" title="站点">
              <Space wrap>
                {detail.siteList?.length ? (
                  detail.siteList.map((site) => (
                    <Tag key={`${site.siteId}-${site.siteName}`} color="blue">
                      {site.siteName || site.siteId || '-'}
                    </Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">暂无站点</Typography.Text>
                )}
              </Space>
            </Card>

            <Card size="small" title="属性列表">
              <Table<SyncGoodsPropertyVO>
                rowKey={(record, index) => `${record.pid}-${record.vid}-${index ?? 0}`}
                columns={propertyColumns}
                dataSource={detail.propertyList || []}
                pagination={false}
                size="small"
              />
            </Card>

            <Card size="small" title="SKU 列表">
              <Table<SyncGoodsSkuVO>
                rowKey="id"
                columns={skuColumns}
                dataSource={detail.skuList || []}
                pagination={false}
                size="small"
                scroll={{ x: 1460 }}
              />
            </Card>

            <Card size="small" title="SKU 调价历史">
              <Table<SyncGoodsPriceChangeVO>
                rowKey={(record, index) => `${record.productSkuId}-${record.siteId}-${record.changedAt}-${index ?? 0}`}
                columns={priceChangeColumns}
                dataSource={detail.priceChangeList || []}
                pagination={false}
                size="small"
                scroll={{ x: 720 }}
              />
            </Card>

            <Card size="small" title="类目原始 JSON">
              <pre style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{prettyJson(detail.categoriesJson)}</pre>
            </Card>
          </Space>
        ) : (
          <Card loading={detailLoading} />
        )}
      </Drawer>
    </Space>
  );
};

export default SyncGoodsPage;
