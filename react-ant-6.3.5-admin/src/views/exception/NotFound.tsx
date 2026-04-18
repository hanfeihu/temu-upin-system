import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';
import { defaultRoute } from '@/config/navigation';

const NotFound = () => {
  const navigate = useNavigate();

  return (
    <Result
      status="404"
      title="404"
      subTitle="页面不存在或还没有迁移到新的 React 管理端。"
      extra={
        <Button type="primary" onClick={() => navigate(defaultRoute, { replace: true })}>
          返回首页
        </Button>
      }
    />
  );
};

export default NotFound;
