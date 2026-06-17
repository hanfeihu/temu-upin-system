import type { ItemType } from 'antd/es/menu/interface';
import { matchPath } from 'react-router-dom';
import {
  LinkOutlined,
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
  PictureOutlined,
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
  UserAddOutlined,
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
    key: '/platform/alibaba1688-card-links',
    path: '/platform/alibaba1688-card-links',
    label: '1688卡片链接',
    description: '1688 搜索卡片链接采集记录、去重状态与筛选查询。',
    icon: <LinkOutlined />,
  },
  {
    key: '/platform/alibaba1688-auth-sessions',
    path: '/platform/alibaba1688-auth-sessions',
    label: '1688凭证管理',
    description: '管理 1688 登录凭证、storageState 与 worker 可用会话。',
    icon: <ApiOutlined />,
  },
  {
    key: '/platform/alibaba1688-detail-tasks',
    path: '/platform/alibaba1688-detail-tasks',
    label: '1688详情采集任务',
    description: '通过对话式输入批量下发 1688 详情采集任务。',
    icon: <HistoryOutlined />,
  },
  {
    key: '/platform/alibaba1688-detail-records',
    path: '/platform/alibaba1688-detail-records',
    label: '1688详情数据',
    description: '查看 Playwright 采回的 1688 详情数据、解析结果与原始 HTML。',
    icon: <DatabaseOutlined />,
  },
  {
    key: '/platform/alibaba1688-selection-pools',
    path: '/platform/alibaba1688-selection-pools',
    label: '1688选品池',
    description: '查看选品池列表、SKU 明细与生成报告入口。',
    icon: <InboxOutlined />,
  },
  {
    key: '/platform/alibaba1688-selection-pool-filter-categories',
    path: '/platform/alibaba1688-selection-pool-filter-categories',
    label: '1688过滤类目配置',
    description: '维护选品池过滤类目、启停状态与备注。',
    icon: <FilterOutlined />,
  },
  {
    key: '/platform/alibaba-image-proxy-config',
    path: '/platform/alibaba-image-proxy-config',
    label: '阿里图片代理服务器配置',
    description: '维护 1688 图片展示代理的基地址、路径与白名单域名。',
    icon: <PictureOutlined />,
  },
  {
    key: 'group-product',
    label: '商品中心',
    icon: <DatabaseOutlined />,
    children: [
      {
        key: '/platform/supplier-product-submissions',
        path: '/platform/supplier-product-submissions',
        label: '供应商提品',
        description: '供货商提交产品图、供货价、重量与尺寸信息。',
        icon: <UserAddOutlined />,
      },
      {
        key: '/platform/supplier-product-packages',
        path: '/platform/supplier-product-packages',
        label: 'AI商品包装台',
        description: '把供应商供品加工成标题、轮播图、详情图并推送采集库。',
        icon: <RobotOutlined />,
      },
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
        key: '/platform/shop-skus',
        path: '/platform/shop-skus',
        label: '店铺SKU',
        description: '按店铺查看已加站 SKU，并维护采购价。',
        icon: <TagsOutlined />,
      },
      {
        key: '/platform/temu-orders',
        path: '/platform/temu-orders',
        label: 'TEMU 订单',
        description: '订单同步、SKU 关联和物流分析入口。',
        icon: <AuditOutlined />,
      },
      {
        key: '/platform/dianxiaomi-package-fees',
        path: '/platform/dianxiaomi-package-fees',
        label: '点小秘费用',
        description: '独立维护店小秘单号，并通过浩远接口查询费用与明细。',
        icon: <GatewayOutlined />,
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
        key: '/platform/ai-variant-publish',
        path: '/platform/ai-variant-publish',
        label: 'AI变体发布',
        description: '直接调用原始 TEMU 发布接口并查看发布记录。',
        icon: <RobotOutlined />,
      },
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
        key: '/platform/temu-forbidden-word-rules',
        path: '/platform/temu-forbidden-word-rules',
        label: 'TEMU违禁词学习库',
        description: 'TEMU 违禁词、替换词与适用字段维护。',
        icon: <FilterOutlined />,
      },
      {
        key: '/platform/temu-site-publish-exceptions',
        path: '/platform/temu-site-publish-exceptions',
        label: 'TEMU加站异常库',
        description: '导入未发布到站点异常，沉淀需要资质或不可加站的商品。',
        icon: <FileSearchOutlined />,
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
