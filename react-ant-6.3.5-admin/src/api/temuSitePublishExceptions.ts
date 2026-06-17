import client from '@/api/client';
import type { ApiResponse, SpringPage, TemuSitePublishExceptionImportResult, TemuSitePublishExceptionVO } from '@/types/api';

export const temuSitePublishExceptionsApi = {
  list(params?: {
    keyword?: string;
    siteName?: string;
    reasonType?: string;
    active?: boolean;
    page?: number;
    size?: number;
  }) {
    return client.get('/platform/temu-site-publish-exceptions', { params }) as Promise<
      ApiResponse<SpringPage<TemuSitePublishExceptionVO>>
    >;
  },

  importFile(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return client.post('/platform/temu-site-publish-exceptions/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
    }) as Promise<ApiResponse<TemuSitePublishExceptionImportResult>>;
  },

  importPath(filePath: string) {
    return client.post('/platform/temu-site-publish-exceptions/import-path', { filePath }, {
      timeout: 120000,
    }) as Promise<ApiResponse<TemuSitePublishExceptionImportResult>>;
  },

  update(id: number, payload: { active?: boolean; reasonType?: string; remark?: string }) {
    return client.put(`/platform/temu-site-publish-exceptions/${id}`, payload) as Promise<ApiResponse<TemuSitePublishExceptionVO>>;
  },

  delete(id: number) {
    return client.delete(`/platform/temu-site-publish-exceptions/${id}`) as Promise<ApiResponse<null>>;
  },
};
