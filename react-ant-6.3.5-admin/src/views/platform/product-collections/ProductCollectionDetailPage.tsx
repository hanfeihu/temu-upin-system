import {
  App,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Drawer,
  Empty,
  Flex,
  Image,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd';
import {
  AppstoreOutlined,
  ArrowLeftOutlined,
  BankOutlined,
  CarOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  EnvironmentOutlined,
  GlobalOutlined,
  LinkOutlined,
  LineChartOutlined,
  PictureOutlined,
  PlayCircleOutlined,
  RedoOutlined,
  ScissorOutlined,
  StarOutlined,
  SwapOutlined,
  TableOutlined,
  TagOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { UploadProps } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { alibabaImageProxyConfigApi } from '@/api/alibabaImageProxyConfig';
import {
  productCollectionsApi,
  type ProductCollectionSplitPayload,
  type ProductCollectionUpdatePayload,
} from '@/api/productCollections';
import type {
  AlibabaImageProxyConfigVO,
  ProductCollectionDetailVO,
  ProductCollectionSkuPropVO,
  ProductCollectionSkuRowVO,
  ProductCollectionTemuSkuVO,
} from '@/types/api';
import { buildAlibabaImageProxyUrl } from '@/utils/alibabaImageProxy';
import { formatDateTime, prettyJson, safeJsonParse } from '@/utils/format';
import './ProductCollectionDetailPage.css';

interface KeyValueRow {
  key: string;
  name: string;
  value: string;
}

function prettyPrintJsonText(text?: string | null) {
  if (!text) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

interface SplitGroupState {
  key: string;
  name: string;
}

interface TranslateState {
  open: boolean;
  kind: 'carousel' | 'detail' | '';
  index: number;
  originalUrl: string;
  translatedUrl: string;
  storedUrl: string;
  taskId: string;
  loading: boolean;
  saving: boolean;
  sourceLanguage: string;
  targetLang: string;
  provider: string;
  containDetail: boolean;
  uploadToOss: boolean;
}

interface FusionState {
  open: boolean;
  selected: string[];
  prompt: string;
  negativePrompt: string;
  width: number | null;
  height: number | null;
  strength: number | null;
  model: string;
  seed: number | null;
  resultUrl: string;
  loading: boolean;
  saving: boolean;
  errorMsg: string;
}

interface TemuSkuImageReplaceState {
  open: boolean;
  row: ProductCollectionTemuSkuVO | null;
  uploading: boolean;
}

const languageOptions = [
  { label: '中文(zh)', value: 'zh' },
  { label: '英文(en)', value: 'en' },
  { label: '西语(es)', value: 'es' },
  { label: '法语(fr)', value: 'fr' },
  { label: '德语(de)', value: 'de' },
  { label: '意大利语(it)', value: 'it' },
  { label: '葡语(pt)', value: 'pt' },
  { label: '俄语(ru)', value: 'ru' },
  { label: '日语(ja)', value: 'ja' },
  { label: '韩语(ko)', value: 'ko' },
];

const providerOptions = [
  { label: 'TEMU', value: 'temu' },
  { label: '阿里云', value: 'aliyun' },
];

const STANDALONE_DETAIL_MAX_WIDTH = 1540;

const fusionModelOptions = [
  { label: 'sd3.5-large', value: 'sd3.5-large' },
  { label: 'sd3.5-large-turbo', value: 'sd3.5-large-turbo' },
  { label: 'sd3.5-medium', value: 'sd3.5-medium' },
];

const thumbButtonStyle = (active: boolean) => ({
  width: 74,
  height: 74,
  padding: 4,
  borderRadius: 12,
  border: `1px solid ${active ? '#1677ff' : '#f0f0f0'}`,
  background: '#fff',
  cursor: 'pointer',
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
});

function createInitialTranslateState(): TranslateState {
  return {
    open: false,
    kind: '',
    index: -1,
    originalUrl: '',
    translatedUrl: '',
    storedUrl: '',
    taskId: '',
    loading: false,
    saving: false,
    sourceLanguage: 'zh',
    targetLang: 'en',
    provider: 'temu',
    containDetail: true,
    uploadToOss: false,
  };
}

function createInitialFusionState(): FusionState {
  return {
    open: false,
    selected: [],
    prompt: '',
    negativePrompt: '',
    width: 1024,
    height: 1024,
    strength: 0.35,
    model: 'sd3.5-large',
    seed: null,
    resultUrl: '',
    loading: false,
    saving: false,
    errorMsg: '',
  };
}

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }
  return `¥${numeric.toFixed(2)}`;
}

function formatRange(minValue?: number | null, maxValue?: number | null) {
  if (minValue === null || minValue === undefined) {
    return maxValue === null || maxValue === undefined ? '-' : formatMoney(maxValue);
  }
  if (maxValue === null || maxValue === undefined) {
    return formatMoney(minValue);
  }
  const minText = formatMoney(minValue);
  const maxText = formatMoney(maxValue);
  return minText === maxText ? minText : `${minText} ~ ${maxText}`;
}

function formatNullable(value?: string | number | null, suffix = '') {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  return `${value}${suffix}`;
}

function formatPercent(value?: number | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }
  return `${numeric}%`;
}

function parseImageList(raw?: string | null, fallback?: string | null) {
  const parsed = safeJsonParse<unknown>(raw, []);
  const values = Array.isArray(parsed)
    ? parsed
        .map((item) => (typeof item === 'string' ? item.trim() : ''))
        .filter((item) => !!item)
    : [];
  if (values.length) {
    return values;
  }
  return fallback ? [fallback] : [];
}

function stringifyValue(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  if (typeof value === 'string') {
    return value.trim() || '-';
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  if (Array.isArray(value)) {
    const parts = value.map((item) => stringifyValue(item)).filter((item) => item !== '-');
    return parts.length ? parts.join(' / ') : '-';
  }
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function parseKeyValueRows(raw?: string | null): KeyValueRow[] {
  const parsed = safeJsonParse<unknown>(raw, null);
  if (!parsed) {
    return [];
  }

  if (Array.isArray(parsed)) {
    return parsed
      .map((item, index) => {
        if (item && typeof item === 'object') {
          const record = item as Record<string, unknown>;
          const name = stringifyValue(record.name ?? record.propName ?? record.key ?? `字段 ${index + 1}`);
          const value = stringifyValue(
            record.value ??
              record.propValue ??
              record.valueName ??
              record.freeText ??
              record.selectedValues ??
              record.selectedVids ??
              record.vid ??
              record.pid,
          );
          return { key: `${name}-${index}`, name, value };
        }
        return {
          key: `row-${index}`,
          name: `字段 ${index + 1}`,
          value: stringifyValue(item),
        };
      })
      .filter((item) => item.value !== '-');
  }

  if (typeof parsed === 'object') {
    return Object.entries(parsed as Record<string, unknown>).map(([key, value], index) => ({
      key: `${key}-${index}`,
      name: key,
      value: stringifyValue(value),
    }));
  }

  return [
    {
      key: 'raw',
      name: '内容',
      value: stringifyValue(parsed),
    },
  ];
}

function parseTemuAttributeRows(raw?: string | null): KeyValueRow[] {
  const parsed = safeJsonParse<unknown>(raw, null);
  if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
    const properties = (parsed as { properties?: unknown }).properties;
    if (Array.isArray(properties)) {
      return properties
        .map((item, index) => {
          if (!item || typeof item !== 'object') {
            return null;
          }
          const record = item as Record<string, unknown>;
          const name = `${stringifyValue(record.name)}${record.required ? '（必填）' : ''}`;
          const selectedValues = Array.isArray(record.selectedValues)
            ? record.selectedValues
                .map((selected) => {
                  if (selected && typeof selected === 'object') {
                    const selectedRecord = selected as Record<string, unknown>;
                    return stringifyValue(selectedRecord.value ?? selectedRecord.name ?? selectedRecord.label);
                  }
                  return stringifyValue(selected);
                })
                .filter((value) => value !== '-')
            : [];
          const value =
            selectedValues.length > 0
              ? selectedValues.join('，')
              : stringifyValue(record.freeText ?? record.value ?? record.selectedVids);
          return {
            key: `${name}-${index}`,
            name,
            value,
          };
        })
        .filter((item): item is KeyValueRow => !!item && item.value !== '-');
    }
  }

  return parseKeyValueRows(raw);
}

function parseSpecJsonText(specJson?: string | null, fallback = '-') {
  const parsed = safeJsonParse<unknown>(specJson, null);
  if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
    const text = Object.entries(parsed as Record<string, unknown>)
      .map(([key, value]) => `${key}: ${stringifyValue(value)}`)
      .join(' | ');
    return text || fallback;
  }
  if (specJson && specJson.trim()) {
    return specJson.trim();
  }
  return fallback;
}

