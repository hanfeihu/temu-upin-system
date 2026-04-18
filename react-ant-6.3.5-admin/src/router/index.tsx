import { Navigate, createBrowserRouter } from 'react-router-dom';
import { defaultRoute, moduleRoutes } from '@/config/navigation';
import BasicLayout from '@/layouts/BasicLayout';
import AuthGuard from '@/router/AuthGuard';
import NotFound from '@/views/exception/NotFound';
import Login from '@/views/auth/Login';
import ModulePage from '@/views/modules/ModulePage';
import ProductCollectionsPage from '@/views/platform/product-collections/ProductCollectionsPage';
import ProductDraftsPage from '@/views/platform/product-drafts/ProductDraftsPage';
import PublishLogsPage from '@/views/platform/publish-logs/PublishLogsPage';
import TemuShopsPage from '@/views/platform/temu-shops/TemuShopsPage';

const routeElementMap: Record<string, JSX.Element> = {
  '/platform/product-collections': <ProductCollectionsPage />,
  '/platform/product-drafts': <ProductDraftsPage />,
  '/platform/publish-logs': <PublishLogsPage />,
  '/platform/temu-shops': <TemuShopsPage />,
};

const router = createBrowserRouter([
  {
    path: '/login',
    element: <Login />,
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
]);

export default router;
