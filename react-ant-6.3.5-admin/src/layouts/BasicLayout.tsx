import {
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Dropdown, Flex, Layout, Menu, Typography, theme } from 'antd';
import { useEffect, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  defaultRoute,
  getMatchedRouteMeta,
  getOpenKeysForPath,
  getSelectedMenuKey,
  menuItems,
} from '@/config/navigation';
import { useAuth } from '@/context/AuthContext';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const BasicLayout = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const [openKeys, setOpenKeys] = useState<string[]>(() => getOpenKeysForPath(location.pathname));
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const matchedRoute = getMatchedRouteMeta(location.pathname);
  const selectedMenuKey = getSelectedMenuKey(location.pathname);

  useEffect(() => {
    if (collapsed) {
      setOpenKeys([]);
      return;
    }

    setOpenKeys((current) => {
      const nextKeys = getOpenKeysForPath(location.pathname);
      return Array.from(new Set([...current, ...nextKeys]));
    });
  }, [collapsed, location.pathname]);

  const userMenuItems = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider trigger={null} collapsible collapsed={collapsed} width={200}>
        <Flex
          align="center"
          justify="center"
          gap={10}
          style={{ height: 64, padding: '0 16px', cursor: 'pointer' }}
          onClick={() => navigate(defaultRoute)}
        >
          <img
            src="/system-logo.png"
            alt="TMINOS"
            style={{
              height: 34,
              width: 34,
              objectFit: 'cover',
              borderRadius: 10,
              flexShrink: 0,
            }}
          />
          {!collapsed && (
            <Text strong style={{ color: '#ffffff', fontSize: 16, whiteSpace: 'nowrap' }}>
              TMINOS
            </Text>
          )}
        </Flex>

        <Menu
          theme="dark"
          mode="inline"
          items={menuItems}
          selectedKeys={selectedMenuKey ? [selectedMenuKey] : []}
          openKeys={collapsed ? [] : openKeys}
          onOpenChange={(keys) => setOpenKeys(keys as string[])}
          onClick={({ key }) => navigate(String(key))}
          style={{ height: 'calc(100vh - 64px)', overflowY: 'auto' }}
        />
      </Sider>

      <Layout>
        <Header style={{ padding: 0, background: colorBgContainer }}>
          <Flex justify="space-between" align="center" style={{ height: '100%', paddingRight: 24 }}>
            <Flex align="center">
              <div
                style={{ padding: '0 24px', fontSize: 18, cursor: 'pointer' }}
                onClick={() => setCollapsed((value) => !value)}
              >
                {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              </div>
              <Text strong style={{ fontSize: 15 }}>
                {matchedRoute?.title || 'TMINOS'}
              </Text>
            </Flex>

            <Dropdown
              menu={{
                items: userMenuItems,
                onClick: ({ key }) => {
                  if (key === 'logout') {
                    logout();
                    navigate('/login', { replace: true });
                  }
                },
              }}
            >
              <Flex align="center" gap={8} style={{ cursor: 'pointer' }}>
                <Avatar size="small" icon={<UserOutlined />} />
                <Text>{user?.displayName || user?.username || '未登录'}</Text>
              </Flex>
            </Dropdown>
          </Flex>
        </Header>

        <Content
          style={{
            margin: '24px 16px',
            padding: 24,
            minHeight: 280,
            background: colorBgContainer,
            borderRadius: borderRadiusLG,
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default BasicLayout;
