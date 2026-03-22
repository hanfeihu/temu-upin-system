<template>
  <ProLayout title="TEMU 店铺管理">
    <div class="page">
      <div class="toolbar">
        <a-space>
          <a-button type="primary" @click="openCreate">新增店铺</a-button>
          <a-button @click="reload" :loading="loading">刷新</a-button>
        </a-space>
      </div>

      <a-table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        row-key="id"
        :pagination="false"
        bordered
        size="middle"
        :scroll="{ x: 1500 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '禁用' }}</a-tag>
          </template>

          <template v-else-if="column.key === 'tokenMasked'">
            <span>{{ record.tokenMasked || '-' }}</span>
          </template>

          <template v-else-if="column.key === 'shopName'">
            <span class="nowrap">{{ record.shopName || '-' }}</span>
          </template>

          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button size="small" @click="openEdit(record)">编辑</a-button>
              <a-button size="small" danger @click="confirmDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <a-modal
        v-model:open="editOpen"
        :title="editForm.id ? '编辑店铺' : '新增店铺'"
        :confirm-loading="saving"
        @ok="save"
        @cancel="closeEdit"
        width="780"
        :bodyStyle="{ maxHeight: '70vh', overflowY: 'auto', overflowX: 'hidden' }"
      >
        <a-form layout="vertical" :model="editForm">
          <a-form-item label="店铺名称" required>
            <a-input v-model:value="editForm.shopName" placeholder="例如：TEMU-店铺A" />
          </a-form-item>

          <a-form-item label="店铺ID" required>
            <a-input v-model:value="editForm.shopId" placeholder="例如：634418212966313" />
          </a-form-item>

          <a-form-item label="TOKEN" :required="!editForm.id">
            <a-input-password
              v-model:value="editForm.token"
              placeholder="新增必填；编辑留空表示不修改"
              autocomplete="new-password"
            />
          </a-form-item>

          <a-form-item label="应用" required>
            <a-select
              v-model:value="editForm.appId"
              style="width: 360px"
              placeholder="请选择应用"
              :loading="appsLoading"
              show-search
              :filter-option="filterAppOption"
            >
              <a-select-option v-for="a in apps" :key="a.id" :value="a.id">
                {{ a.appName }}（{{ a.appKey }}）
              </a-select-option>
            </a-select>
          </a-form-item>

          <a-form-item label="启用">
            <a-switch v-model:checked="editForm.enabled" />
          </a-form-item>
        </a-form>
      </a-modal>
    </div>
  </ProLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { temuShopsApi } from '@/platform/api/temuShops'
import { temuAppsApi } from '@/platform/api/temuApps'

const loading = ref(false)
const rows = ref([])

const appsLoading = ref(false)
const apps = ref([])

const editOpen = ref(false)
const saving = ref(false)
const editForm = reactive({
  id: null,
  shopName: '',
  shopId: '',
  token: '',
  appId: null,
  enabled: true
})

const columns = [
  { title: '店铺名称', dataIndex: 'shopName', key: 'shopName', width: 240 },
  { title: '店铺ID', dataIndex: 'shopId', key: 'shopId', width: 190 },
  { title: '应用', dataIndex: 'appName', key: 'appName', width: 220 },
  { title: 'TOKEN', key: 'tokenMasked', width: 180 },
  { title: '启用', key: 'enabled', width: 90 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 170 },
  { title: '操作', key: 'actions', width: 160, fixed: 'right' }
]

const filterAppOption = (input, option) => {
  const text = (option?.children || '').toString().toLowerCase()
  return text.includes((input || '').toLowerCase())
}

const loadApps = async () => {
  appsLoading.value = true
  try {
    const res = await temuAppsApi.list({ enabled: true })
    if (res?.success) {
      apps.value = Array.isArray(res.data) ? res.data : []
      return
    }
  } catch (e) {
    // ignore
  } finally {
    appsLoading.value = false
  }
}

const reload = async () => {
  loading.value = true
  try {
    const res = await temuShopsApi.list({ enabled: true })
    if (res?.success) {
      rows.value = Array.isArray(res.data) ? res.data : []
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const openCreate = async () => {
  editForm.id = null
  editForm.shopName = ''
  editForm.shopId = ''
  editForm.token = ''
  editForm.appId = null
  editForm.enabled = true
  editOpen.value = true
  await loadApps()
}

const openEdit = async (r) => {
  editForm.id = r?.id ?? null
  editForm.shopName = r?.shopName || ''
  editForm.shopId = r?.shopId || ''
  editForm.token = ''
  editForm.appId = r?.appId ?? null
  editForm.enabled = !!r?.enabled
  editOpen.value = true
  await loadApps()
}

const closeEdit = () => {
  editOpen.value = false
}

const save = async () => {
  const payload = {
    shopName: (editForm.shopName || '').trim(),
    shopId: (editForm.shopId || '').trim(),
    token: (editForm.token || '').trim(),
    appId: editForm.appId,
    enabled: !!editForm.enabled
  }

  if (!payload.shopName) return message.error('请输入店铺名称')
  if (!payload.shopId) return message.error('请输入店铺ID')
  if (!payload.appId) return message.error('请选择应用')
  if (!editForm.id && !payload.token) return message.error('请输入 TOKEN')

  saving.value = true
  try {
    let res
    if (editForm.id) {
      if (!payload.token) delete payload.token
      res = await temuShopsApi.update(editForm.id, payload)
    } else {
      res = await temuShopsApi.create(payload)
    }

    if (res?.success) {
      message.success('已保存')
      editOpen.value = false
      await reload()
      return
    }
    message.error(res?.message || '保存失败')
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const confirmDelete = (r) => {
  if (!r?.id) return
  Modal.confirm({
    title: '删除店铺？',
    content: '删除后不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await temuShopsApi.delete(r.id)
        if (res?.success) {
          message.success('已删除')
          await reload()
          return
        }
        message.error(res?.message || '删除失败')
      } catch (e) {
        message.error(e.message || '删除失败')
      }
    }
  })
}

onMounted(async () => {
  await loadApps()
  await reload()
})
</script>

<style scoped>
.page {
  /* Using block layout here avoids some edge cases with Ant Table fixed columns + horizontal scroll. */
  display: block;
}

.page > * + * {
  margin-top: 12px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
}

.nowrap {
  white-space: nowrap;
}
</style>
