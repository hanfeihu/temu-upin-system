import { App, Button, Card, Form, Input, Modal, Space, Table, Tooltip } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { bizLogsApi } from '@/api/bizLogs';
import type { BizLogVO } from '@/types/api';

function formatDateTime(value: string | number[] | null | undefined) {
  if (!value) return '';
  if (Array.isArray(value) && value.length >= 6) {
    const [year, month, day, hour, minute, second] = value;
    const pad = (input: number | undefined) => String(input || 0).padStart(2, '0');
    return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}`;
  }
  return String(value);
}

const BizLogsPage = () => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<BizLogVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [bizName, setBizName] = useState('');
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingBizName, setEditingBizName] = useState('');
  const [editingContent, setEditingContent] = useState('');

  async function fetchList(nextPage = page, nextPageSize = pageSize, nextBizName = bizName) {
    setLoading(true);
    try {
      const res = await bizLogsApi.list({
        bizName: nextBizName || undefined,
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
    void fetchList(1, 20, '');
  }, []);

  function openCreate() {
    setEditingId(null);
    setEditingBizName('');
    setEditingContent('');
    setEditOpen(true);
  }

  function openEdit(record: BizLogVO) {
    setEditingId(record.id);
    setEditingBizName(record.bizName || '');
    setEditingContent(record.content || '');
    setEditOpen(true);
  }

  async function save() {
    const payload = {
      bizName: editingBizName.trim(),
      content: editingContent,
    };

    if (!payload.bizName) {
      message.error('请输入业务名称');
      return;
    }
    if (!payload.content) {
      message.error('请输入日志内容');
      return;
    }

    setSaving(true);
    try {
      if (editingId) {
        await bizLogsApi.update(editingId, payload);
      } else {
        await bizLogsApi.create(payload);
      }
      message.success('已保存');
      setEditOpen(false);
      await fetchList();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  function deleteLog(record: BizLogVO) {
    Modal.confirm({
      title: '删除日志？',
      content: '删除后不可恢复。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await bizLogsApi.delete(record.id);
          message.success('已删除');
          await fetchList();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        }
      },
    });
  }

  const columns: ColumnsType<BizLogVO> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 90 },
    { title: '业务名称', dataIndex: 'bizName', key: 'bizName', width: 180 },
    {
      title: '日志内容',
      key: 'content',
      render: (_, record) => (
        <Tooltip title={record.content} placement="top">
          <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{record.content}</div>
        </Tooltip>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 190,
      render: (value) => formatDateTime(value),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 190,
      render: (value) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button size="small" danger onClick={() => deleteLog(record)}>
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
          <Input value={bizName} onChange={(e) => setBizName(e.target.value)} placeholder="业务名称模糊匹配" style={{ width: 260 }} allowClear onPressEnter={() => {
            setPage(1);
            void fetchList(1, pageSize, bizName);
          }} />
          <Button type="primary" loading={loading} onClick={() => {
            setPage(1);
            void fetchList(1, pageSize, bizName);
          }}>
            查询
          </Button>
          <Button onClick={() => {
            setBizName('');
            setPage(1);
            void fetchList(1, pageSize, '');
          }}>
            重置
          </Button>
          <Button type="primary" ghost onClick={openCreate}>
            新增日志
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<BizLogVO>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          tableLayout="fixed"
          scroll={{ x: 980 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
            pageSizeOptions: ['10', '20', '50', '100'],
          }}
          onChange={(pagination: TablePaginationConfig) => {
            const nextPage = pagination.current || 1;
            const nextPageSize = pagination.pageSize || 20;
            setPage(nextPage);
            setPageSize(nextPageSize);
            void fetchList(nextPage, nextPageSize, bizName);
          }}
        />
      </Card>

      <Modal
        open={editOpen}
        title={editingId ? '编辑日志' : '新增日志'}
        confirmLoading={saving}
        width={780}
        onOk={() => void save()}
        onCancel={() => setEditOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="业务名称" required>
            <Input value={editingBizName} onChange={(e) => setEditingBizName(e.target.value)} allowClear placeholder="例如: OCR / 发布 / 导入" />
          </Form.Item>
          <Form.Item label="日志内容" required>
            <Input.TextArea value={editingContent} onChange={(e) => setEditingContent(e.target.value)} rows={10} placeholder="支持大文本" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default BizLogsPage;
