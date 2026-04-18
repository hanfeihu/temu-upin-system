import {
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tabs,
  Typography,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { syncApi } from '@/api/sync';
import { temuShopsApi } from '@/api/temuShops';
import type {
  ActivityDetailVO,
  ActivityEnrollmentVO,
  ActivityMatchedProductVO,
  ActivityMatchedSkcVO,
  ActivityMatchedSkuVO,
  ActivitySessionQueryResponseVO,
  ActivitySessionVO,
  ActivityThematicVO,
  ActivityVO,
  TemuShopVO,
} from '@/types/api';
import { formatDateTime, formatPrice } from '@/utils/format';

interface MatchedProductFormVO extends ActivityMatchedProductVO {
  activityStock?: number | null;
  sessionIds: number[];
}

function formatUnixSeconds(value: number | null | undefined) {
  if (!value) {
    return '-';
  }
  const date = new Date(value * 1000);
  const pad = (input: number) => String(input).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function buildSitePricePayload(items: Array<{ siteId: number | null; activityPrice: number | null; suggestActivityPrice: number | null }>) {
  return items
    .filter((item) => item.siteId !== null && item.siteId !== undefined)
    .map((item) => ({
      siteId: Number(item.siteId),
      activityPrice: item.activityPrice ?? item.suggestActivityPrice ?? undefined,
    }))
    .filter((item) => item.activityPrice !== undefined && item.activityPrice !== null);
}

const activityTypeOptions = [
  { value: 1, label: '平台活动' },
  { value: 2, label: '主题活动' },
  { value: 3, label: '大促活动' },
];

const enrollStatusOptions = [
  { value: 1, label: '报名中' },
  { value: 2, label: '报名失败' },
  { value: 3, label: '报名成功待分配场次' },
  { value: 4, label: '报名成功已分配场次' },
  { value: 5, label: '报名活动已结束' },
  { value: 6, label: '报名活动已下线' },
];

const SyncActivityPage = () => {
  const { message } = App.useApp();
  const [shops, setShops] = useState<Array<{ value: string; label: string }>>([]);
  const [shopId, setShopId] = useState<string>();
  const [activityType, setActivityType] = useState<number | undefined>(undefined);
  const [enrollStatus, setEnrollStatus] = useState<number | undefined>(undefined);
  const [loadingActivities, setLoadingActivities] = useState(false);
  const [activities, setActivities] = useState<ActivityVO[]>([]);
  const [loadingEnrollments, setLoadingEnrollments] = useState(false);
  const [enrollments, setEnrollments] = useState<ActivityEnrollmentVO[]>([]);
  const [enrollmentPage, setEnrollmentPage] = useState(1);
  const [enrollmentPageSize, setEnrollmentPageSize] = useState(20);
  const [enrollmentTotal, setEnrollmentTotal] = useState(0);
  const [selectedActivityId, setSelectedActivityId] = useState<number>();
  const [selectedThematicId, setSelectedThematicId] = useState<number>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [activityDetail, setActivityDetail] = useState<ActivityDetailVO | null>(null);
  const [matchLoading, setMatchLoading] = useState(false);
  const [matchedProducts, setMatchedProducts] = useState<MatchedProductFormVO[]>([]);
  const [rowCount, setRowCount] = useState<number>(10);
  const [productIdsText, setProductIdsText] = useState('');
  const [sessionLoading, setSessionLoading] = useState(false);
  const [sessionQueryResult, setSessionQueryResult] = useState<ActivitySessionQueryResponseVO | null>(null);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerRecord, setDrawerRecord] = useState<ActivityEnrollmentVO | null>(null);
  const [drawerLoading, setDrawerLoading] = useState(false);

  const selectedActivity = useMemo(
    () => activities.find((item) => item.id === selectedActivityId) || null,
    [activities, selectedActivityId],
  );
  const thematicOptions = useMemo(
    () =>
      (selectedActivity?.thematicList || []).map((item: ActivityThematicVO) => ({
        value: Number(item.activityThematicId),
        label: item.activityThematicName || `主题 ${item.activityThematicId}`,
      })),
    [selectedActivity],
  );

  async function loadShops() {
    const res = await temuShopsApi.list({ enabled: true });
    const data = Array.isArray(res.data) ? res.data : [];
    const options = data
      .filter((item: TemuShopVO) => item.shopId && item.shopName)
      .map((item: TemuShopVO) => ({ value: item.shopId, label: item.shopName }));
    setShops(options);
    if (!shopId && options[0]) {
      setShopId(options[0].value);
      await Promise.all([loadActivities(options[0].value, activityType), loadEnrollments(1, 20, options[0].value, activityType, enrollStatus)]);
    }
  }

  async function loadActivities(nextShopId = shopId, nextActivityType = activityType) {
    if (!nextShopId) return;
    setLoadingActivities(true);
    try {
      const res = await syncApi.getActivityList({ shopId: nextShopId, activityType: nextActivityType });
      setActivities(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载活动失败');
    } finally {
      setLoadingActivities(false);
    }
  }

  async function loadEnrollments(
    nextPage = enrollmentPage,
    nextPageSize = enrollmentPageSize,
    nextShopId = shopId,
    nextActivityType = activityType,
    nextEnrollStatus = enrollStatus,
  ) {
    if (!nextShopId) return;
    setLoadingEnrollments(true);
    try {
      const res = await syncApi.getEnrollmentList({
        shopId: nextShopId,
        activityType: nextActivityType,
        enrollStatus: nextEnrollStatus,
        page: nextPage,
        pageSize: nextPageSize,
      });
      setEnrollments(Array.isArray(res.data.content) ? res.data.content : []);
      setEnrollmentTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载报名记录失败');
    } finally {
      setLoadingEnrollments(false);
    }
  }

  useEffect(() => {
    void loadShops();
  }, []);

  async function loadDetail() {
    if (!shopId || !selectedActivity) {
      message.warning('请先选择活动');
      return;
    }
    setDetailLoading(true);
    try {
      const res = await syncApi.getActivityDetail({
        shopId,
        activityType: Number(selectedActivity.activityType),
        activityThematicId: selectedThematicId,
      });
      setActivityDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载活动详情失败');
    } finally {
      setDetailLoading(false);
    }
  }

  async function matchProducts() {
    if (!shopId || !selectedActivity) {
      message.warning('请先选择活动');
      return;
    }
    setMatchLoading(true);
    try {
      const productIds = productIdsText
        .split(/[\s,，]+/)
        .map((item) => item.trim())
        .filter(Boolean)
        .map((item) => Number(item))
        .filter((item) => !Number.isNaN(item));

      const res = await syncApi.matchActivityProducts({
        shopId,
        activityType: Number(selectedActivity.activityType),
        activityThematicId: selectedThematicId,
        rowCount,
        productIds: productIds.length ? productIds : undefined,
      });
      const data = Array.isArray(res.data.matchList) ? res.data.matchList : [];
      setMatchedProducts(
        data.map((item) => ({
          ...item,
          activityStock: item.suggestActivityStock ?? item.targetActivityStock ?? null,
          sessionIds: item.enrollSessionIdList || [],
        })),
      );
      message.success(`已匹配 ${data.length} 个商品`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '匹配商品失败');
    } finally {
      setMatchLoading(false);
    }
  }

  async function querySessions() {
    if (!shopId || !selectedActivity) {
      message.warning('请先选择活动');
      return;
    }
    setSessionLoading(true);
    try {
      const res = await syncApi.queryActivitySessions({
        shopId,
        activityType: Number(selectedActivity.activityType),
        activityThematicId: selectedThematicId,
        productIds: matchedProducts.map((item) => item.productId),
      });
      setSessionQueryResult(res.data);
      message.success('可报名场次已刷新');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '查询场次失败');
    } finally {
      setSessionLoading(false);
    }
  }

  function getSessionOptions(record: MatchedProductFormVO) {
    const specific = sessionQueryResult?.productCanEnrollSessionMap?.[String(record.productId)] || [];
    const fallback = sessionQueryResult?.list || [];
    const source = specific.length ? specific : fallback;
    return source.map((item: ActivitySessionVO) => ({
      value: Number(item.sessionId),
      label: `${item.sessionName || item.sessionId} / ${item.siteName || '-'} / ${formatUnixSeconds(item.startTime)}`,
    }));
  }

  function updateMatchedProduct(productId: number, patch: Partial<MatchedProductFormVO>) {
    setMatchedProducts((current) => current.map((item) => (item.productId === productId ? { ...item, ...patch } : item)));
  }

  async function submitEnroll() {
    if (!shopId || !selectedActivity) {
      message.warning('请先选择活动');
      return;
    }
    if (!matchedProducts.length) {
      message.warning('请先匹配可报名商品');
      return;
    }
    setSubmitLoading(true);
    try {
      const payload = {
        shopId,
        activityType: Number(selectedActivity.activityType),
        activityThematicId: selectedThematicId,
        productList: matchedProducts.map((product) => {
          if (!product.activityStock) {
            throw new Error(`商品 ${product.productId} 还没有设置活动库存`);
          }
          return {
            productId: product.productId,
            activityStock: Number(product.activityStock),
            sessionIds: product.sessionIds.length ? product.sessionIds : undefined,
            skcList: (product.skcList || []).map((skc: ActivityMatchedSkcVO) => ({
              skcId: skc.skcId,
              activityPrice: skc.activityPrice ?? skc.suggestActivityPrice ?? undefined,
              siteActivityPriceList: buildSitePricePayload(skc.sitePriceList || []),
              skuList: (skc.skuList || []).map((sku: ActivityMatchedSkuVO) => ({
                skuId: sku.skuId,
                activityPrice: sku.activityPrice ?? sku.suggestActivityPrice ?? undefined,
                siteActivityPriceList: buildSitePricePayload(sku.sitePriceList || []),
              })),
            })),
          };
        }),
      };
      await syncApi.batchEnroll(payload as Record<string, unknown>);
      message.success('批量报名已提交');
      await loadEnrollments(1, enrollmentPageSize, shopId, activityType, enrollStatus);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '批量报名失败');
    } finally {
      setSubmitLoading(false);
    }
  }

  async function openEnrollmentDetail(record: ActivityEnrollmentVO) {
    setDrawerOpen(true);
    setDrawerLoading(true);
    try {
      const res = await syncApi.getEnrollmentDetail(record.id);
      setDrawerRecord(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      setDrawerLoading(false);
    }
  }

  const activityColumns: ColumnsType<ActivityVO> = [
    {
      title: '活动',
      key: 'activityName',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{record.activityName || '-'}</Typography.Text>
          <Typography.Text type="secondary">{record.activityContent || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '类型',
      key: 'activityType',
      width: 140,
      render: (_, record) => activityTypeOptions.find((item) => item.value === record.activityType)?.label || record.activityType || '-',
    },
    {
      title: '主题数',
      key: 'thematicList',
      width: 100,
      render: (_, record) => record.thematicList?.length || 0,
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            type={selectedActivityId === record.id ? 'primary' : 'default'}
            onClick={() => {
              setSelectedActivityId(record.id);
              setSelectedThematicId(record.thematicList?.[0]?.activityThematicId || undefined);
            }}
          >
            选中
          </Button>
          <Button size="small" onClick={() => {
            setSelectedActivityId(record.id);
            setSelectedThematicId(record.thematicList?.[0]?.activityThematicId || undefined);
            void loadDetail();
          }}>
            详情
          </Button>
        </Space>
      ),
    },
  ];

  const enrollmentColumns: ColumnsType<ActivityEnrollmentVO> = [
    { title: '报名ID', dataIndex: 'enrollId', key: 'enrollId', width: 140 },
    { title: '商品ID', dataIndex: 'productId', key: 'productId', width: 120 },
    { title: '活动类型', dataIndex: 'activityTypeName', key: 'activityTypeName', width: 140 },
    { title: '主题', dataIndex: 'activityThematicName', key: 'activityThematicName', width: 220 },
    {
      title: '状态',
      key: 'enrollStatus',
      width: 180,
      render: (_, record) => enrollStatusOptions.find((item) => item.value === record.enrollStatus)?.label || record.enrollStatus || '-',
    },
    { title: '活动库存', dataIndex: 'activityStock', key: 'activityStock', width: 110 },
    {
      title: '同步时间',
      dataIndex: 'syncedAt',
      key: 'syncedAt',
      width: 180,
      render: (value: ActivityEnrollmentVO['syncedAt']) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_, record) => (
        <Button size="small" onClick={() => void openEnrollmentDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  const matchedColumns: ColumnsType<MatchedProductFormVO> = [
    {
      title: '商品',
      key: 'productName',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{record.productName || '-'}</Typography.Text>
          <Typography.Text type="secondary">
            Product {record.productId} / {record.extCode || '-'}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '建议活动价',
      key: 'suggestPrice',
      width: 180,
      render: (_, record) => {
        const firstSkc = record.skcList?.[0];
        return formatPrice(firstSkc?.suggestActivityPrice ?? firstSkc?.activityPrice ?? null);
      },
    },
    {
      title: '活动库存',
      key: 'activityStock',
      width: 140,
      render: (_, record) => (
        <InputNumber
          value={record.activityStock ?? undefined}
          onChange={(value) => updateMatchedProduct(record.productId, { activityStock: value ?? null })}
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: '可报名场次',
      key: 'sessionIds',
      width: 320,
      render: (_, record) => (
        <Select
          mode="multiple"
          value={record.sessionIds}
          onChange={(value) => updateMatchedProduct(record.productId, { sessionIds: value })}
          options={getSessionOptions(record)}
          style={{ width: '100%' }}
          placeholder="未选择则按后台默认策略"
        />
      ),
    },
    {
      title: 'SKU / SKC 数',
      key: 'skuCount',
      width: 120,
      render: (_, record) => `${record.skcList?.length || 0} 个 SKC`,
    },
  ];

  return (
    <>
      <Tabs
        items={[
        {
          key: 'activities',
          label: '活动列表',
          children: (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Card>
                <Space wrap>
                  <Select
                    value={shopId}
                    onChange={(value) => {
                      setShopId(value);
                      void Promise.all([loadActivities(value, activityType), loadEnrollments(1, enrollmentPageSize, value, activityType, enrollStatus)]);
                    }}
                    placeholder="店铺"
                    style={{ width: 220 }}
                    options={shops}
                  />
                  <Select value={activityType} onChange={setActivityType} allowClear placeholder="活动类型" style={{ width: 160 }} options={activityTypeOptions} />
                  <Button type="primary" loading={loadingActivities} onClick={() => void loadActivities(shopId, activityType)}>
                    查询活动
                  </Button>
                  <Button onClick={() => {
                    setActivityType(undefined);
                    void loadActivities(shopId, undefined);
                  }}>
                    重置
                  </Button>
                </Space>
              </Card>
              <Card>
                <Table<ActivityVO> rowKey="id" loading={loadingActivities} columns={activityColumns} dataSource={activities} pagination={false} />
              </Card>
            </Space>
          ),
        },
        {
          key: 'enrollments',
          label: '报名记录',
          children: (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Card>
                <Space wrap>
                  <Select value={shopId} onChange={setShopId} placeholder="店铺" style={{ width: 220 }} options={shops} />
                  <Select value={activityType} onChange={setActivityType} allowClear placeholder="活动类型" style={{ width: 160 }} options={activityTypeOptions} />
                  <Select value={enrollStatus} onChange={setEnrollStatus} allowClear placeholder="报名状态" style={{ width: 180 }} options={enrollStatusOptions} />
                  <Button type="primary" loading={loadingEnrollments} onClick={() => {
                    setEnrollmentPage(1);
                    void loadEnrollments(1, enrollmentPageSize, shopId, activityType, enrollStatus);
                  }}>
                    查询记录
                  </Button>
                </Space>
              </Card>
              <Card>
                <Table<ActivityEnrollmentVO>
                  rowKey="id"
                  loading={loadingEnrollments}
                  columns={enrollmentColumns}
                  dataSource={enrollments}
                  scroll={{ x: 1200 }}
                  pagination={{
                    current: enrollmentPage,
                    pageSize: enrollmentPageSize,
                    total: enrollmentTotal,
                    showSizeChanger: true,
                    showTotal: (count) => `共 ${count} 条`,
                  }}
                  onChange={(pagination: TablePaginationConfig) => {
                    const nextPage = pagination.current || 1;
                    const nextPageSize = pagination.pageSize || 20;
                    setEnrollmentPage(nextPage);
                    setEnrollmentPageSize(nextPageSize);
                    void loadEnrollments(nextPage, nextPageSize, shopId, activityType, enrollStatus);
                  }}
                />
              </Card>
            </Space>
          ),
        },
        {
          key: 'workbench',
          label: '报名工作台',
          children: (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Card>
                <Form layout="inline">
                  <Form.Item label="活动">
                    <Select
                      value={selectedActivityId}
                      onChange={(value) => {
                        setSelectedActivityId(value);
                        const target = activities.find((item) => item.id === value);
                        setSelectedThematicId(target?.thematicList?.[0]?.activityThematicId || undefined);
                      }}
                      style={{ width: 280 }}
                      options={activities.map((item) => ({
                        value: item.id,
                        label: `${item.activityName || '-'} / ${activityTypeOptions.find((option) => option.value === item.activityType)?.label || item.activityType || '-'}`,
                      }))}
                    />
                  </Form.Item>
                  <Form.Item label="主题">
                    <Select value={selectedThematicId} onChange={setSelectedThematicId} allowClear style={{ width: 240 }} options={thematicOptions} />
                  </Form.Item>
                  <Form.Item label="匹配数量">
                    <InputNumber value={rowCount} onChange={(value) => setRowCount(value || 10)} min={1} max={100} />
                  </Form.Item>
                  <Form.Item label="指定商品ID">
                    <Input value={productIdsText} onChange={(e) => setProductIdsText(e.target.value)} placeholder="逗号分隔，可选" style={{ width: 260 }} />
                  </Form.Item>
                </Form>
                <Space wrap style={{ marginTop: 16 }}>
                  <Button onClick={() => void loadDetail()} loading={detailLoading}>
                    加载活动详情
                  </Button>
                  <Button type="primary" loading={matchLoading} onClick={() => void matchProducts()}>
                    匹配可报名商品
                  </Button>
                  <Button loading={sessionLoading} onClick={() => void querySessions()}>
                    查询可报名场次
                  </Button>
                  <Button type="primary" ghost loading={submitLoading} onClick={() => void submitEnroll()}>
                    提交批量报名
                  </Button>
                </Space>
              </Card>

              {activityDetail ? (
                <Card title="活动详情">
                  <Descriptions column={2} size="small">
                    <Descriptions.Item label="活动名称">{activityDetail.activityInfo?.activityName || '-'}</Descriptions.Item>
                    <Descriptions.Item label="可报名">{activityDetail.canEnroll ? '是' : '否'}</Descriptions.Item>
                    <Descriptions.Item label="活动内容">{activityDetail.activityInfo?.activityContent || '-'}</Descriptions.Item>
                    <Descriptions.Item label="主题">{activityDetail.thematicInfo?.activityThematicName || '-'}</Descriptions.Item>
                    <Descriptions.Item label="报名开始">{formatUnixSeconds(activityDetail.thematicInfo?.enrollStartAt ?? null)}</Descriptions.Item>
                    <Descriptions.Item label="报名截止">{formatUnixSeconds(activityDetail.thematicInfo?.enrollDeadLine ?? null)}</Descriptions.Item>
                    <Descriptions.Item label="活动开始">{formatUnixSeconds(activityDetail.thematicInfo?.startTime ?? null)}</Descriptions.Item>
                    <Descriptions.Item label="活动结束">{formatUnixSeconds(activityDetail.thematicInfo?.endTime ?? null)}</Descriptions.Item>
                  </Descriptions>
                </Card>
              ) : null}

              <Card title={`已匹配商品 ${matchedProducts.length} 条`}>
                <Table<MatchedProductFormVO> rowKey="productId" loading={matchLoading} columns={matchedColumns} dataSource={matchedProducts} scroll={{ x: 1300 }} pagination={false} />
              </Card>
            </Space>
          ),
        },
        ]}
      />

      <Drawer open={drawerOpen} width={820} title="报名详情" onClose={() => setDrawerOpen(false)} loading={drawerLoading}>
        {drawerRecord ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card size="small">
              <Descriptions column={2} size="small">
                <Descriptions.Item label="报名ID">{drawerRecord.enrollId || '-'}</Descriptions.Item>
                <Descriptions.Item label="商品ID">{drawerRecord.productId || '-'}</Descriptions.Item>
                <Descriptions.Item label="活动类型">{drawerRecord.activityTypeName || '-'}</Descriptions.Item>
                <Descriptions.Item label="主题">{drawerRecord.activityThematicName || '-'}</Descriptions.Item>
                <Descriptions.Item label="报名状态">
                  {enrollStatusOptions.find((item) => item.value === drawerRecord.enrollStatus)?.label || drawerRecord.enrollStatus || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="活动库存">{drawerRecord.activityStock ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="报名时间">{formatUnixSeconds(drawerRecord.enrollTime)}</Descriptions.Item>
                <Descriptions.Item label="同步时间">{formatDateTime(drawerRecord.syncedAt)}</Descriptions.Item>
              </Descriptions>
            </Card>
            <Card size="small" title="价格明细">
              <Table
                rowKey={(record) => `${record.id}-${record.siteId}-${record.skuId}-${record.skcId}`}
                columns={[
                  { title: '层级', dataIndex: 'level', key: 'level', width: 100 },
                  { title: 'SKC', dataIndex: 'skcId', key: 'skcId', width: 100 },
                  { title: 'SKU', dataIndex: 'skuId', key: 'skuId', width: 100 },
                  { title: '站点', dataIndex: 'siteName', key: 'siteName', width: 140 },
                  { title: '日常价', key: 'dailyPrice', width: 120, render: (_, record) => formatPrice(record.dailyPrice) },
                  { title: '活动价', key: 'activityPrice', width: 120, render: (_, record) => formatPrice(record.activityPrice) },
                  { title: '折扣', dataIndex: 'activityDiscount', key: 'activityDiscount', width: 100 },
                ]}
                dataSource={drawerRecord.priceList || []}
                pagination={false}
              />
            </Card>
          </Space>
        ) : null}
      </Drawer>
    </>
  );
};

export default SyncActivityPage;
