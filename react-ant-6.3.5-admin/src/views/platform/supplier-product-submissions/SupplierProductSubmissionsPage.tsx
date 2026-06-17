import {
  App,
  Button,
  Card,
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
  Upload,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { UploadFile, UploadProps } from 'antd';
import { LinkOutlined, PlusOutlined } from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { supplierProductSubmissionsApi } from '@/api/supplierProductSubmissions';
import type { SupplierProductSubmissionPayload, SupplierProductSubmissionVO } from '@/types/api';
import { formatDateTime } from '@/utils/format';

type UploadRequestOption = Parameters<NonNullable<UploadProps['customRequest']>>[0];

interface Filters {
  keyword: string;
  status?: string;
}

const statusOptions = [
  { label: '待审核', value: 'PENDING', color: 'orange' },
  { label: '已通过', value: 'APPROVED', color: 'green' },
  { label: '已拒绝', value: 'REJECTED', color: 'red' },
  { label: '已转采集库', value: 'PUSHED', color: 'blue' },
];

const initialFilters: Filters = {
  keyword: '',
  status: undefined,
};

const initialFormValues: SupplierProductSubmissionPayload = {
  supplierName: '',
  supplierPhone: '',
  supplierAddress: '',
  productName: '',
  imageUrls: [],
  status: 'PENDING',
  remark: '',
};

function normalizeNumber(value?: number | null) {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function buildPayload(values: SupplierProductSubmissionPayload): SupplierProductSubmissionPayload {
  return {
    supplierName: String(values.supplierName || '').trim(),
    supplierPhone: String(values.supplierPhone || '').trim() || undefined,
    supplierAddress: String(values.supplierAddress || '').trim() || undefined,
    productName: String(values.productName || '').trim(),
    supplyPrice: normalizeNumber(values.supplyPrice),
    weightG: normalizeNumber(values.weightG),
    lengthCm: normalizeNumber(values.lengthCm),
    widthCm: normalizeNumber(values.widthCm),
    heightCm: normalizeNumber(values.heightCm),
    imageUrls: Array.isArray(values.imageUrls) ? values.imageUrls.filter(Boolean) : [],
    status: values.status || 'PENDING',
    remark: String(values.remark || '').trim() || undefined,
  };
}

function statusTag(status?: string | null) {
  const option = statusOptions.find((item) => item.value === status);
  return <Tag color={option?.color || 'default'}>{option?.label || status || '-'}</Tag>;
}

function toUploadFiles(urls: string[]): UploadFile[] {
  return urls.map((url, index) => ({
    uid: `${index}-${url}`,
    name: `图片${index + 1}`,
    status: 'done',
    url,
  }));
}

function toImageUrls(files: UploadFile[]) {
  return files.map((file) => file.url).filter(Boolean) as string[];
}

function buildSupplierSubmitUrl() {
  const basename = (import.meta.env.VITE_ROUTER_BASENAME || '').replace(/\/$/, '');
  return `${window.location.origin}${basename}/supplier-submit`;
}

const SupplierProductSubmissionsPage = () => {
  const { message } = App.useApp();
  const [form] = Form.useForm<SupplierProductSubmissionPayload>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [rows, setRows] = useState<SupplierProductSubmissionVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<SupplierProductSubmissionVO | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [actioningId, setActioningId] = useState<number | null>(null);

  async function load(nextPage = page, nextSize = pageSize) {
    setLoading(true);
    try {
      const res = await supplierProductSubmissionsApi.list({
        keyword: filters.keyword.trim() || undefined,
        status: filters.status,
        page: nextPage - 1,
        size: nextSize,
      });
      setRows(res.data?.content || []);
      setTotal(res.data?.totalElements || 0);
      setPage(nextPage);
      setPageSize(nextSize);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载供应商提品失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(1, pageSize);
  }, [filters.status]);

  function openCreateModal() {
    setEditingRecord(null);
    form.setFieldsValue(initialFormValues);
    setFileList([]);
    setModalOpen(true);
  }

  function openEditModal(record: SupplierProductSubmissionVO) {
    setEditingRecord(record);
    const imageUrls = Array.isArray(record.imageUrls) ? record.imageUrls : [];
    form.setFieldsValue({
      supplierName: record.supplierName || '',
      supplierPhone: record.supplierPhone || '',
      supplierAddress: record.supplierAddress || '',
      productName: record.productName || '',
      supplyPrice: record.supplyPrice ?? undefined,
      weightG: record.weightG ?? undefined,
      lengthCm: record.lengthCm ?? undefined,
      widthCm: record.widthCm ?? undefined,
      heightCm: record.heightCm ?? undefined,
      imageUrls,
      status: record.status || 'PENDING',
      remark: record.remark || '',
    });
    setFileList(toUploadFiles(imageUrls));
    setModalOpen(true);
  }

  async function handleSave() {
    const values = await form.validateFields();
    const payload = buildPayload({
      ...values,
      imageUrls: toImageUrls(fileList),
    });
    if (payload.imageUrls.length === 0) {
      message.warning('请至少上传一张产品实拍图');
      return;
    }

    setSaving(true);
    try {
      if (editingRecord) {
        await supplierProductSubmissionsApi.update(editingRecord.id, payload);
        message.success('供应商提品已更新');
      } else {
        await supplierProductSubmissionsApi.create(payload);
        message.success('供应商提品已保存');
      }
      setModalOpen(false);
      await load(page, pageSize);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存供应商提品失败');
    } finally {
      setSaving(false);
    }
  }

  async function handleUpload(options: UploadRequestOption) {
    const file = options.file as File & { uid: string };
    try {
      const res = await supplierProductSubmissionsApi.uploadImage(file);
      const url = res.data?.url;
      if (!url) {
        throw new Error('上传成功但没有返回图片地址');
      }
      options.onSuccess?.({ url }, file);
      setFileList((current) => current.map((item) => (
        item.uid === file.uid
          ? { ...item, status: 'done', url, thumbUrl: url }
          : item
      )));
    } catch (error) {
      options.onError?.(error as Error);
      message.error(error instanceof Error ? error.message : '图片上传失败');
    }
  }

  async function handleDelete(record: SupplierProductSubmissionVO) {
    Modal.confirm({
      title: '删除供应商提品？',
      content: `确认删除 “${record.productName || `ID ${record.id}`}” 吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        setActioningId(record.id);
        try {
          await supplierProductSubmissionsApi.remove(record.id);
          message.success('供应商提品已删除');
          await load(page, pageSize);
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除供应商提品失败');
          throw error;
        } finally {
          setActioningId(null);
        }
      },
    });
  }

  async function handleApprove(record: SupplierProductSubmissionVO) {
    setActioningId(record.id);
    try {
      await supplierProductSubmissionsApi.approve(record.id);
      message.success('审核已通过，已进入 AI 商品包装台');
      await load(page, pageSize);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '审核通过失败');
    } finally {
      setActioningId(null);
    }
  }

  function handleReject(record: SupplierProductSubmissionVO) {
    Modal.confirm({
      title: '拒绝这个提品？',
      content: (
        <Input.TextArea
          id="supplier-reject-remark"
          rows={3}
          placeholder="可填写拒绝原因，会保存到备注"
        />
      ),
      okText: '拒绝',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        const remark = (document.getElementById('supplier-reject-remark') as HTMLTextAreaElement | null)?.value || record.remark || '';
        setActioningId(record.id);
        try {
          await supplierProductSubmissionsApi.reject(record.id, remark);
          message.success('已拒绝');
          await load(page, pageSize);
        } catch (error) {
          message.error(error instanceof Error ? error.message : '拒绝失败');
          throw error;
        } finally {
          setActioningId(null);
        }
      },
    });
  }

  async function handleCopyH5Entry() {
    const url = buildSupplierSubmitUrl();
    try {
      await navigator.clipboard.writeText(url);
      message.success('H5 提品入口已复制');
    } catch (error) {
      Modal.info({
        title: 'H5 提品入口',
        content: (
          <Input.TextArea
            value={url}
            autoSize
            readOnly
            onFocus={(event) => event.currentTarget.select()}
          />
        ),
        okText: '知道了',
      });
    }
  }

  const summary = useMemo(() => ({
    pending: rows.filter((item) => item.status === 'PENDING').length,
    approved: rows.filter((item) => item.status === 'APPROVED').length,
  }), [rows]);

  const columns: ColumnsType<SupplierProductSubmissionVO> = [
    {
      title: '产品',
      key: 'product',
      width: 320,
      render: (_, record) => (
        <Space align="start">
          {record.imageUrls?.[0] ? (
            <Image
              src={record.imageUrls[0]}
              width={56}
              height={56}
              style={{ objectFit: 'cover', borderRadius: 6 }}
              preview={{ src: record.imageUrls[0] }}
            />
          ) : (
            <div style={{ width: 56, height: 56, borderRadius: 6, background: '#f0f0f0' }} />
          )}
          <Space direction="vertical" size={2}>
            <Typography.Text strong ellipsis style={{ maxWidth: 220 }}>
              {record.productName || '-'}
            </Typography.Text>
            <Typography.Text type="secondary">图片 {record.imageUrls?.length || 0} 张</Typography.Text>
          </Space>
        </Space>
      ),
    },
    {
      title: '供货商',
      key: 'supplier',
      width: 260,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{record.supplierName || '-'}</Typography.Text>
          <Typography.Text type="secondary">{record.supplierPhone || '-'}</Typography.Text>
          <Typography.Paragraph ellipsis={{ rows: 1, tooltip: record.supplierAddress || '' }} style={{ marginBottom: 0 }}>
            {record.supplierAddress || '-'}
          </Typography.Paragraph>
        </Space>
      ),
    },
    {
      title: '价格/重量',
      key: 'priceWeight',
      width: 160,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>供货价：{record.supplyPrice == null ? '-' : `¥${record.supplyPrice}`}</Typography.Text>
          <Typography.Text type="secondary">重量：{record.weightG == null ? '-' : `${record.weightG} g`}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '尺寸',
      key: 'size',
      width: 180,
      render: (_, record) => {
        const values = [record.lengthCm, record.widthCm, record.heightCm].map((value) => value ?? '-');
        return `${values[0]} x ${values[1]} x ${values[2]} cm`;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: statusTag,
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      width: 220,
      render: (value: string | null) => (
        <Typography.Paragraph ellipsis={{ rows: 2, tooltip: value || '' }} style={{ marginBottom: 0 }}>
          {value || '-'}
        </Typography.Paragraph>
      ),
    },
    {
      title: '提交时间',
      key: 'createdAt',
      width: 170,
      render: (_, record) => formatDateTime(record.createdAt),
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            type="primary"
            loading={actioningId === record.id}
            disabled={record.status !== 'PENDING'}
            onClick={() => void handleApprove(record)}
          >
            审核通过
          </Button>
          <Button
            size="small"
            danger
            loading={actioningId === record.id}
            disabled={record.status !== 'PENDING'}
            onClick={() => handleReject(record)}
          >
            拒绝
          </Button>
          <Button size="small" onClick={() => openEditModal(record)}>
            编辑
          </Button>
          <Button
            size="small"
            danger
            loading={actioningId === record.id}
            onClick={() => void handleDelete(record)}
          >
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
          <Input.Search
            value={filters.keyword}
            onChange={(event) => setFilters((current) => ({ ...current, keyword: event.target.value }))}
            onSearch={() => void load(1, pageSize)}
            allowClear
            placeholder="搜索产品 / 供货商 / 手机 / 地址"
            style={{ width: 320 }}
          />
          <Select
            value={filters.status}
            onChange={(value) => setFilters((current) => ({ ...current, status: value }))}
            allowClear
            placeholder="状态"
            style={{ width: 140 }}
            options={statusOptions.map(({ label, value }) => ({ label, value }))}
          />
          <Button loading={loading} onClick={() => void load(1, pageSize)}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
            新增提品
          </Button>
          <Button icon={<LinkOutlined />} onClick={() => void handleCopyH5Entry()}>
            分享到手机
          </Button>
          <Tag color="orange">本页待审核 {summary.pending}</Tag>
          <Tag color="green">本页已通过 {summary.approved}</Tag>
        </Space>
      </Card>

      <Card>
        <Table<SupplierProductSubmissionVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1420 }}
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
        open={modalOpen}
        title={editingRecord ? '编辑供应商提品' : '新增供应商提品'}
        width={820}
        confirmLoading={saving}
        onOk={() => void handleSave()}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" initialValues={initialFormValues}>
          <Space align="start" wrap style={{ width: '100%' }}>
            <Form.Item
              label="供货商名称"
              name="supplierName"
              rules={[{ required: true, message: '请输入供货商名称' }]}
              style={{ width: 240 }}
            >
              <Input allowClear placeholder="例如：张三工厂 / 李姐档口" />
            </Form.Item>
            <Form.Item label="手机号/微信" name="supplierPhone" style={{ width: 220 }}>
              <Input allowClear placeholder="方便后面联系发货" />
            </Form.Item>
            <Form.Item
              label="产品名称"
              name="productName"
              rules={[{ required: true, message: '请输入产品名称' }]}
              style={{ width: 300 }}
            >
              <Input allowClear placeholder="供应商提交的中文产品名" />
            </Form.Item>
          </Space>

          <Form.Item label="供货商地址" name="supplierAddress">
            <Input.TextArea rows={2} allowClear placeholder="发货地址、档口地址或仓库地址" />
          </Form.Item>

          <Space align="start" wrap style={{ width: '100%' }}>
            <Form.Item label="供货价" name="supplyPrice" style={{ width: 160 }}>
              <InputNumber min={0} precision={2} addonAfter="元" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="重量" name="weightG" style={{ width: 160 }}>
              <InputNumber min={0} precision={2} addonAfter="g" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="长" name="lengthCm" style={{ width: 140 }}>
              <InputNumber min={0} precision={2} addonAfter="cm" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="宽" name="widthCm" style={{ width: 140 }}>
              <InputNumber min={0} precision={2} addonAfter="cm" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="高" name="heightCm" style={{ width: 140 }}>
              <InputNumber min={0} precision={2} addonAfter="cm" style={{ width: '100%' }} />
            </Form.Item>
          </Space>

          <Form.Item label="产品实拍图" required extra="可以上传一张或多张，后面 AI 生成详情图、场景图会用这些原图。">
            <Upload
              listType="picture-card"
              multiple
              accept="image/*"
              fileList={fileList}
              customRequest={(options) => void handleUpload(options)}
              onChange={({ fileList: nextFileList }) => setFileList(nextFileList)}
              onRemove={(file) => {
                setFileList((current) => current.filter((item) => item.uid !== file.uid));
                return true;
              }}
            >
              <div>
                <PlusOutlined />
                <div style={{ marginTop: 8 }}>上传</div>
              </div>
            </Upload>
          </Form.Item>

          <Space align="start" wrap style={{ width: '100%' }}>
            <Form.Item label="状态" name="status" style={{ width: 180 }}>
              <Select options={statusOptions.map(({ label, value }) => ({ label, value }))} />
            </Form.Item>
            <Form.Item label="备注" name="remark" style={{ flex: 1, minWidth: 420 }}>
              <Input.TextArea rows={3} allowClear placeholder="比如：需要补图、价格待确认、适合做家居场景图" />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Space>
  );
};

export default SupplierProductSubmissionsPage;
