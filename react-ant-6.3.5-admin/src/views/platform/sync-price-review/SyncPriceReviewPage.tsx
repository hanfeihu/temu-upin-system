import { App, Button, Card, Drawer, Form, Image, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { Key } from 'react';
import { useEffect, useState } from 'react';
import { syncApi } from '@/api/sync';
import { temuShopsApi } from '@/api/temuShops';
import type { PriceReviewBatchPayload, PriceReviewOrderVO, PriceReviewSkuVO, TemuShopVO } from '@/types/api';
import { formatDateTime, formatPrice, safeJsonParse } from '@/utils/format';
import { loadStoredShopFilter, resolveStoredShopFilter, saveStoredShopFilter } from '@/utils/shopFilter';

interface EditableReviewOrder extends PriceReviewOrderVO {
  skuList: Array<PriceReviewSkuVO & { editNewPrice?: number | null }>;
}

const orderStatusMap: Record<number, string> = {
  1: '待处理',
  2: '处理中',
  3: '已完成',
  4: '已关闭',
};

const reviewColorMap: Record<string, string> = {
  APPROVE: 'green',
  APPROVED: 'green',
  REJECT: 'red',
  REJECTED: 'red',
  PENDING: 'processing',
};

function summarizePurchasePrice(skus?: PriceReviewSkuVO[] | null) {
  const prices = Array.from(
    new Set(
      (skus || [])
        .map((item) => item.purchasePrice)
        .filter((item): item is number => item !== null && item !== undefined),
    ),
  );

  if (!prices.length) {
    return '采购价未配置';
  }

  if (prices.length === 1) {
    return `采购价（${formatPrice(prices[0])}）`;
  }

  return `采购价（${formatPrice(Math.min(...prices))} 起）`;
}

const SHOP_FILTER_STORAGE_KEY = 'sync-price-review';

const SyncPriceReviewPage = () => {
  const { message } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: string; label: string }>>([]);
  const [shopId, setShopId] = useState<string | undefined>(() => loadStoredShopFilter(SHOP_FILTER_STORAGE_KEY));
  const [orderStatus, setOrderStatus] = useState<number>(1);
  const [reviewAction, setReviewAction] = useState<string>('PENDING');
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<PriceReviewOrderVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<PriceReviewOrderVO | null>(null);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [reviewing, setReviewing] = useState(false);
  const [reviewMode, setReviewMode] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [reviewTargets, setReviewTargets] = useState<EditableReviewOrder[]>([]);
  const [reasonType, setReasonType] = useState<number>(1);
  const [reasonText, setReasonText] = useState('');
  const [externalLinksText, setExternalLinksText] = useState('');

  async function loadShops() {
    const res = await temuShopsApi.list({ enabled: true });
    const data = Array.isArray(res.data) ? res.data : [];
    const options = data
      .filter((item: TemuShopVO) => item.shopId && item.shopName)
      .map((item: TemuShopVO) => ({ value: item.shopId, label: item.shopName }));
    setShops(options);
    const nextShopId = resolveStoredShopFilter(options, shopId);
    if (!nextShopId) {
      setShopId(undefined);
      saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, undefined);
      return;
    }
    setShopId(nextShopId);
    saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, nextShopId);
    await load(1, 20, nextShopId, orderStatus, reviewAction);
  }

  async function load(nextPage = page, nextPageSize = pageSize, nextShopId = shopId, nextOrderStatus = orderStatus, nextReviewAction = reviewAction) {
    if (!nextShopId) {
      return;
    }
    setLoading(true);
    try {
      const res = await syncApi.getPriceReviewList({
        shopId: nextShopId,
        orderStatus: nextOrderStatus,
        reviewAction: nextReviewAction,
        page: nextPage,
        pageSize: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
      setSelectedRowKeys((current) => current.filter((key) => (res.data.content || []).some((row) => row.id === key)));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadShops();
  }, []);

  function formatSites(value: string | null) {
    const list = safeJsonParse<string[]>(value, []);
    return list.length ? list.join(' / ') : '-';
  }

  function reviewText(value?: string | null) {
    return (
      {
        APPROVE: '已同意',
        APPROVED: '已同意',
        REJECT: '已拒绝',
        REJECTED: '已拒绝',
        PENDING: '待处理',
      }[value || 'PENDING'] || value || '待处理'
    );
  }

  async function openDetail(record: PriceReviewOrderVO) {
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const res = await syncApi.getPriceReviewDetail(record.id);
      setDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  async function loadReviewDetails(targets: PriceReviewOrderVO[]) {
    const details = await Promise.all(targets.map(async (item) => (await syncApi.getPriceReviewDetail(item.id)).data));
    return details.map((item) => ({
      ...item,
      skuList: (item.skuList || []).map((sku) => ({
        ...sku,
        editNewPrice: sku.newPrice ?? null,
      })),
    }));
  }

  async function startReview(records: PriceReviewOrderVO[], mode: 'APPROVE' | 'REJECT') {
    if (!records.length) {
      message.warning('请先选择核价单');
      return;
    }
    setReviewMode(mode);
    setReasonText('');
    setExternalLinksText('');
    setReasonType(1);
    if (mode === 'APPROVE') {
      setReviewTargets(records.map((item) => ({ ...item, skuList: item.skuList || [] })));
      setReviewOpen(true);
      return;
    }
    setReviewing(true);
    try {
      const details = await loadReviewDetails(records);
      setReviewTargets(details);
      setReviewOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载拒绝详情失败');
    } finally {
      setReviewing(false);
    }
  }

  function updateSkuPrice(orderId: number, productSkuId: number | null, value: number | null) {
    setReviewTargets((current) =>
      current.map((order) =>
        order.id === orderId
          ? {
              ...order,
              skuList: order.skuList.map((sku) =>
                sku.productSkuId === productSkuId ? { ...sku, editNewPrice: value } : sku,
              ),
            }
          : order,
      ),
    );
  }

  function buildPayload(): PriceReviewBatchPayload {
    if (!shopId) {
      throw new Error('请选择店铺');
    }

    const payload: PriceReviewBatchPayload = {
      shopId,
      orderIds: reviewTargets.map((item) => item.id),
      action: reviewMode,
    };

    if (reviewMode === 'REJECT') {
      const componentList = reasonText
        .split('\n')
        .map((item) => item.trim())
        .filter(Boolean)
        .map((reason) => ({ reason, type: reasonType }));
      const externalLinkList = externalLinksText
        .split('\n')
        .map((item) => item.trim())
        .filter(Boolean);
      const rejectPrices = reviewTargets.flatMap((item) =>
        item.skuList
          .filter((sku) => sku.editNewPrice !== undefined && sku.editNewPrice !== null && `${sku.editNewPrice}` !== '')
          .map((sku) => ({
            orderId: item.id,
            productSkuId: Number(sku.productSkuId),
            newPrice: Number(sku.editNewPrice),
          })),
      );

      if (!componentList.length && !externalLinkList.length && !rejectPrices.length) {
        throw new Error('拒绝时请至少填写原因、外链或新的申报价');
      }

      if (componentList.length || externalLinkList.length) {
        payload.bargainReasonList = [{ componentList, externalLinkList }];
      }
      payload.rejectPrices = rejectPrices;
    }

    return payload;
  }

  async function submitReview() {
    setReviewing(true);
    try {
      await syncApi.batchReviewPrice(buildPayload());
      message.success(reviewMode === 'APPROVE' ? '审核同意完成' : '审核拒绝完成');
      setReviewOpen(false);
      setSelectedRowKeys([]);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '提交失败');
    } finally {
      setReviewing(false);
    }
  }

  const columns: ColumnsType<PriceReviewOrderVO> = [
    {
      title: '订单信息',
      key: 'orderInfo',
      width: 250,
      render: (_, record) => (
        <Space direction="vertical" size={4}>
          <Typography.Text strong>{record.orderId || '-'}</Typography.Text>
          <Typography.Text type="secondary">{formatSites(record.siteNamesJson)}</Typography.Text>
          <Space size={[6, 6]} wrap>
            <Tag>{orderStatusMap[record.orderStatus || 0] || record.orderStatus || '-'}</Tag>
            <Tag color={reviewColorMap[record.reviewAction || 'PENDING'] || 'processing'}>{reviewText(record.reviewAction)}</Tag>
            <Tag color={record.canBargain ? 'blue' : 'default'}>{record.canBargain ? '可议价' : '不可议价'}</Tag>
          </Space>
          <Typography.Text type="secondary">同步 {formatDateTime(record.syncedAt)}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'SKU 信息',
      key: 'skuInfo',
      render: (_, record) => (
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          {(record.skuList || []).slice(0, 2).map((sku) => (
            <Space key={`${record.id}-${sku.productSkuId}`} size={10} align="start">
              {sku.imageUrl ? <Image width={38} height={38} src={sku.imageUrl} /> : null}
              <Space direction="vertical" size={2}>
                <Typography.Text>SKU {sku.productSkuId || '-'}</Typography.Text>
                <Typography.Text type="secondary">{sku.specInfo || '-'}</Typography.Text>
              </Space>
            </Space>
          ))}
          {(record.skuList || []).length > 2 ? <Typography.Text type="secondary">其余 {(record.skuList || []).length - 2} 个 SKU 请在详情中查看</Typography.Text> : null}
        </Space>
      ),
    },
    {
      title: '价格',
      key: 'priceInfo',
      width: 160,
      render: (_, record) => (
        <Space direction="vertical" size={2} style={{ alignItems: 'center', width: '100%' }}>
          <Typography.Text strong>{formatPrice(record.suggestSupplyPrice)}</Typography.Text>
          <Typography.Text type="secondary">→ {formatPrice(record.supplyPrice)}</Typography.Text>
          <Typography.Text type="secondary">{summarizePurchasePrice(record.skuList || [])}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          <Button size="small" type="link" onClick={() => void startReview([record], 'APPROVE')}>
            同意
          </Button>
          <Button size="small" type="link" danger onClick={() => void startReview([record], 'REJECT')}>
            拒绝
          </Button>
        </Space>
      ),
    },
  ];

  const detailSkuColumns: ColumnsType<PriceReviewSkuVO> = [
    { title: 'SKU', dataIndex: 'productSkuId', key: 'productSkuId', width: 120 },
    { title: '编码', dataIndex: 'extCode', key: 'extCode', width: 120 },
    { title: '规格', dataIndex: 'specInfo', key: 'specInfo' },
    { title: '采购价', key: 'purchasePrice', width: 120, render: (_, record) => record.purchasePrice !== null && record.purchasePrice !== undefined ? formatPrice(record.purchasePrice) : '未配置' },
    { title: '当前供货价', key: 'currentSupplyPrice', width: 140, render: (_, record) => formatPrice(record.currentSupplyPrice) },
    { title: '建议新价', key: 'newPrice', width: 140, render: (_, record) => formatPrice(record.newPrice) },
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
              void load(1, pageSize, value, orderStatus, reviewAction);
            }}
            placeholder="店铺"
            style={{ width: 220 }}
            options={shops}
          />
          <Select value={orderStatus} onChange={setOrderStatus} style={{ width: 150 }} options={[{ value: 1, label: '待处理' }, { value: 2, label: '处理中' }, { value: 3, label: '已完成' }]} />
          <Select value={reviewAction} onChange={setReviewAction} style={{ width: 150 }} options={[{ value: 'PENDING', label: '待处理' }, { value: 'APPROVE', label: '已同意' }, { value: 'REJECT', label: '已拒绝' }]} />
          <Button type="primary" loading={loading} onClick={() => {
            setPage(1);
            void load(1, pageSize, shopId, orderStatus, reviewAction);
          }}>
            查询
          </Button>
          <Button onClick={() => {
            setOrderStatus(1);
            setReviewAction('PENDING');
            setPage(1);
            void load(1, pageSize, shopId, 1, 'PENDING');
          }}>
            重置
          </Button>
          <Button disabled={!selectedRowKeys.length} onClick={() => void startReview(rows.filter((item) => selectedRowKeys.includes(item.id)), 'APPROVE')}>
            批量同意 ({selectedRowKeys.length})
          </Button>
          <Button danger disabled={!selectedRowKeys.length} onClick={() => void startReview(rows.filter((item) => selectedRowKeys.includes(item.id)), 'REJECT')}>
            批量拒绝 ({selectedRowKeys.length})
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<PriceReviewOrderVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
          }}
          scroll={{ x: 1280 }}
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
            void load(nextPage, nextPageSize, shopId, orderStatus, reviewAction);
          }}
        />
      </Card>

      <Drawer open={detailOpen} width={920} title="核价单详情" onClose={() => setDetailOpen(false)} loading={detailLoading}>
        {detail ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card size="small">
              <Space direction="vertical" size={4}>
                <span>订单ID: {detail.orderId || '-'}</span>
                <span>站点: {formatSites(detail.siteNamesJson)}</span>
                <span>申报价: {formatPrice(detail.supplyPrice)}</span>
                <span>建议价: {formatPrice(detail.suggestSupplyPrice)}</span>
                <span>{summarizePurchasePrice(detail.skuList || [])}</span>
                <span>审核状态: {reviewText(detail.reviewAction)}</span>
              </Space>
            </Card>
            <Card size="small" title="SKU 列表">
              <Table<PriceReviewSkuVO> rowKey={(record) => `${record.id}-${record.productSkuId}`} columns={detailSkuColumns} dataSource={detail.skuList || []} pagination={false} />
            </Card>
          </Space>
        ) : null}
      </Drawer>

      <Modal
        open={reviewOpen}
        title={reviewMode === 'APPROVE' ? '核价单同意' : '核价单拒绝'}
        width={980}
        confirmLoading={reviewing}
        onOk={() => void submitReview()}
        onCancel={() => setReviewOpen(false)}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Typography.Text>本次共处理 {reviewTargets.length} 个订单。</Typography.Text>
          {reviewMode === 'REJECT' ? (
            <>
              <Form layout="vertical">
                <Form.Item label="原因类型">
                  <Select value={reasonType} onChange={setReasonType} options={[{ value: 1, label: '一般原因' }, { value: 2, label: '价格原因' }, { value: 3, label: '其他原因' }]} />
                </Form.Item>
                <Form.Item label="拒绝原因，每行一条">
                  <Input.TextArea rows={4} value={reasonText} onChange={(e) => setReasonText(e.target.value)} />
                </Form.Item>
                <Form.Item label="外部链接，每行一条">
                  <Input.TextArea rows={3} value={externalLinksText} onChange={(e) => setExternalLinksText(e.target.value)} />
                </Form.Item>
              </Form>
              {reviewTargets.map((item) => (
                <Card key={item.id} size="small" title={`订单 ${item.orderId || item.id}`}>
                  <Table
                    rowKey={(record) => `${item.id}-${record.productSkuId}`}
                    columns={[
                      { title: 'SKU', dataIndex: 'productSkuId', key: 'productSkuId', width: 120 },
                      { title: '规格', dataIndex: 'specInfo', key: 'specInfo' },
                      { title: '采购价', key: 'purchasePrice', width: 120, render: (_, record) => record.purchasePrice !== null && record.purchasePrice !== undefined ? formatPrice(record.purchasePrice) : '未配置' },
                      { title: '当前供货价', key: 'currentSupplyPrice', width: 140, render: (_, record) => formatPrice(record.currentSupplyPrice) },
                      {
                        title: '新的申报价(分)',
                        key: 'editNewPrice',
                        width: 180,
                        render: (_, record) => (
                          <InputNumber
                            value={record.editNewPrice}
                            onChange={(value) => updateSkuPrice(item.id, record.productSkuId, value)}
                            style={{ width: '100%' }}
                          />
                        ),
                      },
                    ]}
                    dataSource={item.skuList}
                    pagination={false}
                  />
                </Card>
              ))}
            </>
          ) : (
            <Typography.Paragraph style={{ marginBottom: 0 }}>确认将选中的核价单批量标记为“同意”吗？</Typography.Paragraph>
          )}
        </Space>
      </Modal>
    </Space>
  );
};

export default SyncPriceReviewPage;
