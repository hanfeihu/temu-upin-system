import { App, Button, Card, Image, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { supplierProductPackagesApi } from '@/api/supplierProductSubmissions';
import { temuShopsApi } from '@/api/temuShops';
import type { SupplierProductPackageVO, TemuShopVO } from '@/types/api';
import { formatDateTime } from '@/utils/format';

const statusOptions = [
  { label: '待生成', value: 'PENDING', color: 'orange' },
  { label: '生成中', value: 'GENERATING', color: 'processing' },
  { label: '已生成', value: 'GENERATED', color: 'green' },
  { label: '生成失败', value: 'FAILED', color: 'red' },
  { label: '已推采集库', value: 'PUSHED', color: 'blue' },
];

function statusTag(status?: string | null) {
  const item = statusOptions.find((option) => option.value === status);
  return <Tag color={item?.color || 'default'}>{item?.label || status || '-'}</Tag>;
}

const SupplierProductPackagesPage = () => {
  const { message } = App.useApp();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string>();
  const [rows, setRows] = useState<SupplierProductPackageVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [actioningId, setActioningId] = useState<number | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [targetShopOptions, setTargetShopOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [loadingTargetShops, setLoadingTargetShops] = useState(false);
  const [pushOpen, setPushOpen] = useState(false);
  const [pushRecord, setPushRecord] = useState<SupplierProductPackageVO | null>(null);
  const [pushShopId, setPushShopId] = useState<string>();

  async function load(nextPage = page, nextSize = pageSize) {
    setLoading(true);
    try {
      const res = await supplierProductPackagesApi.list({
        keyword: keyword.trim() || undefined,
        status,
        page: nextPage - 1,
        size: nextSize,
      });
      setRows(res.data?.content || []);
      setTotal(res.data?.totalElements || 0);
      setPage(nextPage);
      setPageSize(nextSize);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 AI 商品包装台失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(1, pageSize);
  }, [status]);

  async function loadTargetShops() {
    setLoadingTargetShops(true);
    try {
      const res = await temuShopsApi.list({ enabled: true });
      const shops = Array.isArray(res.data) ? res.data : [];
      setTargetShopOptions(
        shops
          .filter((item: TemuShopVO) => item.shopId && item.shopName)
          .map((item: TemuShopVO) => ({
            value: String(item.shopId),
            label: String(item.shopName),
          })),
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载店铺失败');
    } finally {
      setLoadingTargetShops(false);
    }
  }

  useEffect(() => {
    void loadTargetShops();
  }, []);

  async function handleGenerate(record: SupplierProductPackageVO) {
    setActioningId(record.id);
    try {
      await supplierProductPackagesApi.generate(record.id);
      message.success('AI 素材生成完成');
      await load(page, pageSize);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'AI 生成失败');
      await load(page, pageSize);
    } finally {
      setActioningId(null);
    }
  }

  function openPush(record: SupplierProductPackageVO) {
    setPushRecord(record);
    setPushShopId(undefined);
    setPushOpen(true);
    if (!targetShopOptions.length) {
      void loadTargetShops();
    }
  }

  async function handlePush() {
    if (!pushRecord) {
      return;
    }
    if (!pushShopId) {
      message.error('请选择目标店铺');
      return;
    }
    setActioningId(pushRecord.id);
    try {
      const res = await supplierProductPackagesApi.pushToProductCollection(pushRecord.id, { targetShopIds: [pushShopId] });
      message.success(`已推送到采集商品库：${res.data?.productCollectionId || ''}`);
      setPushOpen(false);
      await load(page, pageSize);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '推送采集商品库失败');
    } finally {
      setActioningId(null);
    }
  }

  const summary = useMemo(() => ({
    pending: rows.filter((item) => item.status === 'PENDING').length,
    generated: rows.filter((item) => item.status === 'GENERATED').length,
  }), [rows]);

  const columns: ColumnsType<SupplierProductPackageVO> = [
    {
      title: '商品',
      key: 'product',
      width: 320,
      render: (_, record) => (
        <Space align="start">
          {record.sourceImageUrls?.[0] ? (
            <Image src={record.sourceImageUrls[0]} width={56} height={56} style={{ objectFit: 'cover', borderRadius: 6 }} />
          ) : null}
          <Space direction="vertical" size={2}>
            <Typography.Text strong>{record.productName || '-'}</Typography.Text>
            <Typography.Text type="secondary">{record.supplierName || '-'}</Typography.Text>
            <Typography.Text type="secondary">供货价：{record.supplyPrice == null ? '-' : `¥${record.supplyPrice}`}</Typography.Text>
          </Space>
        </Space>
      ),
    },
    {
      title: 'AI标题',
      key: 'title',
      width: 320,
      render: (_, record) => (
        <Typography.Paragraph ellipsis={{ rows: 3, tooltip: record.aiTitle || '' }} style={{ marginBottom: 0 }}>
          {record.aiTitle || '-'}
        </Typography.Paragraph>
      ),
    },
    {
      title: '生成图',
      key: 'images',
      width: 260,
      render: (_, record) => (
        <Image.PreviewGroup>
          <Space wrap size={4}>
            {(record.generatedImageUrls || []).slice(0, 5).map((url) => (
              <Image key={url} src={url} width={44} height={44} style={{ objectFit: 'cover', borderRadius: 4 }} />
            ))}
            {(!record.generatedImageUrls || record.generatedImageUrls.length === 0) ? '-' : null}
          </Space>
        </Image.PreviewGroup>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: statusTag,
    },
    {
      title: '采集库',
      key: 'collection',
      width: 120,
      render: (_, record) => record.productCollectionId ? (
        <Link to={`/platform/product-collections/${record.productCollectionId}`}>SPU {record.productCollectionId}</Link>
      ) : '-',
    },
    {
      title: '错误',
      dataIndex: 'lastError',
      key: 'lastError',
      width: 260,
      render: (value: string | null) => (
        <Typography.Paragraph ellipsis={{ rows: 2, tooltip: value || '' }} style={{ marginBottom: 0 }}>
          {value || '-'}
        </Typography.Paragraph>
      ),
    },
    {
      title: '更新时间',
      key: 'updatedAt',
      width: 170,
      render: (_, record) => formatDateTime(record.updatedAt),
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space wrap>
          <Button
            size="small"
            type="primary"
            loading={actioningId === record.id}
            disabled={record.status === 'GENERATING'}
            onClick={() => void handleGenerate(record)}
          >
            AI生成素材
          </Button>
          <Button
            size="small"
            loading={actioningId === record.id}
            disabled={record.status !== 'GENERATED' && !record.generatedImageUrls?.length}
            onClick={() => openPush(record)}
          >
            推采集库
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Input.Search
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onSearch={() => void load(1, pageSize)}
            allowClear
            placeholder="搜索商品 / 供应商 / 备注"
            style={{ width: 300 }}
          />
          <Select
            value={status}
            onChange={setStatus}
            allowClear
            placeholder="包装状态"
            style={{ width: 150 }}
            options={statusOptions.map(({ label, value }) => ({ label, value }))}
          />
          <Button loading={loading} onClick={() => void load(1, pageSize)}>刷新</Button>
          <Tag color="orange">本页待生成 {summary.pending}</Tag>
          <Tag color="green">本页已生成 {summary.generated}</Tag>
        </Space>
      </Card>
      <Card>
        <Table<SupplierProductPackageVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1620 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (currentTotal) => `共 ${currentTotal} 条`,
            onChange: (nextPage, nextSize) => void load(nextPage, nextSize),
          }}
        />
      </Card>
      <Modal
        title="推送到采集商品库"
        open={pushOpen}
        confirmLoading={actioningId === pushRecord?.id}
        onCancel={() => setPushOpen(false)}
        onOk={() => void handlePush()}
        okText="确认推送"
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Typography.Text type="secondary">
            {pushRecord?.productName || '-'}
          </Typography.Text>
          <Select
            value={pushShopId}
            onChange={setPushShopId}
            loading={loadingTargetShops}
            options={targetShopOptions}
            placeholder="请选择目标店铺"
            style={{ width: '100%' }}
            showSearch
            optionFilterProp="label"
          />
        </Space>
      </Modal>
    </Space>
  );
};

export default SupplierProductPackagesPage;
