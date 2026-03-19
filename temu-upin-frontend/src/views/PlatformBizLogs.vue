<template>
  <ProLayout title="业务日志">
    <div class="page">
      <a-card class="toolbar" :bordered="false">
        <a-form layout="inline" :model="filters" @submit.prevent>
          <a-form-item label="业务名称">
            <a-input v-model:value="filters.bizName" placeholder="模糊匹配" style="width: 260px" allow-clear @pressEnter="reload" />
          </a-form-item>
          <a-form-item>
            <a-space>
              <a-button type="primary" :loading="loading" @click="reload">查询</a-button>
              <a-button @click="reset">重置</a-button>
              <a-button type="primary" ghost @click="openCreate">新增日志</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </a-card>

      <a-card class="table-card" :bordered="false">
        <a-table
          rowKey="id"
          :columns="columns"
          :dataSource="rows"
          :loading="loading"
          :pagination="pagination"
          :scroll="{ x: 980 }"
          tableLayout="fixed"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'content'">
              <a-tooltip :title="record.content" placement="top">
                <div class="content-cell">{{ record.content }}</div>
              </a-tooltip>
            </template>
            <template v-else-if="['createdAt', 'updatedAt'].includes(column.key)">
              <span class="mono">{{ formatDateTime(record?.[column.key]) || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="openEdit(record)">编辑</a-button>
                <a-button size="small" danger @click="confirmDelete(record)">删除</a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-modal
        v-model:open="editOpen"
        :title="editForm.id ? '编辑日志' : '新增日志'"
        :confirm-loading="saving"
        @ok="save"
        @cancel="closeEdit"
        width="780"
        :bodyStyle="{ maxHeight: '70vh', overflowY: 'auto', overflowX: 'hidden' }"
      >
        <a-form layout="vertical" :model="editForm">
          <a-form-item label="业务名称" required>
            <a-input v-model:value="editForm.bizName" allow-clear placeholder="例如: OCR / 发布 / 导入" />
          </a-form-item>
          <a-form-item label="日志内容" required>
            <a-textarea v-model:value="editForm.content" :rows="10" placeholder="支持大文本" />
          </a-form-item>
        </a-form>
      </a-modal>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { bizLogsApi } from '@/platform/api/bizLogs'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const filters = reactive({
  bizName: ''
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 90 },
  { title: '业务名称', dataIndex: 'bizName', key: 'bizName', width: 180 },
  { title: '日志内容', key: 'content' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 190 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 190 },
  { title: '操作', key: 'actions', width: 160, fixed: 'right' }
]

const pagination = computed(() => ({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t) => `共 ${t} 条`,
  pageSizeOptions: ['10', '20', '50', '100']
}))

const pad2 = (n) => String(n ?? '').padStart(2, '0')
const formatDateTime = (v) => {
  if (!v) return ''
  if (Array.isArray(v) && v.length >= 6) {
    const [yy, mo, dd, hh, mm, ss] = v
    if (!yy || !mo || !dd) return ''
    return `${yy}-${pad2(mo)}-${pad2(dd)} ${pad2(hh)}:${pad2(mm)}:${pad2(ss)}`
  }
  if (typeof v === 'string') return v
  return ''
}

const fetchList = async () => {
  loading.value = true
  try {
    const params = {
      bizName: filters.bizName || undefined,
      page: page.value - 1,
      size: pageSize.value
    }
    const res = await bizLogsApi.list(params)
    if (res?.success) {
      rows.value = res.data?.content || []
      total.value = res.data?.totalElements || 0
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const reload = async () => {
  page.value = 1
  await fetchList()
}

const reset = async () => {
  filters.bizName = ''
  await reload()
}

const onTableChange = (p) => {
  page.value = p.current
  pageSize.value = p.pageSize
  fetchList()
}

const editOpen = ref(false)
const saving = ref(false)
const editForm = reactive({
  id: null,
  bizName: '',
  content: ''
})

const openCreate = () => {
  editForm.id = null
  editForm.bizName = ''
  editForm.content = ''
  editOpen.value = true
}

const openEdit = (r) => {
  editForm.id = r?.id ?? null
  editForm.bizName = r?.bizName || ''
  editForm.content = r?.content || ''
  editOpen.value = true
}

const closeEdit = () => {
  editOpen.value = false
}

const save = async () => {
  const payload = {
    bizName: String(editForm.bizName || '').trim(),
    content: editForm.content == null ? '' : String(editForm.content)
  }
  if (!payload.bizName) {
    message.error('请输入业务名称')
    return
  }
  if (!payload.content) {
    message.error('请输入日志内容')
    return
  }
  saving.value = true
  try {
    const res = editForm.id
      ? await bizLogsApi.update(editForm.id, payload)
      : await bizLogsApi.create(payload)
    if (res?.success) {
      message.success('已保存')
      editOpen.value = false
      await fetchList()
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
    title: '删除日志？',
    content: '删除后不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await bizLogsApi.delete(r.id)
        if (res?.success) {
          message.success('已删除')
          await fetchList()
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
  fetchList()
})
</script>

<style scoped>
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.toolbar {
  border-radius: 14px;
}

.table-card {
  border-radius: 14px;
}

.content-cell {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar :deep(.ant-form-inline) {
  flex-wrap: wrap;
}

/* Keep page from horizontal scrolling; only table scrolls */
:deep(.ant-table-wrapper) {
  width: 100%;
  max-width: 100%;
}

:deep(.ant-table-content) {
  overflow-x: auto;
  overflow-y: hidden;
}
</style>
