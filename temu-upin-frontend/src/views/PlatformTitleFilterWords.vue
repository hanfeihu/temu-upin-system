<template>
  <ProLayout title="标题过滤词">
    <div class="page">
      <div class="toolbar">
        <a-space>
          <a-button type="primary" @click="openCreate">新增过滤词</a-button>
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
          <template v-if="column.key === 'actions'">
            <a-space>
              <a-button size="small" @click="openEdit(record)">编辑</a-button>
              <a-button size="small" danger @click="confirmDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <a-modal
        v-model:open="editOpen"
        :title="editForm.id ? '编辑过滤词' : '新增过滤词'"
        :confirm-loading="saving"
        @ok="save"
        @cancel="closeEdit"
        width="520"
      >
        <a-form layout="vertical" :model="editForm">
          <a-form-item label="过滤词" required>
            <a-input v-model:value="editForm.word" placeholder="例如: 亚马逊 / 跨境 / TEMU" />
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
import { ocrApi } from '@/platform/api/ocr'

const loading = ref(false)
const rows = ref([])

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 90 },
  { title: '过滤词', dataIndex: 'word', key: 'word' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 190 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 190 },
  { title: '操作', key: 'actions', width: 160 }
]

const reload = async () => {
  loading.value = true
  try {
    const res = await ocrApi.listTitleFilterWords()
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

const editOpen = ref(false)
const saving = ref(false)
const editForm = reactive({ id: null, word: '' })

const openCreate = () => {
  editForm.id = null
  editForm.word = ''
  editOpen.value = true
}

const openEdit = (r) => {
  editForm.id = r?.id ?? null
  editForm.word = r?.word || ''
  editOpen.value = true
}

const closeEdit = () => {
  editOpen.value = false
}

const save = async () => {
  const payload = { word: String(editForm.word || '').trim() }
  if (!payload.word) {
    message.error('请输入过滤词')
    return
  }
  saving.value = true
  try {
    const res = editForm.id
      ? await ocrApi.updateTitleFilterWord(editForm.id, payload)
      : await ocrApi.createTitleFilterWord(payload)
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
    title: '删除过滤词？',
    content: '删除后不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await ocrApi.deleteTitleFilterWord(r.id)
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

onMounted(() => {
  reload()
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
}
</style>
