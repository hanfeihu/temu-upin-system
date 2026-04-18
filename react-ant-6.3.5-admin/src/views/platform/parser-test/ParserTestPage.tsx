import { App, Button, Card, Form, Input, Space, Tabs, Tag, Typography } from 'antd';
import { useState } from 'react';
import { parserTestApi } from '@/api/parserTest';
import type { ParserTestRunVO } from '@/types/api';
import { prettyJson } from '@/utils/format';

const ParserTestPage = () => {
  const { message } = App.useApp();
  const [activeKey, setActiveKey] = useState<'alibaba1688' | 'temu'>('alibaba1688');
  const [html, setHtml] = useState('');
  const [runId, setRunId] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<ParserTestRunVO | null>(null);

  async function executeParse() {
    if (!html.trim()) {
      message.error('请输入 HTML');
      return;
    }
    setLoading(true);
    try {
      const res =
        activeKey === 'alibaba1688'
          ? await parserTestApi.parseAlibaba1688(html)
          : await parserTestApi.parseTemu(html);
      setResult(res.data);
      setRunId(res.data.runId || '');
      message.success('解析完成');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '解析失败');
    } finally {
      setLoading(false);
    }
  }

  async function queryRun() {
    if (!runId.trim()) {
      message.error('请输入 runId');
      return;
    }
    setLoading(true);
    try {
      const res = await parserTestApi.getRun(runId.trim());
      setResult(res.data);
      message.success('已加载运行记录');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Tabs
            activeKey={activeKey}
            onChange={(key) => setActiveKey(key as 'alibaba1688' | 'temu')}
            items={[
              { key: 'alibaba1688', label: '1688 解析器' },
              { key: 'temu', label: 'TEMU 解析器' },
            ]}
          />
          <Form layout="vertical">
            <Form.Item label="HTML 原文" required>
              <Input.TextArea value={html} onChange={(e) => setHtml(e.target.value)} rows={14} placeholder="粘贴原始 DOM / HTML" />
            </Form.Item>
          </Form>
          <Space wrap>
            <Button type="primary" loading={loading} onClick={() => void executeParse()}>
              开始解析
            </Button>
            <Input value={runId} onChange={(e) => setRunId(e.target.value)} placeholder="通过 runId 查询历史" style={{ width: 220 }} allowClear />
            <Button onClick={() => void queryRun()} loading={loading}>
              查询运行记录
            </Button>
          </Space>
        </Space>
      </Card>

      <Card title="解析结果">
        {result ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Space wrap>
              <Tag color={result.success ? 'green' : 'red'}>{result.success ? 'SUCCESS' : 'FAILED'}</Tag>
              <Tag>{result.parserType || '-'}</Tag>
              <Tag>{result.runId || '-'}</Tag>
            </Space>
            {result.errorMessage ? <Typography.Text type="danger">{result.errorMessage}</Typography.Text> : null}
            <pre style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{prettyJson(result.result)}</pre>
          </Space>
        ) : (
          <Typography.Text type="secondary">这里会展示解析响应、运行结果和异常信息。</Typography.Text>
        )}
      </Card>
    </Space>
  );
};

export default ParserTestPage;
