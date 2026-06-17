import { Navigate, createBrowserRouter } from 'react-router-dom';
import { defaultRoute, moduleRoutes } from '@/config/navigation';
import BasicLayout from '@/layouts/BasicLayout';
import AuthGuard from '@/router/AuthGuard';
import NotFound from '@/views/exception/NotFound';
import Login from '@/views/auth/Login';
import ModulePage from '@/views/modules/ModulePage';
import Alibaba1688CardLinksPage from '@/views/platform/alibaba1688-card-links/Alibaba1688CardLinksPage';
import Alibaba1688AuthSessionsPage from '@/views/platform/alibaba1688-auth-sessions/Alibaba1688AuthSessionsPage';
import Alibaba1688DetailRecordsPage from '@/views/platform/alibaba1688-detail-records/Alibaba1688DetailRecordsPage';
import Alibaba1688DetailTasksPage from '@/views/platform/alibaba1688-detail-tasks/Alibaba1688DetailTasksPage';
import AlibabaImageProxyConfigPage from '@/views/platform/alibaba-image-proxy-config/AlibabaImageProxyConfigPage';
import Alibaba1688SelectionPoolsPage from '@/views/platform/alibaba1688-selection-pools/Alibaba1688SelectionPoolsPage';
import Alibaba1688SelectionPoolFilterCategoriesPage from '@/views/platform/alibaba1688-selection-pool-filter-categories/Alibaba1688SelectionPoolFilterCategoriesPage';
import AIChannelsPage from '@/views/platform/ai-channels/AIChannelsPage';
import AiVariantPublishPage from '@/views/platform/ai-variant-publish/AiVariantPublishPage';
import BizLogsPage from '@/views/platform/biz-logs/BizLogsPage';
import DianxiaomiPackageFeesPage from '@/views/platform/dianxiaomi-package-fees/DianxiaomiPackageFeesPage';
import ImageTranslateRecordsPage from '@/views/platform/image-translate-records/ImageTranslateRecordsPage';
import OcrFilterWordsPage from '@/views/platform/ocr-filter-words/OcrFilterWordsPage';
import OcrTasksPage from '@/views/platform/ocr-tasks/OcrTasksPage';
import OrderDashboardPage from '@/views/platform/order-dashboard/OrderDashboardPage';
import ParserTestPage from '@/views/platform/parser-test/ParserTestPage';
import PostImportLogsPage from '@/views/platform/post-import-logs/PostImportLogsPage';
import ProductDashboardPage from '@/views/platform/product-dashboard/ProductDashboardPage';
import ProductCollectionDetailPage from '@/views/platform/product-collections/ProductCollectionDetailPage';
import ProductCollectionsPage from '@/views/platform/product-collections/ProductCollectionsPage';
import ProductDraftsPage from '@/views/platform/product-drafts/ProductDraftsPage';
import PublishLogsPage from '@/views/platform/publish-logs/PublishLogsPage';
import PublishSuccessCasesPage from '@/views/platform/publish-success-cases/PublishSuccessCasesPage';
import ShopSkuPage from '@/views/platform/shop-skus/ShopSkuPage';
import ParentSpecMappingsPage from '@/views/platform/spec-mappings/parent-spec-mappings/ParentSpecMappingsPage';
import SyncActivityPage from '@/views/platform/sync-activity/SyncActivityPage';
import SyncConfigPage from '@/views/platform/sync-config/SyncConfigPage';
import SyncGoodsPage from '@/views/platform/sync-goods/SyncGoodsPage';
import SyncPriceAdjustPage from '@/views/platform/sync-price-adjust/SyncPriceAdjustPage';
import SyncPriceReviewPage from '@/views/platform/sync-price-review/SyncPriceReviewPage';
import SyncTasksPage from '@/views/platform/sync-tasks/SyncTasksPage';
import SupplierProductSubmissionsPage from '@/views/platform/supplier-product-submissions/SupplierProductSubmissionsPage';
import SupplierProductPackagesPage from '@/views/platform/supplier-product-packages/SupplierProductPackagesPage';
import SupplierSubmitPage from '@/views/public/SupplierSubmitPage';
import TemuAttrAiFillPage from '@/views/platform/temu-attr-ai-fill/TemuAttrAiFillPage';
import TemuAttrRulesPage from '@/views/platform/temu-attr-rules/TemuAttrRulesPage';
import TemuAppsPage from '@/views/platform/temu-apps/TemuAppsPage';
import TemuAutoPublishLogsPage from '@/views/platform/temu-auto-publish-logs/TemuAutoPublishLogsPage';
import TemuOrderAftersalesPage from '@/views/platform/temu-order-aftersales/TemuOrderAftersalesPage';
import TemuOrdersPage from '@/views/platform/temu-orders/TemuOrdersPage';
import TemuForbiddenWordRulesPage from '@/views/platform/temu-forbidden-word-rules/TemuForbiddenWordRulesPage';
import TemuShopsPage from '@/views/platform/temu-shops/TemuShopsPage';
import TemuSitePublishExceptionsPage from '@/views/platform/temu-site-publish-exceptions/TemuSitePublishExceptionsPage';
import TemuTitleOptimizerPage from '@/views/platform/temu-title-optimizer/TemuTitleOptimizerPage';
import TitleFilterWordsPage from '@/views/platform/title-filter-words/TitleFilterWordsPage';

