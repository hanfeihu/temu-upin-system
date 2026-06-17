import client from '@/api/client';
import type { ApiResponse, ProductDashboardResponseVO } from '@/types/api';

export const productDashboardApi = {
  dashboard(params?: { days?: number }) {
    return client.get('/platform/product-dashboard', { params }) as Promise<ApiResponse<ProductDashboardResponseVO>>;
  },
};
