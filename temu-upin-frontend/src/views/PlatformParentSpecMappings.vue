<template>
  <ProLayout title="父规格映射表">
    <div class="page">
      <section class="hero-card">
        <div>
          <div class="hero-kicker">Global Parent Spec Mapping</div>
          <h1>父规格映射表</h1>
          <p>维护 1688 源字段与 TEMU 父规格名称的全局映射。发布时会优先读取这里的启用规则。</p>
        </div>
        <div class="hero-actions">
          <a-space>
            <a-select
              v-model:value="enabledFilter"
              :options="filterOptions"
              style="width: 140px"
              @change="reload"
            />
            <a-button @click="reload" :loading="loading">刷新</a-button>
            <a-button type="primary" @click="openCreate">新增映射</a-button>
          </a-space>
        </div>
      </section>

      <a-table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        row-key="id"
        bordered
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '禁用' }}</a-tag>
          </template>

          <template v-else-if="column.key === 'targetParentSpecName'">
            <a-tag color="blue">{{ record.targetParentSpecName || '-' }}</a-tag>
          </template>

          <template v-else-if="column.key === 'notes'">
            <span>{{ record.notes || '-' }}</span>
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
        :title="editForm.id ? '编辑父规格映射' : '新增父规格映射'"
        :confirm-loading="saving"
        @ok="save"
        @cancel="closeEdit"
        width="720"
      >
        <a-form layout="vertical" :model="editForm">
          <a-form-item label="源字段名" required>
            <a-input v-model:value="editForm.sourceFieldName" placeholder="例如 尺寸、规格、型号" />
          </a-form-item>

          <a-form-item label="目标父规格名" required>
            <a-select
              v-model:value="editForm.targetParentSpecName"
              show-search
              allow-clear
              :options="parentSpecOptions"
              placeholder="请选择或输入 TEMU 父规格名"
            />
          </a-form-item>

          <a-form-item label="启用状态">
            <a-switch v-model:checked="editForm.enabled" checked-children="启用" un-checked-children="禁用" />
          </a-form-item>

          <a-form-item label="备注">
            <a-textarea v-model:value="editForm.notes" :rows="4" placeholder="说明适用品类、命名差异或特殊原因" />
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
import { specMappingApi } from '@/platform/api/specMappings'

const loading = ref(false)
const saving = ref(false)
const editOpen = ref(false)
const rows = ref([])
const enabledFilter = ref('ALL')
const parentSpecs = ref([])

const filterOptions = [
  { label: '全部状态', value: 'ALL' },
  { label: '仅启用', value: 'ENABLED' },
  { label: '仅禁用', value: 'DISABLED' }
]

const fallbackParentSpecOptions = [
  { label: '型号', value: '型号' },
  { label: '规格', value: '规格' },
  { label: '尺码', value: '尺码' },
  { label: '颜色', value: '颜色' },
  { label: '数量', value: '数量' }
]

const parentSpecOptions = computed(() => {
  const seen = new Set()
  const options = (Array.isArray(parentSpecs.value) ? parentSpecs.value : [])
    .filter((item) => item?.parentSpecName)
    .map((item) => ({ label: item.parentSpecName, value: item.parentSpecName }))
    .filter((item) => {
      if (seen.has(item.value)) return false
      seen.add(item.value)
      return true
    })
  return options.length ? options : fallbackParentSpecOptions
})

const columns = [
  { title: '源字段名', dataIndex: 'sourceFieldName', key: 'sourceFieldName', width: 220 },
  { title: '目标父规格名', key: 'targetParentSpecName', width: 160 },
  { title: '状态', key: 'enabled', width: 100 },
  { title: '备注', key: 'notes' },
  { title: '操作', key: 'actions', width: 160 }
]

const editForm = reactive({
  id: null,
  sourceFieldName: '',
  targetParentSpecName: '',
  enabled: true,
  notes: ''
})

const currentEnabledParam = () => {
  if (enabledFilter.value === 'ENABLED') return true
  if (enabledFilter.value === 'DISABLED') return false
  return undefined
}

const resetForm = () => {
  editForm.id = null
  editForm.sourceFieldName = ''
  editForm.targetParentSpecName = ''
  editForm.enabled = true
  editForm.notes = ''
}

const openCreate = () => {
  resetForm()
  editOpen.value = true
}

const openEdit = (record) => {
  editForm.id = record?.id ?? null
  editForm.sourceFieldName = record?.sourceFieldName || ''
  editForm.targetParentSpecName = record?.targetParentSpecName || ''
  editForm.enabled = record?.enabled !== false
  editForm.notes = record?.notes || ''
  editOpen.value = true
}

const closeEdit = () => {
  editOpen.value = false
}

const loadParentSpecs = async () => {
  try {
    const res = await specMappingApi.listTemuParentSpecs()
    if (res?.success && Array.isArray(res.data)) {
      parentSpecs.value = res.data
    }
  } catch {
    parentSpecs.value = []
  }
}

const reload = async () => {
  loading.value = true
  try {
    const params = {}
    const enabled = currentEnabledParam()
    if (enabled !== undefined) {
      params.enabled = enabled
    }
    const res = await specMappingApi.listParentSpecMappings(params)
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

const save = async () => {
  const payload = {
    sourceFieldName: String(editForm.sourceFieldName || '').trim(),
    targetParentSpecName: String(editForm.targetParentSpecName || '').trim(),
    enabled: !!editForm.enabled,
    notes: String(editForm.notes || '').trim() || null
  }
  if (!payload.sourceFieldName) {
    message.error('请输入源字段名')
    return
  }
  if (!payload.targetParentSpecName) {
    message.error('请输入目标父规格名')
    return
  }

  saving.value = true
  try {
    let res
    if (editForm.id) {
      res = await specMappingApi.updateParentSpecMapping(editForm.id, payload)
    } else {
      res = await specMappingApi.createParentSpecMapping(payload)
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

const confirmDelete = (record) => {
  if (!record?.id) {
    return
  }
  Modal.confirm({
    title: '删除父规格映射？',
    content: `将删除映射：${record.sourceFieldName || '-'} -> ${record.targetParentSpecName || '-'}`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await specMappingApi.deleteParentSpecMapping(record.id)
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
  await Promise.all([loadParentSpecs(), reload()])
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 22px;
  border-radius: 18px;
  color: #0f172a;
  background:
    radial-gradient(120% 140% at 0% 0%, rgba(14, 165, 233, 0.18), transparent 55%),
    radial-gradient(120% 140% at 100% 0%, rgba(34, 197, 94, 0.16), transparent 48%),
    linear-gradient(135deg, #ffffff 0%, #eef6ff 100%);
  border: 1px solid rgba(14, 165, 233, 0.12);
}

.hero-kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #0ea5e9;
}

.hero-card h1 {
  margin: 6px 0 8px;
  font-size: 28px;
  line-height: 1.1;
}

.hero-card p {
  margin: 0;
  max-width: 760px;
  color: #475569;
}

.hero-actions {
  display: flex;
  align-items: flex-start;
}

@media (max-width: 900px) {
  .hero-card {
    flex-direction: column;
  }

  .hero-actions {
    width: 100%;
  }
}
</style>