const routeElementMap: Record<string, JSX.Element> = {
  '/platform/alibaba1688-auth-sessions': <Alibaba1688AuthSessionsPage />,
  '/platform/alibaba1688-card-links': <Alibaba1688CardLinksPage />,
  '/platform/alibaba1688-detail-records': <Alibaba1688DetailRecordsPage />,
  '/platform/alibaba1688-detail-tasks': <Alibaba1688DetailTasksPage />,
  '/platform/alibaba1688-selection-pools': <Alibaba1688SelectionPoolsPage />,
  '/platform/alibaba1688-selection-pool-filter-categories': <Alibaba1688SelectionPoolFilterCategoriesPage />,
  '/platform/alibaba-image-proxy-config': <AlibabaImageProxyConfigPage />,
  '/platform/ai-channels': <AIChannelsPage />,
  '/platform/ai-variant-publish': <AiVariantPublishPage />,
  '/platform/biz-logs': <BizLogsPage />,
  '/platform/dianxiaomi-package-fees': <DianxiaomiPackageFeesPage />,
  '/platform/image-translate-records': <ImageTranslateRecordsPage />,
  '/platform/ocr-filter-words': <OcrFilterWordsPage />,
  '/platform/ocr-tasks': <OcrTasksPage />,
  '/platform/parser-test': <ParserTestPage />,
  '/platform/post-import-logs': <PostImportLogsPage />,
  '/platform/product-collections/:id': <ProductCollectionDetailPage />,
  '/platform/product-collections': <ProductCollectionsPage />,
  '/platform/product-drafts': <ProductDraftsPage />,
  '/platform/publish-logs': <PublishLogsPage />,
  '/platform/publish-success-cases': <PublishSuccessCasesPage />,
  '/platform/shop-skus': <ShopSkuPage />,
  '/platform/spec-mappings/parent-spec-mappings': <ParentSpecMappingsPage />,
  '/platform/sync-activity': <SyncActivityPage />,
  '/platform/sync-config': <SyncConfigPage />,
  '/platform/sync-goods': <SyncGoodsPage />,
  '/platform/sync-price-adjust': <SyncPriceAdjustPage />,
  '/platform/sync-price-review': <SyncPriceReviewPage />,
  '/platform/sync-tasks': <SyncTasksPage />,
  '/platform/supplier-product-packages': <SupplierProductPackagesPage />,
  '/platform/supplier-product-submissions': <SupplierProductSubmissionsPage />,
  '/platform/temu-attr-ai-fill': <TemuAttrAiFillPage />,
  '/platform/temu-attr-rules': <TemuAttrRulesPage />,
  '/platform/temu-apps': <TemuAppsPage />,
  '/platform/temu-auto-publish-logs': <TemuAutoPublishLogsPage />,
  '/platform/temu-forbidden-word-rules': <TemuForbiddenWordRulesPage />,
  '/platform/temu-order-aftersales': <TemuOrderAftersalesPage />,
  '/platform/temu-orders': <TemuOrdersPage />,
  '/platform/temu-shops': <TemuShopsPage />,
  '/platform/temu-site-publish-exceptions': <TemuSitePublishExceptionsPage />,
  '/platform/temu-title-optimizer': <TemuTitleOptimizerPage />,
  '/platform/title-filter-words': <TitleFilterWordsPage />,
};

const routerBaseName = (import.meta.env.VITE_ROUTER_BASENAME || '').replace(/\/$/, '');

const router = createBrowserRouter([
  {
    path: '/login',
    element: <Login />,
  },
  {
    path: '/supplier-submit',
    element: <SupplierSubmitPage />,
  },
  {
    path: '/goods/:id',
    element: (
      <AuthGuard>
        <ProductCollectionDetailPage />
      </AuthGuard>
    ),
  },
  {
    path: '/platform/product-dashboard',
    element: (
      <AuthGuard>
        <ProductDashboardPage />
      </AuthGuard>
    ),
  },
  {
    path: '/platform/order-dashboard',
    element: (
      <AuthGuard>
        <OrderDashboardPage />
      </AuthGuard>
    ),
  },
  {
    path: '/supplier-product-submissions',
    element: (
      <AuthGuard>
        <SupplierProductSubmissionsPage />
      </AuthGuard>
    ),
  },
  {
    path: '/',
    element: (
      <AuthGuard>
        <BasicLayout />
      </AuthGuard>
    ),
    children: [
      {
        index: true,
        element: <Navigate to={defaultRoute} replace />,
      },
      ...moduleRoutes.map((route) => ({
        path: route.path.replace(/^\//, ''),
        element:
          routeElementMap[route.path] || (
            <ModulePage
              title={route.title}
              description={route.description}
              path={route.path}
            />
          ),
      })),
      {
        path: '*',
        element: <NotFound />,
      },
    ],
  },
], {
  basename: routerBaseName || undefined,
});

export default router;
