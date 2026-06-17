import client from '@/api/client';
import type {
  ApiResponse,
  SpringPage,
  SupplierProductSubmissionPayload,
  SupplierProductSubmissionVO,
  SupplierProductPackageVO,
  UploadImageResult,
} from '@/types/api';

export const supplierProductSubmissionsApi = {
  list(params: {
    keyword?: string;
    status?: string;
    page?: number;
    size?: number;
  }) {
    return client.get('/platform/supplier-product-submissions', {
      params,
    }) as Promise<ApiResponse<SpringPage<SupplierProductSubmissionVO>>>;
  },

  create(payload: SupplierProductSubmissionPayload) {
    return client.post('/platform/supplier-product-submissions', payload) as Promise<ApiResponse<SupplierProductSubmissionVO>>;
  },

  update(id: number, payload: SupplierProductSubmissionPayload) {
    return client.put(`/platform/supplier-product-submissions/${id}`, payload) as Promise<ApiResponse<SupplierProductSubmissionVO>>;
  },

  remove(id: number) {
    return client.delete(`/platform/supplier-product-submissions/${id}`) as Promise<ApiResponse<void>>;
  },

  approve(id: number) {
    return client.post(`/platform/supplier-product-submissions/${id}/approve`) as Promise<ApiResponse<SupplierProductSubmissionVO>>;
  },

  reject(id: number, remark?: string) {
    return client.post(`/platform/supplier-product-submissions/${id}/reject`, { remark }) as Promise<ApiResponse<SupplierProductSubmissionVO>>;
  },

  uploadImage(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return client.post('/upload/image', formData, {
      timeout: 120000,
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    }) as Promise<ApiResponse<UploadImageResult>>;
  },

  publicCreate(payload: SupplierProductSubmissionPayload) {
    return client.post('/public/supplier-product-submissions', payload) as Promise<ApiResponse<SupplierProductSubmissionVO>>;
  },

  publicUploadImage(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return client.post('/public/supplier-product-submissions/upload-image', formData, {
      timeout: 120000,
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    }) as Promise<ApiResponse<UploadImageResult>>;
  },
};

export const supplierProductPackagesApi = {
  list(params: {
    keyword?: string;
    status?: string;
    page?: number;
    size?: number;
  }) {
    return client.get('/platform/supplier-product-packages', { params }) as Promise<ApiResponse<SpringPage<SupplierProductPackageVO>>>;
  },

  generate(id: number) {
    return client.post(`/platform/supplier-product-packages/${id}/generate`, undefined, { timeout: 600000 }) as Promise<ApiResponse<SupplierProductPackageVO>>;
  },

  pushToProductCollection(id: number, payload?: { targetShopIds?: string[] }) {
    return client.post(`/platform/supplier-product-packages/${id}/push-to-product-collection`, payload, { timeout: 180000 }) as Promise<ApiResponse<SupplierProductPackageVO>>;
  },
};
