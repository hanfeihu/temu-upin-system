import {
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Image,
  Input,
  Modal,
  Popover,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState, type ReactNode } from 'react';
import type { Dayjs } from 'dayjs';
import { temuOrdersApi } from '@/api/temuOrders';
import { temuShopsApi } from '@/api/temuShops';
import type { TemuOrderDetailVO, TemuOrderLogisticsRefreshPayload, TemuOrderVO, TemuShopVO } from '@/types/api';
import { formatTimestampMinute, prettyJson } from '@/utils/format';
import { loadStoredNumericShopFilter, resolveStoredShopFilter, saveStoredShopFilter } from '@/utils/shopFilter';
import './TemuOrdersPage.css';

const SHOP_FILTER_STORAGE_KEY = 'temu-orders';

const orderStatusText = (value?: number | null) => {
  switch (value) {
    case 1:
      return '待处理';
    case 2:
      return '待发货';
    case 3:
      return '已取消';
    case 4:
      return '已发货';
    case 5:
      return '已签收';
    case 41:
      return '部分发货';
    case 51:
      return '部分签收';
    default:
      return value ?? '-';
  }
};

const matchColor = (value?: string | null) => {
  switch (value) {
    case 'MATCHED':
      return 'green';
    case 'MATCHED_MULTI':
      return 'blue';
    case 'AMBIGUOUS':
      return 'orange';
    case 'EMPTY':
      return 'default';
    default:
      return 'red';
  }
};

const matchText = (value?: string | null) => {
  switch (value) {
    case 'MATCHED':
      return '已匹配';
    case 'MATCHED_MULTI':
      return '同 SPU 多 SKU';
    case 'AMBIGUOUS':
      return '多商品冲突';
    case 'EMPTY':
      return '无 SKU';
    case 'UNMATCHED':
      return '未匹配';
    default:
      return value || '-';
  }
};

const formatDecimalPrice = (value?: number | null) => {
  if (value === null || value === undefined) {
    return '-';
  }
  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }
  return `¥${numeric.toFixed(2)}`;
};

const formatPercent = (value?: number | null) => {
  if (value === null || value === undefined) {
    return '-';
  }
  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return '-';
  }
  return `${numeric.toFixed(2)}%`;
};

type FeeDetailItem = {
  fee_kind_name?: string | null;
  fee_kind_code?: string | null;
  amount?: string | number | null;
  currency_amount?: string | number | null;
  note?: string | null;
  occur_date?: string | null;
};

