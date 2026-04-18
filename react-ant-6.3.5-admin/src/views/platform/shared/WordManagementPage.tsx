import { App, Button, Card, Form, Input, Modal, Space, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { ocrApi } from '@/api/ocr';
import type { WordVO } from '@/types/api';
import { formatDateTime } from '@/utils/format';

interface WordManagementPageProps {
  mode: 'ocr' | 'title';
}

const WordManagementPage = ({ mode }: WordManagementPageProps) => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<WordVO[]>([]);
  const [keyword, setKeyword] = useState('');
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<WordVO | null>(null);
  const [word, setWord] = useState('');

  async function load() {
    setLoading(true);
    try {
      const res = mode === 'ocr' ? await ocrApi.listFilterWords() : await ocrApi.listTitleFilterWords();
      const list = Array.isArray(res.data) ? res.data : [];
      setRows(list);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [mode]);

  function openCreate() {
    setEditing(null);
    setWord('');
    setEditOpen(true);
  }

  function openEdit(record: WordVO) {
    setEditing(record);
    setWord(record.word || '');
    setEditOpen(true);
  }

  async function save() {
    const normalized = word.trim();
    if (!normalized) {
      message.error('请输入词条');
      return;
    }

    setSaving(true);
    try {
      if (editing) {
        if (mode === 'ocr') {
          await ocrApi.updateFilterWord(editing.id, normalized);
        } else {
          await ocrApi.updateTitleFilterWord(editing.id, normalized);
        }
        message.success('已更新');
      } else {
        if (mode === 'ocr') {
          await ocrApi.createFilterWord(normalized);
        } else {
          await ocrApi.createTitleFilterWord(normalized);
        }
        message.success('已新增');
      }
      setEditOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  function remove(record: WordVO) {
    Modal.confirm({
      title: '删除词条？',
      content: `确认删除“${record.word}”吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          if (mode === 'ocr') {
            await ocrApi.deleteFilterWord(record.id);
          } else {
            await ocrApi.deleteTitleFilterWord(record.id);
          }
          message.success('已删除');
          await load();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        }
      },
    });
  }

  const filteredRows = rows.filter((item) => !keyword.trim() || item.word.toLowerCase().includes(keyword.trim().toLowerCase()));

  const columns: ColumnsType<WordVO> = [
    { title: '词条', dataIndex: 'word', key: 'word' },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (value: WordVO['createdAt']) => formatDateTime(value),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      render: (value: WordVO['updatedAt']) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button size="small" danger onClick={() => remove(record)}>
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
          <Input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="按词条搜索"
            style={{ width: 260 }}
            allowClear
          />
          <Button loading={loading} onClick={() => void load()}>
            刷新
          </Button>
          <Button type="primary" onClick={openCreate}>
            新增词条
          </Button>
        </Space>
      </Card>

      <Card>
        <Table<WordVO> rowKey="id" loading={loading} columns={columns} dataSource={filteredRows} pagination={false} />
      </Card>

      <Modal
        open={editOpen}
        title={editing ? '编辑词条' : '新增词条'}
        confirmLoading={saving}
        onOk={() => void save()}
        onCancel={() => setEditOpen(false)}
      >
        <Form layout="vertical">
          <Form.Item label="词条" required>
            <Input value={word} onChange={(e) => setWord(e.target.value)} maxLength={256} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default WordManagementPage;
