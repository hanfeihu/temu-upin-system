<template>
  <ProLayout title="1688 与 TEMU 规格映射规则">
    <div class="mapping-page">
      <section class="hero-card">
        <div>
          <div class="hero-kicker">Deterministic Mapping</div>
          <h1>规格映射规则中心</h1>
          <p>把 1688 规格字段如何转成 TEMU 主销售属性和组内 SKU 维度，固化成可复用模板。</p>
        </div>
        <div class="hero-actions">
          <a-button @click="reload" :loading="loading">刷新</a-button>
          <a-button type="primary" @click="openCreate">新增模板</a-button>
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

          <template v-else-if="column.key === 'summary'">
            <div class="summary-cell">
              <div>主属性: {{ record.targetParentSpecName || '-' }}</div>
              <div>候选主字段: {{ (record.mainFieldCandidates || []).join(' / ') || '-' }}</div>
              <div>字段映射: {{ (record.fieldMappings || []).filter((item) => item?.enabled !== false).length }}</div>
            </div>
          </template>

          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button size="small" @click="openEdit(record)">编辑</a-button>
              <a-button size="small" danger @click="removeProfile(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <a-modal
        v-model:open="editOpen"
        :title="editForm.id ? '编辑模板' : '新增模板'"
        width="1080"
        :confirm-loading="saving"
        @ok="saveProfile"
      >
        <div class="modal-grid">
          <a-form layout="vertical" :model="editForm">
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="模板名称" required>
                  <a-input v-model:value="editForm.name" placeholder="例如 发饰类单维规格模板" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="主销售属性名称">
                  <a-select v-model:value="editForm.targetParentSpecName" :options="parentSpecOptions" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="来源类目路径">
                  <a-input v-model:value="editForm.sourceCategoryPath" placeholder="可选，用于描述适用商品" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="来源签名">
                  <a-input v-model:value="editForm.sourceSignature" placeholder="例如 尺寸#37|颜色#1" />
                </a-form-item>
              </a-col>
            </a-row>

            <a-form-item label="候选主字段">
              <a-select
                v-model:value="editForm.mainFieldCandidates"
                mode="tags"
                placeholder="例如 尺寸 / 型号"
                style="width: 100%"
              />
            </a-form-item>

            <div class="section-head">字段映射</div>
            <div class="mapping-list">
              <div class="mapping-row mapping-head">
                <span>启用</span>
                <span>来源字段</span>
                <span>目标字段</span>
                <span>角色</span>
                <span>忽略空值</span>
                <span>忽略 *</span>
                <span></span>
              </div>
              <div v-for="(item, index) in editForm.fieldMappings" :key="index" class="mapping-row">
                <a-switch v-model:checked="item.enabled" />
                <a-input v-model:value="item.sourceFieldName" placeholder="尺寸" />
                <a-input v-model:value="item.targetFieldName" placeholder="型号 / 尺寸" />
                <a-select v-model:value="item.role" :options="roleOptions" />
                <a-switch v-model:checked="item.ignoreBlank" />
                <a-switch v-model:checked="item.ignoreAsterisk" />
                <a-button danger @click="editForm.fieldMappings.splice(index, 1)">删</a-button>
              </div>
            </div>
            <a-button class="ghost-btn" @click="addFieldMapping">新增字段映射</a-button>

            <div class="section-head">值归并规则</div>
            <div class="rule-list">
              <div class="rule-row rule-head">
                <span>启用</span>
                <span>来源字段</span>
                <span>匹配方式</span>
                <span>匹配表达式</span>
                <span>目标字段</span>
                <span>目标值</span>
                <span></span>
              </div>
              <div v-for="(item, index) in editForm.valueRules" :key="index" class="rule-row">
                <a-switch v-model:checked="item.enabled" />
                <a-input v-model:value="item.sourceFieldName" placeholder="尺寸" />
                <a-select v-model:value="item.matchType" :options="matchTypeOptions" />
                <a-input v-model:value="item.matchExpr" placeholder="例如 袋装 / 30件" />
                <a-input v-model:value="item.targetFieldName" placeholder="型号" />
                <a-input v-model:value="item.targetValue" placeholder="袋装" />
                <a-button danger @click="editForm.valueRules.splice(index, 1)">删</a-button>
              </div>
            </div>
            <a-button class="ghost-btn" @click="addValueRule">新增值规则</a-button>

            <div class="form-footer">
              <a-space>
                <span>自动忽略恒定字段</span>
                <a-switch v-model:checked="editForm.autoIgnoreConstantFields" />
              </a-space>
              <a-space>
                <span>启用</span>
                <a-switch v-model:checked="editForm.enabled" />
              </a-space>
            </div>

            <a-form-item label="备注">
              <a-textarea v-model:value="editForm.notes" :rows="4" placeholder="记录适用品类、为什么这样映射" />
            </a-form-item>
          </a-form>
        </div>
      </a-modal>
    </div>
  </ProLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { specMappingApi } from '@/platform/api/specMappings'

