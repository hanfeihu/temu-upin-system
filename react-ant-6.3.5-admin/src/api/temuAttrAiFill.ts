import client from '@/api/client';
import type { ApiResponse, SpringPage, TemuAttrAiFillTaskDetailVO, TemuAttrAiFillTaskVO } from '@/types/api';

export const temuAttrAiFillApi = {
  listTasks(params: { q?: string; status?: number; page?: number; size?: number }) {
    return client.get('/platform/temu-attr-ai-fill/tasks', { params }) as Promise<ApiResponse<SpringPage<TemuAttrAiFillTaskVO>>>;
  },

  getTask(id: number) {
    return client.get(`/platform/temu-attr-ai-fill/tasks/${id}`) as Promise<ApiResponse<TemuAttrAiFillTaskDetailVO>>;
  },

  createTask(spuId: number) {
    return client.post('/platform/temu-attr-ai-fill/tasks', { spuId }) as Promise<ApiResponse<TemuAttrAiFillTaskVO>>;
  },

  runTask(id: number) {
    return client.post(`/platform/temu-attr-ai-fill/tasks/${id}/run`) as Promise<ApiResponse<TemuAttrAiFillTaskVO>>;
  },
};
