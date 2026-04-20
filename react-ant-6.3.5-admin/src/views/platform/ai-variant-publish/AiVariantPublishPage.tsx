import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Image,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { aiVariantPublishApi } from '@/api/aiVariantPublish';
import { temuShopsApi } from '@/api/temuShops';
import type {
  AiVariantPublishDraftResponse,
  AiVariantPublishRawRequest,
  AiVariantPublishRecordDetail,
  AiVariantPublishRecordSummary,
  AiVariantPublishSkuPreview,
  TemuShopVO,
} from '@/types/api';

const STORAGE_KEY = 'aiVariantPublish.shopRecordId';

const PAYLOAD_PLACEHOLDER = `{
  "productName": "",
  "carouselImageUrls": [],
  "goodsLayerDecorationReqs": [],
  "productPropertyReqs": [],
  "productSpecPropertyReqs": [],
  "productSkcReqs": [],
  "productShipmentReq": {
    "freightTemplateId": "",
    "shipmentLimitSecond": 777600
  },
  "productWarehouseRouteReq": {
    "targetRouteList": []
  }
}`;

function formatTime(value: string | number[] | null) {
  if (!value) {
    return '';
  }

  if (Array.isArray(value)) {
    const [year, month, day, hour, minute, second, nanoSecond] = value;
    const pad = (input: number | undefined, size = 2) => String(input || 0).padStart(size, '0');
    const millisecond = nanoSecond == null ? 0 : Math.floor(Number(nanoSecond) / 1e6);
    return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}.${pad(millisecond, 3)}`;
  }

  return String(value);
}

function prettyPrint(text?: string | null) {
  if (!text) {
    return '';
  }

  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

function statusColor(status?: string | null) {
  if (status === 'SUCCEEDED') {
    return 'green';
  }
  if (status === 'FAILED') {
    return 'red';
  }
  return 'blue';
}

const initialForm: AiVariantPublishRawRequest = {
  shopRecordId: null,
  sourceType: 'AI_VARIANT',
  sourceBizType: null,
  sourceBizId: null,
  sourceBizName: null,
  sourceNote: '',
  requestPayload: '',
};

const AiVariantPublishPage = () => {
  const { message } = App.useApp();
  const [shops, setShops] = useState<TemuShopVO[]>([]);
  const [shopsLoading, setShopsLoading] = useState(false);
  const [records, setRecords] = useState<AiVariantPublishRecordSummary[]>([]);
  const [recordsLoading, setRecordsLoading] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [generatingDraft, setGeneratingDraft] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<AiVariantPublishRecordDetail | null>(null);
  const [form, setForm] = useState<AiVariantPublishRawRequest>(initialForm);
  const [sourceSpuId, setSourceSpuId] = useState('');
  const [generatedDraft, setGeneratedDraft] = useState<AiVariantPublishDraftResponse | null>(null);

  const shopOptions = useMemo(
    () =>
      shops.map((shop) => ({
        value: shop.id,
        label: `${shop.shopName}（${shop.shopId}）`,
      })),
    [shops],
  );

  function updateForm<K extends keyof AiVariantPublishRawRequest>(key: K, value: AiVariantPublishRawRequest[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function loadShops() {
    setShopsLoading(true);
    try {
      const res = await temuShopsApi.list({ enabled: true });
      const rows = Array.isArray(res.data) ? res.data : [];
      setShops(rows);

      if (!rows.length) {
        return;
      }

      const savedShopRecordId = Number(localStorage.getItem(STORAGE_KEY) || 0) || null;
      const fallbackShopId = savedShopRecordId && rows.some((item) => item.id === savedShopRecordId)
        ? savedShopRecordId
        : rows[0].id;

      setForm((current) => ({
        ...current,
        shopRecordId: current.shopRecordId || fallbackShopId,
      }));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '店铺加载失败');
    } finally {
      setShopsLoading(false);
    }
  }

  async function loadRecords(shopRecordId?: number | null) {
    setRecordsLoading(true);
    try {
      const normalizedShopRecordId = shopRecordId || undefined;
      const res = await aiVariantPublishApi.listRecords({
        shopRecordId: normalizedShopRecordId,
        size: 100,
      });
      setRecords(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布记录加载失败');
    } finally {
      setRecordsLoading(false);
    }
  }

  useEffect(() => {
    void loadShops();
  }, []);

  useEffect(() => {
    if (!form.shopRecordId) {
      return;
    }
    localStorage.setItem(STORAGE_KEY, String(form.shopRecordId));
    void loadRecords(form.shopRecordId);
  }, [form.shopRecordId]);

  async function handlePublish() {
    if (!form.shopRecordId) {
      message.warning('先选择店铺');
      return;
    }
    if (!String(form.requestPayload || '').trim()) {
      message.warning('原始请求 JSON 不能为空');
      return;
    }

    setPublishing(true);
    try {
      const res = await aiVariantPublishApi.publishRaw(form);
      const detail = res.data;
      setSelectedRecord(detail);
      setDetailOpen(true);
      message.success(res.message || (detail.status === 'SUCCEEDED' ? '发布成功' : '发布已执行'));
      await loadRecords(form.shopRecordId);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布失败');
    } finally {
      setPublishing(false);
    }
  }

  async function handleGenerateDraft() {
    if (!form.shopRecordId) {
      message.warning('先选择店铺');
      return;
    }

    const normalizedSpuId = Number(String(sourceSpuId || '').trim());
    if (!normalizedSpuId) {
      message.warning('先输入采集商品 SPU ID');
      return;
    }

    setGeneratingDraft(true);
    try {
      const res = await aiVariantPublishApi.generateDraftFromProductCollection({
        shopRecordId: form.shopRecordId,
        sourceSpuId: normalizedSpuId,
      });
      const draft = res.data;
      setGeneratedDraft(draft);
      setForm((current) => ({
        ...current,
        shopRecordId: draft.shopRecordId,
        sourceType: draft.sourceType || current.sourceType,
        sourceBizType: draft.sourceBizType,
        sourceBizId: draft.sourceBizId,
        sourceBizName: draft.sourceBizName,
        sourceNote: draft.sourceNote || '',
        requestPayload: draft.requestPayload || '',
      }));
      message.success(res.message || '草稿已生成');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '草稿生成失败');
    } finally {
      setGeneratingDraft(false);
    }
  }

  async function openDetail(recordId: number) {
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const res = await aiVariantPublishApi.getRecord(recordId);
      setSelectedRecord(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '记录详情加载失败');
    } finally {
      setDetailLoading(false);
    }
  }

  const recordColumns: ColumnsType<AiVariantPublishRecordSummary> = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 190,
      render: (value) => formatTime(value),
    },
    {
      title: '店铺',
      key: 'shopName',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{record.shopName || '-'}</Typography.Text>
          <Typography.Text type="secondary">{record.shopId || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 110,
      render: (_, record) => <Tag color={statusColor(record.status)}>{record.status || '-'}</Tag>,
    },
    {
      title: '商品',
      key: 'product',
      render: (_, record) => (
        <Space align="start">
          {record.preview?.mainImageUrl ? (
            <Image
              src={record.preview.mainImageUrl}
              width={56}
              height={56}
              style={{ borderRadius: 8, objectFit: 'cover' }}
            />
          ) : null}
          <Space direction="vertical" size={2}>
            <Typography.Text>{record.preview?.productName || '-'}</Typography.Text>
            <Typography.Text type="secondary">
              SKC {record.preview?.skcCount ?? 0} / SKU {record.preview?.skuCount ?? 0}
            </Typography.Text>
          </Space>
        </Space>
      ),
    },
    {
      title: 'goodsId',
      dataIndex: 'goodsId',
      key: 'goodsId',
      width: 150,
      render: (value) => value || '-',
    },
    {
      title: '来源',
      key: 'source',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{record.sourceType || '-'}</Typography.Text>
          <Typography.Text type="secondary">
            {record.sourceBizName || (record.sourceBizId ? `#${record.sourceBizId}` : '-')}
          </Typography.Text>
          <Typography.Text type="secondary">{record.sourceNote || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '结果',
      key: 'result',
      width: 240,
      render: (_, record) => record.errorMessage || '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      fixed: 'right',
      render: (_, record) => (
        <Button size="small" onClick={() => void openDetail(record.id)}>
          查看详情
        </Button>
      ),
    },
  ];

  const skuColumns: ColumnsType<AiVariantPublishSkuPreview> = [
    {
      title: '缩略图',
      dataIndex: 'thumbUrl',
      key: 'thumbUrl',
      width: 90,
      render: (value) =>
        value ? <Image src={value} width={48} height={48} style={{ borderRadius: 6, objectFit: 'cover' }} /> : '-',
    },
    {
      title: 'SKC / SKU',
      key: 'code',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>SKC: {record.skcExtCode || '-'}</Typography.Text>
          <Typography.Text type="secondary">SKU: {record.skuExtCode || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '规格',
      key: 'specNames',
      render: (_, record) => (record.specNames?.length ? record.specNames.join(' / ') : '-'),
    },
    {
      title: '供货价',
      dataIndex: 'supplierPriceText',
      key: 'supplierPriceText',
      width: 140,
      render: (value) => value || '-',
    },
    {
      title: '站点供货价',
      dataIndex: 'siteSupplierPriceText',
      key: 'siteSupplierPriceText',
      width: 240,
      render: (value) => value || '-',
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        title="AI 变体原始发布"
        extra={
          <Typography.Text type="secondary">
            直接提交 `AddGloGoodsRequest` 原始 JSON，不走现有正式发布流程。
          </Typography.Text>
        }
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Space wrap>
            <Input
              value={sourceSpuId}
              onChange={(event) => setSourceSpuId(event.target.value)}
              placeholder="输入采集商品 SPU ID，例如 73"
              style={{ width: 260 }}
            />
            <Button loading={generatingDraft} onClick={() => void handleGenerateDraft()}>
              从采集商品生成草稿
            </Button>
          </Space>

          {generatedDraft ? (
            <Card size="small" title="当前草稿来源">
              <Space align="start">
                {generatedDraft.preview?.mainImageUrl || generatedDraft.sourceProductMainImage ? (
                  <Image
                    src={generatedDraft.preview?.mainImageUrl || generatedDraft.sourceProductMainImage || ''}
                    width={72}
                    height={72}
                    style={{ borderRadius: 8, objectFit: 'cover' }}
                  />
                ) : null}
                <Space direction="vertical" size={4}>
                  <Typography.Text>
                    SPU #{generatedDraft.sourceSpuId} {generatedDraft.sourceProductName || ''}
                  </Typography.Text>
                  <Typography.Text type="secondary">
                    {generatedDraft.shopName || '-'} / {generatedDraft.shopId || '-'}
                  </Typography.Text>
                  <Typography.Text type="secondary">
                    SKC {generatedDraft.preview?.skcCount ?? 0} / SKU {generatedDraft.preview?.skuCount ?? 0}
                  </Typography.Text>
                </Space>
              </Space>
            </Card>
          ) : null}

          {generatedDraft?.warnings?.length ? (
            <Alert
              type="warning"
              showIcon
              message="生成草稿时的提示"
              description={
                <Space direction="vertical" size={4}>
                  {generatedDraft.warnings.map((warning, index) => (
                    <Typography.Text key={`${warning}-${index}`}>{warning}</Typography.Text>
                  ))}
                </Space>
              }
            />
          ) : null}

          <Space wrap>
            <Select
              value={form.shopRecordId ?? undefined}
              options={shopOptions}
              loading={shopsLoading}
              onChange={(value) => updateForm('shopRecordId', value)}
              placeholder="选择店铺"
              style={{ width: 280 }}
            />
            <Input
              value={form.sourceType}
              onChange={(event) => updateForm('sourceType', event.target.value)}
              placeholder="来源类型，例如 AI_VARIANT"
              style={{ width: 220 }}
            />
            <Input
              value={form.sourceBizName || ''}
              onChange={(event) => updateForm('sourceBizName', event.target.value || null)}
              placeholder="来源对象名称，例如 商品标题"
              style={{ width: 260 }}
            />
            <Input
              value={form.sourceNote || ''}
              onChange={(event) => updateForm('sourceNote', event.target.value)}
              placeholder="来源备注，例如 1688 裂变测试"
              style={{ width: 320 }}
            />
            <Button type="primary" loading={publishing} onClick={() => void handlePublish()}>
              原始发布
            </Button>
          </Space>

          <Input.TextArea
            value={form.requestPayload}
            onChange={(event) => updateForm('requestPayload', event.target.value)}
            placeholder={PAYLOAD_PLACEHOLDER}
            autoSize={{ minRows: 14, maxRows: 24 }}
          />
        </Space>
      </Card>

      <Card
        title="发布记录"
        extra={
          <Button loading={recordsLoading} onClick={() => void loadRecords(form.shopRecordId)}>
            刷新记录
          </Button>
        }
      >
        <Table<AiVariantPublishRecordSummary>
          rowKey="id"
          columns={recordColumns}
          dataSource={records}
          loading={recordsLoading}
          pagination={false}
          scroll={{ x: 1400 }}
        />
      </Card>

      <Drawer
        open={detailOpen}
        width={1120}
        title={selectedRecord?.preview?.productName || '发布记录详情'}
        onClose={() => setDetailOpen(false)}
      >
        {selectedRecord ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card loading={detailLoading}>
              <Descriptions column={2} size="small">
                <Descriptions.Item label="记录ID">{selectedRecord.id}</Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Tag color={statusColor(selectedRecord.status)}>{selectedRecord.status || '-'}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="店铺">{selectedRecord.shopName || '-'}</Descriptions.Item>
                <Descriptions.Item label="店铺ID">{selectedRecord.shopId || '-'}</Descriptions.Item>
                <Descriptions.Item label="goodsId">{selectedRecord.goodsId || '-'}</Descriptions.Item>
                <Descriptions.Item label="requestId">{selectedRecord.requestId || '-'}</Descriptions.Item>
                <Descriptions.Item label="来源类型">{selectedRecord.sourceType || '-'}</Descriptions.Item>
                <Descriptions.Item label="来源对象">
                  {selectedRecord.sourceBizName || (selectedRecord.sourceBizId ? `#${selectedRecord.sourceBizId}` : '-')}
                </Descriptions.Item>
                <Descriptions.Item label="来源备注">{selectedRecord.sourceNote || '-'}</Descriptions.Item>
                <Descriptions.Item label="TEMU success">
                  {selectedRecord.responseSuccess == null ? '-' : selectedRecord.responseSuccess ? 'true' : 'false'}
                </Descriptions.Item>
                <Descriptions.Item label="错误信息">
                  {selectedRecord.errorMessage || selectedRecord.responseErrorMsg || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="创建时间">{formatTime(selectedRecord.createdAt)}</Descriptions.Item>
                <Descriptions.Item label="更新时间">{formatTime(selectedRecord.updatedAt)}</Descriptions.Item>
              </Descriptions>
            </Card>

            <Card title="图片预览" loading={detailLoading}>
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                {selectedRecord.preview?.mainImageUrl ? (
                  <Space direction="vertical" size={8}>
                    <Typography.Text strong>主图</Typography.Text>
                    <Image src={selectedRecord.preview.mainImageUrl} width={160} />
                  </Space>
                ) : null}

                {selectedRecord.preview?.carouselImageUrls?.length ? (
                  <Space direction="vertical" size={8}>
                    <Typography.Text strong>轮播图</Typography.Text>
                    <Image.PreviewGroup>
                      <Space wrap>
                        {selectedRecord.preview.carouselImageUrls.map((url) => (
                          <Image key={url} src={url} width={96} height={96} style={{ borderRadius: 8, objectFit: 'cover' }} />
                        ))}
                      </Space>
                    </Image.PreviewGroup>
                  </Space>
                ) : null}

                {selectedRecord.preview?.detailImageUrls?.length ? (
                  <Space direction="vertical" size={8}>
                    <Typography.Text strong>详情图</Typography.Text>
                    <Image.PreviewGroup>
                      <Space wrap>
                        {selectedRecord.preview.detailImageUrls.map((url) => (
                          <Image key={url} src={url} width={96} height={96} style={{ borderRadius: 8, objectFit: 'cover' }} />
                        ))}
                      </Space>
                    </Image.PreviewGroup>
                  </Space>
                ) : null}
              </Space>
            </Card>

            <Card
              title={`SKU 明细（${selectedRecord.preview?.skuCount ?? 0}）`}
              loading={detailLoading}
            >
              <Table<AiVariantPublishSkuPreview>
                rowKey={(record, index) => `${record.skcExtCode || 'skc'}-${record.skuExtCode || 'sku'}-${index}`}
                columns={skuColumns}
                dataSource={selectedRecord.preview?.skuList || []}
                pagination={false}
                scroll={{ x: 900 }}
              />
            </Card>

            <Card title="原始请求 JSON" loading={detailLoading}>
              <Typography.Paragraph
                style={{
                  marginBottom: 0,
                  padding: 12,
                  borderRadius: 8,
                  background: '#fafafa',
                  whiteSpace: 'pre-wrap',
                  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
                }}
              >
                {prettyPrint(selectedRecord.requestPayload) || '-'}
              </Typography.Paragraph>
            </Card>

            <Card title="原始响应 JSON" loading={detailLoading}>
              <Typography.Paragraph
                style={{
                  marginBottom: 0,
                  padding: 12,
                  borderRadius: 8,
                  background: '#fafafa',
                  whiteSpace: 'pre-wrap',
                  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
                }}
              >
                {prettyPrint(selectedRecord.responsePayload) || '-'}
              </Typography.Paragraph>
            </Card>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
};

export default AiVariantPublishPage;
