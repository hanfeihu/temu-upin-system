import {
  App,
  Button,
  Card,
  Checkbox,
  Descriptions,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { alibabaImageProxyConfigApi } from '@/api/alibabaImageProxyConfig';
import { ocrApi } from '@/api/ocr';
import type { AlibabaImageProxyConfigVO, OcrImageTranslateWorkerLogVO, OcrImageTranslateWorkerStatusVO, OcrSizeFilterConfigVO, OcrTaskPayload, OcrTaskVO } from '@/types/api';
import { buildAlibabaImageProxyUrl } from '@/utils/alibabaImageProxy';
import { formatDateTime } from '@/utils/format';

interface Filters {
  spuId?: number;
  productId: string;
  imageType?: number;
  execStatus?: number;
  filtered?: boolean;
  containsChinese?: boolean;
  translateStatus?: string;
  imageWidthMin?: number;
  imageWidthMax?: number;
  imageHeightMin?: number;
  imageHeightMax?: number;
}

const initialFilters: Filters = {
  spuId: undefined,
  productId: '',
  imageType: undefined,
  execStatus: undefined,
  filtered: undefined,
  containsChinese: undefined,
  translateStatus: undefined,
  imageWidthMin: undefined,
  imageWidthMax: undefined,
  imageHeightMin: undefined,
  imageHeightMax: undefined,
};

const initialForm: OcrTaskPayload & { id: number | null } = {
  id: null,
  spuId: 0,
  productId: '',
  imageType: 1,
  imageUrl: '',
  imageWidth: undefined,
  imageHeight: undefined,
  imageMd5: undefined,
  translateStatus: undefined,
  translatedImageUrl: undefined,
  execStatus: 0,
  execResult: '',
  failReason: '',
  executorPublicIp: '',
  filtered: false,
  containsChinese: undefined,
};

const initialSizeFilterForm = {
  id: null as number | null,
  imageWidth: undefined as number | undefined,
  imageHeight: undefined as number | undefined,
  enabled: true,
  remark: '',
};

interface ImageDimension {
  width: number;
  height: number;
}

const imageTypeText: Record<number, string> = {
  1: '轮播图',
  2: '详情图',
  3: 'SKU 图',
};

const execStatusText: Record<number, string> = {
  0: '待执行',
  1: '运行中',
  2: '成功',
  3: '失败',
};

function getTranslateStatusView(record: OcrTaskVO) {
  if (record.translateStatus === 'SUCCESS') {
    return { text: '翻译成功', color: 'green' };
  }
  if (record.translateStatus === 'POSITION_NOT_FOUND') {
    return { text: '未匹配到商品位置', color: 'orange' };
  }
  if (record.filtered) {
    return { text: '无需翻译', color: 'default' };
  }
  if (record.containsChinese === false) {
    return { text: '无需翻译', color: 'default' };
  }
  if (record.containsChinese === true) {
    return { text: '待翻译', color: 'processing' };
  }
  return { text: '未识别', color: 'default' };
}

function getExecStatusColor(status: number) {
  if (status === 2) {
    return 'green';
  }
  if (status === 3) {
    return 'red';
  }
  if (status === 1) {
    return 'processing';
  }
  return 'default';
}

const OcrTasksPage = () => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<OcrTaskVO[]>([]);
  const [stats, setStats] = useState<Record<string, number>>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(initialForm);
  const [translateOpen, setTranslateOpen] = useState(false);
  const [translateLoading, setTranslateLoading] = useState(false);
  const [translateSaving, setTranslateSaving] = useState(false);
  const [translateAction, setTranslateAction] = useState<'start' | 'stop' | null>(null);
  const [translateStatus, setTranslateStatus] = useState<OcrImageTranslateWorkerStatusVO | null>(null);
  const [translateMaxChineseCount, setTranslateMaxChineseCount] = useState(5);
  const [translateBatchSize, setTranslateBatchSize] = useState(1);
  const [translatePollMs, setTranslatePollMs] = useState(60000);
  const [translateProvider, setTranslateProvider] = useState('ai');
  const [translateModel, setTranslateModel] = useState('gpt-image-2');
  const [translateQuality, setTranslateQuality] = useState('medium');
  const [translateLogs, setTranslateLogs] = useState<OcrImageTranslateWorkerLogVO[]>([]);
  const [translateLogPage, setTranslateLogPage] = useState(1);
  const [translateLogTotal, setTranslateLogTotal] = useState(0);
  const [retryingTranslateLogId, setRetryingTranslateLogId] = useState<number | null>(null);
  const [imageProxyConfig, setImageProxyConfig] = useState<AlibabaImageProxyConfigVO | null>(null);
  const [imageDimensions, setImageDimensions] = useState<Record<string, ImageDimension>>({});
  const [sizeFilterOpen, setSizeFilterOpen] = useState(false);
  const [sizeFilterLoading, setSizeFilterLoading] = useState(false);
  const [sizeFilterSaving, setSizeFilterSaving] = useState(false);
  const [sizeFilterDeleting, setSizeFilterDeleting] = useState(false);
  const [sizeFilterRows, setSizeFilterRows] = useState<OcrSizeFilterConfigVO[]>([]);
  const [sizeFilterForm, setSizeFilterForm] = useState(initialSizeFilterForm);

  async function load(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true);
    try {
      const [listRes, statsRes] = await Promise.all([
        ocrApi.listTasks({
          spuId: nextFilters.spuId,
          productId: nextFilters.productId.trim() || undefined,
          imageType: nextFilters.imageType,
          execStatus: nextFilters.execStatus,
          filtered: nextFilters.filtered,
          containsChinese: nextFilters.containsChinese,
          translateStatus: nextFilters.translateStatus,
          imageWidthMin: nextFilters.imageWidthMin,
          imageWidthMax: nextFilters.imageWidthMax,
          imageHeightMin: nextFilters.imageHeightMin,
          imageHeightMax: nextFilters.imageHeightMax,
          page: nextPage - 1,
          size: nextPageSize,
        }),
        ocrApi.statsTasks({
          spuId: nextFilters.spuId,
          productId: nextFilters.productId.trim() || undefined,
          imageType: nextFilters.imageType,
          filtered: nextFilters.filtered,
          containsChinese: nextFilters.containsChinese,
          translateStatus: nextFilters.translateStatus,
          imageWidthMin: nextFilters.imageWidthMin,
          imageWidthMax: nextFilters.imageWidthMax,
          imageHeightMin: nextFilters.imageHeightMin,
          imageHeightMax: nextFilters.imageHeightMax,
        }),
      ]);

      setRows(Array.isArray(listRes.data.content) ? listRes.data.content : []);
      setTotal(Number(listRes.data.totalElements || 0));
      setStats(statsRes.data || {});
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(1, 20, initialFilters);
    void loadImageProxyConfig();
  }, []);

  async function loadImageProxyConfig() {
    try {
      const res = await alibabaImageProxyConfigApi.current();
      setImageProxyConfig(res.data || null);
    } catch (error) {
      setImageProxyConfig(null);
    }
  }

  function recordImageDimension(key: string | undefined, width: number, height: number) {
    if (!key || !width || !height) {
      return;
    }
    setImageDimensions((current) => {
      const existing = current[key];
      if (existing?.width === width && existing?.height === height) {
        return current;
      }
      return {
        ...current,
        [key]: {
          width,
          height,
        },
      };
    });
  }

  useEffect(() => {
    let cancelled = false;
    rows.forEach((record) => {
      if (!record.imageUrl) {
        return;
      }
      const imageUrl = buildAlibabaImageProxyUrl(record.imageUrl, imageProxyConfig);
      if (!imageUrl) {
        return;
      }
      const image = new window.Image();
      image.onload = () => {
        if (!cancelled) {
          recordImageDimension(record.imageUrl, image.naturalWidth, image.naturalHeight);
        }
      };
      image.src = imageUrl;
    });
    return () => {
      cancelled = true;
    };
  }, [rows, imageProxyConfig]);

  function updateForm<K extends keyof typeof form>(key: K, value: (typeof form)[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function updateSizeFilterForm<K extends keyof typeof sizeFilterForm>(key: K, value: (typeof sizeFilterForm)[K]) {
    setSizeFilterForm((current) => ({ ...current, [key]: value }));
  }

  async function loadSizeFilterConfigs() {
    setSizeFilterLoading(true);
    try {
      const res = await ocrApi.listSizeFilterConfigs();
      setSizeFilterRows(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载图片尺寸过滤配置失败');
    } finally {
      setSizeFilterLoading(false);
    }
  }

  async function openSizeFilterConfigs() {
    setSizeFilterOpen(true);
    setSizeFilterForm(initialSizeFilterForm);
    await loadSizeFilterConfigs();
  }

  function editSizeFilterConfig(record: OcrSizeFilterConfigVO) {
    setSizeFilterForm({
      id: record.id,
      imageWidth: record.imageWidth,
      imageHeight: record.imageHeight,
      enabled: record.enabled,
      remark: record.remark || '',
    });
  }

  async function saveSizeFilterConfig() {
    if (!sizeFilterForm.imageWidth || !sizeFilterForm.imageHeight) {
      message.warning('图片宽高不能为空');
      return;
    }
    setSizeFilterSaving(true);
    try {
      const payload = {
        imageWidth: sizeFilterForm.imageWidth,
        imageHeight: sizeFilterForm.imageHeight,
        enabled: sizeFilterForm.enabled,
        remark: sizeFilterForm.remark.trim() || undefined,
      };
      if (sizeFilterForm.id) {
        await ocrApi.updateSizeFilterConfig(sizeFilterForm.id, payload);
        message.success('配置已更新');
      } else {
        await ocrApi.createSizeFilterConfig(payload);
        message.success('配置已保存');
      }
      setSizeFilterForm(initialSizeFilterForm);
      await loadSizeFilterConfigs();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存图片尺寸过滤配置失败');
    } finally {
      setSizeFilterSaving(false);
    }
  }

  async function addRecordSizeToFilterConfig(record: OcrTaskVO) {
    if (!record.imageWidth || !record.imageHeight) {
      message.warning('当前图片没有有效尺寸，不能加入过滤配置');
      return;
    }
    setSizeFilterSaving(true);
    try {
      await ocrApi.createSizeFilterConfig({
        imageWidth: record.imageWidth,
        imageHeight: record.imageHeight,
        enabled: true,
        remark: `来自 OCR #${record.id}`,
      });
      message.success(`${record.imageWidth}×${record.imageHeight} 已加入过滤配置`);
      if (sizeFilterOpen) {
        await loadSizeFilterConfigs();
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加入过滤配置失败');
    } finally {
      setSizeFilterSaving(false);
    }
  }

  async function deleteSizeFilterConfig(record: OcrSizeFilterConfigVO) {
    Modal.confirm({
      title: '删除图片尺寸过滤配置',
      content: `确认删除 ${record.imageWidth}×${record.imageHeight} 吗？`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      async onOk() {
        await ocrApi.deleteSizeFilterConfig(record.id);
        message.success('配置已删除');
        await loadSizeFilterConfigs();
      },
    });
  }

  async function deleteSizeFilteredImages() {
    const enabledCount = sizeFilterRows.filter((item) => item.enabled).length;
    if (!enabledCount) {
      message.warning('没有启用的图片尺寸过滤配置');
      return;
    }
    Modal.confirm({
      title: '删除黑名单图片？',
      content: `将按 ${enabledCount} 个启用的尺寸配置清理已有 OCR 图片：能从采集商品库图片里删除的会同步删除，找不到位置的也会删除 OCR 任务。`,
      okText: '删除黑名单图片',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        setSizeFilterDeleting(true);
        try {
          const res = await ocrApi.deleteSizeFilteredImages();
          const data = res.data;
          message.success(`已删除 OCR ${data.deletedTaskCount} 条，商品图移除 ${data.removedProductImageCount} 张，影响商品 ${data.affectedProductCount} 个`);
          await load(page, pageSize, filters);
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除黑名单图片失败');
          throw error;
        } finally {
          setSizeFilterDeleting(false);
        }
      },
    });
  }

  function openCreate() {
    setForm(initialForm);
    setEditOpen(true);
  }

  async function loadTranslatePanel() {
    setTranslateLoading(true);
    try {
      const [configResult, statusResult, logsResult] = await Promise.allSettled([
        ocrApi.imageTranslateWorkerConfig(),
        ocrApi.imageTranslateWorkerStatus(),
        ocrApi.imageTranslateWorkerLogs({ page: 0, size: 10 }),
      ]);
      if (configResult.status === 'fulfilled') {
        const config = configResult.value.data || null;
        setTranslateMaxChineseCount(config?.maxChineseImageCount || 5);
        setTranslateBatchSize(config?.batchSize || 1);
        setTranslatePollMs(config?.pollMs || 60000);
        setTranslateProvider(config?.provider || 'ai');
        setTranslateModel(config?.model || 'gpt-image-2');
        setTranslateQuality(config?.quality || 'medium');
      }
      if (statusResult.status === 'fulfilled') {
        setTranslateStatus(statusResult.value.data || null);
      }
      if (logsResult.status === 'fulfilled') {
        setTranslateLogs(Array.isArray(logsResult.value.data.content) ? logsResult.value.data.content : []);
        setTranslateLogTotal(Number(logsResult.value.data.totalElements || 0));
        setTranslateLogPage(1);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载翻译任务配置失败');
    } finally {
      setTranslateLoading(false);
    }
  }

  async function loadTranslateLogs(nextPage = translateLogPage) {
    setTranslateLoading(true);
    try {
      const res = await ocrApi.imageTranslateWorkerLogs({ page: nextPage - 1, size: 10 });
      setTranslateLogs(Array.isArray(res.data.content) ? res.data.content : []);
      setTranslateLogTotal(Number(res.data.totalElements || 0));
      setTranslateLogPage(nextPage);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载翻译日志失败');
    } finally {
      setTranslateLoading(false);
    }
  }

  async function openTranslateWorker() {
    setTranslateOpen(true);
    await loadTranslatePanel();
  }

  async function saveTranslateConfig() {
    setTranslateSaving(true);
    try {
      const res = await ocrApi.updateImageTranslateWorkerConfig({
        maxChineseImageCount: translateMaxChineseCount,
        batchSize: translateBatchSize,
        pollMs: translatePollMs,
        provider: translateProvider,
        model: translateModel.trim() || 'gpt-image-2',
        quality: translateQuality,
      });
      if (res.data) {
        setTranslateStatus(res.data);
      }
      message.success(res.message || '翻译任务配置已保存');
      await loadTranslatePanel();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存翻译任务配置失败');
    } finally {
      setTranslateSaving(false);
    }
  }

  async function toggleTranslateWorker(action: 'start' | 'stop') {
    setTranslateAction(action);
    try {
      const res = action === 'start'
        ? await ocrApi.startImageTranslateWorker()
        : await ocrApi.stopImageTranslateWorker();
      if (res.data) {
        setTranslateStatus(res.data);
      }
      message.success(res.message || (action === 'start' ? '翻译任务已启动' : '翻译任务已停止'));
      await loadTranslatePanel();
      await load(page, pageSize, filters);
    } catch (error) {
      message.error(error instanceof Error ? error.message : action === 'start' ? '启动翻译任务失败' : '停止翻译任务失败');
    } finally {
      setTranslateAction(null);
    }
  }

  async function retryTranslateLog(record: OcrImageTranslateWorkerLogVO) {
    if (!record.ocrTaskId) {
      message.warning('这条日志没有 OCR 任务 ID，无法重试');
      return;
    }
    setRetryingTranslateLogId(record.id);
    try {
      const res = await ocrApi.retryImageTranslateWorkerTask(record.ocrTaskId);
      if (res.data) {
        setTranslateStatus(res.data);
      }
      message.success(res.message || `OCR #${record.ocrTaskId} 已提交重试`);
      await loadTranslateLogs(1);
      window.setTimeout(() => {
        void loadTranslateLogs(1);
        void load(page, pageSize, filters);
      }, 5000);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重试失败');
    } finally {
      setRetryingTranslateLogId(null);
    }
  }

  function openEdit(record: OcrTaskVO) {
    setForm({
      id: record.id,
      spuId: record.spuId,
      productId: record.productId || '',
      imageType: record.imageType,
      imageUrl: record.imageUrl,
      imageWidth: record.imageWidth ?? undefined,
      imageHeight: record.imageHeight ?? undefined,
      imageMd5: record.imageMd5 ?? undefined,
      translateStatus: record.translateStatus ?? undefined,
      translatedImageUrl: record.translatedImageUrl ?? undefined,
      execStatus: record.execStatus,
      execResult: record.execResult || '',
      failReason: record.failReason || '',
      executorPublicIp: record.executorPublicIp || '',
      filtered: record.filtered,
      containsChinese: record.containsChinese ?? undefined,
    });
    setEditOpen(true);
  }

  async function save() {
    if (!form.spuId) {
      message.error('SPU ID 不能为空');
      return;
    }
    if (!form.imageUrl.trim()) {
      message.error('图片 URL 不能为空');
      return;
    }

    const payload: OcrTaskPayload = {
      spuId: form.spuId,
      productId: form.productId?.trim() || undefined,
      imageType: form.imageType,
      imageUrl: form.imageUrl.trim(),
      imageWidth: form.imageWidth,
      imageHeight: form.imageHeight,
      imageMd5: form.imageMd5,
      translateStatus: form.translateStatus,
      translatedImageUrl: form.translatedImageUrl,
      execStatus: form.execStatus,
      execResult: form.execResult?.trim() || undefined,
      failReason: form.failReason?.trim() || undefined,
      executorPublicIp: form.executorPublicIp?.trim() || undefined,
      filtered: !!form.filtered,
      containsChinese: form.containsChinese,
    };

    setSaving(true);
    try {
      if (form.id) {
        await ocrApi.updateTask(form.id, payload);
        message.success('任务已更新');
      } else {
        await ocrApi.createTask(payload);
        message.success('任务已创建');
      }
      setEditOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  function remove(record: OcrTaskVO) {
    Modal.confirm({
      title: '删除任务？',
      content: `确认删除 OCR 任务 #${record.id} 吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await ocrApi.deleteTask(record.id);
          message.success('已删除');
          await load();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        }
      },
    });
  }

  const columns: ColumnsType<OcrTaskVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '商品',
      key: 'product',
      width: 180,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Link to={`/platform/product-collections/${record.spuId}`}>
            SPU {record.spuId}
          </Link>
          <Typography.Text type="secondary">{record.productId || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '图片',
      key: 'image',
      width: 140,
      render: (_, record) => {
        const imageUrl = buildAlibabaImageProxyUrl(record.imageUrl, imageProxyConfig);
        const dimension = record.imageWidth && record.imageHeight
          ? { width: record.imageWidth, height: record.imageHeight }
          : record.imageUrl ? imageDimensions[record.imageUrl] : undefined;
        return imageUrl ? (
          <Space direction="vertical" size={2} align="center">
            <Image
              width={64}
              height={64}
              src={imageUrl}
              alt={record.productId || String(record.id)}
              onLoad={(event) => recordImageDimension(record.imageUrl, event.currentTarget.naturalWidth, event.currentTarget.naturalHeight)}
            />
            <Typography.Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
              {dimension ? `${dimension.width}×${dimension.height}` : '-'}
            </Typography.Text>
            {record.imageMd5 ? (
              <Typography.Text copyable={{ text: record.imageMd5 }} type="secondary" style={{ fontSize: 12, maxWidth: 120 }} ellipsis>
                {record.imageMd5.slice(0, 8)}
              </Typography.Text>
            ) : null}
          </Space>
        ) : '-';
      },
    },
    {
      title: '翻译状态',
      key: 'translateStatus',
      width: 150,
      render: (_, record) => {
        const status = getTranslateStatusView(record);
        return <Tag color={status.color}>{status.text}</Tag>;
      },
    },
    {
      title: '翻译图片',
      key: 'translatedImage',
      width: 140,
      render: (_, record) => {
        const translatedUrl = buildAlibabaImageProxyUrl(record.translatedImageUrl, imageProxyConfig);
        return translatedUrl ? (
          <Image width={64} height={64} src={translatedUrl} alt={record.productId || String(record.id)} />
        ) : '-';
      },
    },
    {
      title: '类型 / 状态',
      key: 'meta',
      width: 180,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Tag>{imageTypeText[record.imageType] || `未知类型 ${record.imageType}`}</Tag>
          <Tag color={getExecStatusColor(record.execStatus)}>
            {execStatusText[record.execStatus] || `未知状态 ${record.execStatus}`}
          </Tag>
        </Space>
      ),
    },
    {
      title: '结果',
      key: 'result',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text ellipsis style={{ maxWidth: 320 }}>
            {record.execResult || '-'}
          </Typography.Text>
          {record.failReason ? (
            <Typography.Text type="danger" ellipsis style={{ maxWidth: 320 }}>
              {record.failReason}
            </Typography.Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: '标记',
      key: 'flags',
      width: 160,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Tag color={record.filtered ? 'orange' : 'default'}>{record.filtered ? '已过滤' : '未过滤'}</Tag>
          <Tag color={record.containsChinese ? 'blue' : 'default'}>{record.containsChinese ? '含中文' : '无中文'}</Tag>
        </Space>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      render: (value: OcrTaskVO['updatedAt']) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 250,
      render: (_, record) => (
        <Space wrap>
          <Button size="small" onClick={() => void addRecordSizeToFilterConfig(record)}>
            加入过滤配置
          </Button>
          <Button size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button size="small" danger onClick={() => remove(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const successfulTranslateTaskIds = new Set(
    translateLogs
      .filter((item) => item.status === 'SUCCESS' && item.ocrTaskId)
      .map((item) => item.ocrTaskId),
  );

  const translateLogColumns: ColumnsType<OcrImageTranslateWorkerLogVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '商品 / OCR',
      key: 'target',
      width: 150,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <span>SPU {record.spuId}</span>
          <Typography.Text type="secondary">OCR #{record.ocrTaskId || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 110,
      render: (_, record) => (
        <Tag color={record.status === 'SUCCESS' ? 'green' : record.status === 'SKIPPED' ? 'default' : 'red'}>
          {record.status || '-'}
        </Tag>
      ),
    },
    {
      title: '模型',
      dataIndex: 'model',
      key: 'model',
      width: 140,
      render: (value: string | null) => value || '-',
    },
    {
      title: '原图',
      dataIndex: 'originalUrl',
      key: 'originalUrl',
      width: 260,
      render: (value: string | null) => (
        <Typography.Text copyable={!!value} ellipsis={{ tooltip: value || undefined }} style={{ maxWidth: 230 }}>
          {value || '-'}
        </Typography.Text>
      ),
    },
    {
      title: 'TEMU 图',
      dataIndex: 'temuUrl',
      key: 'temuUrl',
      width: 260,
      render: (value: string | null) => (
        <Typography.Text copyable={!!value} ellipsis={{ tooltip: value || undefined }} style={{ maxWidth: 230 }}>
          {value || '-'}
        </Typography.Text>
      ),
    },
    {
      title: '信息',
      key: 'message',
      width: 380,
      render: (_, record) => (
        <Typography.Text type={record.errorMessage ? 'danger' : 'secondary'} ellipsis={{ tooltip: record.errorMessage || record.message || undefined }} style={{ maxWidth: 350 }}>
          {record.errorMessage || record.message || '-'}
        </Typography.Text>
      ),
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 190,
      render: (value: OcrImageTranslateWorkerLogVO['createdAt']) => (
        <span style={{ whiteSpace: 'nowrap' }}>{formatDateTime(value)}</span>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      render: (_, record) => (
        record.status === 'FAILED' && record.ocrTaskId && !successfulTranslateTaskIds.has(record.ocrTaskId) ? (
          <Button
            size="small"
            danger
            loading={retryingTranslateLogId === record.id}
            onClick={() => void retryTranslateLog(record)}
          >
            失败重试
          </Button>
        ) : record.status === 'FAILED' && record.ocrTaskId && successfulTranslateTaskIds.has(record.ocrTaskId) ? (
          <Tag color="green">已成功</Tag>
        ) : null
      ),
    },
  ];

  const sizeFilterColumns: ColumnsType<OcrSizeFilterConfigVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '图片尺寸',
      key: 'size',
      width: 140,
      render: (_, record) => `${record.imageWidth}×${record.imageHeight}`,
    },
    {
      title: '状态',
      key: 'enabled',
      width: 100,
      render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? '启用' : '停用'}</Tag>,
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      render: (value: string | null) => value || '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      render: (value: OcrSizeFilterConfigVO['updatedAt']) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => editSizeFilterConfig(record)}>
            编辑
          </Button>
          <Button size="small" danger onClick={() => void deleteSizeFilterConfig(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <InputNumber value={filters.spuId} onChange={(value) => setFilters((current) => ({ ...current, spuId: value ?? undefined }))} placeholder="SPU ID" style={{ width: 140 }} />
          <Input value={filters.productId} onChange={(e) => setFilters((current) => ({ ...current, productId: e.target.value }))} placeholder="商品ID" allowClear style={{ width: 180 }} />
          <Select value={filters.imageType} onChange={(value) => setFilters((current) => ({ ...current, imageType: value }))} allowClear placeholder="图片类型" style={{ width: 140 }} options={[{ value: 1, label: '轮播图' }, { value: 2, label: '详情图' }, { value: 3, label: 'SKU 图' }]} />
          <Select value={filters.execStatus} onChange={(value) => setFilters((current) => ({ ...current, execStatus: value }))} allowClear placeholder="执行状态" style={{ width: 140 }} options={[{ value: 0, label: '待执行' }, { value: 1, label: '运行中' }, { value: 2, label: '成功' }, { value: 3, label: '失败' }]} />
          <Select value={filters.filtered} onChange={(value) => setFilters((current) => ({ ...current, filtered: value }))} allowClear placeholder="过滤状态" style={{ width: 140 }} options={[{ value: true, label: '已过滤' }, { value: false, label: '未过滤' }]} />
          <Select value={filters.containsChinese} onChange={(value) => setFilters((current) => ({ ...current, containsChinese: value }))} allowClear placeholder="中文识别" style={{ width: 140 }} options={[{ value: true, label: '含中文' }, { value: false, label: '无中文' }]} />
          <Select
            value={filters.translateStatus}
            onChange={(value) => setFilters((current) => ({ ...current, translateStatus: value }))}
            allowClear
            placeholder="翻译状态"
            style={{ width: 180 }}
            options={[
              { value: 'SUCCESS', label: '翻译成功' },
              { value: 'NOT_REQUIRED', label: '无需翻译' },
              { value: 'PENDING', label: '待翻译' },
              { value: 'POSITION_NOT_FOUND', label: '未匹配到商品位置' },
              { value: 'UNRECOGNIZED', label: '未识别' },
            ]}
          />
          <InputNumber value={filters.imageWidthMin} onChange={(value) => setFilters((current) => ({ ...current, imageWidthMin: value ?? undefined }))} min={1} precision={0} placeholder="最小宽" style={{ width: 110 }} />
          <InputNumber value={filters.imageWidthMax} onChange={(value) => setFilters((current) => ({ ...current, imageWidthMax: value ?? undefined }))} min={1} precision={0} placeholder="最大宽" style={{ width: 110 }} />
          <InputNumber value={filters.imageHeightMin} onChange={(value) => setFilters((current) => ({ ...current, imageHeightMin: value ?? undefined }))} min={1} precision={0} placeholder="最小高" style={{ width: 110 }} />
          <InputNumber value={filters.imageHeightMax} onChange={(value) => setFilters((current) => ({ ...current, imageHeightMax: value ?? undefined }))} min={1} precision={0} placeholder="最大高" style={{ width: 110 }} />
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
          <Button type="primary" ghost onClick={openCreate}>
            新建任务
          </Button>
          <Button onClick={() => void openSizeFilterConfigs()}>
            图片尺寸过滤配置
          </Button>
          <Button onClick={() => void openTranslateWorker()}>
            翻译任务
          </Button>
        </Space>
      </Card>

      <Space size={16} wrap>
        <Card size="small">
          <Statistic title="总任务" value={stats.total || 0} />
        </Card>
        <Card size="small">
          <Statistic title="成功" value={stats.success || 0} />
        </Card>
        <Card size="small">
          <Statistic title="失败" value={stats.failed || 0} />
        </Card>
        <Card size="small">
          <Statistic title="待处理" value={stats.pending || 0} />
        </Card>
      </Space>

      <Card>
        <Table<OcrTaskVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1400 }}
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

      <Modal
        open={editOpen}
        title={form.id ? '编辑 OCR 任务' : '新建 OCR 任务'}
        width={760}
        confirmLoading={saving}
        onOk={() => void save()}
        onCancel={() => setEditOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="SPU ID" required>
            <InputNumber value={form.spuId} onChange={(value) => updateForm('spuId', value ?? 0)} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="商品ID">
            <Input value={form.productId} onChange={(e) => updateForm('productId', e.target.value)} />
          </Form.Item>
          <Form.Item label="图片类型" required>
            <Select value={form.imageType} onChange={(value) => updateForm('imageType', value)} options={[{ value: 1, label: '轮播图' }, { value: 2, label: '详情图' }, { value: 3, label: 'SKU 图' }]} />
          </Form.Item>
          <Form.Item label="图片 URL" required>
            <Input.TextArea rows={3} value={form.imageUrl} onChange={(e) => updateForm('imageUrl', e.target.value)} />
          </Form.Item>
          <Form.Item label="执行状态">
            <Select value={form.execStatus} onChange={(value) => updateForm('execStatus', value)} options={[{ value: 0, label: '待执行' }, { value: 1, label: '运行中' }, { value: 2, label: '成功' }, { value: 3, label: '失败' }]} />
          </Form.Item>
          <Form.Item label="OCR 结果">
            <Input.TextArea rows={4} value={form.execResult} onChange={(e) => updateForm('execResult', e.target.value)} />
          </Form.Item>
          <Form.Item label="失败原因">
            <Input.TextArea rows={3} value={form.failReason} onChange={(e) => updateForm('failReason', e.target.value)} />
          </Form.Item>
          <Form.Item label="执行器 IP">
            <Input value={form.executorPublicIp} onChange={(e) => updateForm('executorPublicIp', e.target.value)} />
          </Form.Item>
          <Form.Item label="标记">
            <Space>
              <Checkbox checked={!!form.filtered} onChange={(e) => updateForm('filtered', e.target.checked)}>
                已过滤
              </Checkbox>
              <Checkbox checked={form.containsChinese === true} onChange={(e) => updateForm('containsChinese', e.target.checked ? true : false)}>
                含中文
              </Checkbox>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={translateOpen}
        title="OCR 中文图片翻译任务"
        width={1280}
        onCancel={() => setTranslateOpen(false)}
        footer={[
          <Button key="refresh" loading={translateLoading} onClick={() => void loadTranslatePanel()}>
            刷新
          </Button>,
          <Button key="save" type="primary" loading={translateSaving} onClick={() => void saveTranslateConfig()}>
            保存配置
          </Button>,
          <Button key="start" loading={translateAction === 'start'} onClick={() => void toggleTranslateWorker('start')}>
            启动
          </Button>,
          <Button key="stop" danger loading={translateAction === 'stop'} onClick={() => void toggleTranslateWorker('stop')}>
            停止
          </Button>,
          <Button key="close" onClick={() => setTranslateOpen(false)}>
            关闭
          </Button>,
        ]}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions size="small" column={4} bordered>
            <Descriptions.Item label="运行状态">
              <Tag color={translateStatus?.running ? 'green' : 'default'}>{translateStatus?.running ? '运行中' : '已停止'}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="候选商品">
              {translateStatus?.pendingProductCount ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="成功">
              {translateStatus?.successCount ?? 0}
            </Descriptions.Item>
            <Descriptions.Item label="失败">
              {translateStatus?.failureCount ?? 0}
            </Descriptions.Item>
            <Descriptions.Item label="最后 SPU">
              {translateStatus?.lastSpuId ? `#${translateStatus.lastSpuId}` : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="翻译渠道">
              {translateStatus?.provider === 'aliyun' ? '阿里云翻译' : 'AI翻译'}
            </Descriptions.Item>
            <Descriptions.Item label="最后检查">
              {formatDateTime(translateStatus?.lastScanAt)}
            </Descriptions.Item>
            <Descriptions.Item label="最后工作">
              {formatDateTime(translateStatus?.lastWorkAt)}
            </Descriptions.Item>
            <Descriptions.Item label="最后错误">
              <Typography.Text type={translateStatus?.lastError ? 'danger' : 'secondary'}>
                {translateStatus?.lastError || '-'}
              </Typography.Text>
            </Descriptions.Item>
          </Descriptions>

          <Card size="small" title="配置">
            <Space wrap>
              <InputNumber
                value={translateMaxChineseCount}
                onChange={(value) => setTranslateMaxChineseCount(Number(value || 5))}
                min={1}
                max={200}
                precision={0}
                addonBefore="中文图不高于"
                addonAfter="张"
                style={{ width: 220 }}
              />
              <InputNumber
                value={translateBatchSize}
                onChange={(value) => setTranslateBatchSize(Number(value || 1))}
                min={1}
                max={20}
                precision={0}
                addonBefore="每轮"
                addonAfter="商品"
                style={{ width: 180 }}
              />
              <InputNumber
                value={Math.round(translatePollMs / 1000)}
                onChange={(value) => setTranslatePollMs(Math.max(Number(value || 60), 10) * 1000)}
                min={10}
                precision={0}
                addonBefore="间隔"
                addonAfter="秒"
                style={{ width: 190 }}
              />
              <Select
                value={translateProvider}
                onChange={setTranslateProvider}
                options={[
                  { value: 'ai', label: 'AI翻译' },
                  { value: 'aliyun', label: '阿里云翻译' },
                ]}
                style={{ width: 150 }}
              />
              <Input
                value={translateModel}
                onChange={(event) => setTranslateModel(event.target.value)}
                addonBefore="模型"
                style={{ width: 260 }}
                placeholder="gpt-image-2"
              />
              <Select
                value={translateQuality}
                onChange={setTranslateQuality}
                options={[
                  { value: 'medium', label: 'medium' },
                  { value: 'low', label: 'low' },
                  { value: 'high', label: 'high' },
                  { value: 'auto', label: 'auto' },
                ]}
                style={{ width: 150 }}
              />
              <Typography.Text type="secondary">
                只处理未正式发布商品；发布中、发布失败也会纳入。已过滤 OCR 图片不会翻译；中文图数量超过阈值的商品会跳过，便于人工确认。
              </Typography.Text>
            </Space>
          </Card>

          <Card size="small" title="翻译日志" bodyStyle={{ padding: 0 }}>
            <Table<OcrImageTranslateWorkerLogVO>
              size="small"
              rowKey="id"
              columns={translateLogColumns}
              dataSource={translateLogs}
              loading={translateLoading}
              tableLayout="fixed"
              scroll={{ x: 1700 }}
              pagination={{
                current: translateLogPage,
                pageSize: 10,
                total: translateLogTotal,
                showSizeChanger: false,
              }}
              onChange={(pagination: TablePaginationConfig) => {
                void loadTranslateLogs(pagination.current || 1);
              }}
            />
          </Card>
        </Space>
      </Modal>

      <Modal
        open={sizeFilterOpen}
        title="图片尺寸过滤配置"
        width={900}
        onCancel={() => setSizeFilterOpen(false)}
        footer={[
          <Button key="delete-images" danger loading={sizeFilterDeleting} onClick={() => void deleteSizeFilteredImages()}>
            删除黑名单图片
          </Button>,
          <Button key="refresh" loading={sizeFilterLoading} onClick={() => void loadSizeFilterConfigs()}>
            刷新
          </Button>,
          <Button key="close" onClick={() => setSizeFilterOpen(false)}>
            关闭
          </Button>,
        ]}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Card size="small">
            <Space wrap>
              <InputNumber
                value={sizeFilterForm.imageWidth}
                min={1}
                precision={0}
                placeholder="图片宽"
                onChange={(value) => updateSizeFilterForm('imageWidth', value ?? undefined)}
                style={{ width: 130 }}
              />
              <InputNumber
                value={sizeFilterForm.imageHeight}
                min={1}
                precision={0}
                placeholder="图片高"
                onChange={(value) => updateSizeFilterForm('imageHeight', value ?? undefined)}
                style={{ width: 130 }}
              />
              <Select
                value={sizeFilterForm.enabled}
                onChange={(value) => updateSizeFilterForm('enabled', value)}
                options={[
                  { value: true, label: '启用' },
                  { value: false, label: '停用' },
                ]}
                style={{ width: 110 }}
              />
              <Input
                value={sizeFilterForm.remark}
                onChange={(event) => updateSizeFilterForm('remark', event.target.value)}
                placeholder="备注"
                allowClear
                style={{ width: 260 }}
              />
              <Button type="primary" loading={sizeFilterSaving} onClick={() => void saveSizeFilterConfig()}>
                {sizeFilterForm.id ? '保存修改' : '新增配置'}
              </Button>
              {sizeFilterForm.id ? (
                <Button onClick={() => setSizeFilterForm(initialSizeFilterForm)}>
                  取消编辑
                </Button>
              ) : null}
            </Space>
          </Card>
          <Table<OcrSizeFilterConfigVO>
            size="small"
            rowKey="id"
            loading={sizeFilterLoading}
            columns={sizeFilterColumns}
            dataSource={sizeFilterRows}
            pagination={false}
          />
        </Space>
      </Modal>
    </Space>
  );
};

export default OcrTasksPage;
