import { App, Button, Card, Drawer, Form, Image, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { Key } from 'react';
import { useEffect, useState } from 'react';
import { syncApi } from '@/api/sync';
import { temuShopsApi } from '@/api/temuShops';
import type { PriceAdjustOrderVO, PriceAdjustSkuVO, TemuShopVO } from '@/types/api';
import { formatDateTime, formatPrice } from '@/utils/format';

const statusMap: Record<number, string> = {
  0: '待核价',
  1: '待供应商确认',
  2: '调价成功',
  3: '调价失败',
};

const reviewTextMap: Record<string, string> = {
  PENDING: '待处理',
  APPROVE: '已同意',
  APPROVED: '已同意',
  REJECT: '已拒绝',
  REJECTED: '已拒绝',
};

const SyncPriceAdjustPage = () => {
  const { message } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: string; label: string }>>([]);
  const [shopId, setShopId] = useState<string>();
  const [status, setStatus] = useState<number>(1);
  const [reviewAction, setReviewAction] = useState<string>('PENDING');
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<PriceAdjustOrderVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<PriceAdjustOrderVO | null>(null);
  const [reviewing, setReviewing] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [rejectTargets, setRejectTargets] = useState<PriceAdjustOrderVO[]>([]);

  async function loadShops() {
    const res = await temuShopsApi.list({ enabled: true });
    const data = Array.isArray(res.data) ? res.data : [];
    const options = data
      .filter((item: TemuShopVO) => item.shopId && item.shopName)
      .map((item: TemuShopVO) => ({ value: item.shopId, label: item.shopName }));
    setShops(options);
    if (!shopId && options[0]) {
      setShopId(options[0].value);
      await load(1, 20, options[0].value, status, reviewAction);
    }
  }

  async function load(nextPage = page, nextPageSize = pageSize, nextShopId = shopId, nextStatus = status, nextReviewAction = reviewAction) {
    if (!nextShopId) {
      return;
    }
    setLoading(true);
    try {
      const res = await syncApi.getPriceAdjustList({
        shopId: nextShopId,
        status: nextStatus,
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

  async function openDetail(record: PriceAdjustOrderVO) {
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const res = await syncApi.getPriceAdjustDetail(record.id);
      setDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  async function approve(orderIds: number[]) {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }
    setReviewing(true);
    try {
      await syncApi.batchReviewAdjust({
        shopId,
        orderIds,
        action: 'APPROVE',
      });
      message.success('审核通过完成');
      setSelectedRowKeys([]);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '审核失败');
    } finally {
      setReviewing(false);
    }
  }

  async function submitReject() {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }
    setReviewing(true);
    try {
      await syncApi.batchReviewAdjust({
        shopId,
        orderIds: rejectTargets.map((item) => item.id),
        action: 'REJECT',
        rejectReason: rejectReason.trim() || undefined,
      });
      message.success('审核拒绝完成');
      setRejectOpen(false);
      setRejectTargets([]);
      setRejectReason('');
      setSelectedRowKeys([]);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '审核失败');
    } finally {
      setReviewing(false);
    }
  }

  async function clearLocalData() {
    if (!shopId) {
      message.error('请先选择店铺');
      return;
    }
    Modal.confirm({
      title: '删除本地调价单数据？',
      content: '会清空当前店铺同步到本地的调价单记录，请确认。',
      okText: '确认删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await syncApi.clearPriceAdjustLocalData(shopId);
          message.success('本地调价单数据已清空');
          await load();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '清空失败');
          throw error;
        }
      },
    });
  }

  const columns: ColumnsType<PriceAdjustOrderVO> = [
    {
      title: '调价单',
      key: 'priceOrderSn',
      width: 240,
      render: (_, record) => (
        <Space direction="vertical" size={4}>
          <Typography.Text strong>{record.priceOrderSn || '-'}</Typography.Text>
          <Typography.Text ellipsis style={{ maxWidth: 220 }}>
            {record.productName || '-'}
          </Typography.Text>
          <Typography.Text type="secondary">SKC {record.skcId ?? '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'SKU 信息',
      key: 'skuInfoList',
      render: (_, record) => (
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          {(record.skuInfoList || record.skuList || []).slice(0, 2).map((sku) => (
            <Space key={`${record.id}-${sku.productSkuId}`} size={10}>
              {sku.imageUrl ? <Image width={38} height={38} src={sku.imageUrl} /> : null}
              <Space direction="vertical" size={2}>
                <Typography.Text>SKU {sku.productSkuId || '-'}</Typography.Text>
                <Typography.Text type="secondary">{sku.specInfo || sku.spec || '-'}</Typography.Text>
                <Typography.Text type="secondary">当前供货价 {formatPrice(sku.currentSupplyPrice ?? sku.price)}</Typography.Text>
              </Space>
            </Space>
          ))}
        </Space>
      ),
    },
    {
      title: '新供货价',
      key: 'newSupplyPrice',
      width: 140,
      render: (_, record) => <Typography.Text strong>{formatPrice(record.newSupplyPrice)}</Typography.Text>,
    },
    {
      title: '站点 / 状态',
      key: 'status',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={4}>
          <Typography.Text>{record.siteNameList?.join(' / ') || '-'}</Typography.Text>
          <Space size={[6, 6]} wrap>
            <Tag>{statusMap[record.status || 0] || record.status || '-'}</Tag>
            <Tag color={record.reviewAction && record.reviewAction !== 'PENDING' ? (record.reviewAction.startsWith('APPRO') ? 'green' : 'red') : 'processing'}>
              {reviewTextMap[record.reviewAction || 'PENDING'] || record.reviewAction || '待处理'}
            </Tag>
            <Tag color={record.trafficLowExpose ? 'orange' : 'default'}>{record.trafficLowExpose ? '低曝光' : '正常曝光'}</Tag>
          </Space>
        </Space>
      ),
    },
    {
      title: '同步时间',
      dataIndex: 'syncedAt',
      key: 'syncedAt',
      width: 180,
      render: (value: PriceAdjustOrderVO['syncedAt']) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          <Button size="small" type="link" onClick={() => void approve([record.id])}>
            通过
          </Button>
          <Button size="small" type="link" danger onClick={() => {
            setRejectTargets([record]);
            setRejectReason(record.rejectReason || '');
            setRejectOpen(true);
          }}>
            拒绝
          </Button>
        </Space>
      ),
    },
  ];

  const detailSkuColumns: ColumnsType<PriceAdjustSkuVO> = [
    { title: 'SKU', dataIndex: 'productSkuId', key: 'productSkuId', width: 120 },
    { title: '编码', dataIndex: 'extCode', key: 'extCode', width: 140 },
    { title: '规格', dataIndex: 'specInfo', key: 'specInfo' },
    { title: '当前供货价', key: 'price', width: 140, render: (_, record) => formatPrice(record.currentSupplyPrice ?? record.price) },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Select
            value={shopId}
            onChange={(value) => {
              setShopId(value);
              setPage(1);
              void load(1, pageSize, value, status, reviewAction);
            }}
            placeholder="店铺"
            style={{ width: 220 }}
            options={shops}
          />
          <Select value={status} onChange={setStatus} style={{ width: 150 }} options={[{ value: 0, label: '待核价' }, { value: 1, label: '待供应商确认' }, { value: 2, label: '调价成功' }, { value: 3, label: '调价失败' }]} />
          <Select value={reviewAction} onChange={setReviewAction} style={{ width: 150 }} options={[{ value: 'PENDING', label: '待处理' }, { value: 'APPROVE', label: '已同意' }, { value: 'REJECT', label: '已拒绝' }]} />
          <Button type="primary" loading={loading} onClick={() => {
            setPage(1);
            void load(1, pageSize, shopId, status, reviewAction);
          }}>
            查询
          </Button>
          <Button onClick={() => {
            setStatus(1);
            setReviewAction('PENDING');
            setPage(1);
            void load(1, pageSize, shopId, 1, 'PENDING');
          }}>
            重置
          </Button>
          <Button disabled={!selectedRowKeys.length} loading={reviewing} onClick={() => void approve(rows.filter((item) => selectedRowKeys.includes(item.id)).map((item) => item.id))}>
            批量通过 ({selectedRowKeys.length})
          </Button>
          <Button danger disabled={!selectedRowKeys.length} onClick={() => {
            const targets = rows.filter((item) => selectedRowKeys.includes(item.id));
            setRejectTargets(targets);
            setRejectReason('');
            setRejectOpen(true);
          }}>
            批量拒绝 ({selectedRowKeys.length})
          </Button>
          <Button danger onClick={() => void clearLocalData()}>
            删除本地数据
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<PriceAdjustOrderVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
          scroll={{ x: 1360 }}
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
            void load(nextPage, nextPageSize, shopId, status, reviewAction);
          }}
        />
      </Card>

      <Drawer open={detailOpen} width={920} title="调价单详情" onClose={() => setDetailOpen(false)} loading={detailLoading}>
        {detail ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card size="small">
              <Space direction="vertical" size={4}>
                <span>调价单号: {detail.priceOrderSn || '-'}</span>
                <span>商品: {detail.productName || '-'}</span>
                <span>站点: {detail.siteNameList?.join(' / ') || '-'}</span>
                <span>新供货价: {formatPrice(detail.newSupplyPrice)}</span>
                <span>拒绝原因: {detail.rejectReason || '-'}</span>
              </Space>
            </Card>
            <Card size="small" title="SKU 列表">
              <Table<PriceAdjustSkuVO> rowKey={(record) => `${record.id}-${record.productSkuId}`} columns={detailSkuColumns} dataSource={detail.skuInfoList || detail.skuList || []} pagination={false} />
            </Card>
          </Space>
        ) : null}
      </Drawer>

      <Modal
        open={rejectOpen}
        title="调价单拒绝"
        confirmLoading={reviewing}
        onOk={() => void submitReject()}
        onCancel={() => setRejectOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label={`本次共 ${rejectTargets.length} 条调价单`}>
            <Typography.Text type="secondary">如需回传原因，可在这里统一填写。</Typography.Text>
          </Form.Item>
          <Form.Item label="拒绝原因">
            <Input.TextArea rows={4} value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} placeholder="可选；如填写将回写到后台" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default SyncPriceAdjustPage;
