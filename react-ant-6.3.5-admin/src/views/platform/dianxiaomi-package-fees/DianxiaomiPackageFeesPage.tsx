import { Alert, App, Button, Card, Descriptions, Empty, Input, Modal, Space, Statistic, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { TablePaginationConfig } from 'antd/es/table/interface';
import { useEffect, useMemo, useState } from 'react';
import { dianxiaomiPackageFeesApi } from '@/api/dianxiaomiPackageFees';
import type {
  DianxiaomiPackageFeeDetailItemVO,
  DianxiaomiPackageFeeImportResultVO,
  DianxiaomiPackageFeeVO,
  DianxiaomiPackageFeeSummaryVO,
} from '@/types/api';
import { formatDateTime } from '@/utils/format';

const DEFAULT_SUMMARY: DianxiaomiPackageFeeSummaryVO = {
  totalCount: 0,
  successCount: 0,
  failedCount: 0,
  totalFee: 0,
  packingFee: 0,
  totalFeeWithPacking: 0,
};

function formatFee(value?: number | string | null) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return String(value);
  }
  return `¥${numeric.toFixed(2)}`;
}

function parsePackageNumbers(text: string) {
  const seen = new Set<string>();
  return text
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter((item) => {
      if (!item || seen.has(item)) {
        return false;
      }
      seen.add(item);
      return true;
    });
}

