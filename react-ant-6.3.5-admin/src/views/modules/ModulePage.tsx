import { Alert, Card, Col, Descriptions, Row, Space, Tag, Typography } from 'antd';

interface ModulePageProps {
  title: string;
  description: string;
  path: string;
}

const ModulePage = ({ title, description, path }: ModulePageProps) => {
  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message="React + Ant Design 6.3.5 新后台骨架"
        description="这一版已经切到参考项目的 React + Ant 结构，当前页面先用模块占位承接，后续可以按页面逐块迁移原有业务。"
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={16}>
          <Card title={title}>
            <Typography.Paragraph style={{ marginBottom: 16 }}>
              {description}
            </Typography.Paragraph>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="当前路由">{path}</Descriptions.Item>
              <Descriptions.Item label="迁移状态">
                <Tag color="processing">骨架已就位</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="布局规范">
                React 18 + antd 6.3.5 + Vite 6
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} xl={8}>
          <Card title="接下来可继续做">
            <Space direction="vertical" size={12}>
              <Typography.Text>1. 把当前 Vue 页面按优先级迁移到 React。</Typography.Text>
              <Typography.Text>2. 把通用查询表单、表格和详情抽成复用组件。</Typography.Text>
              <Typography.Text>3. 逐步接入真实 API、表单校验和交互弹窗。</Typography.Text>
            </Space>
          </Card>
        </Col>
      </Row>
    </Space>
  );
};

export default ModulePage;
