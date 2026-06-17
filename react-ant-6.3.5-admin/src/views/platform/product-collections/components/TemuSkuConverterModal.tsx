import { App, Button, Input, InputNumber, Modal, Popconfirm, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { alibabaImageProxyConfigApi } from '@/api/alibabaImageProxyConfig';
import { productCollectionsApi } from '@/api/productCollections';
import type { AlibabaImageProxyConfigVO, ProductCollectionRow, ProductCollectionTemuSkuVO } from '@/types/api';
import { buildAlibabaImageProxyUrl } from '@/utils/alibabaImageProxy';

interface TemuSkuConverterModalProps {
  open: boolean;
  record: ProductCollectionRow | null;
  onClose: () => void;
}

interface EditableTemuSkuRow extends ProductCollectionTemuSkuVO {
  __key: string;
}

interface ImagePickerState {
  open: boolean;
  index: number;
  url: string;
  uploading: boolean;
}

function parseSpecJson(value?: string | null) {
  if (!value || typeof value !== 'string') {
    return {} as Record<string, string>;
  }

  try {
    const parsed = JSON.parse(value) as Record<string, unknown>;
    if (!parsed || Array.isArray(parsed)) {
      return {} as Record<string, string>;
    }
    return Object.entries(parsed).reduce<Record<string, string>>((accumulator, [key, item]) => {
      accumulator[key] = item == null ? '' : String(item);
      return accumulator;
    }, {});
  } catch {
    return {} as Record<string, string>;
  }
}

function stripVariantPrefix(value?: string | null) {
  return String(value || '')
    .trim()
    .replace(/^G-\d{3}-\s*/i, '');
}

function toNumber(value: unknown) {
  const num = Number(value);
  return Number.isFinite(num) ? num : 0;
}

const SUPPLY_PRICE_FIXED_ADD = 6;
const SUPPLY_PRICE_WEIGHT_RATE_PER_G = 0.069;
const SUPPLY_PRICE_MULTIPLIER = 6;

function calculateSupplyPrice(originPrice: number, weightG: number) {
  return Math.round(
    (originPrice + SUPPLY_PRICE_FIXED_ADD + weightG * SUPPLY_PRICE_WEIGHT_RATE_PER_G)
      * SUPPLY_PRICE_MULTIPLIER
      * 100,
  ) / 100;
}

function buildEditableRow(row: ProductCollectionTemuSkuVO, index: number): EditableTemuSkuRow {
  return {
    ...row,
    __key: `${Date.now()}_${index}_${Math.random().toString(16).slice(2, 8)}`,
  };
}

const TemuSkuConverterModal = ({ open, record, onClose }: TemuSkuConverterModalProps) => {
  const { message } = App.useApp();
  const [initing, setIniting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [uploadingIndex, setUploadingIndex] = useState(-1);
  const [rows, setRows] = useState<EditableTemuSkuRow[]>([]);
  const [imagePicker, setImagePicker] = useState<ImagePickerState>({
    open: false,
    index: -1,
    url: '',
    uploading: false,
  });
  const [imageProxyConfig, setImageProxyConfig] = useState<AlibabaImageProxyConfigVO | null>(null);

  async function loadImageProxyConfig() {
    try {
      const response = await alibabaImageProxyConfigApi.current();
      setImageProxyConfig(response.data || null);
    } catch {
      setImageProxyConfig(null);
    }
  }

  async function loadExisting() {
    if (!record?.id) {
      return;
    }

    try {
      const response = await productCollectionsApi.listTemuSkus(record.id);
      const list = Array.isArray(response.data) ? response.data : [];
      setRows(list.map(buildEditableRow));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 TEMU SKU 失败');
    }
  }

  useEffect(() => {
    if (!open) {
      return;
    }

    setRows([]);
    void loadImageProxyConfig();
    void loadExisting();
  }, [open, record?.id]);

  async function initFromOrigin(force: boolean) {
    if (!record?.id) {
      return;
    }

    setIniting(true);
    try {
      const response = await productCollectionsApi.initTemuSkus(record.id, force);
      const list = Array.isArray(response.data) ? response.data : [];
      setRows(list.map(buildEditableRow));
      message.success('已初始化');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '初始化失败');
    } finally {
      setIniting(false);
    }
  }

  function addRow() {
    setRows((current) => [
      ...current,
      {
        __key: `${Date.now()}_${Math.random().toString(16).slice(2, 8)}`,
        id: null,
        temuSkuId: '',
        originSkuId: '',
        specKey: '',
        specJson: '',
        image: '',
        originPrice: null,
        supplyPrice: null,
        weightG: 150,
        lengthCm: 10,
        widthCm: 5,
        heightCm: 5,
      },
    ]);
  }

  function removeRow(index: number) {
    setRows((current) => current.filter((_, rowIndex) => rowIndex !== index));
  }

  function updateRow(index: number, updater: (row: EditableTemuSkuRow) => EditableTemuSkuRow) {
    setRows((current) => current.map((row, rowIndex) => (rowIndex === index ? updater(row) : row)));
  }

  function getSpecJsonKeys() {
    const keys: string[] = [];
    rows.forEach((row) => {
      Object.keys(parseSpecJson(row.specJson)).forEach((key) => {
        if (key && !keys.includes(key)) {
          keys.push(key);
        }
      });
    });
    return keys;
  }

  function setSpecJsonValue(index: number, key: string, value: string) {
    updateRow(index, (row) => {
      const parsed = parseSpecJson(row.specJson);
      parsed[key] = value;
      return {
        ...row,
        specJson: JSON.stringify(parsed),
      };
    });
  }

  function openImagePicker(index: number) {
    const row = rows[index];
    setImagePicker({
      open: true,
      index,
      url: row?.image || '',
      uploading: false,
    });
  }

  function clearCurrentImage() {
    if (imagePicker.index < 0) {
      return;
    }

    updateRow(imagePicker.index, (row) => ({
      ...row,
      image: '',
    }));
    setImagePicker((current) => ({ ...current, open: false, index: -1, url: '' }));
  }

  async function confirmImagePicker() {
    if (!record?.id || imagePicker.index < 0) {
      return;
    }

    const url = imagePicker.url.trim();
    if (!url) {
      message.error('请填写图片 URL');
      return;
    }

    setImagePicker((current) => ({ ...current, uploading: true }));
    setUploadingIndex(imagePicker.index);
    try {
      const response = await productCollectionsApi.uploadTemuSkuImage(record.id, url);
      if (!response.data?.imageUrl) {
        throw new Error('上传后未返回图片地址');
      }

      updateRow(imagePicker.index, (row) => ({
        ...row,
        image: response.data.imageUrl || '',
      }));
      message.success('图片已更换');
      setImagePicker({
        open: false,
        index: -1,
        url: '',
        uploading: false,
      });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '更换失败');
      setImagePicker((current) => ({ ...current, uploading: false }));
    } finally {
      setUploadingIndex(-1);
    }
  }

  function recalc(index: number) {
    updateRow(index, (row) => {
      const originPrice = toNumber(row.originPrice);
      const weightG = toNumber(row.weightG);
      const supplyPrice = calculateSupplyPrice(originPrice, weightG);
      return {
        ...row,
        supplyPrice,
      };
    });
  }

  function applyVariantPrefix(columnKey: string) {
    if (!rows.length) {
      message.info('暂无可处理的 SKU');
      return;
    }

    setRows((current) =>
      current.map((row, index) => {
        const prefix = `G-${String(index + 1).padStart(3, '0')}-`;
        if (columnKey === 'specKey') {
          return {
            ...row,
            specKey: `${prefix}${stripVariantPrefix(row.specKey)}`,
          };
        }

        const parsed = parseSpecJson(row.specJson);
        const specName = columnKey.replace('specJson:', '');
        parsed[specName] = `${prefix}${stripVariantPrefix(parsed[specName])}`;
        return {
          ...row,
          specJson: JSON.stringify(parsed),
        };
      }),
    );

    message.success('已批量生成变种编号');
  }

  async function save() {
    if (!record?.id) {
      return;
    }

    setSaving(true);
    try {
      const payload = rows.map(({ __key, ...rest }) => rest);
      await productCollectionsApi.saveTemuSkus(record.id, payload);
      message.success('已保存');
      onClose();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  const specJsonKeys = getSpecJsonKeys();

  const columns: ColumnsType<EditableTemuSkuRow> = [
    {
      title: '图片',
      key: 'image',
      width: 110,
      fixed: 'left',
      render: (_, row, index) => (
        <button
          type="button"
          onClick={() => openImagePicker(index)}
          disabled={uploadingIndex === index}
          style={{
            width: 56,
            height: 56,
            padding: 0,
            borderRadius: 12,
            border: '1px solid #e2e8f0',
            overflow: 'hidden',
            cursor: uploadingIndex === index ? 'not-allowed' : 'pointer',
            background: '#fff',
            position: 'relative',
          }}
        >
          {row.image ? (
            <img
              src={buildAlibabaImageProxyUrl(row.image, imageProxyConfig)}
              alt=""
              style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
            />
          ) : (
            <span style={{ fontSize: 12, color: 'rgba(15,23,42,0.55)', fontWeight: 700 }}>点击设置</span>
          )}
          {uploadingIndex === index ? (
            <span
              style={{
                position: 'absolute',
                inset: 0,
                background: 'rgba(15,23,42,0.58)',
                color: '#fff',
                display: 'grid',
                placeItems: 'center',
                fontSize: 12,
                fontWeight: 700,
              }}
            >
              上传中...
            </span>
          ) : null}
        </button>
      ),
    },
    {
      title: (
        <Space size={4}>
          <span>属性组合</span>
          <Button type="link" size="small" onClick={() => applyVariantPrefix('specKey')}>
            变种
          </Button>
        </Space>
      ),
      key: 'specKey',
      width: 220,
      render: (_, row, index) => (
        <Input
          value={row.specKey || ''}
          onChange={(event) => updateRow(index, (current) => ({ ...current, specKey: event.target.value }))}
          placeholder="颜色>尺码..."
          allowClear
        />
      ),
    },
    ...specJsonKeys.map<ColumnsType<EditableTemuSkuRow>[number]>((key) => ({
      title: (
        <Space size={4}>
          <span>{key}</span>
          <Button type="link" size="small" onClick={() => applyVariantPrefix(`specJson:${key}`)}>
            变种
          </Button>
        </Space>
      ),
      key: `specJson:${key}`,
      width: 160,
      render: (_, row, index) => (
        <Input
          value={parseSpecJson(row.specJson)[key] || ''}
          onChange={(event) => setSpecJsonValue(index, key, event.target.value)}
          placeholder="规格值..."
          allowClear
        />
      ),
    })),
    {
      title: 'temu skuId',
      key: 'temuSkuId',
      width: 140,
      render: (_, row, index) => (
        <Input
          value={row.temuSkuId || ''}
          onChange={(event) => updateRow(index, (current) => ({ ...current, temuSkuId: event.target.value }))}
          placeholder="temu skuId"
          allowClear
        />
      ),
    },
    {
      title: '原 skuId',
      key: 'originSkuId',
      width: 140,
      render: (_, row, index) => (
        <Input
          value={row.originSkuId || ''}
          onChange={(event) => updateRow(index, (current) => ({ ...current, originSkuId: event.target.value }))}
          placeholder="原 skuId"
          allowClear
        />
      ),
    },
    {
      title: '原价',
      key: 'originPrice',
      width: 120,
      render: (_, row, index) => (
        <InputNumber
          value={row.originPrice ?? undefined}
          min={0}
          style={{ width: '100%' }}
          onChange={(value) => updateRow(index, (current) => ({ ...current, originPrice: value ?? null }))}
        />
      ),
    },
    {
      title: '供货价',
      key: 'supplyPrice',
      width: 140,
      render: (_, row, index) => (
        <InputNumber
          value={row.supplyPrice ?? undefined}
          min={0}
          style={{ width: '100%' }}
          onChange={(value) => updateRow(index, (current) => ({ ...current, supplyPrice: value ?? null }))}
        />
      ),
    },
    {
      title: '重量(g)',
      key: 'weightG',
      width: 120,
      render: (_, row, index) => (
        <InputNumber
          value={row.weightG ?? undefined}
          min={0}
          style={{ width: '100%' }}
          onChange={(value) => updateRow(index, (current) => ({ ...current, weightG: value ?? null }))}
        />
      ),
    },
    {
      title: '尺寸(cm)',
      key: 'dim',
      width: 300,
      render: (_, row, index) => (
        <Space.Compact block>
          <InputNumber
            value={row.lengthCm ?? undefined}
            min={0}
            style={{ width: '33.33%' }}
            onChange={(value) => updateRow(index, (current) => ({ ...current, lengthCm: value ?? null }))}
          />
          <InputNumber
            value={row.widthCm ?? undefined}
            min={0}
            style={{ width: '33.33%' }}
            onChange={(value) => updateRow(index, (current) => ({ ...current, widthCm: value ?? null }))}
          />
          <InputNumber
            value={row.heightCm ?? undefined}
            min={0}
            style={{ width: '33.33%' }}
            onChange={(value) => updateRow(index, (current) => ({ ...current, heightCm: value ?? null }))}
          />
        </Space.Compact>
      ),
    },
    {
      title: '操作',
      key: 'ops',
      width: 170,
      fixed: 'right',
      render: (_, __, index) => (
        <Space>
          <Button size="small" onClick={() => recalc(index)}>
            重算供货价
          </Button>
          <Popconfirm title="删除这行？" okText="删除" cancelText="取消" onConfirm={() => removeRow(index)}>
            <Button size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Modal
        open={open}
        title="SKU 转换 (TEMU)"
        width={1100}
        destroyOnClose
        maskClosable={!saving && !initing}
        keyboard={!saving && !initing}
        onCancel={onClose}
        footer={
          <Space>
            <Button disabled={saving || initing} onClick={onClose}>
              取消
            </Button>
            <Button loading={initing} disabled={saving} onClick={() => void initFromOrigin(false)}>
              初始化
            </Button>
            <Popconfirm
              title="确定重新初始化？会覆盖你当前编辑的 TEMU SKU"
              okText="覆盖"
              cancelText="取消"
              onConfirm={() => void initFromOrigin(true)}
            >
              <Button loading={initing} disabled={saving} danger>
                重新初始化
              </Button>
            </Popconfirm>
            <Button type="primary" loading={saving} disabled={initing} onClick={() => void save()}>
              保存
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Typography.Text type="secondary">原 SKU 只用于填充，TEMU SKU 可编辑、可增删。</Typography.Text>
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="dashed" onClick={addRow}>
              新增一行
            </Button>
          </div>
          <Table<EditableTemuSkuRow>
            rowKey="__key"
            dataSource={rows}
            columns={columns}
            pagination={false}
            size="small"
            scroll={{ x: 1500 }}
          />
        </Space>
      </Modal>

      <Modal
        open={imagePicker.open}
        title="更换 SKU 图片"
        confirmLoading={imagePicker.uploading}
        okText="上传并替换"
        cancelText="取消"
        maskClosable={!imagePicker.uploading}
        keyboard={!imagePicker.uploading}
        onOk={() => void confirmImagePicker()}
        onCancel={() => {
          if (imagePicker.uploading) {
            return;
          }
          setImagePicker({ open: false, index: -1, url: '', uploading: false });
        }}
      >
        <Space direction="vertical" size={10} style={{ width: '100%' }}>
          <Typography.Text type="secondary">
            填写图片 URL 后，会调用 TEMU 图片上传接口生成新的图片地址。
          </Typography.Text>
          <Input
            value={imagePicker.url}
            onChange={(event) => setImagePicker((current) => ({ ...current, url: event.target.value }))}
            placeholder="https://..."
            allowClear
          />
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="link" danger disabled={imagePicker.uploading} onClick={clearCurrentImage}>
              清空当前图片
            </Button>
          </div>
        </Space>
      </Modal>
    </>
  );
};

export default TemuSkuConverterModal;
