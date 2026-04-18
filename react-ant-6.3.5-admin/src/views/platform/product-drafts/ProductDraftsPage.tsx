import {
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableProps } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { Key } from 'react';
import { useEffect, useState } from 'react';
import { productDraftsApi } from '@/api/productDrafts';
import { temuShopsApi } from '@/api/temuShops';
import type { ProductDraftDetailVO, ProductDraftListItem, TemuShopVO } from '@/types/api';

interface DraftFilters {
  q: string;
  sourcePlatform?: string;
  targetShopId?: string;
  pushedToCollection?: boolean;
  showDeleted: boolean;
}

interface DraftEditFormState {
  id: number | null;
  productName: string;
  productCategory: string;
  originalCategory: string;
  productMainImage: string;
  productUrl: string;
  sourceUrl: string;
  monthlySales: string;
  reviewCount: number | null;
  companyName: string;
}

const initialFilters: DraftFilters = {
  q: '',
  sourcePlatform: undefined,
  targetShopId: undefined,
  pushedToCollection: undefined,
  showDeleted: false,
};

const initialEditForm: DraftEditFormState = {
  id: null,
  productName: '',
  productCategory: '',
  originalCategory: '',
  productMainImage: '',
  productUrl: '',
  sourceUrl: '',
  monthlySales: '',
  reviewCount: null,
  companyName: '',
};

