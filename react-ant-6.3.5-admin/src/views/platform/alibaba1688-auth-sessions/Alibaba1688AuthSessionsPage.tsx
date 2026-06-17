import { Alert, App, Button, Card, Form, Input, Modal, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { alibaba1688AuthSessionsApi } from '@/api/alibaba1688AuthSessions';
import type { Alibaba1688AuthSessionPayload, Alibaba1688AuthSessionVO } from '@/types/api';
import { formatDateTime } from '@/utils/format';
import './Alibaba1688AuthSessionsPage.css';

interface Filters {
  keyword: string;
  enabled?: boolean;
  status?: string;
}

const initialFilters: Filters = {
  keyword: '',
  enabled: undefined,
  status: undefined,
};

const statusColorMap: Record<string, string> = {
  ACTIVE: 'green',
  EXPIRED: 'orange',
  EMPTY: 'default',
  ERROR: 'red',
};

function statusTag(status?: string | null) {
  const text = status || '-';
  return <Tag color={statusColorMap[text] || 'blue'}>{text}</Tag>;
}

const Alibaba1688AuthSessionsPage = () => {
  const { message } = App.useApp();
  const [form] = Form.useForm<Alibaba1688AuthSessionPayload>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [rows, setRows] = useState<Alibaba1688AuthSessionVO[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const [editingRecord, setEditingRecord] = useState<Alibaba1688AuthSessionVO | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [switchingId, setSwitchingId] = useState<number | null>(null);

  async function load(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true);
    try {
      const res = await alibaba1688AuthSessionsApi.list({
        keyword: nextFilters.keyword.trim() || undefined,
        enabled: nextFilters.enabled,
        status: nextFilters.status,
        page: nextPage - 1,
        size: nextPageSize,
      });
      setRows(Array.isArray(res.data.content) ? res.data.content : []);
      setTotal(Number(res.data.totalElements || 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 1688 凭证失败');
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

  function openCreateModal() {
    setEditingRecord(null);
    form.setFieldsValue({
      sessionName: '',
      remark: '',
      enabled: true,
    });
    setModalOpen(true);
  }

  function openEditModal(record: Alibaba1688AuthSessionVO) {
    setEditingRecord(record);
    form.setFieldsValue({
      sessionName: record.sessionName,
      remark: record.remark,
      enabled: record.enabled,
    });
    setModalOpen(true);
  }

  async function handleSave() {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editingRecord) {
        await alibaba1688AuthSessionsApi.update(editingRecord.id, values);
        message.success('1688 凭证已更新');
      } else {
        await alibaba1688AuthSessionsApi.create(values);
        message.success('1688 凭证已创建');
      }
      setModalOpen(false);
      await load(editingRecord ? page : 1, pageSize, filters);
      if (!editingRecord) {
        setPage(1);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  async function handleToggle(record: Alibaba1688AuthSessionVO) {
    setSwitchingId(record.id);
    try {
      await alibaba1688AuthSessionsApi.toggleEnabled(record.id, !record.enabled);
      message.success(record.enabled ? '已停用' : '已启用');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '切换状态失败');
    } finally {
      setSwitchingId(null);
    }
  }

  const columns: ColumnsType<Alibaba1688AuthSessionVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '会话',
      key: 'session',
      width: 250,
      render: (_, record) => (
        <div className="alibaba1688-auth-session-cell">
          <Typography.Text strong>{record.sessionName}</Typography.Text>
          <Space size={6} wrap>
            {statusTag(record.status)}
            <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? '启用中' : '已停用'}</Tag>
            <Tag>{record.hasStorageState ? '已保存登录态' : '未保存登录态'}</Tag>
          </Space>
        </div>
      ),
    },
    {
      title: '账号信息',
      key: 'account',
      width: 260,
      render: (_, record) => (
        <div className="alibaba1688-auth-session-cell">
          <Typography.Text>{record.accountNick || '-'}</Typography.Text>
          <Typography.Text type="secondary">memberId：{record.memberId || '-'}</Typography.Text>
          <Typography.Text type="secondary" title={record.homeUrl || ''}>
            {record.homeUrl || '-'}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      width: 240,
      render: (value: string | null) => (
        <Typography.Paragraph className="alibaba1688-auth-session-remark" ellipsis={{ rows: 2, tooltip: value || '' }}>
          {value || '-'}
        </Typography.Paragraph>
      ),
    },
    {
      title: '登录态',
      key: 'storage',
      width: 180,
      render: (_, record) => (
        <div className="alibaba1688-auth-session-cell">
          <Typography.Text>{record.hasStorageState ? '已入库' : '未入库'}</Typography.Text>
          <Typography.Text type="secondary">更新：{formatDateTime(record.storageStateUpdatedAt)}</Typography.Text>
          <Typography.Text type="secondary">验证：{formatDateTime(record.lastVerifiedAt)}</Typography.Text>
        </div>
      ),
    },
    {
      title: '错误',
      dataIndex: 'lastError',
      key: 'lastError',
      width: 240,
      render: (value: string | null) => (
        <Typography.Paragraph className="alibaba1688-auth-session-remark" ellipsis={{ rows: 2, tooltip: value || '' }}>
          {value || '-'}
        </Typography.Paragraph>
      ),
    },
    {
      title: '更新时间',
      key: 'time',
      width: 180,
      render: (_, record) => (
        <div className="alibaba1688-auth-session-cell">
          <Typography.Text>{formatDateTime(record.updatedAt)}</Typography.Text>
          <Typography.Text type="secondary">{formatDateTime(record.createdAt)}</Typography.Text>
        </div>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 190,
      render: (_, record) => (
        <Space wrap>
          <Button size="small" onClick={() => openEditModal(record)}>
            编辑
          </Button>
          <Button
            size="small"
            loading={switchingId === record.id}
            type={record.enabled ? 'default' : 'primary'}
            onClick={() => void handleToggle(record)}
          >
            {record.enabled ? '停用' : '启用'}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message="1688 凭证管理"
        description={(
          <span>
            先在这里维护一个会话名，再使用本地 <Typography.Text code>alibaba1688-playwright-worker</Typography.Text>
            {' '}登录 1688 并把 storageState 回写到后台。后面的详情采集任务会直接绑定这里的凭证。
          </span>
        )}
      />

      <Card>
        <Space wrap>
          <Input
            value={filters.keyword}
            onChange={(e) => updateFilter('keyword', e.target.value)}
            allowClear
            placeholder="搜索会话名 / 昵称 / memberId / 备注"
            style={{ width: 280 }}
            onPressEnter={() => {
              setPage(1);
              void load(1, pageSize, filters);
            }}
          />
          <Select
            value={filters.enabled}
            onChange={(value) => updateFilter('enabled', value)}
            allowClear
            placeholder="启用状态"
            style={{ width: 140 }}
            options={[
              { value: true, label: '启用中' },
              { value: false, label: '已停用' },
            ]}
          />
          <Select
            value={filters.status}
            onChange={(value) => updateFilter('status', value)}
            allowClear
            placeholder="登录态状态"
            style={{ width: 160 }}
            options={[
              { value: 'ACTIVE', label: 'ACTIVE' },
              { value: 'EXPIRED', label: 'EXPIRED' },
              { value: 'EMPTY', label: 'EMPTY' },
              { value: 'ERROR', label: 'ERROR' },
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
          <Button type="primary" ghost onClick={openCreateModal}>
            新建凭证
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<Alibaba1688AuthSessionVO>
          rowKey="id"
          className="alibaba1688-auth-session-table"
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
        title={editingRecord ? '编辑 1688 凭证' : '新建 1688 凭证'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="会话名称"
            name="sessionName"
            rules={[{ required: true, message: '请输入会话名称' }]}
          >
            <Input placeholder="例如：1688主账号-采购A" maxLength={128} />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea
              placeholder="可以写账号用途、负责人、登录提醒等"
              autoSize={{ minRows: 3, maxRows: 6 }}
              maxLength={1000}
            />
          </Form.Item>
          <Form.Item label="默认启用" name="enabled" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default Alibaba1688AuthSessionsPage;
