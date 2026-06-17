import { Alert, App, Button, Card, Checkbox, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useMemo, useState, type Key } from 'react';
import { alibaba1688AuthSessionsApi } from '@/api/alibaba1688AuthSessions';
import { alibaba1688CardLinksApi } from '@/api/alibaba1688CardLinks';
import { alibaba1688DetailTasksApi } from '@/api/alibaba1688DetailTasks';
import type {
  Alibaba1688AuthSessionOptionVO,
  Alibaba1688CardLinkVO,
  Alibaba1688DetailTaskCreateResultVO,
  Alibaba1688DetailTaskImportFromCardLinksResultVO,
  Alibaba1688DetailTaskVO,
} from '@/types/api';
import { formatDateTime } from '@/utils/format';
import './Alibaba1688DetailTasksPage.css';

interface Filters {
  keyword: string;
  credentialId?: number;
  status?: string;
}

const initialFilters: Filters = {
  keyword: '',
  credentialId: undefined,
  status: undefined,
};

const statusColorMap: Record<string, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  SUCCEEDED: 'green',
  FAILED: 'red',
  CANCELLED: 'orange',
};

function statusTag(status?: string | null) {
  return <Tag color={statusColorMap[status || ''] || 'blue'}>{status || '-'}</Tag>;
}

