import type { ItemType } from 'antd/es/menu/interface';
import { matchPath } from 'react-router-dom';
import {
  ApiOutlined,
  AuditOutlined,
  CheckCircleOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  FilterOutlined,
  FontColorsOutlined,
  GatewayOutlined,
  HistoryOutlined,
  InboxOutlined,
  NodeIndexOutlined,
  ProfileOutlined,
  RobotOutlined,
  ScanOutlined,
  SettingOutlined,
  ShopOutlined,
  SlidersOutlined,
  SyncOutlined,
  TagsOutlined,
  TranslationOutlined,
  TrophyOutlined,
  ToolOutlined,
  RiseOutlined,
} from '@ant-design/icons';
import type { AppNavItem, AppRouteMeta } from '@/types/navigation';

export const defaultRoute = '/platform/product-collections';

const navigationTree: AppNavItem[] = [
  {
    key: '/platform/product-collections',
    path: '/platform/product-collections',
    label: '采集商品库',
    description: '采集商品列表、过滤与归集入口。',
    icon: <InboxOutlined />,
  },
  {
    key: 'group-product',
    label: '商品中心',
    icon: <DatabaseOutlined />,
    children: [
      {
        key: '/platform/product-drafts',
        path: '/platform/product-drafts',
        label: '商品草稿库',
        description: '商品草稿、待整理数据与店铺归属。',
        icon: <ProfileOutlined />,
      },
      {
        key: '/platform/publish-logs',
        path: '/platform/publish-logs',
        label: '发布日志',
        description: '商品发布记录与失败原因排查。',
        icon: <FileTextOutlined />,
      },
      {
        key: '/platform/publish-success-cases',
        path: '/platform/publish-success-cases',
        label: '发布成功案例',
        description: '成功发布样本与对比分析。',
        icon: <CheckCircleOutlined />,
      },
      {
        key: '/platform/temu-auto-publish-logs',
        path: '/platform/temu-auto-publish-logs',
        label: '自动发布日志',
        description: '自动化执行期间的发布处理日志。',
        icon: <HistoryOutlined />,
      },
    ],
  },
  {
    key: 'group-sync',
    label: '数据同步',
    icon: <SyncOutlined />,
    children: [
      {
        key: '/platform/sync-config',
        path: '/platform/sync-config',
        label: '同步配置',
        description: '同步任务基础配置与策略。',
        icon: <SlidersOutlined />,
      },
      {
        key: '/platform/sync-tasks',
        path: '/platform/sync-tasks',
        label: '同步任务',
        description: '同步执行记录、状态与结果。',
        icon: <ProfileOutlined />,
      },
      {
        key: '/platform/sync-goods',
        path: '/platform/sync-goods',
        label: 'TEMU 商品数据',
        description: '来自平台的商品明细与同步结果。',
        icon: <DeploymentUnitOutlined />,
      },
      {
        key: '/platform/temu-orders',
        path: '/platform/temu-orders',
        label: 'TEMU 订单',
        description: '订单同步、SKU 关联和物流分析入口。',
        icon: <AuditOutlined />,
      },
      {
        key: '/platform/temu-order-aftersales',
        path: '/platform/temu-order-aftersales',
        label: 'TEMU 售后',
        description: '售后单同步、状态跟踪和问题归因。',
        icon: <HistoryOutlined />,
      },
      {
        key: '/platform/sync-activity',
        path: '/platform/sync-activity',
        label: '活动报名',
        description: '活动报名列表与回写数据。',
        icon: <TrophyOutlined />,
      },
    ],
  },
  {
    key: '/platform/sync-price-review',
    path: '/platform/sync-price-review',
    label: '核价单管理',
    description: '平台核价单列表、建议价与审核状态。',
    icon: <AuditOutlined />,
  },
  {
    key: '/platform/sync-price-adjust',
    path: '/platform/sync-price-adjust',
    label: '调价单管理',
    description: '调价单处理、跟踪与同步。',
    icon: <RiseOutlined />,
  },
  {
    key: 'group-temu',
    label: 'TEMU 配置',
    icon: <ShopOutlined />,
    children: [
      {
        key: '/platform/temu-apps',
        path: '/platform/temu-apps',
        label: 'TEMU 应用管理',
        description: '应用信息、授权配置与状态。',
        icon: <ApiOutlined />,
      },
      {
        key: '/platform/temu-shops',
        path: '/platform/temu-shops',
        label: 'TEMU 店铺管理',
        description: '店铺列表、店铺配置与凭证。',
        icon: <ShopOutlined />,
      },
      {
        key: '/platform/temu-attr-rules',
        path: '/platform/temu-attr-rules',
        label: '货品属性规则',
        description: '货品属性映射与规则维护。',
        icon: <TagsOutlined />,
      },
      {
        key: '/platform/spec-mappings/parent-spec-mappings',
        path: '/platform/spec-mappings/parent-spec-mappings',
        label: '父规格映射表',
        description: '父规格映射维护与排错入口。',
        icon: <NodeIndexOutlined />,
      },
    ],
  },
  {
    key: 'group-ai-tools',
    label: 'AI 与内容',
    icon: <RobotOutlined />,
    children: [
      {
        key: '/platform/temu-attr-ai-fill',
        path: '/platform/temu-attr-ai-fill',
        label: '类目属性AI填写',
        description: 'AI 辅助类目属性生成与补全。',
        icon: <RobotOutlined />,
      },
      {
        key: '/platform/temu-title-optimizer',
        path: '/platform/temu-title-optimizer',
        label: 'TEMU 标题优化',
        description: '标题优化、关键词生成与对比。',
        icon: <FontColorsOutlined />,
      },
      {
        key: '/platform/image-translate-records',
        path: '/platform/image-translate-records',
        label: '翻译记录',
        description: '图片翻译任务与处理结果。',
        icon: <TranslationOutlined />,
      },
      {
        key: '/platform/ocr-tasks',
        path: '/platform/ocr-tasks',
        label: 'OCR任务',
        description: 'OCR 识别任务与处理状态。',
        icon: <ScanOutlined />,
      },
      {
        key: '/platform/ocr-filter-words',
        path: '/platform/ocr-filter-words',
        label: '图片过滤词',
        description: '图片过滤词、敏感词与规则维护。',
        icon: <FilterOutlined />,
      },
      {
        key: '/platform/title-filter-words',
        path: '/platform/title-filter-words',
        label: '标题过滤词',
        description: '标题过滤策略与敏感词维护。',
        icon: <FilterOutlined />,
      },
      {
        key: '/platform/parser-test',
        path: '/platform/parser-test',
        label: '解析器测试',
        description: '解析器调试与测试入口。',
        icon: <ToolOutlined />,
      },
    ],
  },
  {
    key: 'group-system',
    label: '系统与日志',
    icon: <SettingOutlined />,
    children: [
      {
        key: '/platform/ai-channels',
        path: '/platform/ai-channels',
        label: 'AI 渠道管理',
        description: 'AI 渠道、模型与调用配置。',
        icon: <GatewayOutlined />,
      },
      {
        key: '/platform/post-import-logs',
        path: '/platform/post-import-logs',
        label: '自动化日志',
        description: '自动化任务运行日志与追踪。',
        icon: <FileSearchOutlined />,
      },
      {
        key: '/platform/biz-logs',
        path: '/platform/biz-logs',
        label: '业务日志',
        description: '核心业务操作日志与检索。',
        icon: <FileTextOutlined />,
      },
    ],
  },
];

