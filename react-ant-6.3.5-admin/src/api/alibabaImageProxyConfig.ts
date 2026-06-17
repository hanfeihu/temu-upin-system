import client from '@/api/client';
import type { AlibabaImageProxyConfigPayload, AlibabaImageProxyConfigVO, ApiResponse } from '@/types/api';

export const alibabaImageProxyConfigApi = {
  current() {
    return client.get('/platform/alibaba-image-proxy-config') as Promise<ApiResponse<AlibabaImageProxyConfigVO>>;
  },

  get() {
    return this.current();
  },

  create(payload: AlibabaImageProxyConfigPayload) {
    return client.post('/platform/alibaba-image-proxy-config', payload) as Promise<ApiResponse<AlibabaImageProxyConfigVO>>;
  },

  save(payload: AlibabaImageProxyConfigPayload) {
    return this.create(payload);
  },

  update(id: number, payload: AlibabaImageProxyConfigPayload) {
    return client.put(`/platform/alibaba-image-proxy-config/${id}`, payload) as Promise<ApiResponse<AlibabaImageProxyConfigVO>>;
  },
};