const Alibaba1688DetailTasksPage = () => {
  const { message, modal } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [actionId, setActionId] = useState<number | null>(null);
  const [rows, setRows] = useState<Alibaba1688DetailTaskVO[]>([]);
  const [credentialOptions, setCredentialOptions] = useState<Alibaba1688AuthSessionOptionVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogCredentialId, setDialogCredentialId] = useState<number | undefined>(undefined);
  const [dialogRawInput, setDialogRawInput] = useState('');
  const [dialogForceRefresh, setDialogForceRefresh] = useState(false);
  const [cardImportOpen, setCardImportOpen] = useState(false);
  const [cardImportCredentialId, setCardImportCredentialId] = useState<number | undefined>(undefined);
  const [cardImportForceRefresh, setCardImportForceRefresh] = useState(false);
  const [cardImportLoading, setCardImportLoading] = useState(false);
  const [cardImportSubmitting, setCardImportSubmitting] = useState(false);
  const [cardImportKeyword, setCardImportKeyword] = useState('');
  const [cardImportSelectAll, setCardImportSelectAll] = useState(false);
  const [cardImportRows, setCardImportRows] = useState<Alibaba1688CardLinkVO[]>([]);
  const [cardImportSelectedIds, setCardImportSelectedIds] = useState<number[]>([]);
  const [cardImportPage, setCardImportPage] = useState(1);
  const [cardImportPageSize, setCardImportPageSize] = useState(10);
  const [cardImportTotal, setCardImportTotal] = useState(0);

  async function loadCredentials() {
    try {
      const res = await alibaba1688AuthSessionsApi.listOptions(true);
      setCredentialOptions(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 1688 凭证失败');
    }
  }

  async function load(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true);
    try {
      const res = await alibaba1688DetailTasksApi.list({
        keyword: nextFilters.keyword.trim() || undefined,
        credentialId: nextFilters.credentialId,
        status: nextFilters.status,
        page: nextPage - 1,
        size: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 1688 详情任务失败');
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

  function openDialog() {
    setDialogCredentialId(filters.credentialId || credentialOptions[0]?.id);
    setDialogRawInput('');
    setDialogForceRefresh(false);
    setDialogOpen(true);
  }

  async function loadCardImportRows(nextPage = cardImportPage, nextPageSize = cardImportPageSize, nextKeyword = cardImportKeyword) {
    setCardImportLoading(true);
    try {
      const res = await alibaba1688CardLinksApi.list({
        keyword: nextKeyword.trim() || undefined,
        status: 0,
        page: nextPage - 1,
        size: nextPageSize,
      });
      setCardImportRows(Array.isArray(res.data.content) ? res.data.content : []);
      setCardImportTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载未处理卡片链接失败');
    } finally {
      setCardImportLoading(false);
    }
  }

  function openCardImportDialog() {
    setCardImportCredentialId(filters.credentialId || credentialOptions[0]?.id);
    setCardImportForceRefresh(false);
    setCardImportKeyword('');
    setCardImportSelectAll(false);
    setCardImportSelectedIds([]);
    setCardImportPage(1);
    setCardImportPageSize(10);
    setCardImportOpen(true);
    void loadCardImportRows(1, 10, '');
  }

  function showCreateSummary(result: Alibaba1688DetailTaskCreateResultVO) {
    modal.info({
      title: '1688 详情任务已下发',
      width: 520,
      content: (
        <div className="alibaba1688-detail-task-summary">
          <div>识别到链接：{result.parsedUrlCount}</div>
          <div>成功创建：{result.createdCount}</div>
          <div>已存在详情数据，跳过：{result.skippedExistingRecordCount}</div>
          <div>已有执行中/待执行任务，跳过：{result.skippedActiveTaskCount}</div>
          <div>无效链接：{result.invalidCount}</div>
        </div>
      ),
    });
  }

  function showCardImportSummary(result: Alibaba1688DetailTaskImportFromCardLinksResultVO) {
    modal.info({
      title: '1688 卡片链接导入完成',
      width: 520,
      content: (
        <div className="alibaba1688-detail-task-summary">
          <div>选中卡片链接：{result.selectedCount}</div>
          <div>可用未处理链接：{result.availableCount}</div>
          <div>成功创建任务：{result.createdCount}</div>
          <div>已存在详情数据，跳过：{result.skippedExistingRecordCount}</div>
          <div>已有执行中/待执行任务，跳过：{result.skippedActiveTaskCount}</div>
          <div>不可用/已处理，跳过：{result.skippedUnavailableCount}</div>
          <div>已回写为已处理：{result.processedCount}</div>
        </div>
      ),
    });
  }

  async function handleCreateDialog() {
    if (!dialogCredentialId) {
      message.warning('请先选择 1688 凭证');
      return;
    }
    if (!dialogRawInput.trim()) {
      message.warning('请先粘贴 1688 详情链接');
      return;
    }
    setCreating(true);
    try {
      const res = await alibaba1688DetailTasksApi.createDialog({
        credentialId: dialogCredentialId,
        rawInput: dialogRawInput,
        forceRefresh: dialogForceRefresh,
      });
      setDialogOpen(false);
      showCreateSummary(res.data);
      setPage(1);
      await load(1, pageSize, filters);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '下发 1688 详情任务失败');
    } finally {
      setCreating(false);
    }
  }

  async function handleImportFromCardLinks() {
    if (!cardImportCredentialId) {
      message.warning('请先选择 1688 凭证');
      return;
    }
    if (cardImportSelectAll && cardImportTotal === 0) {
      message.warning('当前筛选条件下没有可导入的卡片链接');
      return;
    }
    if (!cardImportSelectAll && cardImportSelectedIds.length === 0) {
      message.warning('请至少选择 1 条未处理卡片链接');
      return;
    }
    setCardImportSubmitting(true);
    try {
      const res = await alibaba1688DetailTasksApi.importFromCardLinks({
        credentialId: cardImportCredentialId,
        cardLinkIds: cardImportSelectAll ? undefined : cardImportSelectedIds,
        allMatching: cardImportSelectAll,
        keyword: cardImportSelectAll ? cardImportKeyword.trim() || undefined : undefined,
        status: 0,
        forceRefresh: cardImportForceRefresh,
      });
      setCardImportOpen(false);
      showCardImportSummary(res.data);
      setPage(1);
      await load(1, pageSize, filters);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '从卡片链接导入任务失败');
    } finally {
      setCardImportSubmitting(false);
    }
  }

  async function handleRetry(record: Alibaba1688DetailTaskVO) {
    setActionId(record.id);
    try {
      await alibaba1688DetailTasksApi.retry(record.id);
      message.success('任务已重新排队');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重试失败');
    } finally {
      setActionId(null);
    }
  }

  async function handleCancel(record: Alibaba1688DetailTaskVO) {
    setActionId(record.id);
    try {
      await alibaba1688DetailTasksApi.cancel(record.id);
      message.success('任务已取消');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '取消失败');
    } finally {
      setActionId(null);
    }
  }

  const columns: ColumnsType<Alibaba1688DetailTaskVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '商品',
      key: 'offer',
      width: 360,
      render: (_, record) => (
        <div className="alibaba1688-detail-task-cell">
          <Typography.Text strong copyable={{ text: record.offerId }}>
            {record.offerId}
          </Typography.Text>
          <a href={record.detailUrl} target="_blank" rel="noreferrer" className="alibaba1688-detail-task-link" title={record.detailUrl}>
            {record.detailUrl}
          </a>
          <Space size={6} wrap>
            {statusTag(record.status)}
            {record.detailRecordId ? <Tag color="green">已有详情数据 #{record.detailRecordId}</Tag> : null}
          </Space>
        </div>
      ),
    },
    {
      title: '凭证 / worker',
      key: 'worker',
      width: 220,
      render: (_, record) => (
        <div className="alibaba1688-detail-task-cell">
          <Typography.Text>{record.credentialName || '-'}</Typography.Text>
          <Typography.Text type="secondary">worker：{record.workerName || '-'}</Typography.Text>
          <Typography.Text type="secondary">尝试次数：{record.attemptCount ?? 0}</Typography.Text>
        </div>
      ),
    },
    {
      title: '时间',
      key: 'time',
      width: 190,
      render: (_, record) => (
        <div className="alibaba1688-detail-task-cell">
          <Typography.Text>创建：{formatDateTime(record.createdAt)}</Typography.Text>
          <Typography.Text type="secondary">开始：{formatDateTime(record.startedAt)}</Typography.Text>
          <Typography.Text type="secondary">结束：{formatDateTime(record.finishedAt)}</Typography.Text>
        </div>
      ),
    },
    {
      title: '结果',
      key: 'result',
      width: 300,
      render: (_, record) => (
        <Typography.Paragraph className="alibaba1688-detail-task-paragraph" ellipsis={{ rows: 3, tooltip: record.lastError || '' }}>
          {record.lastError || '-'}
        </Typography.Paragraph>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 170,
      render: (_, record) => (
        <Space wrap>
          <Button
            size="small"
            loading={actionId === record.id}
            onClick={() => void handleRetry(record)}
          >
            重新排队
          </Button>
          {record.status === 'PENDING' || record.status === 'RUNNING' ? (
            <Button
              size="small"
              danger
              loading={actionId === record.id}
              onClick={() => void handleCancel(record)}
            >
              取消
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  const cardImportColumns: ColumnsType<Alibaba1688CardLinkVO> = [
    {
      title: '商品',
      key: 'offer',
      width: 260,
      render: (_, record) => (
        <div className="alibaba1688-detail-task-cell">
          <Typography.Text strong copyable={{ text: record.offerId }}>
            {record.offerId}
          </Typography.Text>
          <Space size={6} wrap>
            <Tag color={record.type === 'ad' ? 'purple' : 'blue'}>{record.type || 'normal'}</Tag>
            {record.offerIdSource ? <Tag>{record.offerIdSource}</Tag> : null}
            {record.cardIndex ? <Tag>index {record.cardIndex}</Tag> : null}
          </Space>
        </div>
      ),
    },
    {
      title: '详情链接',
      key: 'detailUrl',
      width: 360,
      render: (_, record) => (
        record.detailUrl ? (
          <a
            href={record.detailUrl}
            target="_blank"
            rel="noreferrer"
            className="alibaba1688-detail-task-link"
            title={record.detailUrl}
          >
            {record.detailUrl}
          </a>
        ) : (
          <Typography.Text type="secondary">-</Typography.Text>
        )
      ),
    },
    {
      title: '渲染信息',
      key: 'render',
      width: 240,
      render: (_, record) => (
        <div className="alibaba1688-detail-task-cell">
          <Typography.Text ellipsis={{ tooltip: record.renderKey || '' }}>
            {record.renderKey || '-'}
          </Typography.Text>
          <Typography.Text type="secondary" ellipsis={{ tooltip: record.cardClass || '' }}>
            {record.cardClass || '-'}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '时间',
      key: 'time',
      width: 160,
      render: (_, record) => (
        <div className="alibaba1688-detail-task-cell">
          <Typography.Text>{formatDateTime(record.updatedAt)}</Typography.Text>
          <Typography.Text type="secondary">{formatDateTime(record.createdAt)}</Typography.Text>
        </div>
      ),
    },
  ];

  const cardImportRowSelection = {
    selectedRowKeys: cardImportSelectedIds,
    preserveSelectedRowKeys: true,
    onChange: (selectedRowKeys: Key[]) => {
      setCardImportSelectedIds(
        selectedRowKeys
          .map((item: Key) => Number(item))
          .filter((item: number) => Number.isFinite(item)),
      );
    },
  };

  const cardImportCanSubmit = Boolean(cardImportCredentialId)
    && (cardImportSelectAll ? cardImportTotal > 0 : cardImportSelectedIds.length > 0);

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message="1688 详情采集任务"
        description={credentialOptions.length > 0
          ? '这里故意做成更像“给 AI 发消息”的下发方式：选一个 1688 凭证，把详情链接整段贴进弹框里就行，后台会自动识别、去重并拆成任务。'
          : '当前还没有可用的 1688 凭证。请先到“1688凭证管理”里创建会话，并用 alibaba1688-playwright-worker 保存一次登录态。'}
      />

      <Card>
        <Space wrap>
          <Input
            value={filters.keyword}
            onChange={(e) => updateFilter('keyword', e.target.value)}
            allowClear
            placeholder="搜索 offerId / 链接 / worker / 错误信息"
            style={{ width: 300 }}
            onPressEnter={() => {
              setPage(1);
              void load(1, pageSize, filters);
            }}
          />
          <Select
            value={filters.credentialId}
            onChange={(value) => updateFilter('credentialId', value)}
            allowClear
            placeholder="1688 凭证"
            style={{ width: 220 }}
            options={credentialOptionItems}
          />
          <Select
            value={filters.status}
            onChange={(value) => updateFilter('status', value)}
            allowClear
            placeholder="任务状态"
            style={{ width: 150 }}
            options={[
              { value: 'PENDING', label: 'PENDING' },
              { value: 'RUNNING', label: 'RUNNING' },
              { value: 'SUCCEEDED', label: 'SUCCEEDED' },
              { value: 'FAILED', label: 'FAILED' },
              { value: 'CANCELLED', label: 'CANCELLED' },
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
          <Button onClick={openCardImportDialog} disabled={credentialOptions.length === 0}>
            从卡片链接导入
          </Button>
          <Button type="primary" ghost onClick={openDialog} disabled={credentialOptions.length === 0}>
            对话式下发任务
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<Alibaba1688DetailTaskVO>
          rowKey="id"
          className="alibaba1688-detail-task-table"
          columns={columns}
          dataSource={rows}
          loading={loading}
          tableLayout="fixed"
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
        title="从 1688 卡片链接导入详情任务"
        open={cardImportOpen}
        onCancel={() => setCardImportOpen(false)}
        onOk={() => void handleImportFromCardLinks()}
        okText="导入并标记已处理"
        confirmLoading={cardImportSubmitting}
        okButtonProps={{ disabled: !cardImportCanSubmit }}
        width={1080}
        destroyOnClose
      >
        <div className="alibaba1688-detail-task-compose">
          <Alert
            type="info"
            showIcon
            message="这里默认只展示未处理卡片链接"
            description="勾选后导入到 1688 详情采集任务，导入完成会把这些卡片链接直接回写成“已处理”。"
          />
          <div className="alibaba1688-detail-task-compose-meta">
            <Select
              value={cardImportCredentialId}
              onChange={setCardImportCredentialId}
              placeholder="先选 1 个 1688 凭证"
              style={{ width: 260 }}
              options={credentialOptionItems}
            />
            <Space size={16} wrap>
              <Checkbox checked={cardImportForceRefresh} onChange={(e) => setCardImportForceRefresh(e.target.checked)}>
                已有详情数据也重新采集
              </Checkbox>
              <Checkbox
                checked={cardImportSelectAll}
                onChange={(e) => {
                  const checked = e.target.checked;
                  setCardImportSelectAll(checked);
                  if (checked) {
                    setCardImportSelectedIds([]);
                  }
                }}
              >
                全部数据
              </Checkbox>
            </Space>
          </div>
          {cardImportSelectAll ? (
            <Alert
              type="success"
              showIcon
              message="已启用“全部数据”模式"
              description="提交时会直接导入当前筛选条件下的全部未处理卡片链接，不需要再勾选表格复选框。"
            />
          ) : null}
          <Space wrap>
            <Input
              value={cardImportKeyword}
              onChange={(e) => setCardImportKeyword(e.target.value)}
              allowClear
              placeholder="搜索 offerId / 链接 / renderKey"
              style={{ width: 320 }}
              onPressEnter={() => {
                setCardImportPage(1);
                void loadCardImportRows(1, cardImportPageSize, cardImportKeyword);
              }}
            />
            <Button
              type="primary"
              loading={cardImportLoading}
              onClick={() => {
                setCardImportPage(1);
                void loadCardImportRows(1, cardImportPageSize, cardImportKeyword);
              }}
            >
              查询
            </Button>
            <Button
              onClick={() => {
                setCardImportKeyword('');
                setCardImportPage(1);
                void loadCardImportRows(1, cardImportPageSize, '');
              }}
            >
              重置
            </Button>
            <Typography.Text type="secondary">
              {cardImportSelectAll
                ? `将导入当前条件下全部数据，共 ${cardImportTotal} 条`
                : `已选择 ${cardImportSelectedIds.length} 条`}
            </Typography.Text>
          </Space>
          <Table<Alibaba1688CardLinkVO>
            rowKey="id"
            size="small"
            columns={cardImportColumns}
            dataSource={cardImportRows}
            loading={cardImportLoading}
            rowSelection={cardImportSelectAll ? undefined : cardImportRowSelection}
            tableLayout="fixed"
            scroll={{ x: 1050, y: 420 }}
            pagination={{
              current: cardImportPage,
              pageSize: cardImportPageSize,
              total: cardImportTotal,
              showSizeChanger: true,
              showTotal: (count) => `共 ${count} 条未处理链接`,
            }}
            onChange={(pagination: TablePaginationConfig) => {
              const nextPage = pagination.current || 1;
              const nextPageSize = pagination.pageSize || 10;
              setCardImportPage(nextPage);
              setCardImportPageSize(nextPageSize);
              void loadCardImportRows(nextPage, nextPageSize, cardImportKeyword);
            }}
          />
        </div>
      </Modal>

      <Modal
        title="像发消息一样下发 1688 详情任务"
        open={dialogOpen}
        onCancel={() => setDialogOpen(false)}
        onOk={() => void handleCreateDialog()}
        okText="创建任务"
        confirmLoading={creating}
        width={760}
        destroyOnClose
      >
        <div className="alibaba1688-detail-task-compose">
          {credentialOptions.length === 0 ? (
            <Alert
              type="warning"
              showIcon
              message="当前没有可用的 1688 凭证"
              description="请先去“1688凭证管理”创建会话并保存登录态，任务下发才会真正可用。"
            />
          ) : null}
          <div className="alibaba1688-detail-task-compose-meta">
            <Select
              value={dialogCredentialId}
              onChange={setDialogCredentialId}
              placeholder="先选 1 个 1688 凭证"
              style={{ width: 260 }}
              options={credentialOptionItems}
            />
            <Checkbox checked={dialogForceRefresh} onChange={(e) => setDialogForceRefresh(e.target.checked)}>
              已有详情数据也重新采集
            </Checkbox>
          </div>
          <Input.TextArea
            value={dialogRawInput}
            onChange={(e) => setDialogRawInput(e.target.value)}
            className="alibaba1688-detail-task-compose-input"
            autoSize={{ minRows: 10, maxRows: 18 }}
            placeholder={[
              '把 1688 详情链接像发消息一样粘到这里，我会自动识别。',
              '',
              '支持：',
              '1. 一行一个链接',
              '2. 一整段带文字的说明，里面夹着多个链接',
              '',
              '例如：',
              '这批先采一下',
              'https://detail.1688.com/offer/842990748379.html',
              'https://detail.m.1688.com/page/index.html?offerId=859676694004',
            ].join('\n')}
          />
        </div>
      </Modal>
    </Space>
  );
};

export default Alibaba1688DetailTasksPage;
