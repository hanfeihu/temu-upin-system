<template>
  <ProLayout title="货品属性规则">
    <div class="page">
      <div class="toolbar">
        <a-space>
          <a-button type="primary" @click="openCreate">新增规则</a-button>
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
          <template v-if="column.key === 'ruleType'">
            <a-tag :color="record.ruleType === 'GENERAL' ? 'blue' : 'gold'">
              {{ record.ruleType === 'GENERAL' ? '通用规则' : '固定类目规则' }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'leafCatId'">
            <span>{{ record.leafCatId || '-' }}</span>
          </template>

          <template v-else-if="column.key === 'fillMode'">
            <a-tag
              :color="fillModeColor(record.fillMode)"
            >
              {{ fillModeLabel(record.fillMode) }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '禁用' }}</a-tag>
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
        :title="editForm.id ? '编辑规则' : '新增规则'"
        :confirm-loading="saving"
        @ok="save"
        @cancel="closeEdit"
        width="760"
        :bodyStyle="{ maxHeight: '70vh', overflowY: 'auto', overflowX: 'hidden' }"
      >
        <a-form layout="vertical" :model="editForm">
          <a-form-item label="规则类型" required>
            <a-radio-group v-model:value="editForm.ruleType">
              <a-radio value="GENERAL">通用规则（应用全部类目）</a-radio>
              <a-radio value="FIXED_CATEGORY">固定类目规则</a-radio>
            </a-radio-group>
          </a-form-item>

          <a-form-item v-if="editForm.ruleType === 'FIXED_CATEGORY'" label="TEMU 类目 ID（叶子节点）" required>
            <a-input v-model:value="editForm.leafCatId" placeholder="例如 11800" />
          </a-form-item>

          <a-form-item label="属性名称（中文）" required>
            <a-input v-model:value="editForm.attrName" placeholder="需要与模板属性 name 匹配" />
          </a-form-item>

          <a-form-item label="填写方式" required>
            <a-select v-model:value="editForm.fillMode" style="width: 320px">
              <a-select-option value="FORCE_EMPTY">必须为空（值保存为空字符串，不能是 0）</a-select-option>
              <a-select-option value="FIXED_VALUE">固定值</a-select-option>
              <a-select-option value="SKIP">跳过（不显示，保存时不包含）</a-select-option>
            </a-select>
          </a-form-item>

          <a-form-item v-if="editForm.fillMode === 'FIXED_VALUE'" label="固定值" required>
            <a-input v-model:value="editForm.fixedValue" placeholder="将覆盖用户与 AI 的输入" />
          </a-form-item>

          <a-form-item label="启用">
            <a-switch v-model:checked="editForm.enabled" />
          </a-form-item>

          <a-form-item label="排序（越小越靠前）">
            <a-input-number v-model:value="editForm.sortOrder" style="width: 200px" :min="0" :max="9999" />
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
import { productCollectionApi } from '@/platform/api/productCollections'

const loading = ref(false)
const rows = ref([])

const editOpen = ref(false)
const saving = ref(false)
const editForm = reactive({
  id: null,
  ruleType: 'GENERAL',
  leafCatId: '',
  attrName: '',
  fillMode: 'FORCE_EMPTY',
  fixedValue: '',
  enabled: true,
  sortOrder: 0
})

const columns = [
  { title: '规则类型', key: 'ruleType', width: 140 },
  { title: '类目ID', key: 'leafCatId', width: 120 },
  { title: '属性名称', dataIndex: 'attrName', key: 'attrName' },
  { title: '填写方式', key: 'fillMode', width: 140 },
  { title: '固定值', dataIndex: 'fixedValue', key: 'fixedValue', width: 180 },
  { title: '启用', key: 'enabled', width: 90 },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 80 },
  { title: '操作', key: 'actions', width: 160 }
]

const fillModeLabel = (m) => {
  if (m === 'FORCE_EMPTY') return '必须为空'
  if (m === 'FIXED_VALUE') return '固定值'
  if (m === 'SKIP') return '跳过'
  return m || '-'
}

const fillModeColor = (m) => {
  if (m === 'FORCE_EMPTY') return 'purple'
  if (m === 'FIXED_VALUE') return 'cyan'
  if (m === 'SKIP') return 'volcano'
  return 'default'
}

const reload = async () => {
  loading.value = true
  try {
    const res = await productCollectionApi.listTemuAttrRules({ enabled: true })
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
  editForm.ruleType = 'GENERAL'
  editForm.leafCatId = ''
  editForm.attrName = ''
  editForm.fillMode = 'FORCE_EMPTY'
  editForm.fixedValue = ''
  editForm.enabled = true
  editForm.sortOrder = 0
  editOpen.value = true
}

const openEdit = (r) => {
  editForm.id = r?.id ?? null
  editForm.ruleType = r?.ruleType || 'GENERAL'
  editForm.leafCatId = r?.leafCatId || ''
  editForm.attrName = r?.attrName || ''
  editForm.fillMode = r?.fillMode || 'FORCE_EMPTY'
  editForm.fixedValue = r?.fixedValue || ''
  editForm.enabled = !!r?.enabled
  editForm.sortOrder = r?.sortOrder ?? 0
  editOpen.value = true
}

const closeEdit = () => {
  editOpen.value = false
}

const save = async () => {
  const payload = {
    ruleType: editForm.ruleType,
    leafCatId: editForm.ruleType === 'FIXED_CATEGORY' ? (editForm.leafCatId || '').trim() : null,
    attrName: (editForm.attrName || '').trim(),
    fillMode: editForm.fillMode,
    fixedValue: editForm.fillMode === 'FIXED_VALUE' ? (editForm.fixedValue || '').trim() : null,
    enabled: !!editForm.enabled,
    sortOrder: Number(editForm.sortOrder || 0)
  }
  if (!payload.attrName) {
    message.error('请输入属性名称')
    return
  }
  if (payload.ruleType === 'FIXED_CATEGORY' && !payload.leafCatId) {
    message.error('请输入叶子类目 ID')
    return
  }
  if (payload.fillMode === 'FIXED_VALUE' && !payload.fixedValue) {
    message.error('请输入固定值')
    return
  }

  saving.value = true
  try {
    let res
    if (editForm.id) {
      res = await productCollectionApi.updateTemuAttrRule(editForm.id, payload)
    } else {
      res = await productCollectionApi.createTemuAttrRule(payload)
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
    title: '删除规则？',
    content: '删除后不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await productCollectionApi.deleteTemuAttrRule(r.id)
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
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
