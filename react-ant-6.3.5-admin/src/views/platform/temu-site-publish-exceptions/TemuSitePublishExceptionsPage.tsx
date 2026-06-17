import { App, Button, Card, Input, Modal, Select, Space, Switch, Table, Tag, Typography, Upload } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { InboxOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { temuSitePublishExceptionsApi } from '@/api/temuSitePublishExceptions';
import type { TemuSitePublishExceptionVO } from '@/types/api';
import { formatDateTime } from '@/utils/format';

const reasonOptions = [
  { value: 'QUALIFICATION', label: '资质缺失' },
  { value: 'REAL_PHOTO_OR_LABEL', label: '实拍/标签' },
  { value: 'SENSITIVE_ATTRIBUTE', label: '敏感属性' },
  { value: 'COMPLIANCE', label: '合规问题' },
  { value: 'OTHER', label: '其他' },
];

const reasonLabel = (value?: string | null) => reasonOptions.find((item) => item.value === value)?.label || value || '-';

const activeParam = (value: 'ALL' | 'ACTIVE' | 'DISABLED') => {
  if (value === 'ACTIVE') return true;
  if (value === 'DISABLED') return false;
  return undefined;
};

export default function TemuSitePublishExceptionsPage() {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<TemuSitePublishExceptionVO[]>([]);
  const [keyword, setKeyword] = useState('');
  const [queryKeyword, setQueryKeyword] = useState('');
  const [siteName, setSiteName] = useState('');
  const [reasonType, setReasonType] = useState('');
  const [activeFilter, setActiveFilter] = useState<'ALL' | 'ACTIVE' | 'DISABLED'>('ACTIVE');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [pathOpen, setPathOpen] = useState(false);
  const [filePath, setFilePath] = useState('');
  const [importing, setImporting] = useState(false);

  async function load(nextPage = page, nextPageSize = pageSize) {
    setLoading(true);
    try {
      const res = await temuSitePublishExceptionsApi.list({
        keyword: queryKeyword.trim() || undefined,
        siteName: siteName || undefined,
        reasonType: reasonType || undefined,
        active: activeParam(activeFilter),
        page: nextPage - 1,
        size: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载加站异常库失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(1, pageSize);
  }, []);

  async function importFile(file: File) {
    setImporting(true);
    try {
      const res = await temuSitePublishExceptionsApi.importFile(file);
      message.success(`导入完成：新增 ${res.data.created}，更新 ${res.data.updated}，跳过 ${res.data.skipped}`);
      setPage(1);
      await load(1, pageSize);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '导入失败');
    } finally {
      setImporting(false);
    }
  }

  async function importPath() {
    const path = filePath.trim();
    if (!path) {
      message.warning('请输入服务器上的 JSON 文件路径');
      return;
    }
    setImporting(true);
    try {
      const res = await temuSitePublishExceptionsApi.importPath(path);
      message.success(`导入完成：新增 ${res.data.created}，更新 ${res.data.updated}，跳过 ${res.data.skipped}`);
      setPathOpen(false);
      setFilePath('');
      setPage(1);
      await load(1, pageSize);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '导入失败');
    } finally {
      setImporting(false);
    }
  }

  async function toggleActive(record: TemuSitePublishExceptionVO, active: boolean) {
    try {
      await temuSitePublishExceptionsApi.update(record.id, { active });
      message.success(active ? '已启用排除' : '已停用排除');
      await load(page, pageSize);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '更新失败');
    }
  }

  function remove(record: TemuSitePublishExceptionVO) {
    modal.confirm({
      title: '删除加站异常记录？',
      content: `确认删除 ${record.skc} 吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await temuSitePublishExceptionsApi.delete(record.id);
          message.success('已删除');
          await load(page, pageSize);
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        }
      },
    });
  }

  const columns: ColumnsType<TemuSitePublishExceptionVO> = [
    {
      title: '商品',
      key: 'product',
      width: 360,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text copyable={{ text: record.skc }}>{record.skc}</Typography.Text>
          <Typography.Text type="secondary" ellipsis style={{ maxWidth: 330 }}>
            {record.productTitle || '-'}
          </Typography.Text>
          <Space size={4} wrap>
            {record.offerId ? <Tag>1688: {record.offerId}</Tag> : null}
            {record.temuSpuId ? <Tag>SPU: {record.temuSpuId}</Tag> : null}
            {record.productCollectionId ? (
              <Typography.Link onClick={() => navigate(`/platform/product-collections/${record.productCollectionId}`)}>
                采集库 #{record.productCollectionId}
              </Typography.Link>
            ) : null}
          </Space>
        </Space>
      ),
    },
    {
      title: '站点/类型',
      key: 'site',
      width: 180,
      render: (_, record) => (
        <Space direction="vertical" size={4}>
          <Tag color="blue">{record.siteName || '-'}</Tag>
          <Tag color={record.active === false ? 'default' : 'red'}>{reasonLabel(record.reasonType)}</Tag>
          <Switch size="small" checked={record.active !== false} checkedChildren="排除" unCheckedChildren="停用" onChange={(checked) => void toggleActive(record, checked)} />
        </Space>
      ),
    },
    {
      title: '异常原因',
      dataIndex: 'reasonText',
      key: 'reasonText',
      ellipsis: true,
      render: (value) => value || '-',
    },
    {
      title: '价格/状态',
      key: 'price',
      width: 170,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>申报价：{record.declaredPrice ?? '-'}</Typography.Text>
          <Typography.Text type="secondary">状态：{record.statusText || '-'}</Typography.Text>
          <Typography.Text type="secondary">运营：{record.operatorName || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '时间',
      key: 'time',
      width: 210,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text type="secondary">采集：{formatDateTime(record.sourceCollectedAt)}</Typography.Text>
          <Typography.Text type="secondary">创建：{record.createdTimeText || '-'}</Typography.Text>
          <Typography.Text type="secondary">更新：{formatDateTime(record.updatedAt)}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_, record) => (
        <Button size="small" danger onClick={() => remove(record)}>
          删除
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索 SKC / SPU / 1688ID / 标题 / 原因"
            style={{ width: 340 }}
            allowClear
            onPressEnter={() => {
              setQueryKeyword(keyword);
              setPage(1);
              void load(1, pageSize);
            }}
          />
          <Select
            value={siteName}
            onChange={setSiteName}
            style={{ width: 130 }}
            options={[
              { value: '', label: '全部站点' },
              { value: '美国站', label: '美国站' },
              { value: '全球', label: '全球' },
            ]}
          />
          <Select
            value={reasonType}
            onChange={setReasonType}
            style={{ width: 150 }}
            options={[{ value: '', label: '全部类型' }, ...reasonOptions]}
          />
          <Select
            value={activeFilter}
            onChange={setActiveFilter}
            style={{ width: 130 }}
            options={[
              { value: 'ACTIVE', label: '排除中' },
              { value: 'DISABLED', label: '已停用' },
              { value: 'ALL', label: '全部' },
            ]}
          />
          <Button
            type="primary"
            loading={loading}
            onClick={() => {
              setQueryKeyword(keyword);
              setPage(1);
              void load(1, pageSize);
            }}
          >
            查询
          </Button>
          <Button
            onClick={() => {
              setKeyword('');
              setQueryKeyword('');
              setSiteName('');
              setReasonType('');
              setActiveFilter('ACTIVE');
              setPage(1);
              void load(1, pageSize);
            }}
          >
            重置
          </Button>
          <Upload
            accept=".json,application/json"
            showUploadList={false}
            beforeUpload={(file) => {
              void importFile(file);
              return false;
            }}
          >
            <Button icon={<InboxOutlined />} loading={importing}>
              上传JSON导入
            </Button>
          </Upload>
          <Button onClick={() => setPathOpen(true)}>按服务器路径导入</Button>
        </Space>
      </Card>

      <Card>
        <Table<TemuSitePublishExceptionVO>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          tableLayout="fixed"
          scroll={{ x: 1300 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
          }}
          onChange={(pagination: TablePaginationConfig) => {
            const nextPage = pagination.current || 1;
            const nextPageSize = pagination.pageSize || pageSize;
            setPage(nextPage);
            setPageSize(nextPageSize);
            void load(nextPage, nextPageSize);
          }}
        />
      </Card>

      <Modal
        title="按服务器路径导入"
        open={pathOpen}
        onCancel={() => setPathOpen(false)}
        onOk={() => void importPath()}
        confirmLoading={importing}
        okText="导入"
        cancelText="取消"
      >
        <Input
          value={filePath}
          onChange={(event) => setFilePath(event.target.value)}
          placeholder="/home/xxx/tminos-temu-skc.json"
        />
      </Modal>
    </Space>
  );
}
