import { SaveOutlined } from '@ant-design/icons';
import { App, Button, Card, Image, Input, InputNumber, Select, Space, Table, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { syncApi } from '@/api/sync';
import { temuShopsApi } from '@/api/temuShops';
import type { ShopSkuItemVO, TemuShopVO } from '@/types/api';
import { loadStoredShopFilter, resolveStoredShopFilter, saveStoredShopFilter } from '@/utils/shopFilter';

const SHOP_FILTER_STORAGE_KEY = 'shop-skus';

function centsToYuan(value?: number | null) {
  if (value === null || value === undefined) {
    return null;
  }
  return Number((value / 100).toFixed(2));
}

function yuanToCents(value?: number | null) {
  if (value === null || value === undefined) {
    return null;
  }
  return Math.round(value * 100);
}

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  return `¥${(value / 100).toFixed(2)}`;
}

function hasDraftValue(drafts: Record<number, number | null>, productSkuId?: number | null) {
  if (!productSkuId) {
    return false;
  }
  return Object.prototype.hasOwnProperty.call(drafts, productSkuId);
}

function parseNumericKeyword(raw: string, label: string) {
  const trimmed = raw.trim();
  if (!trimmed) {
    return undefined;
  }
  if (!/^\d+$/.test(trimmed)) {
    throw new Error(`${label} 只能输入数字`);
  }
  return Number(trimmed);
}

