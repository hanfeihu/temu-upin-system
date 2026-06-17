import { App, Button, Card, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { alibaba1688CardLinksApi } from '@/api/alibaba1688CardLinks';
import type { Alibaba1688CardLinkVO } from '@/types/api';
import { formatDateTime, prettyJson } from '@/utils/format';
import './Alibaba1688CardLinksPage.css';

interface Filters {
  keyword: string;
  type?: string;
  status?: number;
}

const initialFilters: Filters = {
  keyword: '',
  type: undefined,
  status: undefined,
};

function statusTag(status?: number | null) {
  return status === 1 ? <Tag color="green">已处理</Tag> : <Tag color="gold">未处理</Tag>;
}

const Alibaba1688CardLinksPage = () => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Alibaba1688CardLinkVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const [updatingId, setUpdatingId] = useState<number | null>(null);
  const [previewRecord, setPreviewRecord] = useState<Alibaba1688CardLinkVO | null>(null);

  async function load(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true);
    try {
      const res = await alibaba1688CardLinksApi.list({
        keyword: nextFilters.keyword.trim() || undefined,
        type: nextFilters.type,
        status: nextFilters.status,
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
    void load(1, 20, initialFilters);
  }, []);

  function updateFilter<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((current) => ({ ...current, [key]: value }));
  }

  async function toggleStatus(record: Alibaba1688CardLinkVO) {
    const nextStatus = record.status === 1 ? 0 : 1;
    setUpdatingId(record.id);
    try {
      await alibaba1688CardLinksApi.updateStatus(record.id, nextStatus);
      message.success(nextStatus === 1 ? '已标记为已处理' : '已改回未处理');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '状态更新失败');
    } finally {
      setUpdatingId(null);
    }
  }

  const columns: ColumnsType<Alibaba1688CardLinkVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '商品标识',
      key: 'offerId',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={4} style={{ width: '100%', minWidth: 0 }}>
          <Typography.Text copyable={{ text: record.offerId }} strong>
            {record.offerId}
          </Typography.Text>
          <Space size={6} wrap>
            <Tag color={record.type === 'ad' ? 'purple' : 'blue'}>{record.type || '-'}</Tag>
            {record.offerIdSource ? <Tag>{record.offerIdSource}</Tag> : null}
            {record.cardIndex ? <Tag>index {record.cardIndex}</Tag> : null}
          </Space>
        </Space>
      ),
    },
    {
      title: '链接',
      key: 'urls',
      width: 480,
      render: (_, record) => (
        <div className="alibaba1688-card-links-cell">
          {record.detailUrl ? (
            <a
              className="alibaba1688-card-links-link"
              href={record.detailUrl}
              target="_blank"
              rel="noreferrer"
              title={record.detailUrl}
            >
              {record.detailUrl}
            </a>
          ) : (
            <Typography.Text type="secondary">-</Typography.Text>
          )}
          <Typography.Text
            className="alibaba1688-card-links-subtext"
            type="secondary"
            title={record.cardHref || ''}
          >
            {record.cardHref || '-'}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '渲染信息',
      key: 'renderInfo',
      width: 280,
      render: (_, record) => (
        <div className="alibaba1688-card-links-cell">
          <Typography.Text className="alibaba1688-card-links-text" title={record.renderKey || ''}>
            {record.renderKey || '-'}
          </Typography.Text>
          <Typography.Text className="alibaba1688-card-links-subtext" type="secondary" title={record.cardClass || ''}>
            {record.cardClass || '-'}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 110,
      render: (_, record) => statusTag(record.status),
    },
    {
      title: '更新时间',
      key: 'time',
      width: 180,
      render: (_, record) => (
        <Space direction="vertical" size={2} style={{ width: '100%', minWidth: 0 }}>
          <Typography.Text>{formatDateTime(record.updatedAt)}</Typography.Text>
          <Typography.Text type="secondary">{formatDateTime(record.createdAt)}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            type={record.status === 1 ? 'default' : 'primary'}
            loading={updatingId === record.id}
            onClick={() => void toggleStatus(record)}
          >
            {record.status === 1 ? '改回未处理' : '标记已处理'}
          </Button>
          <Button size="small" onClick={() => setPreviewRecord(record)}>
            查看JSON
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Input
            value={filters.keyword}
            onChange={(e) => updateFilter('keyword', e.target.value)}
            placeholder="搜索 offerId / 链接 / renderKey / class"
            allowClear
            style={{ width: 320 }}
            onPressEnter={() => {
              setPage(1);
              void load(1, pageSize, filters);
            }}
          />
          <Select
            value={filters.type}
            onChange={(value) => updateFilter('type', value)}
            allowClear
            placeholder="类型"
            style={{ width: 140 }}
            options={[
              { value: 'normal', label: 'normal' },
              { value: 'ad', label: 'ad' },
            ]}
          />
          <Select
            value={filters.status}
            onChange={(value) => updateFilter('status', value)}
            allowClear
            placeholder="处理状态"
            style={{ width: 160 }}
            options={[
              { value: 0, label: '未处理' },
              { value: 1, label: '已处理' },
            ]}
          />
          <Button
            type="primary"
            loading={loading}
            onClick={() => {
              setPage(1);
              void load(1, pageSize, filters);
            }}
          >
            查询
          </Button>
          <Button
            onClick={() => {
              setFilters(initialFilters);
              setPage(1);
              setPageSize(20);
              void load(1, 20, initialFilters);
            }}
          >
            重置
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<Alibaba1688CardLinkVO>
          rowKey="id"
          className="alibaba1688-card-links-table"
          columns={columns}
          dataSource={rows}
          loading={loading}
          tableLayout="fixed"
          scroll={{ x: 1550 }}
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
        open={!!previewRecord}
        title={previewRecord ? `记录 ${previewRecord.offerId}` : '记录详情'}
        width={860}
        footer={null}
        onCancel={() => setPreviewRecord(null)}
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Typography.Text>详情链接：{previewRecord?.detailUrl || '-'}</Typography.Text>
          <Typography.Text>卡片链接：{previewRecord?.cardHref || '-'}</Typography.Text>
          <pre
            style={{
              margin: 0,
              padding: 12,
              borderRadius: 8,
              maxHeight: 480,
              overflow: 'auto',
              background: '#f6f8fa',
            }}
          >
            {prettyJson(previewRecord?.rawPayload || '') || '{}'}
          </pre>
        </Space>
      </Modal>
    </Space>
  );
};

export default Alibaba1688CardLinksPage;
