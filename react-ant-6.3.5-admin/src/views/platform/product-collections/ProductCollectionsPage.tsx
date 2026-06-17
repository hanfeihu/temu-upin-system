import {
  App,
  Button,
  Card,
  Checkbox,
  Dropdown,
  Flex,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Radio,
  Row,
  Col,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { MenuProps, TablePaginationConfig } from 'antd';
import { useEffect, useState } from 'react';
import { alibabaImageProxyConfigApi } from '@/api/alibabaImageProxyConfig';
import { productCollectionsApi } from '@/api/productCollections';
import { publishLogsApi } from '@/api/publishLogs';
import { temuShopsApi } from '@/api/temuShops';
import type {
  AlibabaImageProxyConfigVO,
  ProductCollectionRow,
  PublishLogVO,
  TemuCategoryOption,
  TemuCategorySummary,
  TemuShopVO,
} from '@/types/api';
import { buildAlibabaImageProxyUrl } from '@/utils/alibabaImageProxy';
import TemuAttributesModal from './components/TemuAttributesModal';
import TemuSkuConverterModal from './components/TemuSkuConverterModal';

interface CollectionFilters {
  id?: number;
  q: string;
  skuIdKeyword: string;
  sourcePlatform?: string;
  targetShopId?: string;
  collectionStatus?: number;
  showDeleted: boolean;
  temuCatid?: string;
  moqMin?: number;
  moqMax?: number;
  carouselImageCountMin?: number;
  carouselImageCountMax?: number;
  detailImageCountMin?: number;
  detailImageCountMax?: number;
  skuCountMin?: number;
  skuCountMax?: number;
}

interface PublishTipStateItem {
  loading: boolean;
  loaded: boolean;
  lines: string[];
  error: string;
}

const initialFilters: CollectionFilters = {
  id: undefined,
  q: '',
  skuIdKeyword: '',
  sourcePlatform: undefined,
  targetShopId: undefined,
  collectionStatus: undefined,
  showDeleted: false,
  temuCatid: undefined,
  moqMin: undefined,
  moqMax: undefined,
  carouselImageCountMin: undefined,
  carouselImageCountMax: undefined,
  detailImageCountMin: undefined,
  detailImageCountMax: undefined,
  skuCountMin: undefined,
  skuCountMax: undefined,
};

const statusOptions = [
  { label: '全部', value: undefined },
  { label: '未发布', value: 0 },
  { label: '已发布', value: 3 },
  { label: '发布中', value: 1 },
  { label: '发布失败', value: 2 },
];

const lineClampTextStyle = {
  display: '-webkit-box',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  WebkitLineClamp: 2,
  WebkitBoxOrient: 'vertical' as const,
  lineHeight: 1.5,
};

function extractMoq(text?: string | null, moq?: number | null) {
  if (moq != null) {
    return String(moq);
  }

  if (!text) {
    return '';
  }

  const normalized = String(text).replace(/\s+/g, ' ').trim();
  if (!normalized) {
    return '';
  }

  const matched =
    normalized.match(/(\d+(?:\.\d+)?)\s*(个|件|只|条|双|套|箱|对|张|包|瓶|盒|台|本|幅|米)\s*起批/) ||
    normalized.match(/(\d+(?:\.\d+)?)\s*(个|件|只|条|双|套|箱|对|张|包|瓶|盒|台|本|幅|米)\s*预估/) ||
    normalized.match(/(\d+(?:\.\d+)?)\s*(个|件|只|条|双|套|箱|对|张|包|瓶|盒|台|本|幅|米)\s*起/) ||
    normalized.match(/\b(\d+(?:\.\d+)?)\b/);

  if (!matched) {
    return '';
  }

  return matched[2] ? `${matched[1]}${matched[2]}` : matched[1];
}

function extractPublishFailLines(logs: PublishLogVO[]) {
  const pickLine = (log: PublishLogVO) => {
    const stage = log.stage ? `[${log.stage}]` : '';
    const message = log.message || '';
    return `${stage}${message}`.trim() || '(empty message)';
  };

  const errors = logs
    .filter((item) => String(item.level || '').toUpperCase() === 'ERROR')
    .slice(-6)
    .map(pickLine);

  if (errors.length) {
    return errors;
  }

  const warnings = logs
    .filter((item) => String(item.level || '').toUpperCase() === 'WARN')
    .slice(-6)
    .map(pickLine);

  if (warnings.length) {
    return warnings;
  }

  for (let index = logs.length - 1; index >= 0; index -= 1) {
    const record = logs[index];
    if (!record.dataJson) {
      continue;
    }

    try {
      const outer = JSON.parse(record.dataJson) as { raw?: string; errorMsg?: string; errorCode?: string | number };
      const inner = outer.raw ? (JSON.parse(outer.raw) as { errorMsg?: string; errorCode?: string | number }) : null;
      const errorCode = inner?.errorCode ?? outer.errorCode;
      const errorMsg = inner?.errorMsg ?? outer.errorMsg;
      if (errorCode || errorMsg) {
        return [
          [errorCode ? `errorCode=${errorCode}` : '', errorMsg ? `errorMsg=${errorMsg}` : '']
            .filter(Boolean)
            .join(' '),
        ];
      }
    } catch {
      // ignore parse errors
    }
  }

  return logs
    .filter((item) => String(item.level || '').toUpperCase() === 'INFO')
    .slice(-2)
    .map(pickLine);
}

function renderRangeInput(
  minValue: number | undefined,
  maxValue: number | undefined,
  onChangeMin: (value: number | null) => void,
  onChangeMax: (value: number | null) => void,
) {
  return (
    <Space.Compact block>
      <InputNumber value={minValue} min={0} placeholder="最小" style={{ width: '50%' }} onChange={onChangeMin} />
      <InputNumber value={maxValue} min={0} placeholder="最大" style={{ width: '50%' }} onChange={onChangeMax} />
    </Space.Compact>
  );
}

const ProductCollectionsPage = () => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<ProductCollectionRow[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState<CollectionFilters>(initialFilters);
  const [targetShopOptions, setTargetShopOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [loadingTargetShops, setLoadingTargetShops] = useState(false);
  const [temuCategoryOptions, setTemuCategoryOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [loadingTemuCategories, setLoadingTemuCategories] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<ProductCollectionRow | null>(null);
  const [editingTitle, setEditingTitle] = useState('');
  const [editingShopId, setEditingShopId] = useState<string | undefined>();
  const [savingEdit, setSavingEdit] = useState(false);
  const [matchOpen, setMatchOpen] = useState(false);
  const [matchingRecord, setMatchingRecord] = useState<ProductCollectionRow | null>(null);
  const [matchOptions, setMatchOptions] = useState<TemuCategoryOption[]>([]);
  const [selectedMatchKey, setSelectedMatchKey] = useState<string>();
  const [savingMatch, setSavingMatch] = useState(false);
  const [temuAttrOpen, setTemuAttrOpen] = useState(false);
  const [temuAttrRecord, setTemuAttrRecord] = useState<ProductCollectionRow | null>(null);
  const [skuConvertOpen, setSkuConvertOpen] = useState(false);
  const [skuConvertRecord, setSkuConvertRecord] = useState<ProductCollectionRow | null>(null);
  const [publishTipState, setPublishTipState] = useState<Record<string, PublishTipStateItem>>({});
  const [imageProxyConfig, setImageProxyConfig] = useState<AlibabaImageProxyConfigVO | null>(null);

  async function loadImageProxyConfig() {
    try {
      const res = await alibabaImageProxyConfigApi.current();
      setImageProxyConfig(res.data || null);
    } catch {
      setImageProxyConfig(null);
    }
  }

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

  async function loadTemuCategories(showDeleted: boolean) {
    setLoadingTemuCategories(true);
    try {
      const res = await productCollectionsApi.listTemuCategories({ showDeleted: showDeleted || undefined });
      const categories = Array.isArray(res.data) ? res.data : [];
      setTemuCategoryOptions(
        categories
          .filter((item: TemuCategorySummary) => item.temuCatid && item.temuCatname)
          .map((item: TemuCategorySummary) => ({
            value: String(item.temuCatid),
            label: String(item.temuCatname),
          })),
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载类目失败');
    } finally {
      setLoadingTemuCategories(false);
    }
  }

  async function fetchList(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true);
    try {
      const res = await productCollectionsApi.list({
        id: nextFilters.id,
        q: nextFilters.q || undefined,
        skuIdKeyword: nextFilters.skuIdKeyword || undefined,
        sourcePlatform: nextFilters.sourcePlatform || undefined,
        targetShopId: nextFilters.targetShopId || undefined,
        collectionStatus: nextFilters.collectionStatus,
        showDeleted: nextFilters.showDeleted || undefined,
        temuCatid: nextFilters.temuCatid || undefined,
        moqMin: nextFilters.moqMin,
        moqMax: nextFilters.moqMax,
        carouselImageCountMin: nextFilters.carouselImageCountMin,
        carouselImageCountMax: nextFilters.carouselImageCountMax,
        detailImageCountMin: nextFilters.detailImageCountMin,
        detailImageCountMax: nextFilters.detailImageCountMax,
        skuCountMin: nextFilters.skuCountMin,
        skuCountMax: nextFilters.skuCountMax,
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
    void loadImageProxyConfig();
    void loadTargetShops();
    void loadTemuCategories(initialFilters.showDeleted);
    void fetchList(1, 20, initialFilters);
  }, []);

  useEffect(() => {
    void loadTemuCategories(filters.showDeleted);
  }, [filters.showDeleted]);

  function updateFilter<K extends keyof CollectionFilters>(key: K, value: CollectionFilters[K]) {
    setFilters((current) => ({
      ...current,
      [key]: value,
    }));
  }

  function reload() {
    setPage(1);
    void fetchList(1, pageSize, filters);
  }

  function reset() {
    setFilters(initialFilters);
    setPage(1);
    setPageSize(20);
    void loadTemuCategories(false);
    void fetchList(1, 20, initialFilters);
  }

  async function loadPublishTip(runId: number) {
    const key = String(runId);
    const current = publishTipState[key];
    if (current?.loading || current?.loaded) {
      return;
    }

    setPublishTipState((state) => ({
      ...state,
      [key]: { loading: true, loaded: false, lines: [], error: '' },
    }));

    try {
      const res = await publishLogsApi.listLogs(runId);
      setPublishTipState((state) => ({
        ...state,
        [key]: {
          loading: false,
          loaded: true,
          lines: extractPublishFailLines(Array.isArray(res.data) ? res.data : []),
          error: '',
        },
      }));
    } catch (error) {
      setPublishTipState((state) => ({
        ...state,
        [key]: {
          loading: false,
          loaded: true,
          lines: [],
          error: error instanceof Error ? error.message : '加载失败',
        },
      }));
    }
  }

  function openEdit(record: ProductCollectionRow) {
    setEditingRecord(record);
    setEditingTitle(record.productName || '');
    setEditingShopId(record.targetShopIds?.length === 1 ? record.targetShopIds[0] : undefined);
    setEditOpen(true);
  }

  async function saveEdit() {
    if (!editingRecord?.id) {
      return;
    }

    const title = editingTitle.trim();
    if (!title) {
      message.error('标题不能为空');
      return;
    }
    if (!editingShopId) {
      message.error('请选择一个店铺');
      return;
    }

    setSavingEdit(true);
    try {
      await productCollectionsApi.update(editingRecord.id, {
        productName: title,
        targetShopIds: [editingShopId],
      });
      message.success('已保存');
      setEditOpen(false);
      await fetchList();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSavingEdit(false);
    }
  }

  async function openMatchCategory(record: ProductCollectionRow) {
    setMatchingRecord(record);
    setSelectedMatchKey(undefined);
    setMatchOptions([]);
    try {
      const res = await productCollectionsApi.matchTemuCategory(record.id);
      setMatchOptions(Array.isArray(res.data.options) ? res.data.options : []);
      setMatchOpen(true);
      if (!res.data.options?.length) {
        message.warning('未匹配到可选类目');
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '匹配失败');
    }
  }

  async function saveMatchCategory() {
    if (!matchingRecord?.id || !selectedMatchKey) {
      return;
    }

    const selected = matchOptions.find((item) => String(item.leafId) === String(selectedMatchKey));
    if (!selected) {
      return;
    }

    setSavingMatch(true);
    try {
      await productCollectionsApi.saveTemuCategory(matchingRecord.id, {
        temuCatid: String(selected.pathIds || selected.leafId),
        temuCatname: String(selected.pathNames || selected.leafName),
      });
      message.success('已保存 TEMU 类目');
      setMatchOpen(false);
      await fetchList();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSavingMatch(false);
    }
  }

  function canPublish(record: ProductCollectionRow) {
    return !!(record.temuCatid && record.temuCatname);
  }

  function publish(record: ProductCollectionRow) {
    Modal.confirm({
      title: '发布到 TEMU？',
      content: '将自动检查图片与发布数据，然后推送到店铺。',
      okText: '发布',
      cancelText: '取消',
      async onOk() {
        const loadingKey = `publish-${record.id}`;
        message.loading({ content: '正在发布（可能需要 1-3 分钟）...', key: loadingKey, duration: 0 });
        try {
          const res = await productCollectionsApi.publishToTemu(record.id);
          message.success({
            content: `发布成功 goodsId=${res.data.goodsId || '-'}${res.data.runId ? ` (runId=${res.data.runId})` : ''}`,
            key: loadingKey,
          });
          await fetchList();
        } catch (error) {
          message.error({
            content: error instanceof Error ? error.message : '发布失败',
            key: loadingKey,
          });
          throw error;
        }
      },
    });
  }

  function requeue(record: ProductCollectionRow) {
    const running = record.execStatus === 1;
    Modal.confirm({
      title: '退回待执行？',
      content: running
        ? '当前任务处于执行中，强制退回后可能导致重复执行，确定继续吗？'
        : '将把当前商品退回待执行状态，由后台重新处理。',
      okText: '退回',
      cancelText: '取消',
      async onOk() {
        try {
          await productCollectionsApi.requeuePostImportTask(record.id, running);
          message.success('已退回待执行');
          await fetchList();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '操作失败');
          throw error;
        }
      },
    });
  }

  function deleteRecord(record: ProductCollectionRow) {
    Modal.confirm({
      title: '确定删除这条记录？',
      content: '会删除该商品及其关联数据，删除后无法恢复。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await productCollectionsApi.delete(record.id);
          message.success('已删除');
          await fetchList();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        }
      },
    });
  }

  function openDetail(record: ProductCollectionRow) {
    window.open(`${window.location.origin}/goods/${record.id}`, '_blank', 'noopener,noreferrer');
  }

  function openTemuAttributes(record: ProductCollectionRow) {
    setTemuAttrRecord(record);
    setTemuAttrOpen(true);
  }

  function openSkuConvert(record: ProductCollectionRow) {
    setSkuConvertRecord(record);
    setSkuConvertOpen(true);
  }

  function renderStatus(record: ProductCollectionRow) {
    if (record.collectionStatus === 1) {
      return <Tag color="processing">发布中</Tag>;
    }

    if (record.collectionStatus === 2 && record.lastPublishRunId) {
      const tipState = publishTipState[String(record.lastPublishRunId)];
      return (
        <Tooltip
          title={
            <Space direction="vertical" size={2}>
              <Typography.Text style={{ color: '#fff' }}>
                runId={record.lastPublishRunId}
              </Typography.Text>
              {tipState?.loading && <Typography.Text style={{ color: '#fff' }}>加载错误详情中...</Typography.Text>}
              {!tipState?.loading &&
                tipState?.lines.map((line) => (
                  <Typography.Text key={line} style={{ color: '#fff' }}>
                    {line}
                  </Typography.Text>
                ))}
              {!tipState?.loading && tipState?.error && (
                <Typography.Text style={{ color: '#fff' }}>{tipState.error}</Typography.Text>
              )}
            </Space>
          }
          onOpenChange={(open) => {
            if (open && record.lastPublishRunId) {
              void loadPublishTip(record.lastPublishRunId);
            }
          }}
        >
          <Tag color="red">发布失败</Tag>
        </Tooltip>
      );
    }

    if (record.collectionStatus === 2) {
      return <Tag color="red">发布失败</Tag>;
    }

    if (record.temuPublished) {
      return (
        <Tooltip title={record.temuGoodsId ? `goodsId=${record.temuGoodsId}` : '已发布'}>
          <Tag color="green">已发布</Tag>
        </Tooltip>
      );
    }

    return <Tag>未发布</Tag>;
  }

  const columns: ColumnsType<ProductCollectionRow> = [
    {
      title: '图片',
      dataIndex: 'productMainImage',
      key: 'productMainImage',
      width: 88,
      render: (value: string | null, record) => {
        const imageUrl = buildAlibabaImageProxyUrl(value, imageProxyConfig);
        return imageUrl ? (
          <Image
            src={imageUrl}
            width={64}
            height={64}
            alt={record.productName || String(record.id)}
            style={{ borderRadius: 8, objectFit: 'cover' }}
          />
        ) : (
          <Typography.Text type="secondary">-</Typography.Text>
        );
      },
    },
    {
      title: '商品',
      key: 'productName',
      width: 360,
      render: (_, record) => (
        <Space direction="vertical" size={2} style={{ width: '100%', minWidth: 0 }}>
          <Typography.Text
            strong
            title={record.productName || ''}
            style={{
              ...lineClampTextStyle,
              width: '100%',
            }}
          >
            {record.productName || '-'}
          </Typography.Text>
          <Space size={6} wrap>
            <Tag bordered={false}>#{record.id}</Tag>
            <Tag bordered={false}>{record.productId || '-'}</Tag>
          </Space>
        </Space>
      ),
    },
    {
      title: '店铺',
      key: 'targetShopNames',
      width: 180,
      render: (_, record) =>
        record.targetShopNames?.length ? (
          <Space size={[4, 4]} wrap>
            {record.targetShopNames.map((shopName) => (
              <Tag key={`${record.id}-${shopName}`} color="blue">
                {shopName}
              </Tag>
            ))}
          </Space>
        ) : (
          <Typography.Text type="secondary">-</Typography.Text>
        ),
    },
    {
      title: 'OCR',
      key: 'ocrStatus',
      width: 96,
      render: (_, record) => {
        if (record.ocrStatus === 2) return <Tag color="success">已完成</Tag>;
        if (record.ocrStatus === 1) return <Tag color="processing">OCR中</Tag>;
        if (record.ocrStatus === 3) return <Tag color="error">失败</Tag>;
        return <Tag>待OCR</Tag>;
      },
    },
    {
      title: '中文图片',
      dataIndex: 'chineseImageCount',
      key: 'chineseImageCount',
      width: 100,
      render: (value: ProductCollectionRow['chineseImageCount']) => {
        const count = Number(value || 0);
        return count > 0 ? <Tag color="orange">{count}</Tag> : <Typography.Text type="secondary">0</Typography.Text>;
      },
    },
    {
      title: '执行状态',
      key: 'execStatus',
      width: 110,
      render: (_, record) => {
        if (record.execStatus === 3 && record.execResult) {
          return (
            <Tooltip title={record.execResult}>
              <Tag color="red">失败</Tag>
            </Tooltip>
          );
        }
        if (record.execStatus === 2) return <Tag color="green">成功</Tag>;
        if (record.execStatus === 1) return <Tag color="processing">执行中</Tag>;
        return <Tag>待执行</Tag>;
      },
    },
    {
      title: '发布状态',
      key: 'status',
      width: 110,
      render: (_, record) => renderStatus(record),
    },
    {
      title: '起批量',
      key: 'moq',
      width: 90,
      render: (_, record) => extractMoq(record.moqText, record.moq) || '-',
    },
    {
      title: '重量',
      key: 'weight',
      width: 110,
      render: (_, record) => record.packagingWeight ?? record.netWeight ?? '-',
    },
    {
      title: '轮播图数',
      dataIndex: 'carouselImageCount',
      key: 'carouselImageCount',
      width: 100,
    },
    {
      title: '详情图数',
      dataIndex: 'detailImageCount',
      key: 'detailImageCount',
      width: 100,
    },
    {
      title: 'SKU数',
      dataIndex: 'skuCount',
      key: 'skuCount',
      width: 80,
    },
    {
      title: 'Temu类目',
      key: 'temuCatname',
      width: 180,
      render: (_, record) => (
        <Typography.Text ellipsis={{ tooltip: record.temuCatname || '' }}>
          {record.temuCatname || '-'}
        </Typography.Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 240,
      render: (_, record) => {
        const menuItems: MenuProps['items'] = [
          {
            key: 'match',
            label: '匹配TEMU类目',
          },
          {
            key: 'temu-attributes',
            label: 'TEMU属性',
          },
          {
            key: 'sku-convert',
            label: 'SKU 转换',
          },
          {
            key: 'edit',
            label: '编辑商品',
          },
          ...(record.execStatus === 1 || record.execStatus === 3
            ? [
                {
                  key: 'requeue',
                  label: '退回待执行',
                },
              ]
            : []),
          {
            type: 'divider',
          },
          {
            key: 'delete',
            label: '删除',
            danger: true,
          },
        ];

        return (
          <Space wrap>
            <Button size="small" type="primary" onClick={() => openDetail(record)}>
              详情
            </Button>
            <Button size="small" ghost type="primary" disabled={!canPublish(record)} onClick={() => publish(record)}>
              发布
            </Button>
            <Dropdown
              menu={{
                items: menuItems,
                onClick: ({ key }) => {
                  if (key === 'match') {
                    void openMatchCategory(record);
                  }
                  if (key === 'edit') {
                    openEdit(record);
                  }
                  if (key === 'temu-attributes') {
                    openTemuAttributes(record);
                  }
                  if (key === 'sku-convert') {
                    openSkuConvert(record);
                  }
                  if (key === 'requeue') {
                    requeue(record);
                  }
                  if (key === 'delete') {
                    deleteRecord(record);
                  }
                },
              }}
            >
              <Button size="small">更多</Button>
            </Dropdown>
          </Space>
        );
      },
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card bodyStyle={{ paddingBottom: 18 }}>
        <Form layout="vertical" onFinish={reload}>
          <Row gutter={[16, 8]}>
            <Col xs={24} md={12} xl={4}>
              <Form.Item label="ID">
                <InputNumber
                  value={filters.id}
                  min={1}
                  precision={0}
                  onChange={(value) => updateFilter('id', value ?? undefined)}
                  placeholder="商品ID"
                  style={{ width: '100%' }}
                  onPressEnter={reload}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={6}>
              <Form.Item label="关键词">
                <Input
                  value={filters.q}
                  onChange={(event) => updateFilter('q', event.target.value)}
                  placeholder="商品名 / product_id"
                  allowClear
                  onPressEnter={reload}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={6}>
              <Form.Item label="SKUID">
                <Input
                  value={filters.skuIdKeyword}
                  onChange={(event) => updateFilter('skuIdKeyword', event.target.value)}
                  placeholder="模糊搜索 SKU ID"
                  allowClear
                  onPressEnter={reload}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={6}>
              <Form.Item label="平台">
                <Input
                  value={filters.sourcePlatform}
                  onChange={(event) => updateFilter('sourcePlatform', event.target.value || undefined)}
                  placeholder="如 temu / 1688"
                  allowClear
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={6}>
              <Form.Item label="店铺">
                <Select
                  value={filters.targetShopId}
                  onChange={(value) => updateFilter('targetShopId', value)}
                  options={targetShopOptions}
                  loading={loadingTargetShops}
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder="全部店铺"
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={6}>
              <Form.Item label="状态">
                <Select
                  value={filters.collectionStatus}
                  onChange={(value) => updateFilter('collectionStatus', value)}
                  options={statusOptions}
                  allowClear
                  placeholder="全部"
                />
              </Form.Item>
            </Col>

            <Col xs={24} md={12} xl={8}>
              <Form.Item label="类目">
                <Select
                  value={filters.temuCatid}
                  onChange={(value) => updateFilter('temuCatid', value)}
                  options={temuCategoryOptions}
                  loading={loadingTemuCategories}
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder="请选择"
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={4}>
              <Form.Item label="起批量">
                {renderRangeInput(
                  filters.moqMin,
                  filters.moqMax,
                  (value) => updateFilter('moqMin', value ?? undefined),
                  (value) => updateFilter('moqMax', value ?? undefined),
                )}
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={4}>
              <Form.Item label="轮播图数">
                {renderRangeInput(
                  filters.carouselImageCountMin,
                  filters.carouselImageCountMax,
                  (value) => updateFilter('carouselImageCountMin', value ?? undefined),
                  (value) => updateFilter('carouselImageCountMax', value ?? undefined),
                )}
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={4}>
              <Form.Item label="详情图数">
                {renderRangeInput(
                  filters.detailImageCountMin,
                  filters.detailImageCountMax,
                  (value) => updateFilter('detailImageCountMin', value ?? undefined),
                  (value) => updateFilter('detailImageCountMax', value ?? undefined),
                )}
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={4}>
              <Form.Item label="SKU数">
                {renderRangeInput(
                  filters.skuCountMin,
                  filters.skuCountMax,
                  (value) => updateFilter('skuCountMin', value ?? undefined),
                  (value) => updateFilter('skuCountMax', value ?? undefined),
                )}
              </Form.Item>
            </Col>

            <Col span={24}>
              <Flex justify="space-between" align="center" wrap="wrap" gap={12}>
                <Checkbox
                  checked={filters.showDeleted}
                  onChange={(event) => updateFilter('showDeleted', event.target.checked)}
                >
                  显示已删除
                </Checkbox>
                <Space>
                  <Button type="primary" loading={loading} onClick={reload}>
                    查询
                  </Button>
                  <Button onClick={reset}>重置</Button>
                </Space>
              </Flex>
            </Col>
          </Row>
        </Form>
      </Card>

      <Card>
        <Table<ProductCollectionRow>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          tableLayout="fixed"
          scroll={{ x: 1860 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            showTotal: (count) => `共 ${count} 条`,
          }}
          onChange={(pagination: TablePaginationConfig) => {
            const nextPage = pagination.current || 1;
            const nextPageSize = pagination.pageSize || 20;
            setPage(nextPage);
            setPageSize(nextPageSize);
            void fetchList(nextPage, nextPageSize, filters);
          }}
        />
      </Card>

      <Modal
        open={editOpen}
        title="编辑商品"
        confirmLoading={savingEdit}
        onOk={() => {
          void saveEdit();
        }}
        onCancel={() => {
          setEditOpen(false);
          setEditingShopId(undefined);
        }}
      >
        <Form layout="vertical">
          <Form.Item label="标题" required>
            <Input value={editingTitle} onChange={(event) => setEditingTitle(event.target.value)} allowClear />
          </Form.Item>
          <Form.Item label="绑定店铺" required>
            <Select
              value={editingShopId}
              onChange={(value) => setEditingShopId(value)}
              options={targetShopOptions}
              loading={loadingTargetShops}
              showSearch
              optionFilterProp="label"
              placeholder="请选择一个店铺"
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={matchOpen}
        title="匹配 TEMU 类目"
        confirmLoading={savingMatch}
        okButtonProps={{ disabled: !selectedMatchKey }}
        onOk={() => {
          void saveMatchCategory();
        }}
        onCancel={() => setMatchOpen(false)}
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <div>
            <Typography.Text type="secondary">商品标题</Typography.Text>
            <div>{matchingRecord?.productName || '-'}</div>
          </div>

          {matchOptions.length ? (
            <Radio.Group
              value={selectedMatchKey}
              onChange={(event) => setSelectedMatchKey(String(event.target.value))}
              style={{ width: '100%' }}
            >
              <Space direction="vertical" size={10} style={{ width: '100%' }}>
                {matchOptions.map((item) => (
                  <Radio key={String(item.leafId)} value={String(item.leafId)}>
                    <Space direction="vertical" size={2}>
                      <Typography.Text>{item.pathNames || item.pathText || item.leafName}</Typography.Text>
                      <Typography.Text type="secondary">
                        leaf: {item.leafName} ({item.leafId})
                      </Typography.Text>
                    </Space>
                  </Radio>
                ))}
              </Space>
            </Radio.Group>
          ) : (
            <Typography.Text type="secondary">未返回可选类目</Typography.Text>
          )}
        </Space>
      </Modal>

      <TemuAttributesModal
        open={temuAttrOpen}
        record={temuAttrRecord}
        onClose={() => {
          setTemuAttrOpen(false);
          setTemuAttrRecord(null);
        }}
      />

      <TemuSkuConverterModal
        open={skuConvertOpen}
        record={skuConvertRecord}
        onClose={() => {
          setSkuConvertOpen(false);
          setSkuConvertRecord(null);
        }}
      />
    </Space>
  );
};

export default ProductCollectionsPage;
