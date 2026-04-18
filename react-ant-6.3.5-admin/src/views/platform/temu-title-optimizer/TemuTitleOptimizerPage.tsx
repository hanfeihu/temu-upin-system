import { App, Button, Card, Drawer, Input, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { productCollectionsApi } from '@/api/productCollections';
import { temuShopsApi } from '@/api/temuShops';
import type { ProductCollectionDetailVO, ProductCollectionRow, TemuShopVO, TemuTitleOptimizationResponseVO } from '@/types/api';
import { prettyJson } from '@/utils/format';

interface Filters {
  q: string;
  targetShopId?: string;
}

const initialFilters: Filters = {
  q: '',
  targetShopId: undefined,
};

const TemuTitleOptimizerPage = () => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<ProductCollectionRow[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const [shopOptions, setShopOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [running, setRunning] = useState<number | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<ProductCollectionRow | null>(null);
  const [detail, setDetail] = useState<ProductCollectionDetailVO | null>(null);
  const [result, setResult] = useState<TemuTitleOptimizationResponseVO | null>(null);

  async function loadShops() {
    const res = await temuShopsApi.list({ enabled: true });
    const shops = Array.isArray(res.data) ? res.data : [];
    setShopOptions(
      shops
        .filter((item: TemuShopVO) => item.shopId && item.shopName)
        .map((item: TemuShopVO) => ({ value: item.shopId, label: item.shopName })),
    );
  }

  async function load(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true);
    try {
      const res = await productCollectionsApi.list({
        q: nextFilters.q.trim() || undefined,
        targetShopId: nextFilters.targetShopId,
        page: nextPage - 1,
        size: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadShops();
    void load(1, 20, initialFilters);
  }, []);

  async function openDetail(record: ProductCollectionRow) {
    setDrawerOpen(true);
    setDetailLoading(true);
    setSelectedRecord(record);
    setResult(null);
    try {
      const res = await productCollectionsApi.get(record.id);
      setDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  async function generate(record: ProductCollectionRow) {
    setRunning(record.id);
    setDrawerOpen(true);
    setSelectedRecord(record);
    try {
      const [detailRes, generateRes] = await Promise.all([
        productCollectionsApi.get(record.id),
        productCollectionsApi.generateTemuTitleOptimization(record.id),
      ]);
      setDetail(detailRes.data);
      setResult(generateRes.data);
      message.success('标题优化已生成');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '生成失败');
    } finally {
      setRunning(null);
    }
  }

  const columns: ColumnsType<ProductCollectionRow> = [
    {
      title: '商品',
      key: 'product',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text ellipsis style={{ maxWidth: 280 }}>
            {record.productName || '-'}
          </Typography.Text>
          <Typography.Text type="secondary">{record.productId || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '类目',
      key: 'category',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <span>{record.temuCatname || '-'}</span>
          {record.temuCatid ? <Tag>{record.temuCatid}</Tag> : null}
        </Space>
      ),
    },
    {
      title: '店铺',
      key: 'shops',
      width: 220,
      render: (_, record) => (record.targetShopNames?.length ? record.targetShopNames.join(' / ') : '-'),
    },
    {
      title: '发布状态',
      key: 'collectionStatus',
      width: 120,
      render: (_, record) => <Tag color={record.collectionStatus === 3 ? 'green' : 'default'}>{record.collectionStatus ?? '-'}</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void openDetail(record)}>
            查看
          </Button>
          <Button size="small" type="primary" ghost loading={running === record.id} onClick={() => void generate(record)}>
            生成
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Input value={filters.q} onChange={(e) => setFilters((current) => ({ ...current, q: e.target.value }))} placeholder="按商品名 / 产品ID搜索" allowClear style={{ width: 260 }} />
          <Select value={filters.targetShopId} onChange={(value) => setFilters((current) => ({ ...current, targetShopId: value }))} allowClear placeholder="店铺" style={{ width: 220 }} options={shopOptions} />
          <Button type="primary" loading={loading} onClick={() => {
            setPage(1);
            void load(1, pageSize, filters);
          }}>
            查询
          </Button>
          <Button onClick={() => {
            setFilters(initialFilters);
            setPage(1);
            setPageSize(20);
            void load(1, 20, initialFilters);
          }}>
            重置
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<ProductCollectionRow>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
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
            void load(nextPage, nextPageSize, filters);
          }}
        />
      </Card>

      <Drawer open={drawerOpen} width={860} title="标题优化详情" onClose={() => setDrawerOpen(false)} loading={detailLoading}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Card size="small" title="当前商品">
            <Space direction="vertical" size={4}>
              <span>商品: {selectedRecord?.productName || '-'}</span>
              <span>类目: {selectedRecord?.temuCatname || '-'}</span>
              <span>当前英文标题: {detail?.temuOptimizedTitleEn || '-'}</span>
              <span>当前中文标题: {detail?.temuOptimizedTitleZh || '-'}</span>
              <span>当前类目关键词: {detail?.temuCategoryKeywords || '-'}</span>
            </Space>
          </Card>
          <Card size="small" title="本次生成结果">
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              <Typography.Paragraph>{result?.optimizedTitleZh || '尚未生成'}</Typography.Paragraph>
              {result?.optimizedTitleEn ? <Typography.Paragraph>{result.optimizedTitleEn}</Typography.Paragraph> : null}
              {result?.categoryKeywords ? <Typography.Paragraph type="secondary">{result.categoryKeywords}</Typography.Paragraph> : null}
              {result?.errorMsg ? <Typography.Text type="danger">{result.errorMsg}</Typography.Text> : null}
              <pre style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{prettyJson(result)}</pre>
            </Space>
          </Card>
        </Space>
      </Drawer>
    </Space>
  );
};

export default TemuTitleOptimizerPage;
