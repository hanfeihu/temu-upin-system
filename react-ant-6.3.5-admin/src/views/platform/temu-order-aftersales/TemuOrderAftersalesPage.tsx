import { Alert, App, Button, Card, DatePicker, Input, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import type { Dayjs } from 'dayjs';
import { temuOrderAftersalesApi } from '@/api/temuOrderAftersales';
import { temuShopsApi } from '@/api/temuShops';
import type { TemuOrderAftersaleVO, TemuShopVO } from '@/types/api';
import { formatTimestamp } from '@/utils/format';

type DateRangeValue = [Dayjs, Dayjs] | null;

const AFTERSALE_STATUS_GROUP_META: Record<number, { label: string; color: string; description: string }> = {
  1: { label: '待处理', color: 'gold', description: '售后还在等待平台或商家处理。' },
  2: { label: '已申请', color: 'blue', description: '买家已经发起售后申请，正在进入处理流程。' },
  3: { label: '包裹已寄回', color: 'cyan', description: '买家已把退货包裹寄出。' },
  4: { label: '平台审核中', color: 'processing', description: '售后进入平台侧审核节点。' },
  5: { label: '已退款', color: 'success', description: '退款已经处理完成。' },
  6: { label: '已拒绝', color: 'error', description: '本次售后申请被拒绝。' },
  7: { label: '已取消', color: 'default', description: '买家或系统取消了本次售后。' },
};

const PARENT_AFTERSALE_STATUS_META: Record<number, string> = {
  1: '买家申请退款，待处理',
  2: '买家已寄出退货包裹',
  3: '已收到退货，待商家处理',
  4: '已发起退款，系统处理中',
  5: '已退款完成',
  6: '买家已取消售后',
  7: '退款申请已拒绝',
  8: '买家使用商家面单退货，待商家审核并上传面单',
  9: '已发起退款，系统处理中',
  10: '买家已申请退货',
  11: '平台审核中',
};

const AFTERSALE_TYPE_META: Record<number, string> = {
  1: '仅退款',
  2: '退货退款',
};

const aftersaleStatusGroupLabel = (record: TemuOrderAftersaleVO) =>
  record.afterSalesStatusGroupName || (record.afterSalesStatusGroup ? AFTERSALE_STATUS_GROUP_META[record.afterSalesStatusGroup]?.label : null) || '-';

const aftersaleStatusDescription = (record: TemuOrderAftersaleVO) =>
  (record.afterSalesStatusGroup ? AFTERSALE_STATUS_GROUP_META[record.afterSalesStatusGroup]?.description : null) || '暂无状态说明';

const parentAftersaleStatusLabel = (record: TemuOrderAftersaleVO) =>
  record.parentAfterSalesStatusName || (record.parentAfterSalesStatus ? PARENT_AFTERSALE_STATUS_META[record.parentAfterSalesStatus] : null) || '-';

const aftersaleTypeLabel = (record: TemuOrderAftersaleVO) =>
  record.afterSalesTypeName || (record.afterSalesType ? AFTERSALE_TYPE_META[record.afterSalesType] : null) || '-';

const toRangeParams = (range: DateRangeValue) => {
  if (!range || range.length !== 2) {
    return { startMs: undefined, endMs: undefined };
  }
  return {
    startMs: range[0].startOf('day').valueOf(),
    endMs: range[1].endOf('day').valueOf(),
  };
};

const TemuOrderAftersalesPage = () => {
  const { RangePicker } = DatePicker;
  const { message } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: number; label: string }>>([]);
  const [shopRecordId, setShopRecordId] = useState<number>();
  const [keyword, setKeyword] = useState('');
  const [afterSalesStatusGroup, setAfterSalesStatusGroup] = useState<number>();
  const [createAtRange, setCreateAtRange] = useState<DateRangeValue>(null);
  const [updateAtRange, setUpdateAtRange] = useState<DateRangeValue>(null);
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<TemuOrderAftersaleVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);

  async function loadShops() {
    const res = await temuShopsApi.list({ enabled: true });
    const data = Array.isArray(res.data) ? res.data : [];
    const options = data
      .filter((item: TemuShopVO) => item.id && item.shopName)
      .map((item: TemuShopVO) => ({ value: item.id, label: `${item.shopName}（${item.shopId}）` }));
    setShops(options);
    if (!shopRecordId && options[0]) {
      setShopRecordId(options[0].value);
      await load(1, pageSize, options[0].value, keyword, afterSalesStatusGroup, createAtRange, updateAtRange);
    }
  }

  async function load(
    nextPage = page,
    nextPageSize = pageSize,
    nextShopRecordId = shopRecordId,
    nextKeyword = keyword,
    nextAfterSalesStatusGroup = afterSalesStatusGroup,
    nextCreateAtRange = createAtRange,
    nextUpdateAtRange = updateAtRange,
  ) {
    if (!nextShopRecordId) {
      return;
    }
    setLoading(true);
    try {
      const createAtParams = toRangeParams(nextCreateAtRange);
      const updateAtParams = toRangeParams(nextUpdateAtRange);
      const res = await temuOrderAftersalesApi.list({
        shopRecordId: nextShopRecordId,
        keyword: nextKeyword.trim() || undefined,
        afterSalesStatusGroup: nextAfterSalesStatusGroup,
        createAtStartMs: createAtParams.startMs,
        createAtEndMs: createAtParams.endMs,
        updateAtStartMs: updateAtParams.startMs,
        updateAtEndMs: updateAtParams.endMs,
        page: nextPage,
        pageSize: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载售后失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadShops();
  }, []);

  const columns: ColumnsType<TemuOrderAftersaleVO> = [
    {
      title: '售后单',
      key: 'afterSales',
      width: 260,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{record.parentAfterSalesSn}</Typography.Text>
          <Typography.Text type="secondary">{record.parentOrderSn || '-'}</Typography.Text>
          <Typography.Text type="secondary">{record.shopName || record.shopId}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 240,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Tag color={record.afterSalesStatusGroup ? AFTERSALE_STATUS_GROUP_META[record.afterSalesStatusGroup]?.color || 'default' : 'default'}>
            {aftersaleStatusGroupLabel(record)}
            {record.afterSalesStatusGroup ? ` · G${record.afterSalesStatusGroup}` : ''}
          </Tag>
          <Typography.Text>{parentAftersaleStatusLabel(record)}</Typography.Text>
          <Typography.Text type="secondary">
            {record.parentAfterSalesStatus ? `父状态 ${record.parentAfterSalesStatus}` : '父状态未知'} · {aftersaleStatusDescription(record)}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '类型',
      key: 'type',
      width: 180,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{aftersaleTypeLabel(record)}</Typography.Text>
          <Typography.Text type="secondary">{record.afterSalesType ? `类型 ${record.afterSalesType}` : '类型未知'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '时间',
      key: 'time',
      width: 180,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{formatTimestamp(record.createAtMs)}</Typography.Text>
          <Typography.Text type="secondary">{formatTimestamp(record.updateAtMs)}</Typography.Text>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message="状态组是 TEMU 的聚合筛选，父状态才是售后单当前实际节点。"
        description="例如“已申请”只是大类，真正的进度要看下面那行父状态；类型里 1=仅退款，2=退货退款。"
      />
      <Card>
        <Space wrap>
          <Select
            value={shopRecordId}
            onChange={(value) => {
              setShopRecordId(value);
              setPage(1);
              void load(1, pageSize, value, keyword, afterSalesStatusGroup, createAtRange, updateAtRange);
            }}
            options={shops}
            style={{ width: 280 }}
            placeholder="请选择店铺"
            showSearch
            optionFilterProp="label"
          />
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="售后单号 / 父订单号"
            style={{ width: 220 }}
          />
          <Select
            allowClear
            value={afterSalesStatusGroup}
            onChange={(value) => setAfterSalesStatusGroup(value)}
            style={{ width: 180 }}
            placeholder="售后状态分组"
            options={[
              { value: 1, label: '待处理' },
              { value: 2, label: '已申请' },
              { value: 3, label: '包裹已寄回' },
              { value: 4, label: '平台审核中' },
              { value: 5, label: '已退款' },
              { value: 6, label: '已拒绝' },
              { value: 7, label: '已取消' },
            ]}
          />
          <Space size={8}>
            <Typography.Text type="secondary">申请时间</Typography.Text>
            <RangePicker
              value={createAtRange ?? undefined}
              onChange={(value) => setCreateAtRange((value as DateRangeValue) ?? null)}
              placeholder={['开始日期', '结束日期']}
            />
          </Space>
          <Space size={8}>
            <Typography.Text type="secondary">更新时间</Typography.Text>
            <RangePicker
              value={updateAtRange ?? undefined}
              onChange={(value) => setUpdateAtRange((value as DateRangeValue) ?? null)}
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
              setAfterSalesStatusGroup(undefined);
              setCreateAtRange(null);
              setUpdateAtRange(null);
              setPage(1);
              void load(1, pageSize, shopRecordId, '', undefined, null, null);
            }}
          >
            重置
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<TemuOrderAftersaleVO>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
          }}
          onChange={(pagination: TablePaginationConfig) => {
            const nextPage = pagination.current || 1;
            const nextPageSize = pagination.pageSize || 20;
            setPage(nextPage);
            setPageSize(nextPageSize);
            void load(nextPage, nextPageSize);
          }}
        />
      </Card>
    </Space>
  );
};

export default TemuOrderAftersalesPage;
