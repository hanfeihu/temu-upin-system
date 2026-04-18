import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { App, Button, Card, Flex, Form, Input, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { defaultRoute } from '@/config/navigation';
import { useAuth } from '@/context/AuthContext';

interface LoginFormValues {
  username: string;
  password: string;
}

function getErrorMessage(error: unknown) {
  if (error instanceof Error && error.message) {
    return error.message;
  }

  return '登录失败，请稍后重试';
}

const Login = () => {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const { authenticated, login } = useAuth();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (authenticated) {
      navigate(defaultRoute, { replace: true });
    }
  }, [authenticated, navigate]);

  const handleFinish = async (values: LoginFormValues) => {
    setLoading(true);
    try {
      await login(values.username, values.password);
      message.success('登录成功');
      navigate(defaultRoute, { replace: true });
    } catch (error) {
      message.error(getErrorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Flex justify="center" align="center" style={{ minHeight: '100vh', background: '#f0f2f5', padding: 24 }}>
      <Card style={{ width: 420 }}>
        <Flex vertical align="center" gap={12} style={{ marginBottom: 24 }}>
          <Typography.Title level={3} style={{ margin: 0 }}>
            TMINOS
          </Typography.Title>
          <Typography.Text type="secondary">TEMU 上品系统后台管理端</Typography.Text>
        </Flex>

        <Form<LoginFormValues> layout="vertical" onFinish={handleFinish} autoComplete="off">
          <Form.Item
            label="账号"
            name="username"
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input size="large" prefix={<UserOutlined />} placeholder="请输入后台账号" />
          </Form.Item>

          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password size="large" prefix={<LockOutlined />} placeholder="请输入后台密码" />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" size="large" block loading={loading}>
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </Flex>
  );
};

export default Login;