const DianxiaomiPackageFeesPage = () => {
  const { message, modal } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [retryingFailed, setRetryingFailed] = useState(false);
  const [retryingId, setRetryingId] = useState<number | null>(null);
  const [clearing, setClearing] = useState(false);
  const [clearingSuccess, setClearingSuccess] = useState(false);
  const [rows, setRows] = useState<DianxiaomiPackageFeeVO[]>([]);
  const [summary, setSummary] = useState<DianxiaomiPackageFeeSummaryVO>(DEFAULT_SUMMARY);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [importOpen, setImportOpen] = useState(false);
  const [importText, setImportText] = useState('');
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailRecord, setDetailRecord] = useState<DianxiaomiPackageFeeVO | null>(null);

  const importPreviewCount = useMemo(() => parsePackageNumbers(importText).length, [importText]);

  async function load(nextPage = page, nextPageSize = pageSize, nextKeyword = keyword) {
    setLoading(true);
    try {
      const res = await dianxiaomiPackageFeesApi.list({
        keyword: nextKeyword.trim() || undefined,
        page: nextPage,
        pageSize: nextPageSize,
      });
      const data = res.data;
      setRows(Array.isArray(data?.content) ? data.content : []);
      setTotal(data?.totalElements ?? 0);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载店小秘费用列表失败');
    } finally {
      setLoading(false);
    }
  }

  async function loadSummary(nextKeyword = keyword) {
    setSummaryLoading(true);
    try {
      const res = await dianxiaomiPackageFeesApi.summary({
        keyword: nextKeyword.trim() || undefined,
      });
      setSummary(res.data || DEFAULT_SUMMARY);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载统计失败');
    } finally {
      setSummaryLoading(false);
    }
  }

  async function refresh(nextPage = page, nextPageSize = pageSize, nextKeyword = keyword) {
    await Promise.all([
      load(nextPage, nextPageSize, nextKeyword),
      loadSummary(nextKeyword),
    ]);
  }

  useEffect(() => {
    void refresh(1, 20, '');
  }, []);

  function openImport() {
    setImportText('');
    setImportOpen(true);
  }

  function openDetail(record: DianxiaomiPackageFeeVO) {
    setDetailRecord(record);
    setDetailOpen(true);
  }

  function confirmClear() {
    modal.confirm({
      title: '确认清空数据？',
      content: '会清空当前模块全部已保存的店小秘费用数据，且不可恢复。',
      okText: '确认清空',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setClearing(true);
        try {
          const res = await dianxiaomiPackageFeesApi.clear();
          setDetailOpen(false);
          setDetailRecord(null);
          await refresh(1, pageSize, keyword);
          setPage(1);
          message.success(res.message || `已清空 ${res.data ?? 0} 条数据`);
        } catch (error) {
          message.error(error instanceof Error ? error.message : '清空数据失败');
          throw error;
        } finally {
          setClearing(false);
        }
      },
    });
  }

  function confirmClearSuccess() {
    modal.confirm({
      title: '确认清空已成功记录？',
      content: '只会删除已成功查询到费用的店小秘单号，失败记录会保留，方便你后续重试或排查。',
      okText: '清空已成功',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setClearingSuccess(true);
        try {
          const res = await dianxiaomiPackageFeesApi.clearSuccess();
          setDetailOpen(false);
          setDetailRecord(null);
          await refresh(1, pageSize, keyword);
          setPage(1);
          message.success(res.message || `已清空成功记录 ${res.data ?? 0} 条`);
        } catch (error) {
          message.error(error instanceof Error ? error.message : '清空已成功记录失败');
          throw error;
        } finally {
          setClearingSuccess(false);
        }
      },
    });
  }

  function showImportResult(result: DianxiaomiPackageFeeImportResultVO) {
    const failureLines = (result.failures || []).map((item) => `${item.dianxiaomiPackageNumber}: ${item.message}`);
    modal.info({
      title: '导入完成',
      width: 760,
      content: (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="原始行数">{result.inputCount ?? 0}</Descriptions.Item>
            <Descriptions.Item label="去重后单号数">{result.acceptedCount ?? 0}</Descriptions.Item>
            <Descriptions.Item label="新增">{result.createdCount ?? 0}</Descriptions.Item>
            <Descriptions.Item label="更新">{result.updatedCount ?? 0}</Descriptions.Item>
            <Descriptions.Item label="成功">{result.successCount ?? 0}</Descriptions.Item>
            <Descriptions.Item label="失败">{result.failedCount ?? 0}</Descriptions.Item>
          </Descriptions>
          {failureLines.length ? (
            <Typography.Paragraph style={{ marginBottom: 0 }}>
              <Typography.Text type="danger">失败明细</Typography.Text>
              <pre style={{ marginTop: 8, maxHeight: 280, overflow: 'auto', whiteSpace: 'pre-wrap' }}>
                {failureLines.join('\n')}
              </pre>
            </Typography.Paragraph>
          ) : null}
        </Space>
      ),
    });
  }

  async function submitImport() {
    const packageNumbers = parsePackageNumbers(importText);
    if (!packageNumbers.length) {
      message.error('请先粘贴店小秘单号');
      return;
    }

    setImporting(true);
    try {
      const res = await dianxiaomiPackageFeesApi.import({ packageNumbers });
      const result = res.data;
      setImportOpen(false);
      if (result) {
        showImportResult(result);
        message.success(`已处理 ${result.acceptedCount ?? packageNumbers.length} 个店小秘单号`);
      } else {
        message.success('导入完成');
      }
      await refresh(1, pageSize, keyword);
      setPage(1);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '导入失败');
    } finally {
      setImporting(false);
    }
  }

  async function retryFailedRecords() {
    setRetryingFailed(true);
    try {
      const res = await dianxiaomiPackageFeesApi.retryFailed();
      const result = res.data;
      if (result) {
        showImportResult(result);
        message.success(`已重试 ${result.acceptedCount ?? 0} 个失败单号`);
      } else {
        message.success('重试完成');
      }
      await refresh(1, pageSize, keyword);
      setPage(1);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重试全部失败记录失败');
    } finally {
      setRetryingFailed(false);
    }
  }

  async function retryRecord(record: DianxiaomiPackageFeeVO) {
    if (!record?.id) {
      return;
    }
    setRetryingId(record.id);
    try {
      const res = await dianxiaomiPackageFeesApi.retryRecord(record.id);
      const result = res.data;
      if (result) {
        showImportResult(result);
        message.success(`已重试 ${record.dianxiaomiPackageNumber}`);
      } else {
        message.success('重试完成');
      }
      setDetailOpen(false);
      setDetailRecord(null);
      await refresh(page, pageSize, keyword);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重试失败记录失败');
    } finally {
      setRetryingId(null);
    }
  }

  const feeDetailColumns: ColumnsType<DianxiaomiPackageFeeDetailItemVO> = [
    {
      title: '费用项',
      key: 'feeKindName',
      render: (_, record) => record.feeKindName || record.feeKindCode || '-',
    },
    {
      title: '金额',
      key: 'amount',
      width: 140,
      render: (_, record) => formatFee(record.currencyAmount ?? record.amount),
    },
    {
      title: '币种',
      key: 'currency',
      width: 120,
      render: (_, record) => record.currencyName || record.currencyCode || '-',
    },
    {
      title: '备注',
      dataIndex: 'note',
      key: 'note',
      ellipsis: true,
      render: (value: string | null | undefined) => value || '-',
    },
    {
      title: '发生时间',
      key: 'occurDate',
      width: 180,
      render: (_, record) => record.occurDate || record.billDate || record.createDate || '-',
    },
  ];

  const columns: ColumnsType<DianxiaomiPackageFeeVO> = [
    {
      title: '店小秘单号',
      dataIndex: 'dianxiaomiPackageNumber',
      key: 'dianxiaomiPackageNumber',
      render: (value: string) => value || '-',
    },
    {
      title: '物流费用',
      dataIndex: 'totalFee',
      key: 'totalFee',
      width: 150,
      render: (_, record) => {
        if (record.totalFee !== null && record.totalFee !== undefined) {
          return (
            <Button type="link" style={{ padding: 0 }} onClick={() => openDetail(record)}>
              {formatFee(record.totalFee)}
            </Button>
          );
        }
        if (record.errorMessage) {
          return (
            <Button danger type="link" style={{ padding: 0 }} onClick={() => openDetail(record)}>
              查询失败
            </Button>
          );
        }
        return '-';
      },
    },
    {
      title: '打包费',
      dataIndex: 'packingFee',
      key: 'packingFee',
      width: 120,
      render: (_, record) => (record.errorMessage ? '-' : formatFee(record.packingFee)),
    },
    {
      title: '合计费用',
      dataIndex: 'totalFeeWithPacking',
      key: 'totalFeeWithPacking',
      width: 150,
      render: (_, record) => (record.errorMessage ? '-' : formatFee(record.totalFeeWithPacking)),
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      render: (_, record) => (
        <Space size={8}>
          <Button type="link" style={{ padding: 0 }} onClick={() => openDetail(record)}>
            详情
          </Button>
          {record.errorMessage ? (
            <Button
              type="link"
              style={{ padding: 0 }}
              loading={retryingId === record.id}
              onClick={() => void retryRecord(record)}
            >
              重试
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card loading={summaryLoading}>
        <Space size={32} wrap>
          <Statistic title="物流费用" value={formatFee(summary.totalFee)} />
          <Statistic title="打包费用" value={formatFee(summary.packingFee)} />
          <Statistic title="合计费用" value={formatFee(summary.totalFeeWithPacking)} valueStyle={{ color: '#1677ff' }} />
          <Statistic title="总单数" value={summary.totalCount ?? 0} />
          <Statistic title="成功单数" value={summary.successCount ?? 0} valueStyle={{ color: '#389e0d' }} />
          <Statistic title="失败单数" value={summary.failedCount ?? 0} valueStyle={{ color: '#cf1322' }} />
        </Space>
      </Card>

      <Card>
        <Space wrap>
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索店小秘单号"
            style={{ width: 280 }}
            allowClear
            onPressEnter={() => {
              setPage(1);
              void refresh(1, pageSize, keyword);
            }}
          />
          <Button
            type="primary"
            onClick={() => {
              setPage(1);
              void refresh(1, pageSize, keyword);
            }}
          >
            查询
          </Button>
          <Button
            onClick={() => {
              setKeyword('');
              setPage(1);
              void refresh(1, pageSize, '');
            }}
          >
            重置
          </Button>
          <Button loading={loading || summaryLoading} onClick={() => void refresh()}>
            刷新
          </Button>
          <Button danger loading={clearing} onClick={confirmClear}>
            清空数据
          </Button>
          <Button danger loading={clearingSuccess} onClick={confirmClearSuccess}>
            清空已成功
          </Button>
          <Button loading={retryingFailed} onClick={() => void retryFailedRecords()}>
            重试全部失败
          </Button>
          <Button type="primary" onClick={openImport}>
            导入
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<DianxiaomiPackageFeeVO>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
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
            void load(nextPage, nextPageSize, keyword);
          }}
        />
      </Card>

      <Modal
        open={importOpen}
        title="导入店小秘单号"
        width={920}
        destroyOnClose
        confirmLoading={importing}
        onOk={() => void submitImport()}
        onCancel={() => setImportOpen(false)}
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Typography.Text type="secondary">
            一行一个店小秘单号，提交后会逐条调用浩远接口查询费用和明细，并保存到数据库。
          </Typography.Text>
          <Input.TextArea
            value={importText}
            onChange={(event) => setImportText(event.target.value)}
            rows={18}
            placeholder={`XM1234567890\nXM1234567891`}
          />
          <Typography.Text type="secondary">当前识别到 {importPreviewCount} 个单号</Typography.Text>
        </Space>
      </Modal>

      <Modal
        open={detailOpen}
        title={detailRecord?.dianxiaomiPackageNumber || '费用明细'}
        width={960}
        footer={detailRecord?.errorMessage ? (
          <Space>
            <Button onClick={() => setDetailOpen(false)}>关闭</Button>
            <Button
              type="primary"
              loading={retryingId === detailRecord.id}
              onClick={() => void retryRecord(detailRecord)}
            >
              重试
            </Button>
          </Space>
        ) : null}
        onCancel={() => setDetailOpen(false)}
      >
        {detailRecord ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="店小秘单号">{detailRecord.dianxiaomiPackageNumber || '-'}</Descriptions.Item>
              <Descriptions.Item label="物流费用">{formatFee(detailRecord.totalFee)}</Descriptions.Item>
              <Descriptions.Item label="打包费">{detailRecord.errorMessage ? '-' : formatFee(detailRecord.packingFee)}</Descriptions.Item>
              <Descriptions.Item label="合计费用">{detailRecord.errorMessage ? '-' : formatFee(detailRecord.totalFeeWithPacking)}</Descriptions.Item>
              <Descriptions.Item label="最近查询时间">{formatDateTime(detailRecord.lastQueriedAt)}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatDateTime(detailRecord.updatedAt)}</Descriptions.Item>
              {detailRecord.errorMessage ? (
                <Descriptions.Item label="错误信息" span={2}>
                  <Typography.Text type="danger">{detailRecord.errorMessage}</Typography.Text>
                </Descriptions.Item>
              ) : null}
            </Descriptions>

            {detailRecord.errorMessage ? (
              <Alert type="error" showIcon message={detailRecord.errorMessage} />
            ) : null}

            {detailRecord.feeDetailItems?.length ? (
              <Table<DianxiaomiPackageFeeDetailItemVO>
                rowKey={(record, index) => `${record.feeKindCode || record.feeKindName || 'item'}-${index}`}
                columns={feeDetailColumns}
                dataSource={detailRecord.feeDetailItems}
                pagination={false}
              />
            ) : (
              <Empty description="暂无费用明细" />
            )}
          </Space>
        ) : null}
      </Modal>
    </Space>
  );
};

export default DianxiaomiPackageFeesPage;
