import { SaveOutlined } from '@ant-design/icons';
import { App, Button, Card, Image, Input, InputNumber, Select, Space, Table, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { syncApi } from '@/api/sync';
import { temuShopsApi } from '@/api/temuShops';
import type { BatchZeroShopSkuVirtualStockResultVO, ShopSkuItemVO, ShopSkuWarehouseVO, TemuShopVO } from '@/types/api';
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

function buildWarehouseOptionLabel(item: ShopSkuWarehouseVO) {
  const name = item.warehouseName?.trim() || '未命名仓库';
  const tags: string[] = [];
  if (item.siteName?.trim()) {
    tags.push(item.siteName.trim());
  }
  if (item.defaultWarehouse) {
    tags.push('默认仓');
  }
  return tags.length ? `${name} ${item.warehouseId}（${tags.join(' / ')}）` : `${name} ${item.warehouseId}`;
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
  const { message, modal } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: string; label: string }>>([]);
  const [shopId, setShopId] = useState<string | undefined>(() => loadStoredShopFilter(SHOP_FILTER_STORAGE_KEY));
  const [warehouseOptions, setWarehouseOptions] = useState<ShopSkuWarehouseVO[]>([]);
  const [warehouseLoading, setWarehouseLoading] = useState(false);
  const [productSkcIdKeyword, setProductSkcIdKeyword] = useState('');
  const [productSkuIdKeyword, setProductSkuIdKeyword] = useState('');
  const [skuExtCodeKeyword, setSkuExtCodeKeyword] = useState('');
  const [virtualStockGtZero, setVirtualStockGtZero] = useState<boolean | undefined>(undefined);
  const [minSupplierPriceYuan, setMinSupplierPriceYuan] = useState<number | null>(null);
  const [maxSupplierPriceYuan, setMaxSupplierPriceYuan] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [batchZeroLoading, setBatchZeroLoading] = useState(false);
  const [rows, setRows] = useState<ShopSkuItemVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [draftPrices, setDraftPrices] = useState<Record<number, number | null>>({});
  const [savingSkuIds, setSavingSkuIds] = useState<Record<number, boolean>>({});

  async function loadWarehouses(nextShopId = shopId) {
    if (!nextShopId) {
      setWarehouseOptions([]);
      return;
    }

    setWarehouseLoading(true);
    try {
      const res = await syncApi.getShopSkuWarehouses(nextShopId);
      setWarehouseOptions(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      setWarehouseOptions([]);
      message.error(error instanceof Error ? error.message : '加载仓库失败');
    } finally {
      setWarehouseLoading(false);
    }
  }

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
        setWarehouseOptions([]);
        saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, undefined);
        return;
      }
      setShopId(nextShopId);
      saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, nextShopId);
      await Promise.all([
        load(1, pageSize, nextShopId, productSkcIdKeyword, productSkuIdKeyword, skuExtCodeKeyword, virtualStockGtZero, minSupplierPriceYuan, maxSupplierPriceYuan),
        loadWarehouses(nextShopId),
      ]);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载店铺失败');
    }
  }

  function buildQueryFilters(
    nextProductSkcIdKeyword = productSkcIdKeyword,
    nextProductSkuIdKeyword = productSkuIdKeyword,
    nextSkuExtCodeKeyword = skuExtCodeKeyword,
    nextVirtualStockGtZero = virtualStockGtZero,
    nextMinSupplierPriceYuan = minSupplierPriceYuan,
    nextMaxSupplierPriceYuan = maxSupplierPriceYuan,
  ) {
    const productSkcId = parseNumericKeyword(nextProductSkcIdKeyword, 'SKC ID');
    const productSkuId = parseNumericKeyword(nextProductSkuIdKeyword, 'SKUID');
    const minSupplierPrice = yuanToCents(nextMinSupplierPriceYuan);
    const maxSupplierPrice = yuanToCents(nextMaxSupplierPriceYuan);

    if (
      minSupplierPrice !== null &&
      minSupplierPrice !== undefined &&
      maxSupplierPrice !== null &&
      maxSupplierPrice !== undefined &&
      minSupplierPrice > maxSupplierPrice
    ) {
      throw new Error('最低供货价不能大于最高供货价');
    }

    return {
      productSkcId,
      productSkuId,
      skuExtCode: nextSkuExtCodeKeyword.trim() || undefined,
      virtualStockGtZero: nextVirtualStockGtZero ? true : undefined,
      minSupplierPrice: minSupplierPrice ?? undefined,
      maxSupplierPrice: maxSupplierPrice ?? undefined,
    };
  }

  async function load(
    nextPage = page,
    nextPageSize = pageSize,
    nextShopId = shopId,
    nextProductSkcIdKeyword = productSkcIdKeyword,
    nextProductSkuIdKeyword = productSkuIdKeyword,
    nextSkuExtCodeKeyword = skuExtCodeKeyword,
    nextVirtualStockGtZero = virtualStockGtZero,
    nextMinSupplierPriceYuan = minSupplierPriceYuan,
    nextMaxSupplierPriceYuan = maxSupplierPriceYuan,
  ) {
    if (!nextShopId) {
      return;
    }

    let filters: ReturnType<typeof buildQueryFilters>;
    try {
      filters = buildQueryFilters(
        nextProductSkcIdKeyword,
        nextProductSkuIdKeyword,
        nextSkuExtCodeKeyword,
        nextVirtualStockGtZero,
        nextMinSupplierPriceYuan,
        nextMaxSupplierPriceYuan,
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : '查询条件不正确');
      return;
    }

    setLoading(true);
    try {
      const res = await syncApi.getShopSkuList({
        shopId: nextShopId,
        ...filters,
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

  function buildBatchFilterSummary(filters: ReturnType<typeof buildQueryFilters>) {
    const items: string[] = [];
    if (filters.productSkcId !== undefined) {
      items.push(`SKC ID=${filters.productSkcId}`);
    }
    if (filters.productSkuId !== undefined) {
      items.push(`SKUID=${filters.productSkuId}`);
    }
    if (filters.skuExtCode) {
      items.push(`SKU外部编码包含“${filters.skuExtCode}”`);
    }
    if (filters.virtualStockGtZero) {
      items.push('库存>0');
    }
    if (filters.minSupplierPrice !== undefined) {
      items.push(`最低供货价>=${formatMoney(filters.minSupplierPrice)}`);
    }
    if (filters.maxSupplierPrice !== undefined) {
      items.push(`最高供货价<=${formatMoney(filters.maxSupplierPrice)}`);
    }
    return items.length ? items.join('；') : '未设置';
  }

  function showBatchResult(result: BatchZeroShopSkuVirtualStockResultVO, responseMessage?: string) {
    const messages = Array.isArray(result.messages) ? result.messages.filter(Boolean) : [];
    modal.info({
      title: '批量置0结果',
      width: 720,
      content: (
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          <Typography.Text>{responseMessage || '批量置0已执行完成'}</Typography.Text>
          <Typography.Text>
            命中 {result.matchedCount} 个，成功 {result.updatedCount} 个，已是0 {result.alreadyZeroCount} 个，失败 {result.failedCount} 个
          </Typography.Text>
          {messages.length ? (
            <div
              style={{
                maxHeight: 260,
                overflowY: 'auto',
                padding: 12,
                borderRadius: 8,
                background: '#fafafa',
                border: '1px solid #f0f0f0',
              }}
            >
              <Space direction="vertical" size={6} style={{ width: '100%' }}>
                {messages.slice(0, 18).map((item, index) => (
                  <Typography.Text key={`${index}-${item}`} type={item.includes('失败') ? 'danger' : undefined}>
                    {item}
                  </Typography.Text>
                ))}
              </Space>
            </div>
          ) : null}
        </Space>
      ),
    });
  }

  async function handleBatchZeroVirtualStock() {
    if (!shopId) {
      message.warning('请先选择店铺');
      return;
    }

    let filters: ReturnType<typeof buildQueryFilters>;
    try {
      filters = buildQueryFilters();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '筛选条件不正确');
      return;
    }

    const hasBatchFilter = Boolean(
        filters.productSkcId !== undefined ||
        filters.productSkuId !== undefined ||
        filters.skuExtCode ||
        filters.virtualStockGtZero ||
        filters.minSupplierPrice !== undefined ||
        filters.maxSupplierPrice !== undefined,
    );
    if (!hasBatchFilter) {
      message.warning('请至少填写一个筛选条件后再批量置0库存');
      return;
    }
    if (warehouseLoading) {
      message.info('仓库列表加载中，请稍后再试');
      return;
    }
    if (!warehouseOptions.length) {
      message.warning('当前店铺暂无可用仓库，请先同步仓库数据后再批量置0');
      return;
    }

    const warehouseSelectOptions = warehouseOptions.map((item) => ({
      value: item.warehouseId,
      label: buildWarehouseOptionLabel(item),
    }));
    let selectedWarehouseId: string | undefined;

    modal.confirm({
      title: '确认批量置0库存',
      okText: '确认置0',
      okButtonProps: { danger: true },
      cancelText: '取消',
      content: (
        <Space direction="vertical" size={8}>
          <Typography.Paragraph style={{ marginBottom: 0 }}>
            会对当前筛选命中的店铺 SKU 调用库存接口，并按你选择的仓库把匹配到的库存降到 0。
          </Typography.Paragraph>
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="请选择本次批量置0使用的仓库"
            options={warehouseSelectOptions}
            style={{ width: '100%' }}
            onChange={(value) => {
              selectedWarehouseId = value;
            }}
          />
          <Typography.Text type="secondary">当前筛选条件：{buildBatchFilterSummary(filters)}</Typography.Text>
          <Typography.Text type="secondary">如果某些 SKU 提示仓库 ID 错误，换成该 SKU 所属仓库后再执行。</Typography.Text>
          <Typography.Text type="secondary">为了安全起见，这个操作只会处理当前筛选命中的数据，不会只处理当前分页。</Typography.Text>
        </Space>
      ),
      onOk: async () => {
        if (!selectedWarehouseId) {
          message.warning('请先选择仓库');
          return Promise.reject();
        }
        setBatchZeroLoading(true);
        try {
          const res = await syncApi.batchZeroShopSkuVirtualStock({
            shopId,
            ...filters,
            warehouseId: selectedWarehouseId,
          });
          message.success(res.message || '批量置0完成');
          showBatchResult(res.data, res.message);
          await load(page, pageSize, shopId, productSkcIdKeyword, productSkuIdKeyword, skuExtCodeKeyword, virtualStockGtZero, minSupplierPriceYuan, maxSupplierPriceYuan);
        } catch (error) {
          message.error(error instanceof Error ? error.message : '批量置0失败');
          throw error;
        } finally {
          setBatchZeroLoading(false);
        }
      },
    });
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

  const hasBatchFilter = Boolean(
      productSkcIdKeyword.trim() ||
      productSkuIdKeyword.trim() ||
      skuExtCodeKeyword.trim() ||
      virtualStockGtZero === true ||
      (minSupplierPriceYuan !== null && minSupplierPriceYuan !== undefined) ||
      (maxSupplierPriceYuan !== null && maxSupplierPriceYuan !== undefined),
  );

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
              void loadWarehouses(value);
              void load(1, pageSize, value, productSkcIdKeyword, productSkuIdKeyword, skuExtCodeKeyword, virtualStockGtZero, minSupplierPriceYuan, maxSupplierPriceYuan);
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
          <Select
            value={virtualStockGtZero}
            onChange={(value) => setVirtualStockGtZero(value)}
            placeholder="库存筛选"
            allowClear
            style={{ width: 160 }}
            options={[
              { value: true, label: '库存大于0' },
            ]}
          />
          <InputNumber
            min={0}
            precision={2}
            controls={false}
            value={minSupplierPriceYuan ?? undefined}
            onChange={(value) => setMinSupplierPriceYuan(typeof value === 'number' ? value : null)}
            placeholder="最低供货价(元)"
            style={{ width: 180 }}
          />
          <InputNumber
            min={0}
            precision={2}
            controls={false}
            value={maxSupplierPriceYuan ?? undefined}
            onChange={(value) => setMaxSupplierPriceYuan(typeof value === 'number' ? value : null)}
            placeholder="最高供货价(元)"
            style={{ width: 180 }}
          />
          <Button
            type="primary"
            loading={loading}
            onClick={() => {
              setPage(1);
              void load(1, pageSize, shopId, productSkcIdKeyword, productSkuIdKeyword, skuExtCodeKeyword, virtualStockGtZero, minSupplierPriceYuan, maxSupplierPriceYuan);
            }}
          >
            查询
          </Button>
          <Button
            danger
            loading={batchZeroLoading}
            disabled={!shopId || !hasBatchFilter}
            onClick={() => void handleBatchZeroVirtualStock()}
          >
            批量置0库存
          </Button>
          <Button
            onClick={() => {
              setProductSkcIdKeyword('');
              setProductSkuIdKeyword('');
              setSkuExtCodeKeyword('');
              setVirtualStockGtZero(undefined);
              setMinSupplierPriceYuan(null);
              setMaxSupplierPriceYuan(null);
              setPage(1);
              void load(1, pageSize, shopId, '', '', '', undefined, null, null);
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
            void load(nextPage, nextPageSize, shopId, productSkcIdKeyword, productSkuIdKeyword, skuExtCodeKeyword, virtualStockGtZero, minSupplierPriceYuan, maxSupplierPriceYuan);
          }}
        />
      </Card>
    </Space>
  );
};

export default ShopSkuPage;