const parseFeeDetailItems = (value?: string | null): FeeDetailItem[] => {
  if (!value) {
    return [];
  }
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

const resolveFeeAmount = (item: FeeDetailItem) => {
  const candidate = item.amount ?? item.currency_amount;
  const numeric = Number(candidate);
  return Number.isFinite(numeric) ? numeric : null;
};

type DateRangeValue = [Dayjs, Dayjs] | null;

const toRangeParams = (range: DateRangeValue) => {
  if (!range || range.length !== 2) {
    return { startMs: undefined, endMs: undefined };
  }
  return {
    startMs: range[0].startOf('day').valueOf(),
    endMs: range[1].endOf('day').valueOf(),
  };
};

const TemuOrdersPage = () => {
  const { RangePicker } = DatePicker;
  const { message } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: number; label: string; shopId: string }>>([]);
  const [shopRecordId, setShopRecordId] = useState<number | undefined>(() => loadStoredNumericShopFilter(SHOP_FILTER_STORAGE_KEY));
  const [keyword, setKeyword] = useState('');
  const [matchedTemuSkuIdLike, setMatchedTemuSkuIdLike] = useState('');
  const [cancelState, setCancelState] = useState<'ACTIVE' | 'CANCELLED' | 'ALL'>('ACTIVE');
  const [aftersaleState, setAftersaleState] = useState<'ALL' | 'REFUNDED' | 'NOT_REFUNDED'>('ALL');
  const [orderStatus, setOrderStatus] = useState<number>();
  const [matchStatus, setMatchStatus] = useState<string>();
  const [orderTimeRange, setOrderTimeRange] = useState<DateRangeValue>(null);
  const [updateTimeRange, setUpdateTimeRange] = useState<DateRangeValue>(null);
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<TemuOrderVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<TemuOrderDetailVO | null>(null);
  const [logisticsOpen, setLogisticsOpen] = useState(false);
  const [refreshingLogistics, setRefreshingLogistics] = useState(false);
  const [logisticsOrderId, setLogisticsOrderId] = useState<number | null>(null);
  const [logisticsForm, setLogisticsForm] = useState<TemuOrderLogisticsRefreshPayload>({
    providerCode: 'HAOYUAN',
    referenceNo: '',
    shippingMethodNo: '',
    trackingNumber: '',
  });

  const renderFeeDetailPopover = (
    fee?: number | null,
    feeDetailJson?: string | null,
    triggerLabel?: ReactNode,
  ) => {
    const items = parseFeeDetailItems(feeDetailJson);
    const hasItems = items.length > 0;
    const total = fee ?? items.reduce<number | null>((sum, item) => {
      const amount = resolveFeeAmount(item);
      if (amount === null) {
        return sum;
      }
      return (sum ?? 0) + amount;
    }, null);

    if (!hasItems) {
      return <>{triggerLabel ?? formatDecimalPrice(total)}</>;
    }

    return (
      <Popover
        trigger="click"
        placement="leftTop"
        content={
          <Space direction="vertical" size={8} style={{ minWidth: 260 }}>
            {items.map((item, index) => {
              const amount = resolveFeeAmount(item);
              return (
                <div key={`${item.fee_kind_code || 'fee'}-${index}`}>
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Typography.Text>{item.fee_kind_name || item.fee_kind_code || `费用${index + 1}`}</Typography.Text>
                    <Typography.Text strong>{formatDecimalPrice(amount)}</Typography.Text>
                  </Space>
                  {item.note || item.occur_date ? (
                    <Typography.Text type="secondary">
                      {[item.note, item.occur_date].filter(Boolean).join(' / ')}
                    </Typography.Text>
                  ) : null}
                </div>
              );
            })}
            <Space style={{ width: '100%', justifyContent: 'space-between' }}>
              <Typography.Text strong>合计</Typography.Text>
              <Typography.Text strong>{formatDecimalPrice(total)}</Typography.Text>
            </Space>
          </Space>
        }
      >
        <Typography.Link>{triggerLabel ?? formatDecimalPrice(total)}</Typography.Link>
      </Popover>
    );
  };

  async function loadShops() {
    const res = await temuShopsApi.list({ enabled: true });
    const data = Array.isArray(res.data) ? res.data : [];
    const options = data
      .filter((item: TemuShopVO) => item.id && item.shopName)
      .map((item: TemuShopVO) => ({
        value: item.id,
        label: `${item.shopName}（${item.shopId}）`,
        shopId: item.shopId,
      }));
    setShops(options);
    const nextShopRecordId = resolveStoredShopFilter(options, shopRecordId);
    if (!nextShopRecordId) {
      setShopRecordId(undefined);
      saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, undefined);
      return;
    }
    setShopRecordId(nextShopRecordId);
    saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, nextShopRecordId);
    await load(
      1,
      pageSize,
      nextShopRecordId,
      keyword,
      matchedTemuSkuIdLike,
      cancelState,
      aftersaleState,
      orderStatus,
      matchStatus,
      orderTimeRange,
      updateTimeRange,
    );
  }

  async function load(
    nextPage = page,
    nextPageSize = pageSize,
    nextShopRecordId = shopRecordId,
    nextKeyword = keyword,
    nextMatchedTemuSkuIdLike = matchedTemuSkuIdLike,
    nextCancelState = cancelState,
    nextAftersaleState = aftersaleState,
    nextOrderStatus = orderStatus,
    nextMatchStatus = matchStatus,
    nextOrderTimeRange = orderTimeRange,
    nextUpdateTimeRange = updateTimeRange,
  ) {
    if (!nextShopRecordId) {
      return;
    }
    setLoading(true);
    try {
      const orderTimeParams = toRangeParams(nextOrderTimeRange);
      const updateTimeParams = toRangeParams(nextUpdateTimeRange);
      const res = await temuOrdersApi.list({
        shopRecordId: nextShopRecordId,
        keyword: nextKeyword.trim() || undefined,
        matchedTemuSkuIdLike: nextMatchedTemuSkuIdLike.trim() || undefined,
        cancelState: nextCancelState === 'ALL' ? undefined : nextCancelState,
        aftersaleState: nextAftersaleState === 'ALL' ? undefined : nextAftersaleState,
        orderStatus: nextOrderStatus,
        matchStatus: nextMatchStatus || undefined,
        orderTimeStartMs: orderTimeParams.startMs,
        orderTimeEndMs: orderTimeParams.endMs,
        updateTimeStartMs: updateTimeParams.startMs,
        updateTimeEndMs: updateTimeParams.endMs,
        page: nextPage,
        pageSize: nextPageSize,
      });
      const content = Array.isArray(res.data.content) ? [...res.data.content] : [];
      content.sort((left, right) => {
        const diff = (right.orderTimeMs ?? 0) - (left.orderTimeMs ?? 0);
        if (diff !== 0) {
          return diff;
        }
        return (right.id ?? 0) - (left.id ?? 0);
      });
      setRows(content);
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载订单失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadShops();
  }, []);

  async function openDetail(record: TemuOrderVO) {
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const res = await temuOrdersApi.detail(record.id);
      setDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载订单详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  function openLogisticsModal(record: TemuOrderVO) {
    setLogisticsOrderId(record.id);
    setLogisticsForm({
      providerCode: 'HAOYUAN',
      referenceNo:
        detail?.id === record.id
          ? detail?.logistics?.referenceNo || detail?.dianxiaomiPackageNumber || ''
          : record.dianxiaomiPackageNumber || '',
      shippingMethodNo:
        detail?.id === record.id
          ? detail?.dianxiaomiPackageNumber || detail?.logistics?.shippingMethodNo || ''
          : record.dianxiaomiPackageNumber || '',
      trackingNumber: record.logisticsTrackingNumber || '',
    });
    setLogisticsOpen(true);
  }

  async function refreshLogistics() {
    if (!logisticsOrderId) {
      return;
    }
    setRefreshingLogistics(true);
    try {
      await temuOrdersApi.refreshLogistics(logisticsOrderId, logisticsForm);
      message.success('物流信息已刷新');
      setLogisticsOpen(false);
      await load();
      if (detail?.id === logisticsOrderId) {
        const res = await temuOrdersApi.detail(logisticsOrderId);
        setDetail(res.data);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '刷新物流失败');
    } finally {
      setRefreshingLogistics(false);
    }
  }

  const columns: ColumnsType<TemuOrderVO> = [
    {
      title: '订单',
      key: 'order',
      width: 300,
      render: (_, record) => (
        <div className="temu-orders-cell temu-orders-order-cell">
          <Typography.Text strong ellipsis>
            {record.parentOrderSn || '-'}
          </Typography.Text>
          <Typography.Text ellipsis type={record.dianxiaomiPackageNumber ? undefined : 'secondary'}>
            {record.dianxiaomiPackageNumber ? `店小秘 ${record.dianxiaomiPackageNumber}` : '店小秘单号未回填'}
          </Typography.Text>
          <Typography.Text type="secondary" ellipsis>
            {record.orderSn || '-'}
          </Typography.Text>
          <Typography.Text type="secondary" ellipsis>
            {record.shopName || record.shopId}
          </Typography.Text>
          <Tag>{orderStatusText(record.orderStatus)}</Tag>
        </div>
      ),
    },
    {
      title: '商品',
      key: 'goods',
      width: 420,
      render: (_, record) => (
        <div className="temu-orders-goods">
          {record.thumbUrl ? <Image width={48} height={48} src={record.thumbUrl} preview={false} /> : null}
          <div className="temu-orders-goods-content">
            <Typography.Paragraph className="temu-orders-title" ellipsis={{ rows: 2, tooltip: record.goodsName || '-' }}>
              {record.goodsName || '-'}
            </Typography.Paragraph>
            <Typography.Paragraph className="temu-orders-subtext" ellipsis={{ rows: 2, tooltip: record.spec || '-' }}>
              {record.spec || '-'}
            </Typography.Paragraph>
            <Typography.Text type="secondary">数量 {record.quantity ?? '-'}</Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: '匹配',
      key: 'match',
      width: 300,
      render: (_, record) => (
        <div className="temu-orders-cell">
          <Tag color={matchColor(record.matchStatus)}>{matchText(record.matchStatus)}</Tag>
          <Typography.Paragraph
            className="temu-orders-title"
            ellipsis={{ rows: 2, tooltip: record.matchedSkuSpecName || '-' }}
          >
            {record.matchedSkuSpecName || '-'}
          </Typography.Paragraph>
          <Typography.Text type="secondary" ellipsis>
            货品编码 {record.matchedOriginSkuId || '-'}
          </Typography.Text>
          <Typography.Text type="secondary">供货价 {formatDecimalPrice(record.matchedSupplyPrice)}</Typography.Text>
          <Typography.Text type="secondary">销量 {record.salesQuantity ?? 0} / 售后 {record.aftersaleQuantity ?? 0}</Typography.Text>
          <Typography.Text type="secondary">已签收 {record.signedQuantity ?? 0} / 已签收售后 {record.signedAftersaleQuantity ?? 0}</Typography.Text>
          <Typography.Text type="secondary">售后率 {formatPercent(record.aftersaleRate)}</Typography.Text>
        </div>
      ),
    },
    {
      title: '物流',
      key: 'logistics',
      width: 250,
      render: (_, record) => (
        <div className="temu-orders-cell">
          <Typography.Text ellipsis>{record.dianxiaomiPackageNumber || '-'}</Typography.Text>
          <Typography.Text type="secondary" ellipsis>
            {record.logisticsTrackingNumber ? `运单 ${record.logisticsTrackingNumber}` : record.logisticsTrackStatusName || '已回填店小秘单号'}
          </Typography.Text>
          <Space size={4} wrap>
            <Typography.Text type="secondary">头程</Typography.Text>
            {renderFeeDetailPopover(record.firstLegLogisticsFee, record.orderFeeDetailJson)}
            <Typography.Text type="secondary">/ {record.chargeWeight ? `${record.chargeWeight}kg` : '-'}</Typography.Text>
          </Space>
        </div>
      ),
    },
    {
      title: '订单创建时间',
      key: 'time',
      width: 220,
      render: (_, record) => <Typography.Text>{formatTimestampMinute(record.orderTimeMs)}</Typography.Text>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 168,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          <Button size="small" onClick={() => openLogisticsModal(record)}>
            刷新物流
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
            value={shopRecordId}
            onChange={(value) => {
              setShopRecordId(value);
              saveStoredShopFilter(SHOP_FILTER_STORAGE_KEY, value);
              setPage(1);
              void load(1, pageSize, value, keyword, matchedTemuSkuIdLike, cancelState, aftersaleState, orderStatus, matchStatus, orderTimeRange, updateTimeRange);
            }}
            options={shops}
            style={{ width: 280 }}
            placeholder="请选择店铺"
            showSearch
            optionFilterProp="label"
          />
          <Segmented
            value={cancelState}
            onChange={(value) => {
              const nextValue = value as 'ACTIVE' | 'CANCELLED' | 'ALL';
              setCancelState(nextValue);
              if (nextValue === 'CANCELLED' && orderStatus !== undefined && orderStatus !== 3) {
                setOrderStatus(undefined);
              }
              if (nextValue === 'ACTIVE' && orderStatus === 3) {
                setOrderStatus(undefined);
              }
              setPage(1);
              void load(
                1,
                pageSize,
                shopRecordId,
                keyword,
                matchedTemuSkuIdLike,
                nextValue,
                aftersaleState,
                nextValue === 'CANCELLED' && orderStatus !== undefined && orderStatus !== 3
                  ? undefined
                  : nextValue === 'ACTIVE' && orderStatus === 3
                    ? undefined
                    : orderStatus,
                matchStatus,
                orderTimeRange,
                updateTimeRange,
              );
            }}
            options={[
              { label: '未取消', value: 'ACTIVE' },
              { label: '已取消', value: 'CANCELLED' },
              { label: '全部', value: 'ALL' },
            ]}
          />
          <Segmented
            value={aftersaleState}
            onChange={(value) => {
              const nextValue = value as 'ALL' | 'REFUNDED' | 'NOT_REFUNDED';
              setAftersaleState(nextValue);
              setPage(1);
              void load(
                1,
                pageSize,
                shopRecordId,
                keyword,
                matchedTemuSkuIdLike,
                cancelState,
                nextValue,
                orderStatus,
                matchStatus,
                orderTimeRange,
                updateTimeRange,
              );
            }}
            options={[
              { label: '未售后', value: 'NOT_REFUNDED' },
              { label: '已售后', value: 'REFUNDED' },
              { label: '全部', value: 'ALL' },
            ]}
          />
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="PO号 / 店小秘单号 / 子订单号 / 商品名"
            style={{ width: 240 }}
          />
          <Input
            value={matchedTemuSkuIdLike}
            onChange={(event) => setMatchedTemuSkuIdLike(event.target.value)}
            placeholder="SKUID 模糊筛选"
            style={{ width: 180 }}
          />
          <Select
            allowClear
            value={orderStatus}
            onChange={(value) => {
              setOrderStatus(value);
              if (value === 3) {
                setCancelState('CANCELLED');
              } else if (value !== undefined && value !== null) {
                setCancelState('ACTIVE');
              }
            }}
            style={{ width: 160 }}
            placeholder="订单状态"
            options={[
              { value: 1, label: '待处理' },
              { value: 2, label: '待发货' },
              { value: 3, label: '已取消' },
              { value: 4, label: '已发货' },
              { value: 5, label: '已签收' },
              { value: 41, label: '部分发货' },
              { value: 51, label: '部分签收' },
            ]}
          />
          <Select
            allowClear
            value={matchStatus}
            onChange={(value) => setMatchStatus(value)}
            style={{ width: 160 }}
            placeholder="匹配状态"
            options={[
              { value: 'MATCHED', label: '已匹配' },
              { value: 'MATCHED_MULTI', label: '同 SPU 多 SKU' },
              { value: 'UNMATCHED', label: '未匹配' },
              { value: 'AMBIGUOUS', label: '多商品冲突' },
              { value: 'EMPTY', label: '无 SKU' },
            ]}
          />
          <Space size={8}>
            <Typography.Text type="secondary">下单时间</Typography.Text>
            <RangePicker
              value={orderTimeRange ?? undefined}
              onChange={(value) => setOrderTimeRange((value as DateRangeValue) ?? null)}
              placeholder={['开始日期', '结束日期']}
            />
          </Space>
          <Space size={8}>
            <Typography.Text type="secondary">更新时间</Typography.Text>
            <RangePicker
              value={updateTimeRange ?? undefined}
              onChange={(value) => setUpdateTimeRange((value as DateRangeValue) ?? null)}
              placeholder={['开始日期', '结束日期']}
            />
          </Space>
          <Button
            type="primary"
            onClick={() => {
              setPage(1);
              void load(1);
            }}
          >
            搜索
          </Button>
          <Button
            onClick={() => {
              setKeyword('');
              setMatchedTemuSkuIdLike('');
              setCancelState('ACTIVE');
              setAftersaleState('ALL');
              setOrderStatus(undefined);
              setMatchStatus(undefined);
              setOrderTimeRange(null);
              setUpdateTimeRange(null);
              setPage(1);
              void load(1, pageSize, shopRecordId, '', '', 'ACTIVE', 'ALL', undefined, undefined, null, null);
            }}
          >
            重置
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<TemuOrderVO>
          className="temu-orders-table"
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          tableLayout="fixed"
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
          }}
          scroll={{ x: 1628 }}
          onChange={(pagination: TablePaginationConfig) => {
            const nextPage = pagination.current || 1;
            const nextPageSize = pagination.pageSize || 20;
            setPage(nextPage);
            setPageSize(nextPageSize);
            void load(nextPage, nextPageSize);
          }}
        />
      </Card>

      <Drawer
        open={detailOpen}
        title={detail?.parentOrderSn || '订单详情'}
        width={920}
        onClose={() => setDetailOpen(false)}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Card loading={detailLoading}>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="店铺">{detail?.shopName || detail?.shopId || '-'}</Descriptions.Item>
              <Descriptions.Item label="订单状态">{orderStatusText(detail?.orderStatus)}</Descriptions.Item>
              <Descriptions.Item label="父订单号">{detail?.parentOrderSn || '-'}</Descriptions.Item>
              <Descriptions.Item label="店小秘单号">{detail?.dianxiaomiPackageNumber || '-'}</Descriptions.Item>
              <Descriptions.Item label="子订单号">{detail?.orderSn || '-'}</Descriptions.Item>
              <Descriptions.Item label="商品">{detail?.goodsName || '-'}</Descriptions.Item>
              <Descriptions.Item label="规格">{detail?.spec || '-'}</Descriptions.Item>
              <Descriptions.Item label="SKU规格名">{detail?.matchedSkuSpecName || '-'}</Descriptions.Item>
              <Descriptions.Item label="货品编码">{detail?.matchedOriginSkuId || '-'}</Descriptions.Item>
              <Descriptions.Item label="匹配结果">
                <Tag color={matchColor(detail?.matchStatus)}>{matchText(detail?.matchStatus)}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="供货价">{formatDecimalPrice(detail?.matchedSupplyPrice)}</Descriptions.Item>
              <Descriptions.Item label="下单时间">{formatTimestampMinute(detail?.orderTimeMs)}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatTimestampMinute(detail?.updateTimeMs)}</Descriptions.Item>
            </Descriptions>
          </Card>

          <Card title="物流快照">
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="服务商">{detail?.logistics?.providerName || '-'}</Descriptions.Item>
              <Descriptions.Item label="店小秘查询号">
                {detail?.logistics?.referenceNo || detail?.dianxiaomiPackageNumber || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="浩远服务单号">{detail?.logistics?.shippingMethodNo || '-'}</Descriptions.Item>
              <Descriptions.Item label="运单号">{detail?.logistics?.trackingNumber || '-'}</Descriptions.Item>
              <Descriptions.Item label="轨迹状态">{detail?.logistics?.trackStatusName || '-'}</Descriptions.Item>
              <Descriptions.Item label="头程运费">
                {renderFeeDetailPopover(
                  detail?.logistics?.firstLegLogisticsFee,
                  detail?.logistics?.orderFeeDetailJson,
                  formatDecimalPrice(detail?.logistics?.firstLegLogisticsFee),
                )}
              </Descriptions.Item>
              <Descriptions.Item label="计费重">{detail?.logistics?.chargeWeight ? `${detail.logistics.chargeWeight}kg` : '-'}</Descriptions.Item>
            </Descriptions>
          </Card>

          <Card title="TEMU SKU 原始数据">
            <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{prettyJson(detail?.productSkusJson)}</pre>
          </Card>

          <Card title="订单原始返回">
            <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{prettyJson(detail?.rawJson)}</pre>
          </Card>
        </Space>
      </Drawer>

      <Modal
        open={logisticsOpen}
        title="刷新浩远物流"
        confirmLoading={refreshingLogistics}
        onOk={() => void refreshLogistics()}
        onCancel={() => setLogisticsOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="物流服务商">
            <Input value="HAOYUAN" disabled />
          </Form.Item>
          <Form.Item label="浩远查询号">
            <Input
              value={logisticsForm.shippingMethodNo}
              onChange={(event) => setLogisticsForm((current) => ({ ...current, shippingMethodNo: event.target.value }))}
              placeholder="默认优先使用店小秘单号，可手动覆盖"
            />
          </Form.Item>
          <Form.Item label="参考号">
            <Input
              value={logisticsForm.referenceNo}
              onChange={(event) => setLogisticsForm((current) => ({ ...current, referenceNo: event.target.value }))}
              placeholder="可选，只有拿到真实参考号时才填写"
            />
          </Form.Item>
          <Form.Item label="运单号">
            <Input
              value={logisticsForm.trackingNumber}
              onChange={(event) => setLogisticsForm((current) => ({ ...current, trackingNumber: event.target.value }))}
              placeholder="可留空，存在历史值时会沿用"
            />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default TemuOrdersPage;
