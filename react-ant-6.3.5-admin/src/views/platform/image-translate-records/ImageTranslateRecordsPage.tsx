import { Button, Card, Image, Input, InputNumber, Select, Space, Table, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { imageTranslateRecordsApi } from '@/api/imageTranslateRecords';
import type { ImageTranslateRecordVO } from '@/types/api';
import { formatDateTime } from '@/utils/format';

interface Filters {
  spuId?: number;
  provider?: string;
  sourceUrl: string;
  status?: string;
}

const initialFilters: Filters = {
  spuId: undefined,
  provider: undefined,
  sourceUrl: '',
  status: undefined,
};

const ImageTranslateRecordsPage = () => {
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<ImageTranslateRecordVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState<Filters>(initialFilters);

  async function load(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true);
    try {
      const res = await imageTranslateRecordsApi.list({
        spuId: nextFilters.spuId,
        provider: nextFilters.provider,
        sourceUrl: nextFilters.sourceUrl.trim() || undefined,
        status: nextFilters.status,
        page: nextPage - 1,
        size: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
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

  const columns: ColumnsType<ImageTranslateRecordVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: 'SPU',
      dataIndex: 'spuId',
      key: 'spuId',
      width: 100,
    },
    {
      title: '图片',
      key: 'images',
      width: 220,
      render: (_, record) => (
        <Space size={12}>
          <Image width={64} height={64} src={record.sourceUrl} />
          {record.translatedUrl ? <Image width={64} height={64} src={record.translatedUrl} /> : null}
        </Space>
      ),
    },
    {
      title: '来源 / 结果',
      key: 'links',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text ellipsis style={{ maxWidth: 420 }}>
            {record.sourceUrl}
          </Typography.Text>
          <Typography.Text type="secondary" ellipsis style={{ maxWidth: 420 }}>
            {record.translatedUrl || record.errorMsg || '-'}
          </Typography.Text>
        </Space>
      ),
    },
    { title: '服务商', dataIndex: 'provider', key: 'provider', width: 120 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 120 },
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      key: 'startedAt',
      width: 180,
      render: (value: ImageTranslateRecordVO['startedAt']) => formatDateTime(value),
    },
    {
      title: '结束时间',
      dataIndex: 'endedAt',
      key: 'endedAt',
      width: 180,
      render: (value: ImageTranslateRecordVO['endedAt']) => formatDateTime(value),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <InputNumber
            value={filters.spuId}
            onChange={(value) => updateFilter('spuId', value ?? undefined)}
            placeholder="SPU ID"
            style={{ width: 140 }}
          />
          <Select
            value={filters.provider}
            onChange={(value) => updateFilter('provider', value)}
            allowClear
            placeholder="服务商"
            style={{ width: 160 }}
            options={[
              { value: 'temu', label: 'TEMU' },
              { value: 'aliyun', label: 'Aliyun' },
            ]}
          />
          <Select
            value={filters.status}
            onChange={(value) => updateFilter('status', value)}
            allowClear
            placeholder="状态"
            style={{ width: 160 }}
            options={[
              { value: 'SUCCESS', label: 'SUCCESS' },
              { value: 'FAILED', label: 'FAILED' },
              { value: 'RUNNING', label: 'RUNNING' },
            ]}
          />
          <Input
            value={filters.sourceUrl}
            onChange={(e) => updateFilter('sourceUrl', e.target.value)}
            placeholder="按来源 URL 搜索"
            allowClear
            style={{ width: 260 }}
          />
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
        </Space>
      </Card>

      <Card>
        <Table<ImageTranslateRecordVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
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
            const nextPageSize = pagination.pageSize || 20;
            setPage(nextPage);
            setPageSize(nextPageSize);
            void load(nextPage, nextPageSize, filters);
          }}
        />
      </Card>
    </Space>
  );
};

export default ImageTranslateRecordsPage;