const loading = ref(false)
const saving = ref(false)
const editOpen = ref(false)
const rows = ref([])

const parentSpecOptions = [
  { label: '型号', value: '型号' },
  { label: '规格', value: '规格' },
  { label: '尺码', value: '尺码' },
  { label: '颜色', value: '颜色' },
  { label: '数量', value: '数量' }
]

const roleOptions = [
  { label: '主属性候选', value: 'main_candidate' },
  { label: '组内 SKU 维度', value: 'sku_dimension' },
  { label: '忽略', value: 'ignore' }
]

const matchTypeOptions = [
  { label: '包含', value: 'contains' },
  { label: '等于', value: 'equals' },
  { label: '正则', value: 'regex' }
]

const createEmptyFieldMapping = () => ({
  sourceFieldName: '',
  targetFieldName: '',
  role: 'main_candidate',
  enabled: true,
  ignoreBlank: true,
  ignoreAsterisk: true,
  sortOrder: 0
})

const createEmptyValueRule = () => ({
  sourceFieldName: '',
  matchType: 'contains',
  matchExpr: '',
  targetFieldName: '',
  targetValue: '',
  enabled: true,
  sortOrder: 0
})

const editForm = reactive({
  id: null,
  name: '',
  enabled: true,
  sourceCategoryPath: '',
  targetCategoryId: '',
  targetCategoryName: '',
  targetParentSpecName: '型号',
  sourceSignature: '',
  mainFieldCandidates: [],
  fieldMappings: [createEmptyFieldMapping()],
  valueRules: [],
  autoIgnoreConstantFields: true,
  notes: ''
})

const columns = [
  { title: '模板名称', dataIndex: 'name', key: 'name', width: 220 },
  { title: '来源类目', dataIndex: 'sourceCategoryPath', key: 'sourceCategoryPath' },
  { title: '结构摘要', key: 'summary', width: 320 },
  { title: '状态', key: 'enabled', width: 90 },
  { title: '操作', key: 'actions', width: 160 }
]

const assignForm = (record = null) => {
  editForm.id = record?.id ?? null
  editForm.name = record?.name || ''
  editForm.enabled = record?.enabled !== false
  editForm.sourceCategoryPath = record?.sourceCategoryPath || ''
  editForm.targetCategoryId = record?.targetCategoryId || ''
  editForm.targetCategoryName = record?.targetCategoryName || ''
  editForm.targetParentSpecName = record?.targetParentSpecName || '型号'
  editForm.sourceSignature = record?.sourceSignature || ''
  editForm.mainFieldCandidates = Array.isArray(record?.mainFieldCandidates) ? [...record.mainFieldCandidates] : []
  editForm.fieldMappings = Array.isArray(record?.fieldMappings) && record.fieldMappings.length
    ? record.fieldMappings.map((item, index) => ({ ...createEmptyFieldMapping(), ...item, sortOrder: index }))
    : [createEmptyFieldMapping()]
  editForm.valueRules = Array.isArray(record?.valueRules)
    ? record.valueRules.map((item, index) => ({ ...createEmptyValueRule(), ...item, sortOrder: index }))
    : []
  editForm.autoIgnoreConstantFields = record?.autoIgnoreConstantFields !== false
  editForm.notes = record?.notes || ''
}