const ProductDraftsPage = () => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [batchPushing, setBatchPushing] = useState(false);
  const [pushingDraftId, setPushingDraftId] = useState<number | null>(null);
  const [rows, setRows] = useState<ProductDraftListItem[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState<DraftFilters>(initialFilters);
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const [shopOptions, setShopOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [loadingShops, setLoadingShops] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [importHtml, setImportHtml] = useState('');
  const [importJson, setImportJson] = useState('');
  const [importShopIds, setImportShopIds] = useState<string[]>([]);
  const [editOpen, setEditOpen] = useState(false);
  const [editForm, setEditForm] = useState<DraftEditFormState>(initialEditForm);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<ProductDraftDetailVO | null>(null);

  async function loadShops() {
    setLoadingShops(true);
    try {
      const res = await temuShopsApi.list({ enabled: true });
      const shops = Array.isArray(res.data) ? res.data : [];
      setShopOptions(
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
      setLoadingShops(false);
    }
  }

  async function fetchList(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true);
    try {
      const res = await productDraftsApi.list({
        q: nextFilters.q || undefined,
        sourcePlatform: nextFilters.sourcePlatform || undefined,
        targetShopId: nextFilters.targetShopId || undefined,
        pushedToCollection: nextFilters.pushedToCollection,
        showDeleted: nextFilters.showDeleted || undefined,
        page: nextPage - 1,
        size: nextPageSize,
      });

      const content = Array.isArray(res.data.content) ? res.data.content : [];
      setRows(content);
      setSelectedRowKeys((current) => current.filter((key) => content.some((row) => row.id === key)));
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadShops();
    void fetchList(1, 20, initialFilters);
  }, []);

  function updateFilter<K extends keyof DraftFilters>(key: K, value: DraftFilters[K]) {
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
    setSelectedRowKeys([]);
    setPage(1);
    setPageSize(20);
    void fetchList(1, 20, initialFilters);
  }

  function openImport() {
    setImportHtml('');
    setImportJson('');
    setImportShopIds([]);
    setImportOpen(true);
  }

  async function submitImport() {
    if (!importHtml.trim()) {
      message.error('请输入原始 HTML');
      return;
    }

    setImporting(true);
    try {
      await productDraftsApi.importDraft({
        html: importHtml,
        extractedJson: importJson || undefined,
        targetShopIds: importShopIds.length ? importShopIds : undefined,
      });
      message.success('已导入草稿库');
      setImportOpen(false);
      await fetchList();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '导入失败');
    } finally {
      setImporting(false);
    }
  }

  function openEdit(record: ProductDraftListItem) {
    setEditForm({
      id: record.id,
      productName: record.productName || '',
      productCategory: record.productCategory || '',
      originalCategory: record.originalCategory || '',
      productMainImage: record.productMainImage || '',
      productUrl: record.productUrl || '',
      sourceUrl: record.sourceUrl || '',
      monthlySales: record.monthlySales || '',
      reviewCount: record.reviewCount,
      companyName: record.companyName || '',
    });
    setEditOpen(true);
  }

  async function saveEdit() {
    if (!editForm.id) {
      return;
    }

    setSaving(true);
    try {
      await productDraftsApi.update(editForm.id, {
        productName: editForm.productName,
        productCategory: editForm.productCategory,
        originalCategory: editForm.originalCategory,
        productMainImage: editForm.productMainImage,
        productUrl: editForm.productUrl,
        sourceUrl: editForm.sourceUrl,
        monthlySales: editForm.monthlySales,
        reviewCount: editForm.reviewCount,
        companyName: editForm.companyName,
      });
      message.success('已保存');
      setEditOpen(false);
      await fetchList();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  async function openDetail(record: ProductDraftListItem) {
    try {
      const res = await productDraftsApi.get(record.id);
      setDetail(res.data);
      setDetailOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    }
  }

  function pushDraft(record: ProductDraftListItem) {
    Modal.confirm({
      title: '推送商品库',
      content: `确认将“${record.productName || record.productId || '当前草稿'}”推送到商品库吗？`,
      okText: '确认推送',
      cancelText: '取消',
      async onOk() {
        setPushingDraftId(record.id);
        try {
          const res = await productDraftsApi.pushToCollection(record.id);
          message.success(`已推送到采集商品库，ID=${res.data.collectionId || '-'}`);
          await fetchList();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '推送失败');
          throw error;
        } finally {
          setPushingDraftId(null);
        }
      },
    });
  }

  function pushSelectedDrafts() {
    const selectedRows = rows.filter((row) => selectedRowKeys.includes(row.id));
    const pendingRows = selectedRows.filter((row) => !row.pushedToCollection);

    if (!pendingRows.length) {
      message.warning(selectedRows.length ? '选中的草稿都已推送，无需重复操作' : '请先选择要推送的草稿');
      return;
    }

    const skippedCount = selectedRows.length - pendingRows.length;
    Modal.confirm({
      title: '批量推送商品库',
      content:
        skippedCount > 0
          ? `本次将推送 ${pendingRows.length} 条未推送草稿，跳过 ${skippedCount} 条已推送草稿，确认继续吗？`
          : `确认将选中的 ${pendingRows.length} 条草稿批量推送到商品库吗？`,
      okText: '确认推送',
      cancelText: '取消',
      async onOk() {
        setBatchPushing(true);
        const loadingKey = 'product-draft-batch-push';
        const failures: string[] = [];
        let successCount = 0;

        message.loading({ content: `正在批量推送 ${pendingRows.length} 条草稿...`, key: loadingKey, duration: 0 });
        try {
          for (const record of pendingRows) {
            try {
              await productDraftsApi.pushToCollection(record.id);
              successCount += 1;
              message.loading({
                content: `批量推送中 ${successCount}/${pendingRows.length}`,
                key: loadingKey,
                duration: 0,
              });
            } catch (error) {
              failures.push(`${record.productName || record.productId || `ID=${record.id}`}: ${error instanceof Error ? error.message : '推送失败'}`);
            }
          }

          await fetchList();
          setSelectedRowKeys([]);

          if (failures.length) {
            message.warning({
              content: `批量推送完成，成功 ${successCount} 条，失败 ${failures.length} 条`,
              key: loadingKey,
            });
            Modal.info({
              title: '部分草稿推送失败',
              width: 720,
              content: failures.join('；'),
            });
            return;
          }

          message.success({
            content: `批量推送完成，共成功 ${successCount} 条`,
            key: loadingKey,
          });
        } finally {
          setBatchPushing(false);
        }
      },
    });
  }

  function deleteDraft(record: ProductDraftListItem) {
    Modal.confirm({
      title: '删除草稿？',
      content: '删除后将从默认列表隐藏，但仍可在“显示已删除”中查看。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await productDraftsApi.delete(record.id);
          message.success('已删除');
          await fetchList();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        }
      },
    });
  }

  const rowSelection: TableProps<ProductDraftListItem>['rowSelection'] = {
    selectedRowKeys,
    onChange: (keys: Key[]) => setSelectedRowKeys(keys),
    getCheckboxProps: (record: ProductDraftListItem) => ({
      disabled: batchPushing || !!pushingDraftId || !record.id,
    }),
  };

  const columns: ColumnsType<ProductDraftListItem> = [
    {
      title: '主图',
      dataIndex: 'productMainImage',
      key: 'productMainImage',
      width: 90,
      fixed: 'left',
      render: (value: string | null) =>
        value ? (
          <Image src={value} width={64} height={64} style={{ objectFit: 'cover', borderRadius: 8 }} />
        ) : (
          '-'
        ),
    },
    {
      title: '商品',
      key: 'productName',
      width: 280,
      fixed: 'left',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong ellipsis={{ tooltip: record.productName || '' }}>
            {record.productName || '-'}
          </Typography.Text>
          <Typography.Text type="secondary">{record.productId || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '来源平台',
      dataIndex: 'sourcePlatform',
      key: 'sourcePlatform',
      width: 110,
      render: (value: string) => <Tag color={value === 'TEMU' ? 'blue' : 'gold'}>{value || '-'}</Tag>,
    },
    {
      title: '店铺',
      key: 'targetShopNames',
      width: 220,
      render: (_, record) =>
        record.targetShopNames?.length ? (
          <Space size={[4, 4]} wrap>
            {record.targetShopNames.map((name) => (
              <Tag key={`${record.id}-${name}`} color="blue">
                {name}
              </Tag>
            ))}
          </Space>
        ) : (
          '-'
        ),
    },
    {
      title: '类目',
      dataIndex: 'productCategory',
      key: 'productCategory',
      width: 150,
    },
    {
      title: '原始类目',
      dataIndex: 'originalCategory',
      key: 'originalCategory',
      width: 220,
    },
    {
      title: '销量',
      dataIndex: 'monthlySales',
      key: 'monthlySales',
      width: 110,
    },
    {
      title: '评论数',
      dataIndex: 'reviewCount',
      key: 'reviewCount',
      width: 100,
    },
    {
      title: '推送状态',
      key: 'pushedToCollection',
      width: 120,
      render: (_, record) =>
        record.pushedToCollection ? <Tag color="green">已推送</Tag> : <Tag>未推送</Tag>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      fixed: 'right',
      render: (_, record) => (
        <Space wrap>
          <Button size="small" type="link" onClick={() => void openDetail(record)}>
            查看
          </Button>
          <Button size="small" type="link" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button
            size="small"
            type="link"
            onClick={() => pushDraft(record)}
            loading={pushingDraftId === record.id}
            disabled={batchPushing || (!!pushingDraftId && pushingDraftId !== record.id)}
          >
            推送商品库
          </Button>
          <Button size="small" type="link" danger onClick={() => deleteDraft(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Form layout="inline" onFinish={reload}>
          <Form.Item label="关键词">
            <Input
              value={filters.q}
              onChange={(event) => updateFilter('q', event.target.value)}
              placeholder="商品名 / productId"
              style={{ width: 240 }}
              allowClear
              onPressEnter={reload}
            />
          </Form.Item>
          <Form.Item label="来源平台">
            <Select
              value={filters.sourcePlatform}
              onChange={(value) => updateFilter('sourcePlatform', value)}
              options={[
                { label: 'TEMU', value: 'TEMU' },
                { label: '1688', value: '1688' },
              ]}
              allowClear
              placeholder="全部"
              style={{ width: 160 }}
            />
          </Form.Item>
          <Form.Item label="店铺">
            <Select
              value={filters.targetShopId}
              onChange={(value) => updateFilter('targetShopId', value)}
              options={shopOptions}
              loading={loadingShops}
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="全部店铺"
              style={{ width: 220 }}
            />
          </Form.Item>
          <Form.Item label="推送状态">
            <Select
              value={filters.pushedToCollection}
              onChange={(value) => updateFilter('pushedToCollection', value)}
              options={[
                { label: '全部', value: undefined },
                { label: '未推送', value: false },
                { label: '已推送', value: true },
              ]}
              allowClear
              placeholder="全部"
              style={{ width: 160 }}
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={openImport}>
              导入草稿
            </Button>
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              ghost
              loading={batchPushing}
              disabled={!selectedRowKeys.length || !!pushingDraftId}
              onClick={pushSelectedDrafts}
            >
              批量推送商品库（{selectedRowKeys.length}）
            </Button>
          </Form.Item>
          <Form.Item>
            <Button loading={loading} onClick={reload}>
              查询
            </Button>
          </Form.Item>
          <Form.Item>
            <Button onClick={reset}>重置</Button>
          </Form.Item>
        </Form>
      </Card>

      <Card>
        <Table<ProductDraftListItem>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          rowSelection={rowSelection}
          scroll={{ x: 1500 }}
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
            setSelectedRowKeys([]);
            setPage(nextPage);
            setPageSize(nextPageSize);
            void fetchList(nextPage, nextPageSize, filters);
          }}
        />
      </Card>

      <Modal
        open={importOpen}
        title="导入商品草稿"
        width={920}
        confirmLoading={importing}
        onOk={() => {
          void submitImport();
        }}
        onCancel={() => setImportOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="原始 HTML" required>
            <Input.TextArea
              value={importHtml}
              onChange={(event) => setImportHtml(event.target.value)}
              rows={14}
              placeholder="插件或手工采集的原始 html"
            />
          </Form.Item>
          <Form.Item label="提取补充 JSON（可选）">
            <Input.TextArea
              value={importJson}
              onChange={(event) => setImportJson(event.target.value)}
              rows={5}
              placeholder='例如：{"detailImages": [...]}'
            />
          </Form.Item>
          <Form.Item label="目标店铺（可选）">
            <Select
              value={importShopIds}
              onChange={(value) => setImportShopIds(value)}
              mode="multiple"
              options={shopOptions}
              loading={loadingShops}
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="不选则不绑定店铺"
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={editOpen}
        title="编辑草稿"
        width={760}
        confirmLoading={saving}
        onOk={() => {
          void saveEdit();
        }}
        onCancel={() => setEditOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="商品名">
            <Input
              value={editForm.productName}
              onChange={(event) => setEditForm((current) => ({ ...current, productName: event.target.value }))}
            />
          </Form.Item>
          <Form.Item label="类目">
            <Input
              value={editForm.productCategory}
              onChange={(event) => setEditForm((current) => ({ ...current, productCategory: event.target.value }))}
            />
          </Form.Item>
          <Form.Item label="原始类目">
            <Input
              value={editForm.originalCategory}
              onChange={(event) => setEditForm((current) => ({ ...current, originalCategory: event.target.value }))}
            />
          </Form.Item>
          <Form.Item label="主图 URL">
            <Input
              value={editForm.productMainImage}
              onChange={(event) => setEditForm((current) => ({ ...current, productMainImage: event.target.value }))}
            />
          </Form.Item>
          <Form.Item label="商品 URL">
            <Input
              value={editForm.productUrl}
              onChange={(event) => setEditForm((current) => ({ ...current, productUrl: event.target.value }))}
            />
          </Form.Item>
          <Form.Item label="货源链接">
            <Input
              value={editForm.sourceUrl}
              onChange={(event) => setEditForm((current) => ({ ...current, sourceUrl: event.target.value }))}
            />
          </Form.Item>
          <Form.Item label="销量">
            <Input
              value={editForm.monthlySales}
              onChange={(event) => setEditForm((current) => ({ ...current, monthlySales: event.target.value }))}
            />
          </Form.Item>
          <Form.Item label="评论数">
            <InputNumber
              value={editForm.reviewCount}
              min={0}
              style={{ width: '100%' }}
              onChange={(value) => setEditForm((current) => ({ ...current, reviewCount: value ?? null }))}
            />
          </Form.Item>
          <Form.Item label="店铺/公司名">
            <Input
              value={editForm.companyName}
              onChange={(event) => setEditForm((current) => ({ ...current, companyName: event.target.value }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer open={detailOpen} title="草稿详情" width={920} onClose={() => setDetailOpen(false)}>
        {detail ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="来源平台">{detail.sourcePlatform || '-'}</Descriptions.Item>
              <Descriptions.Item label="productId">{detail.productId || '-'}</Descriptions.Item>
              <Descriptions.Item label="商品名" span={2}>
                {detail.productName || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="类目">{detail.productCategory || '-'}</Descriptions.Item>
              <Descriptions.Item label="原始类目">{detail.originalCategory || '-'}</Descriptions.Item>
              <Descriptions.Item label="店铺" span={2}>
                {(detail.targetShopNames || []).join('，') || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="货源链接" span={2}>
                {detail.sourceUrl || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="销量">{detail.monthlySales || '-'}</Descriptions.Item>
              <Descriptions.Item label="评论数">{detail.reviewCount ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="推送状态">
                {detail.pushedToCollection ? `已推送（${detail.pushedCollectionId || '-'}）` : '未推送'}
              </Descriptions.Item>
              <Descriptions.Item label="推送备注">{detail.pushMessage || '-'}</Descriptions.Item>
            </Descriptions>

            <div>
              <Typography.Text strong>原始 HTML</Typography.Text>
              <Input.TextArea value={detail.originalHtml || ''} rows={12} readOnly style={{ marginTop: 8 }} />
            </div>
            <div>
              <Typography.Text strong>解析快照</Typography.Text>
              <Input.TextArea value={detail.parserSnapshotJson || ''} rows={10} readOnly style={{ marginTop: 8 }} />
            </div>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
};

export default ProductDraftsPage;