const ShopSkuPage = () => {
  const { message } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: string; label: string }>>([]);
  const [shopId, setShopId] = useState<string | undefined>(() => loadStoredShopFilter(SHOP_FILTER_STORAGE_KEY));
  const [productSkcIdKeyword, setProductSkcIdKeyword] = useState('');
  const [productSkuIdKeyword, setProductSkuIdKeyword] = useState('');
  const [skuExtCodeKeyword, setSkuExtCodeKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<ShopSkuItemVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [draftPrices, setDraftPrices] = useState<Record<number, number | null>>({});
  const [savingSkuIds, setSavingSkuIds] = useState<Record<number, boolean>>({});

  async function loadShops() {
    try {
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
      await load(1, pageSize, nextShopId, productSkcIdKeyword, productSkuIdKeyword, skuExtCodeKeyword);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载店铺失败');
    }
  }

  async function load(
    nextPage = page,
    nextPageSize = pageSize,
    nextShopId = shopId,
    nextProductSkcIdKeyword = productSkcIdKeyword,
    nextProductSkuIdKeyword = productSkuIdKeyword,
    nextSkuExtCodeKeyword = skuExtCodeKeyword,
  ) {
    if (!nextShopId) {
      return;
    }

    let productSkcId: number | undefined;
    let productSkuId: number | undefined;
    try {
      productSkcId = parseNumericKeyword(nextProductSkcIdKeyword, 'SKC ID');
      productSkuId = parseNumericKeyword(nextProductSkuIdKeyword, 'SKUID');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '查询条件不正确');
      return;
    }

    setLoading(true);
    try {
      const res = await syncApi.getShopSkuList({
        shopId: nextShopId,
        productSkcId,
        productSkuId,
        skuExtCode: nextSkuExtCodeKeyword.trim() || undefined,
        page: nextPage,
        pageSize: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
      setDraftPrices({});
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载店铺 SKU 失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadShops();
  }, []);

  function resolveEditableValue(record: ShopSkuItemVO) {
    if (record.productSkuId && hasDraftValue(draftPrices, record.productSkuId)) {
      return draftPrices[record.productSkuId];
    }
    return centsToYuan(record.purchasePrice);
  }

  function updateDraftPrice(record: ShopSkuItemVO, value: number | null) {
    if (!record.productSkuId) {
      return;
    }
    setDraftPrices((current) => ({
      ...current,
      [record.productSkuId as number]: value,
    }));
  }

  async function savePurchasePrice(record: ShopSkuItemVO) {
    if (!shopId || !record.productSkuId) {
      return;
    }
    const nextPriceYuan = resolveEditableValue(record);
    const purchasePrice = yuanToCents(nextPriceYuan);

    setSavingSkuIds((current) => ({ ...current, [record.productSkuId as number]: true }));
    try {
      const res = await syncApi.updateShopSkuPurchasePrice(record.productSkuId, {
        shopId,
        purchasePrice,
      });
      setRows((current) => current.map((item) => (item.productSkuId === record.productSkuId ? res.data : item)));
      setDraftPrices((current) => {
        const next = { ...current };
        delete next[record.productSkuId as number];
        return next;
      });
      message.success('采购价已保存');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存采购价失败');
    } finally {
      setSavingSkuIds((current) => ({ ...current, [record.productSkuId as number]: false }));
    }
  }

  const columns: ColumnsType<ShopSkuItemVO> = [
    {
      title: 'SKU图片',
      dataIndex: 'mainImageUrl',
      key: 'mainImageUrl',
      width: 104,
      render: (value: ShopSkuItemVO['mainImageUrl']) =>
        value ? (
          <Image src={value} width={56} height={56} style={{ borderRadius: 10, objectFit: 'cover' }} />
        ) : (
          <Typography.Text type="secondary">-</Typography.Text>
        ),
    },
    {
      title: 'SKUID',
      key: 'productSkuId',
      width: 280,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong copyable={record.productSkuId ? { text: String(record.productSkuId) } : undefined}>
            {record.productSkuId ?? '-'}
          </Typography.Text>
          <Typography.Text type="secondary">SKC ID: {record.productSkcId ?? '-'}</Typography.Text>
          <Typography.Text ellipsis style={{ maxWidth: 240 }}>
            {record.productName || '-'}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '采购价',
      key: 'purchasePrice',
      width: 220,
      render: (_, record) => {
        const editableValue = resolveEditableValue(record);
        const isSaving = Boolean(record.productSkuId && savingSkuIds[record.productSkuId]);
        return (
          <Space direction="vertical" size={4}>
            <Space.Compact>
              <InputNumber
                min={0}
                precision={2}
                controls={false}
                value={editableValue ?? undefined}
                onChange={(value) => updateDraftPrice(record, typeof value === 'number' ? value : null)}
                placeholder="采购价"
                style={{ width: 140 }}
              />
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={isSaving}
                onClick={() => void savePurchasePrice(record)}
              />
            </Space.Compact>
            <Typography.Text type="secondary">
              {record.purchasePrice !== null && record.purchasePrice !== undefined
                ? `已保存 ${formatMoney(record.purchasePrice)}`
                : record.usSiteSupplierPrice !== null && record.usSiteSupplierPrice !== undefined
                  ? `美国站点供货价 ${formatMoney(record.usSiteSupplierPrice)}`
                  : record.referenceSupplierPrice !== null && record.referenceSupplierPrice !== undefined
                    ? `默认供货价 ${formatMoney(record.referenceSupplierPrice)}`
                  : '采购价未配置'}
            </Typography.Text>
          </Space>
        );
      },
    },
    {
      title: '供货价(美国站点)',
      dataIndex: 'usSiteSupplierPrice',
      key: 'usSiteSupplierPrice',
      width: 150,
      render: (value: ShopSkuItemVO['usSiteSupplierPrice']) => formatMoney(value),
    },
    {
      title: 'SKU规格名称',
      dataIndex: 'skuSpecName',
      key: 'skuSpecName',
      width: 320,
      render: (value: ShopSkuItemVO['skuSpecName']) => value || '-',
    },
    {
      title: 'SKU外部编码',
      dataIndex: 'skuExtCode',
      key: 'skuExtCode',
      width: 180,
      render: (value: ShopSkuItemVO['skuExtCode']) => value || '-',
    },
    {
      title: '虚拟库存数量',
      dataIndex: 'virtualStock',
      key: 'virtualStock',
      width: 120,
      render: (value: ShopSkuItemVO['virtualStock']) => value ?? '-',
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
              void load(1, pageSize, value, productSkcIdKeyword, productSkuIdKeyword, skuExtCodeKeyword);
            }}
            placeholder="选择店铺"
            style={{ width: 220 }}
            options={shops}
          />
          <Input
            value={productSkcIdKeyword}
            onChange={(event) => setProductSkcIdKeyword(event.target.value)}
            placeholder="SKC ID，例如 71376687072"
            allowClear
            style={{ width: 220 }}
          />
          <Input
            value={productSkuIdKeyword}
            onChange={(event) => setProductSkuIdKeyword(event.target.value)}
            placeholder="SKUID，例如 85855391884"
            allowClear
            style={{ width: 220 }}
          />
          <Input
            value={skuExtCodeKeyword}
            onChange={(event) => setSkuExtCodeKeyword(event.target.value)}
            placeholder="SKU外部编码"
            allowClear
            style={{ width: 220 }}
          />
          <Button
            type="primary"
            loading={loading}
            onClick={() => {
              setPage(1);
              void load(1, pageSize, shopId, productSkcIdKeyword, productSkuIdKeyword, skuExtCodeKeyword);
            }}
          >
            查询
          </Button>
          <Button
            onClick={() => {
              setProductSkcIdKeyword('');
              setProductSkuIdKeyword('');
              setSkuExtCodeKeyword('');
              setPage(1);
              void load(1, pageSize, shopId, '', '', '');
            }}
          >
            重置
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<ShopSkuItemVO>
          rowKey={(record) => record.productSkuId ?? record.id}
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1390 }}
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
            void load(nextPage, nextPageSize, shopId, productSkcIdKeyword, productSkuIdKeyword, skuExtCodeKeyword);
          }}
        />
      </Card>
    </Space>
  );
};

export default ShopSkuPage;
