import { App, Button, Card, Descriptions, Image, Input, Modal, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { alibaba1688AuthSessionsApi } from '@/api/alibaba1688AuthSessions';
import { alibaba1688DetailRecordsApi } from '@/api/alibaba1688DetailRecords';
import { alibaba1688SelectionPoolsApi } from '@/api/alibaba1688SelectionPools';
import type {
  Alibaba1688AuthSessionOptionVO,
  Alibaba1688DetailRecordDetailVO,
  Alibaba1688DetailRecordVO,
} from '@/types/api';
import { formatDateTime, prettyJson } from '@/utils/format';
import './Alibaba1688DetailRecordsPage.css';

interface Filters {
  keyword: string;
  credentialId?: number;
}

const initialFilters: Filters = {
  keyword: '',
  credentialId: undefined,
};

function formatMetricValue(value?: number | null, suffix = '') {
  if (value === null || value === undefined) {
    return '-';
  }
  return `${value}${suffix}`;
}

function formatPowerSeller(value?: boolean | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  return value ? '是' : '否';
}

function recordStatusTag(status?: string | null) {
  if (status === 'READY') {
    return <Tag color="green">READY</Tag>;
  }
  if (status === 'FAILED') {
    return <Tag color="red">FAILED</Tag>;
  }
  return <Tag>{status || '-'}</Tag>;
}

const Alibaba1688DetailRecordsPage = () => {
  const { message, modal } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [importingId, setImportingId] = useState<number | null>(null);
  const [rows, setRows] = useState<Alibaba1688DetailRecordVO[]>([]);
  const [detailRecord, setDetailRecord] = useState<Alibaba1688DetailRecordDetailVO | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [credentialOptions, setCredentialOptions] = useState<Alibaba1688AuthSessionOptionVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState<Filters>(initialFilters);

  async function loadCredentials() {
    try {
      const res = await alibaba1688AuthSessionsApi.listOptions(false);
      setCredentialOptions(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 1688 凭证失败');
    }
  }

  async function load(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true);
    try {
      const res = await alibaba1688DetailRecordsApi.list({
        keyword: nextFilters.keyword.trim() || undefined,
        credentialId: nextFilters.credentialId,
        page: nextPage - 1,
        size: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 1688 详情数据失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void Promise.all([
      loadCredentials(),
      load(1, 20, initialFilters),
    ]);
  }, []);

  const credentialOptionItems = useMemo(
    () => credentialOptions.map((item) => ({
      value: item.id,
      label: item.accountNick ? `${item.sessionName} · ${item.accountNick}` : item.sessionName,
    })),
    [credentialOptions],
  );

  function updateFilter<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((current) => ({ ...current, [key]: value }));
  }

  async function openDetail(record: Alibaba1688DetailRecordVO) {
    setDetailLoading(true);
    setDetailRecord(null);
    setDetailOpen(true);
    try {
      const res = await alibaba1688DetailRecordsApi.detail(record.id);
      setDetailRecord(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败');
      setDetailOpen(false);
    } finally {
      setDetailLoading(false);
    }
  }

  async function handleImportToSelectionPool(record: Alibaba1688DetailRecordVO) {
    setImportingId(record.id);
    try {
      await alibaba1688SelectionPoolsApi.importFromDetail({
        detailRecordId: record.id,
      });
      message.success('已加入选品池');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加入选品池失败');
    } finally {
      setImportingId(null);
    }
  }

  const columns: ColumnsType<Alibaba1688DetailRecordVO> = [
    {
      title: '商品',
      key: 'goods',
      width: 380,
      render: (_, record) => (
        <div className="alibaba1688-detail-record-goods">
          {record.productMainImage ? (
            <Image width={56} height={56} src={record.productMainImage} alt={record.productName || record.offerId} />
          ) : null}
          <div className="alibaba1688-detail-record-goods-content">
            <Typography.Text strong className="alibaba1688-detail-record-title">
              {record.productName || '-'}
            </Typography.Text>
            <Typography.Text type="secondary">{record.companyName || '-'}</Typography.Text>
            <Space size={6} wrap>
              {recordStatusTag(record.status)}
              <Tag>{record.sourcePlatform || '1688'}</Tag>
            </Space>
          </div>
        </div>
      ),
    },
    {
      title: 'offerId / 链接',
      key: 'offer',
      width: 320,
      render: (_, record) => (
        <div className="alibaba1688-detail-record-cell">
          <Typography.Text strong copyable={{ text: record.offerId }}>
            {record.offerId}
          </Typography.Text>
          {record.detailUrl ? (
            <a href={record.detailUrl} target="_blank" rel="noreferrer" className="alibaba1688-detail-record-link" title={record.detailUrl}>
              {record.detailUrl}
            </a>
          ) : (
            <Typography.Text type="secondary">-</Typography.Text>
          )}
        </div>
      ),
    },
    {
      title: '价格 / 凭证',
      key: 'price',
      width: 220,
      render: (_, record) => (
        <div className="alibaba1688-detail-record-cell">
          <Typography.Text>
            价格：{record.minPrice ?? '-'} ~ {record.maxPrice ?? '-'}
          </Typography.Text>
          <Typography.Text type="secondary">凭证：{record.credentialName || '-'}</Typography.Text>
          <Typography.Text type="secondary">任务：{record.lastTaskId || '-'}</Typography.Text>
        </div>
      ),
    },
    {
      title: '采集时间',
      key: 'time',
      width: 200,
      render: (_, record) => (
        <div className="alibaba1688-detail-record-cell">
          <Typography.Text>采集：{formatDateTime(record.lastCollectedAt)}</Typography.Text>
          <Typography.Text type="secondary">更新：{formatDateTime(record.updatedAt)}</Typography.Text>
        </div>
      ),
    },
    {
      title: '错误',
      dataIndex: 'lastError',
      key: 'lastError',
      width: 260,
      render: (value: string | null) => (
        <Typography.Paragraph className="alibaba1688-detail-record-paragraph" ellipsis={{ rows: 3, tooltip: value || '' }}>
          {value || '-'}
        </Typography.Paragraph>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      render: (_, record) => (
        <Space wrap>
          <Button size="small" onClick={() => void openDetail(record)}>
            查看详情
          </Button>
          <Button
            size="small"
            disabled={record.status !== 'READY'}
            loading={importingId === record.id}
            onClick={() => {
              modal.confirm({
                title: '加入选品池',
                content: `确认把详情记录 #${record.id} 加入选品池吗？`,
                okText: '加入',
                cancelText: '取消',
                onOk: () => handleImportToSelectionPool(record),
              });
            }}
          >
            {record.status === 'READY' ? '加入选品池' : '待解析完成'}
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
            allowClear
            placeholder="搜索 offerId / 标题 / 公司 / 链接"
            style={{ width: 320 }}
            onPressEnter={() => {
              setPage(1);
              void load(1, pageSize, filters);
            }}
          />
          <Select
            value={filters.credentialId}
            onChange={(value) => updateFilter('credentialId', value)}
            allowClear
            placeholder="按凭证筛选"
            style={{ width: 220 }}
            options={credentialOptionItems}
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
        <Table<Alibaba1688DetailRecordVO>
          rowKey="id"
          className="alibaba1688-detail-record-table"
          columns={columns}
          dataSource={rows}
          loading={loading}
          tableLayout="fixed"
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
            setPage(nextPage);
            setPageSize(nextPageSize);
            void load(nextPage, nextPageSize, filters);
          }}
        />
      </Card>

      <Modal
        title={detailRecord?.productName || '1688 详情数据'}
        open={detailOpen}
        onCancel={() => {
          setDetailOpen(false);
          setDetailRecord(null);
        }}
        footer={null}
        width={1180}
        destroyOnClose
      >
        {detailLoading || !detailRecord ? (
          <Typography.Text>加载中...</Typography.Text>
        ) : (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="offerId">{detailRecord.offerId}</Descriptions.Item>
              <Descriptions.Item label="状态">{recordStatusTag(detailRecord.status)}</Descriptions.Item>
              <Descriptions.Item label="凭证">{detailRecord.credentialName || '-'}</Descriptions.Item>
              <Descriptions.Item label="最后采集">{formatDateTime(detailRecord.lastCollectedAt)}</Descriptions.Item>
              <Descriptions.Item label="标题" span={2}>{detailRecord.productName || '-'}</Descriptions.Item>
              <Descriptions.Item label="公司">{detailRecord.companyName || '-'}</Descriptions.Item>
              <Descriptions.Item label="价格">
                {detailRecord.minPrice ?? '-'} ~ {detailRecord.maxPrice ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="详情链接" span={2}>
                {detailRecord.detailUrl ? (
                  <a href={detailRecord.detailUrl} target="_blank" rel="noreferrer">
                    {detailRecord.detailUrl}
                  </a>
                ) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="错误" span={2}>{detailRecord.lastError || '-'}</Descriptions.Item>
            </Descriptions>

            <Descriptions bordered size="small" column={2} title="店铺能力">
              <Descriptions.Item label="店铺回头率">
                {formatMetricValue(detailRecord.repeatCustomerRate, '%')}
              </Descriptions.Item>
              <Descriptions.Item label="店铺服务分">
                {formatMetricValue(detailRecord.serviceScore)}
              </Descriptions.Item>
              <Descriptions.Item label="准时发货率">
                {formatMetricValue(detailRecord.onTimeDeliveryRate, '%')}
              </Descriptions.Item>
              <Descriptions.Item label="店铺好评率">
                {formatMetricValue(detailRecord.shopPositiveRate, '%')}
              </Descriptions.Item>
              <Descriptions.Item label="实力商家">
                {formatPowerSeller(detailRecord.powerSeller)}
              </Descriptions.Item>
              <Descriptions.Item label="入驻信息">
                {detailRecord.settledYearsText || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="主营类目" span={2}>
                {detailRecord.mainBusiness || '-'}
              </Descriptions.Item>
            </Descriptions>

            <Tabs
              items={[
                {
                  key: 'extracted',
                  label: '提取JSON',
                  children: (
                    <pre className="alibaba1688-detail-record-pre">
                      {prettyJson(detailRecord.extractedJson) || '-'}
                    </pre>
                  ),
                },
                {
                  key: 'parsed',
                  label: '解析JSON',
                  children: (
                    <pre className="alibaba1688-detail-record-pre">
                      {prettyJson(detailRecord.parsedJson) || '-'}
                    </pre>
                  ),
                },
                {
                  key: 'html',
                  label: '原始HTML',
                  children: (
                    <pre className="alibaba1688-detail-record-pre alibaba1688-detail-record-pre-html">
                      {detailRecord.rawHtml || '-'}
                    </pre>
                  ),
                },
              ]}
            />
          </Space>
        )}
      </Modal>
    </Space>
  );
};

export default Alibaba1688DetailRecordsPage;