const normalizePayload = () => ({
  name: String(editForm.name || '').trim(),
  enabled: !!editForm.enabled,
  sourceCategoryPath: String(editForm.sourceCategoryPath || '').trim() || null,
  targetCategoryId: String(editForm.targetCategoryId || '').trim() || null,
  targetCategoryName: String(editForm.targetCategoryName || '').trim() || null,
  targetParentSpecName: String(editForm.targetParentSpecName || '').trim() || '型号',
  sourceSignature: String(editForm.sourceSignature || '').trim() || null,
  mainFieldCandidates: (editForm.mainFieldCandidates || []).map((item) => String(item || '').trim()).filter(Boolean),
  fieldMappings: (editForm.fieldMappings || [])
    .map((item, index) => ({
      ...item,
      sourceFieldName: String(item.sourceFieldName || '').trim(),
      targetFieldName: String(item.targetFieldName || '').trim(),
      role: String(item.role || '').trim() || 'main_candidate',
      enabled: item.enabled !== false,
      ignoreBlank: item.ignoreBlank !== false,
      ignoreAsterisk: item.ignoreAsterisk !== false,
      sortOrder: index
    }))
    .filter((item) => item.sourceFieldName),
  valueRules: (editForm.valueRules || [])
    .map((item, index) => ({
      ...item,
      sourceFieldName: String(item.sourceFieldName || '').trim(),
      matchType: String(item.matchType || '').trim() || 'contains',
      matchExpr: String(item.matchExpr || '').trim(),
      targetFieldName: String(item.targetFieldName || '').trim(),
      targetValue: String(item.targetValue || '').trim(),
      enabled: item.enabled !== false,
      sortOrder: index
    }))
    .filter((item) => item.sourceFieldName && item.matchExpr && item.targetFieldName && item.targetValue),
  autoIgnoreConstantFields: !!editForm.autoIgnoreConstantFields,
  notes: String(editForm.notes || '').trim() || null
})

const reload = async () => {
  loading.value = true
  try {
    const res = await specMappingApi.listProfiles()
    if (res?.success) {
      rows.value = Array.isArray(res.data) ? res.data : []
      return
    }
    message.error(res?.message || '加载失败')
  } catch (error) {
    message.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  assignForm()
  editOpen.value = true
}

const openEdit = (record) => {
  assignForm(record)
  editOpen.value = true
}

const addFieldMapping = () => {
  editForm.fieldMappings.push({ ...createEmptyFieldMapping(), sortOrder: editForm.fieldMappings.length })
}

const addValueRule = () => {
  editForm.valueRules.push({ ...createEmptyValueRule(), sortOrder: editForm.valueRules.length })
}

const saveProfile = async () => {
  const payload = normalizePayload()
  if (!payload.name) {
    message.error('请输入模板名称')
    return
  }
  if (!payload.fieldMappings.length) {
    message.error('至少保留一条字段映射')
    return
  }

  saving.value = true
  try {
    const res = editForm.id
      ? await specMappingApi.updateProfile(editForm.id, payload)
      : await specMappingApi.createProfile(payload)
    if (res?.success) {
      message.success('已保存')
      editOpen.value = false
      await reload()
      return
    }
    message.error(res?.message || '保存失败')
  } catch (error) {
    message.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const removeProfile = (record) => {
  if (!record?.id) return
  Modal.confirm({
    title: '删除模板？',
    content: '删除后不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await specMappingApi.deleteProfile(record.id)
        if (res?.success) {
          message.success('已删除')
          await reload()
          return
        }
        message.error(res?.message || '删除失败')
      } catch (error) {
        message.error(error.message || '删除失败')
      }
    }
  })
}

onMounted(() => {
  reload()
})
</script>

<style scoped>
.mapping-page {
  display: grid;
  gap: 16px;
}

.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 28px;
  border-radius: 24px;
  background:
    radial-gradient(circle at top left, rgba(14, 165, 233, 0.22), transparent 32%),
    radial-gradient(circle at right center, rgba(251, 191, 36, 0.18), transparent 28%),
    linear-gradient(135deg, #fffaf0 0%, #ffffff 52%, #f0f9ff 100%);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.hero-kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #0f766e;
}

.hero-card h1 {
  margin: 8px 0;
  font-size: 28px;
}

.hero-card p {
  margin: 0;
  max-width: 760px;
  color: #475569;
}

.hero-actions {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.summary-cell {
  display: grid;
  gap: 4px;
  color: #334155;
}

.modal-grid {
  max-height: 72vh;
  overflow: auto;
}

.section-head {
  margin: 8px 0 10px;
  font-weight: 700;
  color: #0f172a;
}

.mapping-list,
.rule-list {
  display: grid;
  gap: 8px;
}

.mapping-row,
.rule-row {
  display: grid;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #f8fafc;
}

.mapping-row {
  grid-template-columns: 72px 1.1fr 1.1fr 140px 92px 92px 60px;
}

.rule-row {
  grid-template-columns: 72px 0.9fr 120px 1.1fr 0.8fr 0.8fr 60px;
}

.mapping-head,
.rule-head {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  background: transparent;
  border-style: dashed;
}

.ghost-btn {
  margin-top: 10px;
}

.form-footer {
  display: flex;
  justify-content: space-between;
  margin: 18px 0;
}

@media (max-width: 1100px) {
  .hero-card,
  .form-footer {
    flex-direction: column;
  }

  .mapping-row,
  .rule-row {
    grid-template-columns: 1fr;
  }

  .mapping-head,
  .rule-head {
    display: none;
  }
}
</style>