function buildInitialSkuSelection(props: ProductCollectionSkuPropVO[]) {
  const next: Record<string, string> = {};
  props.forEach((prop) => {
    const propName = (prop.name || '').trim();
    const firstValue = prop.values?.find((value) => value.value)?.value?.trim();
    if (propName && firstValue) {
      next[propName] = firstValue;
    }
  });
  return next;
}

function matchesSkuSelection(row: ProductCollectionSkuRowVO, selection: Record<string, string>) {
  const entries = Object.entries(selection).filter(([key, value]) => key && value);
  if (!entries.length) {
    return true;
  }

  const specJson = safeJsonParse<Record<string, unknown> | null>(row.specJson, null);
  const tokens = `${row.specKey || ''} ${row.specJson || ''}`;
  return entries.every(([key, value]) => {
    const exactValue = specJson && typeof specJson === 'object' ? stringifyValue(specJson[key]) : '';
    if (exactValue && exactValue !== '-' && exactValue === value) {
      return true;
    }
    return tokens.includes(value);
  });
}

function formatTemuSkuSize(row: ProductCollectionTemuSkuVO) {
  const values = [row.lengthCm, row.widthCm, row.heightCm].filter((item) => item !== null && item !== undefined);
  return values.length === 3 ? `${row.lengthCm} x ${row.widthCm} x ${row.heightCm} cm` : '-';
}

function hasPackagingInfo(detail: ProductCollectionDetailVO | null) {
  if (!detail) {
    return false;
  }
  return !!(
    detail.packagingDimensions ||
    detail.packagingWeight !== null ||
    detail.packagingLength !== null ||
    detail.packagingWidth !== null ||
    detail.packagingHeight !== null
  );
}

