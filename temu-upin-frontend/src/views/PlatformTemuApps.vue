<template>
  <ProLayout title="TEMU 自研应用管理">
    <div class="page">
      <div class="toolbar">
        <a-space>
          <a-button type="primary" @click="openCreate">新增应用</a-button>
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
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '禁用' }}</a-tag>
          </template>

          <template v-else-if="column.key === 'appSecretMasked'">
            <span>{{ record.appSecretMasked || '-' }}</span>
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
        :title="editForm.id ? '编辑应用' : '新增应用'"
        :confirm-loading="saving"
        @ok="save"
        @cancel="closeEdit"
        width="760"
        :bodyStyle="{ maxHeight: '70vh', overflowY: 'auto', overflowX: 'hidden' }"
      >
        <a-form layout="vertical" :model="editForm">
          <a-form-item label="应用名称" required>
            <a-input v-model:value="editForm.appName" placeholder="例如：TEMU-自研-应用A" />
          </a-form-item>

          <a-form-item label="App Key" required>
            <a-input v-model:value="editForm.appKey" placeholder="TEMU App Key" />
          </a-form-item>

          <a-form-item label="App Secret" :required="!editForm.id">
            <a-input-password
              v-model:value="editForm.appSecret"
              placeholder="新增必填；编辑留空表示不修改"
              autocomplete="new-password"
            />
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
import { temuAppsApi } from '@/platform/api/temuApps'

const loading = ref(false)
const rows = ref([])

const editOpen = ref(false)
const saving = ref(false)
const editForm = reactive({
  id: null,
  appName: '',
  appKey: '',
  appSecret: '',
  enabled: true
})

const columns = [
  { title: '应用名称', dataIndex: 'appName', key: 'appName' },
  { title: 'App Key', dataIndex: 'appKey', key: 'appKey', width: 240 },
  { title: 'App Secret', key: 'appSecretMasked', width: 180 },
  { title: '启用', key: 'enabled', width: 90 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 170 },
  { title: '操作', key: 'actions', width: 160 }
]

const reload = async () => {
  loading.value = true
  try {
    const res = await temuAppsApi.list({ enabled: true })
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

const openCreate = () => {
  editForm.id = null
  editForm.appName = ''
  editForm.appKey = ''
  editForm.appSecret = ''
  editForm.enabled = true
  editOpen.value = true
}

const openEdit = (r) => {
  editForm.id = r?.id ?? null
  editForm.appName = r?.appName || ''
  editForm.appKey = r?.appKey || ''
  editForm.appSecret = ''
  editForm.enabled = !!r?.enabled
  editOpen.value = true
}

const closeEdit = () => {
  editOpen.value = false
}

const save = async () => {
  const payload = {
    appName: (editForm.appName || '').trim(),
    appKey: (editForm.appKey || '').trim(),
    appSecret: (editForm.appSecret || '').trim(),
    enabled: !!editForm.enabled
  }

  if (!payload.appName) return message.error('请输入应用名称')
  if (!payload.appKey) return message.error('请输入 App Key')
  if (!editForm.id && !payload.appSecret) return message.error('请输入 App Secret')

  saving.value = true
  try {
    let res
    if (editForm.id) {
      // update: allow empty secret meaning keep existing
      if (!payload.appSecret) delete payload.appSecret
      res = await temuAppsApi.update(editForm.id, payload)
    } else {
      res = await temuAppsApi.create(payload)
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
    title: '删除应用？',
    content: '删除后不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await temuAppsApi.delete(r.id)
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

onMounted(reload)
</script>

<style scoped>
.page {
  display: grid;
  gap: 12px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
}
</style>