const hiddenRoutes: AppRouteMeta[] = [
  {
    path: '/platform/product-collections/:id',
    title: '采集商品详情',
    description: '采集商品详情页迁移占位。',
    menuKey: '/platform/product-collections',
  },
];

const parentKeyMap = new Map<string, string[]>();
const visibleRoutes: AppRouteMeta[] = [];

function buildAntMenuItems(items: AppNavItem[]): ItemType[] {
  return items.map((item) => {
    if (item.children?.length) {
      return {
        key: item.key,
        icon: item.icon,
        label: item.label,
        children: buildAntMenuItems(item.children),
      };
    }

    return {
      key: item.path || item.key,
      icon: item.icon,
      label: item.label,
    };
  });
}

function collectRoutes(items: AppNavItem[], parents: string[] = []) {
  items.forEach((item) => {
    if (item.children?.length) {
      collectRoutes(item.children, [...parents, item.key]);
      return;
    }

    if (!item.path) {
      return;
    }

    visibleRoutes.push({
      path: item.path,
      title: item.label,
      description: item.description || `${item.label}页面`,
      menuKey: item.path,
    });

    parentKeyMap.set(item.path, parents);
  });
}

collectRoutes(navigationTree);

export const menuItems = buildAntMenuItems(navigationTree);
export const moduleRoutes = [...visibleRoutes, ...hiddenRoutes];

export function getMatchedRouteMeta(pathname: string) {
  return moduleRoutes.find((route) => !!matchPath({ path: route.path, end: true }, pathname)) || null;
}

export function getSelectedMenuKey(pathname: string) {
  return getMatchedRouteMeta(pathname)?.menuKey || null;
}

export function getOpenKeysForPath(pathname: string) {
  const menuKey = getSelectedMenuKey(pathname);
  if (!menuKey) {
    return [];
  }

  return parentKeyMap.get(menuKey) || [];
}