function buildSplitGroupKey() {
  return `group_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

function createSplitGroup(index: number): SplitGroupState {
  return {
    key: buildSplitGroupKey(),
    name: `拆分商品${index}`,
  };
}

function renderKeyValueGrid(rows: KeyValueRow[], emptyText: string) {
  if (!rows.length) {
    return <div className="pcd-empty-block">{emptyText}</div>;
  }

  return (
    <div className="pcd-attr-grid">
      {rows.map((item) => (
        <div key={item.key} className="pcd-attr-item">
          <Typography.Text type="secondary" className="pcd-attr-label">
            {item.name}
          </Typography.Text>
          <Typography.Text className="pcd-attr-value">{item.value}</Typography.Text>
        </div>
      ))}
    </div>
  );
}

const ProductCollectionDetailPage = () => {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const params = useParams<{ id: string }>();
  const collectionId = Number(params.id || 0);
  const isStandalone = location.pathname.startsWith('/goods/');

  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<ProductCollectionDetailVO | null>(null);
  const [showVideo, setShowVideo] = useState(false);
  const [activeImageIndex, setActiveImageIndex] = useState(0);
  const [selectedSku, setSelectedSku] = useState<Record<string, string>>({});
  const [rawOpen, setRawOpen] = useState(false);
  const [replacingKwcdn, setReplacingKwcdn] = useState(false);
  const [normalizingImages, setNormalizingImages] = useState(false);
  const [batchTranslatingImages, setBatchTranslatingImages] = useState(false);
  const [swappingImages, setSwappingImages] = useState(false);
  const [addingDetailImage, setAddingDetailImage] = useState('');
  const [translateState, setTranslateState] = useState<TranslateState>(createInitialTranslateState);
  const [fusionState, setFusionState] = useState<FusionState>(createInitialFusionState);
  const [splitOpen, setSplitOpen] = useState(false);
  const [splitSaving, setSplitSaving] = useState(false);
  const [splitGroups, setSplitGroups] = useState<SplitGroupState[]>([]);
  const [splitAssignments, setSplitAssignments] = useState<Record<number, string>>({});
  const [imageProxyConfig, setImageProxyConfig] = useState<AlibabaImageProxyConfigVO | null>(null);
  const [temuSkuImageReplace, setTemuSkuImageReplace] = useState<TemuSkuImageReplaceState>({
    open: false,
    row: null,
    uploading: false,
  });

  async function loadImageProxyConfig() {
    try {
      const res = await alibabaImageProxyConfigApi.current();
      setImageProxyConfig(res.data || null);
    } catch {
      setImageProxyConfig(null);
    }
  }

  function displayImageUrl(url?: string | null) {
    return buildAlibabaImageProxyUrl(url, imageProxyConfig);
  }

  async function loadDetail(showPageLoading = true) {
    if (!Number.isFinite(collectionId) || collectionId <= 0) {
      message.error('缺少商品 ID');
      navigate('/platform/product-collections', { replace: true });
      return;
    }

    if (showPageLoading) {
      setLoading(true);
    }

    try {
      const res = await productCollectionsApi.get(collectionId);
      setDetail(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
    } finally {
      if (showPageLoading) {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    void loadImageProxyConfig();
    void loadDetail();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [collectionId]);

  useEffect(() => {
    const nextTitle = detail?.productName?.trim() || '采集商品详情';
    document.title = isStandalone ? `${nextTitle} - 商品详情` : nextTitle;
  }, [detail?.productName, isStandalone]);

  const carouselOnlyImages = useMemo(() => parseImageList(detail?.carouselImages), [detail?.carouselImages]);
  const galleryImages = useMemo(() => {
    const list: string[] = [];
    if (detail?.productMainImage) {
      list.push(detail.productMainImage);
    }
    carouselOnlyImages.forEach((item) => {
      if (!list.includes(item)) {
        list.push(item);
      }
    });
    return list;
  }, [carouselOnlyImages, detail?.productMainImage]);
  const detailImages = useMemo(() => parseImageList(detail?.detailImages), [detail?.detailImages]);
  const attributeRows = useMemo(() => parseKeyValueRows(detail?.attributesData), [detail?.attributesData]);
  const temuAttributeRows = useMemo(() => parseTemuAttributeRows(detail?.temuAttributes), [detail?.temuAttributes]);
  const priceStepsRows = useMemo(() => parseKeyValueRows(detail?.priceSteps), [detail?.priceSteps]);
  const totalStock = useMemo(() => {
    if (!detail?.skuRows?.length) {
      return null;
    }
    return detail.skuRows.reduce((sum, row) => sum + (row.stock || 0), 0);
  }, [detail?.skuRows]);
  const selectedSkuKey = useMemo(() => {
    if (!detail?.skuPropsExt?.length) {
      return '';
    }
    return detail.skuPropsExt
      .map((prop) => {
        const propName = prop.name || '';
        return selectedSku[propName] || '';
      })
      .filter((item) => !!item)
      .join(' > ');
  }, [detail?.skuPropsExt, selectedSku]);
  const selectedSkuRow = useMemo(() => {
    if (!detail?.skuRows?.length) {
      return null;
    }
    return detail.skuRows.find((row) => matchesSkuSelection(row, selectedSku)) || detail.skuRows[0] || null;
  }, [detail?.skuRows, selectedSku]);
  const allFieldKeys = useMemo(() => (detail ? Object.keys(detail).sort((a, b) => a.localeCompare(b)) : []), [detail]);
  const hasPublishResponse = !!(
    detail?.lastPublishRunId ||
    detail?.lastPublishStatus ||
    detail?.lastPublishGoodsId ||
    detail?.lastPublishResponseRaw ||
    detail?.lastPublishError ||
    detail?.lastPublishStartedAt ||
    detail?.lastPublishFinishedAt
  );
  const splitSkuRows = useMemo(
    () =>
      (detail?.skuRows || [])
        .filter((row) => row.id !== null && row.id !== undefined)
        .map((row) => ({
          id: row.id,
          skuId: row.skuId || '',
          specKey: row.specKey || '',
          specJsonText: parseSpecJsonText(row.specJson, row.specKey || '-'),
          stock: row.stock,
          price: row.price,
          image: row.image || '',
        })),
    [detail?.skuRows],
  );

  useEffect(() => {
    if (!galleryImages.length) {
      setActiveImageIndex(0);
      setShowVideo(false);
      return;
    }
    setActiveImageIndex((current) => (current < galleryImages.length ? current : 0));
  }, [galleryImages]);

  useEffect(() => {
    if (!detail?.skuPropsExt?.length) {
      setSelectedSku({});
      return;
    }
    setSelectedSku(buildInitialSkuSelection(detail.skuPropsExt));
  }, [detail?.id, detail?.skuPropsExt]);

  const activeImage = galleryImages[activeImageIndex] || '';
  const videoPoster = galleryImages[0] || '';
  const canSplitProduct = splitSkuRows.length >= 2;
  const heroSection = detail ? (
    <div className="pcd-product-grid">
      <div className="pcd-media-col">
        {detail.companyName ? (
          <div className="pcd-company-strip">
            <div className="pcd-company-name">
              <BankOutlined />
              {detail.companyName}
            </div>
            <div className="pcd-company-meta">
              <span className="pcd-meta-item">
                <EnvironmentOutlined />
                {detail.shippingLocation || '-'}
              </span>
              <span className="pcd-meta-item">
                <LineChartOutlined />
                年销 {detail.annualSales || '-'}
              </span>
            </div>
            <div className="pcd-service-scores">
              <span>
                <RedoOutlined style={{ color: '#3b82f6' }} />
                回头率 {formatPercent(detail.repeatCustomerRate)}
              </span>
              <span>
                <StarOutlined style={{ color: '#eab308' }} />
                服务 {formatNullable(detail.serviceScore)}
              </span>
            </div>
          </div>
        ) : null}

        <div className="pcd-media-grid">
          {galleryImages.length || detail.carouselVideo ? (
            <div className="pcd-thumb-col">
              {detail.carouselVideo ? (
                <button
                  type="button"
                  className={`pcd-thumbnail-item pcd-video-thumb${showVideo ? ' active' : ''}`}
                  onClick={() => setShowVideo(true)}
                  title="点击播放视频"
                >
                  {videoPoster ? (
                    <>
                      <img src={displayImageUrl(videoPoster)} alt="video cover" loading="lazy" />
                      <span className="pcd-video-play" aria-hidden="true">
                        <PlayCircleOutlined />
                      </span>
                    </>
                  ) : (
                    <>
                      <PlayCircleOutlined />
                      <span>视频</span>
                    </>
                  )}
                </button>
              ) : null}

              {galleryImages.map((imageUrl, index) => (
                <button
                  key={`${imageUrl}-${index}`}
                  type="button"
                  className={`pcd-thumbnail-item${!showVideo && activeImageIndex === index ? ' active' : ''}`}
                  onClick={() => {
                    setShowVideo(false);
                    setActiveImageIndex(index);
                  }}
                  title={`图片 ${index + 1}`}
                >
                  <img src={displayImageUrl(imageUrl)} alt={`thumb-${index + 1}`} loading="lazy" />
                </button>
              ))}
            </div>
          ) : null}

          <div className="pcd-main-col">
            {!showVideo ? (
              <div className="pcd-main-image-container">
                {activeImage ? (
                  <>
                    <img src={displayImageUrl(activeImage)} alt={detail.productName || 'product'} loading="lazy" />
                    <div className="pcd-main-actions">
                      <button className="pcd-main-action" type="button" title="图片融合" onClick={openFusion}>
                        <AppstoreOutlined />
                      </button>
                      <button
                        className="pcd-main-action"
                        type="button"
                        title="翻译"
                        onClick={() => openTranslate('carousel', activeImageIndex, activeImage)}
                      >
                        <GlobalOutlined />
                      </button>
                      {isDeletableCarouselImage(activeImage) ? (
                        <button
                          className="pcd-main-action pcd-main-action-danger"
                          type="button"
                          title="删除"
                          onClick={() => void deleteCarouselImage(activeImage)}
                        >
                          <DeleteOutlined />
                        </button>
                      ) : null}
                    </div>
                  </>
                ) : (
                  <div className="pcd-img-empty">暂无图片</div>
                )}
              </div>
            ) : (
              <div className="pcd-video-container">
                <video src={detail.carouselVideo || undefined} controls preload="metadata" poster={videoPoster} />
              </div>
            )}
          </div>
        </div>
      </div>

        <div className="pcd-info-col">
        <div className="pcd-product-title">{detail.productName || '-'}</div>

        {detail.temuPublished ? (
          <div className="pcd-publish-row">
            <Tag color="green">已发布</Tag>
            <span>发布时间 {formatDateTime(detail.temuPublishedAt)}</span>
            {detail.temuGoodsId ? <span>goodsId {detail.temuGoodsId}</span> : null}
            {detail.lastPublishRunId ? <span>runId {detail.lastPublishRunId}</span> : null}
          </div>
        ) : null}

        {hasPublishResponse ? (
          <Card
            size="small"
            className="pcd-publish-response-card"
            title="发布返回"
            extra={<Tag color={detail.lastPublishStatus === 'SUCCEEDED' ? 'green' : detail.lastPublishStatus === 'FAILED' ? 'red' : 'blue'}>{detail.lastPublishStatus || '-'}</Tag>}
          >
            <Descriptions size="small" column={2}>
              <Descriptions.Item label="runId">{detail.lastPublishRunId || '-'}</Descriptions.Item>
              <Descriptions.Item label="goodsId">{detail.lastPublishGoodsId || detail.temuGoodsId || '-'}</Descriptions.Item>
              <Descriptions.Item label="开始时间">{formatDateTime(detail.lastPublishStartedAt) || '-'}</Descriptions.Item>
              <Descriptions.Item label="结束时间">{formatDateTime(detail.lastPublishFinishedAt) || '-'}</Descriptions.Item>
            </Descriptions>
            {detail.lastPublishResponseRaw ? (
              <Typography.Paragraph
                copyable
                style={{
                  marginTop: 10,
                  marginBottom: 0,
                  maxHeight: 220,
                  overflow: 'auto',
                  padding: 10,
                  borderRadius: 6,
                  background: '#fafafa',
                  whiteSpace: 'pre-wrap',
                  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
                }}
              >
                {prettyPrintJsonText(detail.lastPublishResponseRaw)}
              </Typography.Paragraph>
            ) : detail.lastPublishError ? (
              <Typography.Paragraph type="danger" copyable style={{ marginTop: 10, marginBottom: 0 }}>
                {detail.lastPublishError}
              </Typography.Paragraph>
            ) : (
              <Typography.Text type="secondary">暂无 TEMU 返回内容</Typography.Text>
            )}
          </Card>
        ) : null}

        {(detail.targetShopNames || []).length ? (
          <div className="pcd-shop-row">
            <span className="pcd-cat-label">采集店铺</span>
            <div className="pcd-shop-tags">
              {(detail.targetShopNames || []).map((shopName) => (
                <Tag key={shopName} color="blue">
                  {shopName}
                </Tag>
              ))}
            </div>
          </div>
        ) : null}

        <div className="pcd-category-row">
          <span className="pcd-cat-label">原始类目</span>
          <span className="pcd-cat-value">{detail.originalCategory || '-'}</span>
        </div>

        {detail.temuCatname ? (
          <div className="pcd-category-row">
            <span className="pcd-cat-label">TEMU类目</span>
            <span className="pcd-cat-value">{detail.temuCatname}</span>
          </div>
        ) : null}

        {detail.temuOptimizedTitleEn || detail.temuOptimizedTitleZh || detail.temuCategoryKeywords ? (
          <div className="pcd-ai-title-card">
            <div className="pcd-ai-title-head">AI 标题与类目关键词</div>
            {detail.temuOptimizedTitleEn ? (
              <div className="pcd-ai-title-row">
                <span className="pcd-ai-title-label">英文标题</span>
                <span className="pcd-ai-title-value">{detail.temuOptimizedTitleEn}</span>
              </div>
            ) : null}
            {detail.temuOptimizedTitleZh ? (
              <div className="pcd-ai-title-row">
                <span className="pcd-ai-title-label">中文标题</span>
                <span className="pcd-ai-title-value">{detail.temuOptimizedTitleZh}</span>
              </div>
            ) : null}
            {detail.temuCategoryKeywords ? (
              <div className="pcd-ai-title-row">
                <span className="pcd-ai-title-label">类目关键词</span>
                <span className="pcd-ai-title-value">{detail.temuCategoryKeywords}</span>
              </div>
            ) : null}
          </div>
        ) : null}

        <div className="pcd-price-section">
          <div className="pcd-price-range">
            <span className="pcd-price-label">价格区间</span>
            <span className="pcd-price-value">{formatRange(detail.minPrice, detail.maxPrice)}</span>
            <span className="pcd-price-unit">/个</span>
          </div>
          <div className="pcd-moq-info">
            <div>
              <span>起订量</span>
              {detail.moqText || formatNullable(detail.moq)}
            </div>
            <div>
              <span>库存</span>
              {formatNullable(totalStock)}
            </div>
          </div>
        </div>

        {detail.skuPropsExt?.length ? (
          <div className="pcd-sku-selector">
            <div className="pcd-sku-title">
              <AppstoreOutlined />
              规格选择
            </div>
            <div className="pcd-sku-props">
              {detail.skuPropsExt.map((prop) => {
                const propName = prop.name || '';
                return (
                  <div key={prop.id} className="pcd-sku-prop">
                    <div className="pcd-sku-prop-name">{propName || '未命名规格'}</div>
                    <div className="pcd-sku-prop-values">
                      {(prop.values || []).map((value) => {
                        const currentValue = value.value || '';
                        const active = selectedSku[propName] === currentValue;
                        return (
                          <button
                            key={value.id}
                            type="button"
                            className={`pcd-sku-value${active ? ' active' : ''}`}
                            onClick={() =>
                              setSelectedSku((current) => ({
                                ...current,
                                [propName]: currentValue,
                              }))
                            }
                          >
                            {value.image ? <img src={displayImageUrl(value.image)} alt={currentValue} loading="lazy" /> : null}
                            <span>{currentValue || '-'}</span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>

            {selectedSkuKey ? (
              <div className="pcd-sku-selected">
                <div className="pcd-sku-selected-row">
                  <span className="pcd-sku-selected-key">当前</span>
                  <span className="pcd-sku-selected-value">{selectedSkuKey}</span>
                </div>
                <div className="pcd-sku-selected-row">
                  <span className="pcd-sku-selected-key">skuId</span>
                  <span className="pcd-sku-selected-value">{selectedSkuRow?.skuId || '-'}</span>
                </div>
                <div className="pcd-sku-selected-row">
                  <span className="pcd-sku-selected-key">库存</span>
                  <span className="pcd-sku-selected-value">{formatNullable(selectedSkuRow?.stock)}</span>
                </div>
                <div className="pcd-sku-selected-row">
                  <span className="pcd-sku-selected-key">价格</span>
                  <span className="pcd-sku-selected-value">{formatMoney(selectedSkuRow?.price)}</span>
                </div>
              </div>
            ) : null}
          </div>
        ) : null}

        {detail.shippingServicesInfo || detail.baseFreight !== null ? (
          <div className="pcd-shipping-info">
            {detail.shippingServicesInfo ? (
              <div className="pcd-shipping-line">
                <CarOutlined style={{ color: '#2563eb' }} />
                <span>{detail.shippingServicesInfo}</span>
              </div>
            ) : null}
            {detail.baseFreight !== null ? (
              <div className="pcd-freight-wrap">
                <span className="pcd-freight">
                  <TagOutlined />
                  基础运费 {formatMoney(detail.baseFreight)}
                </span>
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  ) : null;

  const mainDetailSection = detail ? (
    <div className="pcd-detail-section">
      <div className="pcd-detail-layout">
        <div className="pcd-detail-images-col">
          <div className="pcd-section-title">
            <PictureOutlined />
            商品细节图
          </div>
          {detailImages.length ? (
            <div className="pcd-detail-images">
              {detailImages.map((imageUrl, index) => (
                <div key={`${imageUrl}-${index}`} className="pcd-detail-image-item">
                  <img src={displayImageUrl(imageUrl)} alt={`detail-${index + 1}`} loading="lazy" />
                  <button
                    className="pcd-detail-add-carousel"
                    type="button"
                    disabled={carouselOnlyImages.includes(imageUrl)}
                    title={carouselOnlyImages.includes(imageUrl) ? '这张图已在轮播图中' : '添加到轮播'}
                    onClick={() => void addDetailImageToCarousel(imageUrl)}
                  >
                    {addingDetailImage === imageUrl ? '添加中...' : '添加到轮播'}
                  </button>
                  <button
                    className="pcd-detail-translate"
                    type="button"
                    title="翻译"
                    onClick={() => openTranslate('detail', index, imageUrl)}
                  >
                    <GlobalOutlined />
                  </button>
                  <button
                    className="pcd-detail-del"
                    type="button"
                    title="删除"
                    onClick={() => void deleteDetailImage(imageUrl)}
                  >
                    <DeleteOutlined />
                  </button>
                </div>
              ))}
            </div>
          ) : (
            <div className="pcd-empty-block">暂无详情图</div>
          )}
        </div>

        <div className="pcd-detail-meta-col">
          <div className="pcd-section-title">
            <TableOutlined />
            产品参数
          </div>
          {renderKeyValueGrid(attributeRows, '暂无参数')}

          {temuAttributeRows.length ? (
            <div className="pcd-meta-block">
              <div className="pcd-section-title pcd-section-title-small">
                <TableOutlined />
                TEMU 商品属性
              </div>
              {renderKeyValueGrid(temuAttributeRows, '暂无 TEMU 属性')}
            </div>
          ) : null}

          {(detail.temuSkus || []).length ? (
            <div className="pcd-meta-block">
              <div className="pcd-section-title pcd-section-title-small">
                <TableOutlined />
                TEMU SKU
              </div>
              <div className="pcd-temu-sku-table">
                <div className="pcd-temu-sku-head">
                  <span>图片</span>
                  <span>属性</span>
                  <span>供货价</span>
                  <span>重量</span>
                  <span>尺寸</span>
                </div>
                {(detail.temuSkus || []).map((row, index) => (
                  <div
                    key={`${row.temuSkuId || row.originSkuId || row.specKey || 'temu'}-${index}`}
                    className="pcd-temu-sku-row"
                  >
                    <button
                      className="pcd-temu-sku-image-btn"
                      type="button"
                      title="点击替换 SKU 图片"
                      onClick={() => openTemuSkuImageReplace(row)}
                    >
                      {row.image ? (
                        <Image
                          src={displayImageUrl(row.image)}
                          width={46}
                          height={46}
                          preview={false}
                          style={{ borderRadius: 8, objectFit: 'cover' }}
                        />
                      ) : (
                        <PictureOutlined />
                      )}
                    </button>
                    <span className="pcd-mono" title={row.specKey || ''}>
                      {row.specKey || parseSpecJsonText(row.specJson)}
                    </span>
                    <span className="pcd-mono">{formatMoney(row.supplyPrice)}</span>
                    <span className="pcd-mono">{formatNullable(row.weightG, 'g')}</span>
                    <span className="pcd-mono">{formatTemuSkuSize(row)}</span>
                  </div>
                ))}
              </div>
            </div>
          ) : null}

          {priceStepsRows.length ? (
            <div className="pcd-meta-block">
              <div className="pcd-section-title pcd-section-title-small">
                <TagOutlined />
                阶梯价 / 补充信息
              </div>
              {renderKeyValueGrid(priceStepsRows, '暂无阶梯价信息')}
            </div>
          ) : null}

          {hasPackagingInfo(detail) ? (
            <div className="pcd-packaging-info">
              <div>
                包装尺寸 <span>{detail.packagingDimensions || '-'}</span>
              </div>
              <div>
                包装重量 <span>{formatNullable(detail.packagingWeight, 'g')}</span>
              </div>
              <div>
                长×宽×高
                <span>
                  {detail.packagingLength ?? '-'}×{detail.packagingWidth ?? '-'}×{detail.packagingHeight ?? '-'}
                </span>
              </div>
              {detail.customMadeSpecs ? (
                <div>
                  定制规格 <span>{detail.customMadeSpecs}</span>
                </div>
              ) : null}
            </div>
          ) : null}

          <div className="pcd-footer-note">
            <CheckCircleOutlined />
            {detail.sourcePlatform || '-'} 商品ID {detail.productId || '-'} · 数据同步于 {formatDateTime(detail.collectionTime)}
          </div>
        </div>
      </div>
    </div>
  ) : null;

  const originSkuColumns: ColumnsType<ProductCollectionSkuRowVO> = [
    {
      title: '图片',
      key: 'image',
      width: 84,
      render: (_, record) =>
        record.image ? (
          <Image
            src={displayImageUrl(record.image)}
            width={56}
            height={56}
            preview={false}
            style={{ borderRadius: 8, objectFit: 'cover' }}
          />
        ) : (
          '-'
        ),
    },
    {
      title: '规格',
      key: 'specKey',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{record.specKey || '-'}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {parseSpecJsonText(record.specJson, '-')}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'SKU ID',
      dataIndex: 'skuId',
      key: 'skuId',
      width: 160,
      render: (value: ProductCollectionSkuRowVO['skuId']) => value || '-',
    },
    {
      title: '库存',
      dataIndex: 'stock',
      key: 'stock',
      width: 100,
      render: (value: ProductCollectionSkuRowVO['stock']) => formatNullable(value),
    },
    {
      title: '价格',
      dataIndex: 'price',
      key: 'price',
      width: 120,
      render: (value: ProductCollectionSkuRowVO['price']) => formatMoney(value),
    },
  ];

  async function saveUpdate(payload: ProductCollectionUpdatePayload, successText: string) {
    await productCollectionsApi.update(collectionId, payload);
    await loadDetail(false);
    message.success(successText);
  }

  function openTemuSkuImageReplace(row: ProductCollectionTemuSkuVO) {
    if (!row.id) {
      message.warning('请先保存 TEMU SKU 后再替换图片');
      return;
    }
    setTemuSkuImageReplace({
      open: true,
      row,
      uploading: false,
    });
  }

  async function uploadAndReplaceTemuSkuImage(file: File) {
    const row = temuSkuImageReplace.row;
    if (!detail?.id || !row?.id) {
      message.error('缺少 SKU 信息');
      return;
    }
    setTemuSkuImageReplace((current) => ({ ...current, uploading: true }));
    try {
      await productCollectionsApi.uploadTemuSkuImageFile(detail.id, row.id, file);
      await loadDetail(false);
      message.success('SKU 图片已替换');
      setTemuSkuImageReplace({ open: false, row: null, uploading: false });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '替换失败');
      setTemuSkuImageReplace((current) => ({ ...current, uploading: false }));
    }
  }

  const temuSkuImageUploadProps: UploadProps = {
    accept: 'image/*',
    maxCount: 1,
    showUploadList: false,
    beforeUpload: (file) => {
      void uploadAndReplaceTemuSkuImage(file);
      return false;
    },
  };

  function openExternal(url?: string | null) {
    if (!url) {
      return;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  function goBackToCollections() {
    if (isStandalone && window.opener) {
      window.close();
      return;
    }
    navigate('/platform/product-collections');
  }

  function isDeletableCarouselImage(imageUrl: string) {
    if (!imageUrl) {
      return false;
    }
    if (detail?.productMainImage && detail.productMainImage === imageUrl) {
      return false;
    }
    return carouselOnlyImages.includes(imageUrl);
  }

  function openSplitModal() {
    if (!canSplitProduct) {
      message.info('至少需要 2 个 SKU 才能拆分商品');
      return;
    }
    setSplitGroups([createSplitGroup(1), createSplitGroup(2)]);
    setSplitAssignments({});
    setSplitOpen(true);
  }

  function addSplitGroup() {
    setSplitGroups((current) => [...current, createSplitGroup(current.length + 1)]);
  }

  function removeSplitGroup(groupKey: string) {
    if (splitGroups.length <= 2) {
      message.warning('至少保留两个分组');
      return;
    }
    setSplitGroups((current) => current.filter((group) => group.key !== groupKey));
    setSplitAssignments((current) => {
      const next: Record<number, string> = {};
      Object.entries(current).forEach(([rowId, assignedGroupKey]) => {
        if (assignedGroupKey !== groupKey) {
          next[Number(rowId)] = assignedGroupKey;
        }
      });
      return next;
    });
  }

  function buildSplitPayload(): ProductCollectionSplitPayload {
    const groups = splitGroups
      .map((group, index) => ({
        name: group.name.trim() || `拆分商品${index + 1}`,
        skuRowIds: splitSkuRows.filter((row) => splitAssignments[row.id] === group.key).map((row) => row.id),
      }))
      .filter((group) => group.skuRowIds.length > 0);

    if (Object.keys(splitAssignments).length !== splitSkuRows.length) {
      throw new Error('请先为所有 SKU 选择分组');
    }
    if (groups.length < 2) {
      throw new Error('至少需要两个有 SKU 的分组');
    }

    return { groups };
  }

  async function submitSplit() {
    try {
      const payload = buildSplitPayload();
      setSplitSaving(true);
      const res = await productCollectionsApi.splitProduct(collectionId, payload);
      const products = Array.isArray(res.data?.products) ? res.data.products : [];
      message.success(`拆分完成，已生成 ${products.length} 个商品`);
      setSplitOpen(false);
      const firstProductId = products[0]?.id;
      if (firstProductId) {
        navigate(isStandalone ? `/goods/${firstProductId}` : `/platform/product-collections/${firstProductId}`, {
          replace: true,
        });
        return;
      }
      navigate('/platform/product-collections', { replace: true });
    } catch (error) {
      message.warning(error instanceof Error ? error.message : '拆分失败');
    } finally {
      setSplitSaving(false);
    }
  }

  async function replaceImagesToKwcdn() {
    setReplacingKwcdn(true);
    try {
      const res = await productCollectionsApi.replaceImagesToKwcdn(collectionId);
      await loadDetail(false);
      message.success(res.data?.changed ? '已替换并保存到数据库' : '图片已是 kwcdn 域名，无需替换');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '替换失败');
    } finally {
      setReplacingKwcdn(false);
    }
  }

  async function normalizeAllImagesTo800() {
    setNormalizingImages(true);
    try {
      const res = await productCollectionsApi.normalizeAllImagesTo800(collectionId);
      await loadDetail(false);
      modal.info({
        title: '图片规范化结果',
        width: 820,
        content: (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="总图片数">{res.data?.totalImages ?? 0}</Descriptions.Item>
              <Descriptions.Item label="已处理变更">{Array.isArray(res.data?.changes) ? res.data?.changes.length : 0}</Descriptions.Item>
              <Descriptions.Item label="SKU 图片变更">{res.data?.skuImageChanged ?? 0}</Descriptions.Item>
              <Descriptions.Item label="结果">{res.data?.changed ? '已写回数据库' : '所有图片已是 800x800，无需处理'}</Descriptions.Item>
            </Descriptions>
            <Table<Record<string, unknown>>
              size="small"
              rowKey={(_, index) => `normalize-${index ?? 0}`}
              pagination={false}
              locale={{ emptyText: '没有图片需要变更' }}
              dataSource={Array.isArray(res.data?.changes) ? res.data.changes : []}
              columns={[
                { title: '字段', dataIndex: 'field', key: 'field', width: 150 },
                {
                  title: '原尺寸',
                  key: 'size',
                  width: 120,
                  render: (_, record) => `${record.width ?? '-'} x ${record.height ?? '-'}`,
                },
                { title: '原因', dataIndex: 'reason', key: 'reason', width: 180 },
                { title: '原图', dataIndex: 'original', key: 'original', render: (value) => String(value || '-') },
                { title: '结果', dataIndex: 'uploaded', key: 'uploaded', render: (value) => String(value || '-') },
              ]}
            />
          </Space>
        ),
      });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '规范化失败');
    } finally {
      setNormalizingImages(false);
    }
  }

  async function translateAllImages() {
    setBatchTranslatingImages(true);
    try {
      const res = await productCollectionsApi.translateAllImages(collectionId, { provider: 'aliyun' });
      await loadDetail(false);
      modal.info({
        title: '批量翻译结果',
        width: 860,
        content: (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="翻译通道">{res.data?.provider || 'aliyun'}</Descriptions.Item>
              <Descriptions.Item label="字段数">{res.data?.totalFields ?? 0}</Descriptions.Item>
              <Descriptions.Item label="去重后图片数">{res.data?.uniqueImages ?? 0}</Descriptions.Item>
              <Descriptions.Item label="成功">{res.data?.successCount ?? 0}</Descriptions.Item>
              <Descriptions.Item label="失败">{res.data?.failedCount ?? 0}</Descriptions.Item>
              <Descriptions.Item label="SKU 图片变更">{res.data?.skuChanged ?? 0}</Descriptions.Item>
            </Descriptions>
            <Table<Record<string, unknown>>
              size="small"
              rowKey={(_, index) => `translate-all-${index ?? 0}`}
              pagination={false}
              locale={{ emptyText: '没有可翻译图片' }}
              dataSource={Array.isArray(res.data?.changes) ? res.data.changes : []}
              columns={[
                {
                  title: '字段引用',
                  key: 'fieldRefs',
                  render: (_, record) =>
                    Array.isArray(record.fieldRefs) ? (record.fieldRefs as unknown[]).map((item) => String(item)).join('，') : '-',
                },
                { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
                { title: '通道', dataIndex: 'provider', key: 'provider', width: 100 },
                { title: '原图', dataIndex: 'sourceUrl', key: 'sourceUrl', render: (value) => String(value || '-') },
                { title: '译图', dataIndex: 'translatedUrl', key: 'translatedUrl', render: (value) => String(value || '-') },
                { title: '错误', dataIndex: 'errorMsg', key: 'errorMsg', render: (value) => String(value || '-') },
              ]}
            />
          </Space>
        ),
      });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '批量翻译失败');
    } finally {
      setBatchTranslatingImages(false);
    }
  }

  async function swapCarouselAndDetail() {
    const nextCarousel = JSON.stringify(detailImages);
    const nextDetail = JSON.stringify(carouselOnlyImages);

    if (!detailImages.length && !carouselOnlyImages.length) {
      message.info('暂无可交换的图片数据');
      return;
    }

    setSwappingImages(true);
    try {
      await saveUpdate(
        {
          carouselImages: nextCarousel,
          detailImages: nextDetail,
        },
        '已交换轮播图与详情图',
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : '交换失败');
    } finally {
      setSwappingImages(false);
    }
  }

  async function deleteCarouselImage(imageUrl: string) {
    modal.confirm({
      title: '删除轮播图',
      content: '确定删除这张轮播图吗？',
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          const next = carouselOnlyImages.filter((item) => item && item !== imageUrl);
          await saveUpdate({ carouselImages: JSON.stringify(next) }, '已删除轮播图');
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
        }
      },
    });
  }

  async function deleteDetailImage(imageUrl: string) {
    modal.confirm({
      title: '删除详情图',
      content: '确定删除这张详情图吗？',
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          const next = detailImages.filter((item) => item && item !== imageUrl);
          await saveUpdate({ detailImages: JSON.stringify(next) }, '已删除详情图');
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
        }
      },
    });
  }

  async function addDetailImageToCarousel(imageUrl: string) {
    if (carouselOnlyImages.includes(imageUrl)) {
      message.info('这张图已经在轮播图里了');
      return;
    }

    setAddingDetailImage(imageUrl);
    try {
      const next = [...carouselOnlyImages, imageUrl];
      await saveUpdate({ carouselImages: JSON.stringify(next) }, '已添加到轮播图');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '添加失败');
    } finally {
      setAddingDetailImage('');
    }
  }

  function openTranslate(kind: 'carousel' | 'detail', index: number, imageUrl: string) {
    setTranslateState({
      ...createInitialTranslateState(),
      open: true,
      kind,
      index,
      originalUrl: imageUrl,
    });
  }

  async function runTranslate() {
    if (!translateState.originalUrl) {
      return;
    }

    setTranslateState((current) => ({
      ...current,
      loading: true,
      translatedUrl: '',
      storedUrl: '',
      taskId: '',
    }));

    try {
      const res = await productCollectionsApi.translateImage(collectionId, {
        imageUrl: translateState.originalUrl,
        sourceLanguage: translateState.sourceLanguage,
        targetLang: translateState.targetLang,
        provider: translateState.provider,
        containDetail: translateState.containDetail,
        uploadToOss: translateState.uploadToOss,
      });
      setTranslateState((current) => ({
        ...current,
        loading: false,
        translatedUrl: res.data?.translatedUrl || '',
        storedUrl: res.data?.storedUrl || '',
        taskId: res.data?.taskId || '',
      }));
      message.success('翻译完成');
    } catch (error) {
      setTranslateState((current) => ({ ...current, loading: false }));
      message.error(error instanceof Error ? error.message : '翻译失败');
    }
  }

  async function saveTranslated() {
    const nextUrl = translateState.storedUrl || translateState.translatedUrl;
    if (!nextUrl) {
      message.error('还没有翻译结果');
      return;
    }

    setTranslateState((current) => ({ ...current, saving: true }));
    try {
      if (translateState.kind === 'carousel') {
        const original = translateState.originalUrl;
        const nextMainImage = detail?.productMainImage === original ? nextUrl : detail?.productMainImage;
        const nextCarouselImages = carouselOnlyImages.map((item) => (item === original ? nextUrl : item));
        await saveUpdate(
          {
            productMainImage: nextMainImage || undefined,
            carouselImages: JSON.stringify(nextCarouselImages),
          },
          '已保存并替换轮播图',
        );
      }

      if (translateState.kind === 'detail') {
        const nextDetailImages = detailImages.map((item, index) => (index === translateState.index ? nextUrl : item));
        await saveUpdate({ detailImages: JSON.stringify(nextDetailImages) }, '已保存并替换详情图');
      }

      setTranslateState(createInitialTranslateState());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      setTranslateState((current) => ({ ...current, saving: false }));
      return;
    }
  }

  function openFusion() {
    setFusionState((current) => ({
      ...createInitialFusionState(),
      prompt: current.prompt,
      negativePrompt: current.negativePrompt,
      width: current.width,
      height: current.height,
      strength: current.strength,
      model: current.model,
      seed: current.seed,
      open: true,
    }));
  }

  function toggleFusionImage(imageUrl: string) {
    setFusionState((current) => {
      if (current.selected.includes(imageUrl)) {
        return {
          ...current,
          selected: current.selected.filter((item) => item !== imageUrl),
        };
      }
      if (current.selected.length >= 4) {
        message.warning('最多选择 4 张图片');
        return current;
      }
      return {
        ...current,
        selected: [...current.selected, imageUrl],
      };
    });
  }

  async function runFusion() {
    if (fusionState.selected.length < 2) {
      message.error('至少选择 2 张图片');
      return;
    }

    setFusionState((current) => ({
      ...current,
      loading: true,
      resultUrl: '',
      errorMsg: '',
    }));

    try {
      const res = await productCollectionsApi.fuseImages(collectionId, {
        imageUrls: fusionState.selected,
        prompt: fusionState.prompt || undefined,
        negativePrompt: fusionState.negativePrompt || undefined,
        width: fusionState.width ?? undefined,
        height: fusionState.height ?? undefined,
        strength: fusionState.strength ?? undefined,
        model: fusionState.model || undefined,
        seed: fusionState.seed ?? undefined,
      });

      if (!res.data?.imageUrl) {
        throw new Error('未返回生成结果');
      }

      setFusionState((current) => ({
        ...current,
        loading: false,
        resultUrl: res.data?.imageUrl || '',
      }));
      message.success('生成完成');
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '生成失败';
      setFusionState((current) => ({
        ...current,
        loading: false,
        errorMsg: errorMessage,
      }));
      message.error(errorMessage);
    }
  }

  async function saveFusionToCarousel() {
    if (!fusionState.resultUrl) {
      return;
    }

    setFusionState((current) => ({ ...current, saving: true }));
    try {
      const next = [...carouselOnlyImages, fusionState.resultUrl];
      await saveUpdate({ carouselImages: JSON.stringify(next) }, '已保存到轮播图');
      setFusionState(createInitialFusionState());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      setFusionState((current) => ({ ...current, saving: false }));
    }
  }

  if (!detail && !loading) {
    return (
      <div
        style={{
          minHeight: isStandalone ? '100vh' : undefined,
          background: isStandalone
            ? 'linear-gradient(180deg, #f3f6fb 0%, #f7f9fc 45%, #ffffff 100%)'
            : 'transparent',
          padding: isStandalone ? '32px 20px 48px' : 0,
        }}
      >
        <div style={{ maxWidth: STANDALONE_DETAIL_MAX_WIDTH, margin: '0 auto' }}>
          <Card>
            <Empty description="暂无商品详情" />
          </Card>
        </div>
      </div>
    );
  }

  const pageContent = (
    <Space direction="vertical" size={18} style={{ width: '100%' }} className="pcd-page">
      <div className="pcd-head">
        <Button icon={<ArrowLeftOutlined />} onClick={goBackToCollections}>
          {isStandalone ? '返回商品库' : '返回列表'}
        </Button>
        <div className="pcd-head-actions">
          <Button icon={<ScissorOutlined />} onClick={openSplitModal} disabled={!canSplitProduct}>
            商品拆分
          </Button>
          <Button icon={<SwapOutlined />} loading={swappingImages} onClick={() => void swapCarouselAndDetail()}>
            轮播详情图交换
          </Button>
          <Button loading={replacingKwcdn} onClick={() => void replaceImagesToKwcdn()}>
            一键替换图片链接
          </Button>
          <Button loading={batchTranslatingImages} onClick={() => void translateAllImages()}>
            一键翻译所有图片
          </Button>
          <Button loading={normalizingImages} onClick={() => void normalizeAllImagesTo800()}>
            一键规范化图片
          </Button>
          <Button icon={<LinkOutlined />} onClick={() => openExternal(detail?.productUrl)} disabled={!detail?.productUrl}>
            打开链接
          </Button>
          <Button type="primary" onClick={() => setRawOpen(true)}>
            全部字段
          </Button>
        </div>
      </div>

      {loading && !detail ? <Card loading /> : null}

      {detail ? (
        <>
          <div className="pcd-shell">
            {heroSection}
            {mainDetailSection}
          </div>

          <Card className="pcd-origin-sku-card" title="原始 SKU">
            <Table<ProductCollectionSkuRowVO>
              rowKey={(record, index) => `${record.id ?? 'origin'}-${record.skuId ?? index ?? 0}`}
              size="small"
              columns={originSkuColumns}
              dataSource={detail.skuRows || []}
              pagination={false}
              scroll={{ x: 980 }}
              locale={{ emptyText: '暂无原始 SKU' }}
            />
          </Card>
        </>
      ) : null}

      <Drawer open={rawOpen} title="全部字段" width={900} onClose={() => setRawOpen(false)}>
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          {allFieldKeys.map((fieldKey) => {
            const value = detail ? (detail as unknown as Record<string, unknown>)[fieldKey] : null;
            const text = prettyJson(value);
            const isLarge = text.length > 160 || text.includes('\n');
            return (
              <div key={fieldKey} className="pcd-raw-row">
                <Typography.Text strong className="pcd-raw-key">
                  {fieldKey}
                </Typography.Text>
                {isLarge ? (
                  <pre className="pcd-raw-pre">{text || '-'}</pre>
                ) : (
                  <Typography.Text className="pcd-raw-value">{text || '-'}</Typography.Text>
                )}
              </div>
            );
          })}
        </Space>
      </Drawer>

      <Modal
        open={translateState.open}
        title="图片翻译"
        width={920}
        onCancel={() => setTranslateState(createInitialTranslateState())}
        footer={[
          <Button key="cancel" onClick={() => setTranslateState(createInitialTranslateState())}>
            取消
          </Button>,
          <Button key="run" type="primary" loading={translateState.loading} onClick={() => void runTranslate()}>
            开始翻译
          </Button>,
          <Button
            key="save"
            type="primary"
            ghost
            disabled={!(translateState.storedUrl || translateState.translatedUrl)}
            loading={translateState.saving}
            onClick={() => void saveTranslated()}
          >
            保存并替换
          </Button>,
        ]}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }} className="pcd-translate-modal">
          <div className="pcd-translate-controls">
            <Row gutter={[12, 12]}>
              <Col xs={24} md={8}>
                <Typography.Text type="secondary">源语言</Typography.Text>
                <Select
                  style={{ width: '100%', marginTop: 6 }}
                  value={translateState.sourceLanguage}
                  options={languageOptions}
                  onChange={(value) => setTranslateState((current) => ({ ...current, sourceLanguage: value }))}
                />
              </Col>
              <Col xs={24} md={8}>
                <Typography.Text type="secondary">目标语言</Typography.Text>
                <Select
                  style={{ width: '100%', marginTop: 6 }}
                  value={translateState.targetLang}
                  options={languageOptions}
                  onChange={(value) => setTranslateState((current) => ({ ...current, targetLang: value }))}
                />
              </Col>
              <Col xs={24} md={8}>
                <Typography.Text type="secondary">翻译通道</Typography.Text>
                <Select
                  style={{ width: '100%', marginTop: 6 }}
                  value={translateState.provider}
                  options={providerOptions}
                  onChange={(value) => setTranslateState((current) => ({ ...current, provider: value }))}
                />
              </Col>
            </Row>

            <Space wrap style={{ marginTop: 12 }}>
              <Checkbox
                checked={translateState.containDetail}
                onChange={(event) =>
                  setTranslateState((current) => ({ ...current, containDetail: event.target.checked }))
                }
              >
                包含细节
              </Checkbox>
              <Checkbox
                checked={translateState.uploadToOss}
                onChange={(event) =>
                  setTranslateState((current) => ({ ...current, uploadToOss: event.target.checked }))
                }
              >
                上传到 OSS
              </Checkbox>
              {translateState.taskId ? <Tag color="processing">taskId: {translateState.taskId}</Tag> : null}
            </Space>
          </div>

          <div className="pcd-translate-compare">
            <div className="pcd-img-box">
              <div className="pcd-img-title">原图</div>
              <div className="pcd-img-frame">
                {translateState.originalUrl ? (
                  <img src={displayImageUrl(translateState.originalUrl)} alt="original" />
                ) : (
                  <div className="pcd-img-placeholder">暂无原图</div>
                )}
              </div>
            </div>

            <div className="pcd-img-box">
              <div className="pcd-img-title">翻译结果</div>
              <div className="pcd-img-frame">
                {translateState.storedUrl || translateState.translatedUrl ? (
                  <img src={displayImageUrl(translateState.storedUrl || translateState.translatedUrl)} alt="translated" />
                ) : (
                  <div className="pcd-img-placeholder">
                    {translateState.loading ? '正在翻译...' : '点击“开始翻译”生成结果'}
                  </div>
                )}
              </div>
            </div>
          </div>
        </Space>
      </Modal>

      <Modal
        open={fusionState.open}
        title="图片融合"
        width={960}
        onCancel={() => setFusionState(createInitialFusionState())}
        footer={[
          <Button key="close" onClick={() => setFusionState(createInitialFusionState())}>
            关闭
          </Button>,
          <Button key="run" type="primary" loading={fusionState.loading} onClick={() => void runFusion()}>
            开始生成
          </Button>,
          <Button
            key="save"
            type="primary"
            ghost
            disabled={!fusionState.resultUrl}
            loading={fusionState.saving}
            onClick={() => void saveFusionToCarousel()}
          >
            保存到轮播图
          </Button>,
        ]}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <div>
            <Typography.Text type="secondary">从轮播图选择 2-4 张图片作为融合输入</Typography.Text>
            <Flex gap={8} wrap="wrap" style={{ marginTop: 10 }}>
              {carouselOnlyImages.map((imageUrl, index) => {
                const active = fusionState.selected.includes(imageUrl);
                return (
                  <button
                    key={`${imageUrl}-${index}`}
                    type="button"
                    onClick={() => toggleFusionImage(imageUrl)}
                    style={{
                      ...thumbButtonStyle(active),
                      width: 86,
                      height: 86,
                    }}
                  >
                    <img
                      src={displayImageUrl(imageUrl)}
                      alt={`fusion-${index + 1}`}
                      style={{ width: 74, height: 74, borderRadius: 8, objectFit: 'cover' }}
                    />
                  </button>
                );
              })}
            </Flex>
          </div>

          <Row gutter={[16, 16]}>
            <Col xs={24} lg={14}>
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <div>
                  <Typography.Text type="secondary">提示词</Typography.Text>
                  <Input.TextArea
                    rows={3}
                    value={fusionState.prompt}
                    onChange={(event) => setFusionState((current) => ({ ...current, prompt: event.target.value }))}
                    placeholder="描述你希望生成的效果，例如：白底电商主图、双商品同框、柔光棚拍、真实质感"
                  />
                </div>
                <div>
                  <Typography.Text type="secondary">负面提示词</Typography.Text>
                  <Input
                    value={fusionState.negativePrompt}
                    onChange={(event) =>
                      setFusionState((current) => ({ ...current, negativePrompt: event.target.value }))
                    }
                    placeholder="可选，例如：blurry, watermark, low quality"
                  />
                </div>

                <Row gutter={[12, 12]}>
                  <Col xs={12}>
                    <Typography.Text type="secondary">宽度</Typography.Text>
                    <InputNumber
                      min={256}
                      max={2048}
                      style={{ width: '100%', marginTop: 6 }}
                      value={fusionState.width}
                      onChange={(value) => setFusionState((current) => ({ ...current, width: value }))}
                    />
                  </Col>
                  <Col xs={12}>
                    <Typography.Text type="secondary">高度</Typography.Text>
                    <InputNumber
                      min={256}
                      max={2048}
                      style={{ width: '100%', marginTop: 6 }}
                      value={fusionState.height}
                      onChange={(value) => setFusionState((current) => ({ ...current, height: value }))}
                    />
                  </Col>
                  <Col xs={12}>
                    <Typography.Text type="secondary">strength</Typography.Text>
                    <InputNumber
                      min={0}
                      max={1}
                      step={0.05}
                      style={{ width: '100%', marginTop: 6 }}
                      value={fusionState.strength}
                      onChange={(value) => setFusionState((current) => ({ ...current, strength: value }))}
                    />
                  </Col>
                  <Col xs={12}>
                    <Typography.Text type="secondary">seed</Typography.Text>
                    <InputNumber
                      min={0}
                      max={4294967294}
                      style={{ width: '100%', marginTop: 6 }}
                      value={fusionState.seed}
                      onChange={(value) => setFusionState((current) => ({ ...current, seed: value }))}
                    />
                  </Col>
                  <Col span={24}>
                    <Typography.Text type="secondary">模型</Typography.Text>
                    <Select
                      style={{ width: '100%', marginTop: 6 }}
                      value={fusionState.model}
                      options={fusionModelOptions}
                      onChange={(value) => setFusionState((current) => ({ ...current, model: value }))}
                    />
                  </Col>
                </Row>
              </Space>
            </Col>

            <Col xs={24} lg={10}>
              <Card size="small" title="生成结果">
                {fusionState.resultUrl ? (
                  <Space direction="vertical" size={12} style={{ width: '100%' }}>
                    <Image src={displayImageUrl(fusionState.resultUrl)} width="100%" />
                    <Typography.Paragraph copyable style={{ marginBottom: 0 }}>
                      {fusionState.resultUrl}
                    </Typography.Paragraph>
                  </Space>
                ) : (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={fusionState.loading ? '正在生成...' : '点击“开始生成”'}
                  />
                )}
                {fusionState.errorMsg ? (
                  <Typography.Text type="danger" style={{ marginTop: 8, display: 'block' }}>
                    {fusionState.errorMsg}
                  </Typography.Text>
                ) : null}
              </Card>
            </Col>
          </Row>
        </Space>
      </Modal>

      <Modal
        open={temuSkuImageReplace.open}
        title="替换 TEMU SKU 图片"
        width={520}
        footer={null}
        onCancel={() => setTemuSkuImageReplace({ open: false, row: null, uploading: false })}
      >
        <Space direction="vertical" size={14} style={{ width: '100%' }}>
          <div className="pcd-temu-sku-upload-preview">
            {temuSkuImageReplace.row?.image ? (
              <Image src={displayImageUrl(temuSkuImageReplace.row.image)} width={120} height={120} />
            ) : (
              <PictureOutlined />
            )}
          </div>
          <Descriptions size="small" column={1} bordered>
            <Descriptions.Item label="SKU">
              {temuSkuImageReplace.row?.specKey || parseSpecJsonText(temuSkuImageReplace.row?.specJson)}
            </Descriptions.Item>
          </Descriptions>
          <Upload {...temuSkuImageUploadProps}>
            <Button type="primary" icon={<UploadOutlined />} loading={temuSkuImageReplace.uploading}>
              选择本地图片并替换
            </Button>
          </Upload>
          <Typography.Text type="secondary">图片会先上传到系统存储，再上传到 TEMU 图片空间，并写回当前 SKU。</Typography.Text>
        </Space>
      </Modal>

      <Modal
        open={splitOpen}
        title="按 SKU 拆分商品"
        width={1080}
        okText="保存拆分"
        cancelText="取消"
        confirmLoading={splitSaving}
        onOk={() => void submitSplit()}
        onCancel={() => setSplitOpen(false)}
      >
        <div className="pcd-split-modal">
          <div className="pcd-split-toolbar">
            <div>
              <div className="pcd-split-title">新商品分组</div>
              <div className="pcd-split-subtitle">每个 SKU 只能归到一个分组，保存后原商品会被删除并替换为新商品。</div>
            </div>
            <Button type="dashed" onClick={addSplitGroup}>
              新增分组
            </Button>
          </div>

          <div className="pcd-split-groups">
            {splitGroups.map((group) => (
              <div key={group.key} className="pcd-split-group-card">
                <Input
                  value={group.name}
                  maxLength={50}
                  onChange={(event) =>
                    setSplitGroups((current) =>
                      current.map((item) => (item.key === group.key ? { ...item, name: event.target.value } : item)),
                    )
                  }
                  placeholder="请输入分组名称"
                />
                <div className="pcd-split-group-meta">
                  已分配 {splitSkuRows.filter((row) => splitAssignments[row.id] === group.key).length} 个 SKU
                  <Button danger type="link" onClick={() => removeSplitGroup(group.key)}>
                    删除
                  </Button>
                </div>
              </div>
            ))}
          </div>

          <div className="pcd-split-sku-list">
            {splitSkuRows.map((row) => (
              <div key={row.id} className="pcd-split-sku-item">
                <div className="pcd-split-sku-main">
                  {row.image ? (
                    <img src={displayImageUrl(row.image)} alt={row.specKey || row.skuId} className="pcd-split-sku-image" />
                  ) : (
                    <div className="pcd-split-sku-image pcd-split-sku-image-empty">无图</div>
                  )}
                  <div className="pcd-split-sku-info">
                    <div className="pcd-split-sku-title">{row.specKey || row.skuId || `SKU ${row.id}`}</div>
                    <div className="pcd-split-sku-meta">SKU ID: {row.skuId || '-'}</div>
                    <div className="pcd-split-sku-meta">{row.specJsonText}</div>
                    <div className="pcd-split-sku-meta">
                      库存: {formatNullable(row.stock)} | 价格: {formatMoney(row.price)}
                    </div>
                  </div>
                </div>
                <Select
                  className="pcd-split-sku-select"
                  placeholder="选择分组"
                  value={splitAssignments[row.id]}
                  options={splitGroups.map((group) => ({
                    label: group.name || '未命名分组',
                    value: group.key,
                  }))}
                  onChange={(value) =>
                    setSplitAssignments((current) => ({
                      ...current,
                      [row.id]: value,
                    }))
                  }
                />
              </div>
            ))}
          </div>
        </div>
      </Modal>
    </Space>
  );

  if (!isStandalone) {
    return pageContent;
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        background:
          'radial-gradient(circle at top left, rgba(230, 240, 255, 0.92) 0%, rgba(244, 247, 252, 0.98) 40%, #ffffff 100%)',
      }}
    >
      <div
        style={{
          maxWidth: STANDALONE_DETAIL_MAX_WIDTH,
          margin: '0 auto',
          padding: '28px 22px 42px',
        }}
      >
        <div
          style={{
            marginBottom: 18,
            padding: '18px 22px',
            borderRadius: 22,
            background: 'linear-gradient(135deg, rgba(255,255,255,0.96) 0%, rgba(248,250,255,0.96) 100%)',
            border: '1px solid rgba(22, 119, 255, 0.08)',
            boxShadow: '0 18px 48px rgba(15, 23, 42, 0.06)',
          }}
        >
          <Flex justify="space-between" align="center" wrap="wrap" gap={16}>
            <Space size={14} align="center">
              <img
                src="/system-logo.png"
                alt="TMINOS"
                style={{
                  width: 46,
                  height: 46,
                  borderRadius: 14,
                  objectFit: 'cover',
                  boxShadow: '0 10px 20px rgba(22, 119, 255, 0.12)',
                }}
              />
              <Space direction="vertical" size={1}>
                <Typography.Text type="secondary" style={{ letterSpacing: 1.2 }}>
                  TMINOS PRODUCT DETAIL
                </Typography.Text>
                <Typography.Title level={3} style={{ margin: 0 }}>
                  采集商品详情
                </Typography.Title>
              </Space>
            </Space>

            <Space size={[8, 8]} wrap>
              {detail?.sourcePlatform ? <Tag color="blue">{detail.sourcePlatform}</Tag> : null}
              {detail?.productId ? <Tag>商品ID: {detail.productId}</Tag> : null}
              {detail?.alibabaProductId ? <Tag>1688: {detail.alibabaProductId}</Tag> : null}
              {(detail?.targetShopNames || []).map((shopName) => (
                <Tag key={`standalone-${shopName}`} color="processing">
                  {shopName}
                </Tag>
              ))}
            </Space>
          </Flex>
        </div>

        {pageContent}
      </div>
    </div>
  );
};

export default ProductCollectionDetailPage;
