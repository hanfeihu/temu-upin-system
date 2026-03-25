<template>
  <ProLayout title="1688 与 TEMU 规格映射工作台">
    <div class="workbench-page">
      <section class="top-banner">
        <div>
          <div class="banner-kicker">SPU {{ spuId }}</div>
          <h1>{{ workbench.productName || '规格映射工作台' }}</h1>
          <p>{{ workbench.sourceCategoryPath || '未识别原始类目' }}</p>
          <div class="banner-tags">
            <a-tag color="blue">TEMU 类目 {{ workbench.targetCategoryName || workbench.targetCategoryId || '未绑定' }}</a-tag>
            <a-tag color="gold">模板 {{ (workbench.profiles || []).length }}</a-tag>
          </div>
        </div>
        <div class="banner-actions">
          <a-button @click="goBack">返回商品详情</a-button>
          <a-button @click="goProfiles">规则中心</a-button>
          <a-button @click="reload" :loading="loading">刷新数据</a-button>
          <a-button type="primary" @click="saveDraft" :loading="saving">保存草案</a-button>
        </div>
      </section>

      <div class="panel-grid">
        <section class="panel source-panel">
          <div class="panel-title-row">
            <h2>1688 来源规格结构</h2>
            <a-tag color="blue">{{ workbench.sourceSignature || '未生成签名' }}</a-tag>
          </div>
          <div class="panel-tip">
            左侧都是 1688 原始规格。这里的“来源签名”是当前规格结构的指纹，比如 尺寸#12|颜色#3，表示字段名和各自去重值数量。
          </div>
          <div class="field-cards">
            <div v-for="field in workbench.sourceFields || []" :key="field.sourceFieldName" class="field-card">
              <div class="field-name">{{ field.sourceFieldName }}</div>
              <div class="field-meta">去重值 {{ field.distinctCount || 0 }}</div>
              <div class="field-meta">总值 {{ field.valueCount || 0 }}</div>
              <div class="sample-list">{{ (field.sampleValues || []).join(' / ') || '-' }}</div>
            </div>
          </div>
        </section>

        <section class="panel config-panel">
          <div class="panel-title-row">
            <h2>TEMU 目标映射配置</h2>
            <a-space>
              <a-select
                v-model:value="form.profileId"
                allow-clear
                placeholder="套用规则模板"
                style="width: 260px"
                @change="applyProfileById"
              >
                <a-select-option v-for="item in workbench.profiles || []" :key="item.id" :value="item.id">
                  {{ item.name }}
                </a-select-option>
              </a-select>
              <a-button @click="previewNow" :loading="previewLoading">重新预演</a-button>
            </a-space>
          </div>

          <a-row :gutter="16">
            <a-col :span="8">
              <a-form-item label="TEMU 父规格名称">
                <a-select v-model:value="form.targetParentSpecName" :options="parentSpecOptions" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="主分组字段（SKC 维度）">
                <a-select v-model:value="form.selectedMainField" allow-clear :options="candidateFieldOptions" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="组内 SKU 维度（同一 SKC 下继续区分）">
                <a-select v-model:value="form.selectedSkuFields" mode="multiple" :options="skuFieldOptions" @change="previewNow" />
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="候选主字段池（从 1688 字段里圈定谁可以做主规格）">
                <a-select
                  v-model:value="form.mainFieldCandidates"
                  mode="multiple"
                  :options="sourceFieldOptions"
                  placeholder="限制哪些字段可以参与主字段选择"
                  @change="previewNow"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <div class="switch-line">
            <span>自动忽略恒定字段</span>
            <a-switch v-model:checked="form.autoIgnoreConstantFields" @change="previewNow" />
          </div>

          <div class="editor-block">
            <div class="editor-title">字段映射</div>
            <div class="editor-tip">把 1688 规格字段归一成你在 TEMU 映射里要使用的字段名。左边是 1688，右边是归一后的目标字段。</div>
            <div class="edit-grid field-grid header-row">
              <span>启用</span>
              <span>1688 规格字段</span>
              <span>归一后目标字段</span>
              <span>角色</span>
              <span>忽略空值</span>
              <span>忽略 *</span>
              <span></span>
            </div>
            <div v-for="(item, index) in form.fieldMappings" :key="index" class="edit-grid field-grid">
              <a-switch v-model:checked="item.enabled" />
              <a-input v-model:value="item.sourceFieldName" placeholder="尺寸" />
              <a-input v-model:value="item.targetFieldName" placeholder="型号" />
              <a-select v-model:value="item.role" :options="roleOptions" />
              <a-switch v-model:checked="item.ignoreBlank" />
              <a-switch v-model:checked="item.ignoreAsterisk" />
              <a-button danger @click="form.fieldMappings.splice(index, 1)">删</a-button>
            </div>
            <a-button class="editor-btn" @click="addFieldMapping">新增字段映射</a-button>
          </div>

          <div class="editor-block">
            <div class="editor-title">值归并规则</div>
            <div class="editor-tip">当 1688 原值过碎时，在这里把多个值归并成 TEMU 侧更适合的值。</div>
            <div class="edit-grid rule-grid header-row">
              <span>启用</span>
              <span>1688 规格字段</span>
              <span>匹配方式</span>
              <span>匹配表达式</span>
              <span>归一后目标字段</span>
              <span>TEMU 使用值</span>
              <span></span>
            </div>
            <div v-for="(item, index) in form.valueRules" :key="index" class="edit-grid rule-grid">
              <a-switch v-model:checked="item.enabled" />
              <a-input v-model:value="item.sourceFieldName" placeholder="尺寸" />
              <a-select v-model:value="item.matchType" :options="matchTypeOptions" />
              <a-input v-model:value="item.matchExpr" placeholder="例如 30件 / 套餐" />
              <a-input v-model:value="item.targetFieldName" placeholder="型号" />
              <a-input v-model:value="item.targetValue" placeholder="袋装" />
              <a-button danger @click="form.valueRules.splice(index, 1)">删</a-button>
            </div>
            <a-button class="editor-btn" @click="addValueRule">新增值规则</a-button>
          </div>
        </section>
      </div>

      <div class="result-grid">
        <section class="panel result-panel">
          <div class="panel-title-row">
            <h2>预演结果</h2>
            <a-space>
              <a-tag :color="preview.publishable ? 'green' : 'red'">{{ preview.publishable ? '可继续保存' : '需调整规则' }}</a-tag>
              <a-tag color="gold">SKC {{ preview.skcCount || 0 }}</a-tag>
              <a-tag color="cyan">SKU {{ preview.skuCount || 0 }}</a-tag>
            </a-space>
          </div>

          <div class="validation-list">
            <div v-for="(item, index) in preview.validations || []" :key="index" class="validation-item" :class="item.severity">
              <strong>{{ item.code }}</strong>
              <span>{{ item.message }}</span>
            </div>
          </div>

          <div class="candidate-wrap">
            <div v-for="item in preview.candidateFields || []" :key="item.fieldName" class="candidate-card">
              <div class="candidate-name">{{ item.fieldName }}</div>
              <div class="candidate-meta">去重值 {{ item.distinctCount || 0 }}</div>
              <div class="candidate-meta">{{ item.mainCandidate ? '可做主字段' : '仅建议组内维度' }}</div>
              <div class="sample-list">{{ (item.sampleValues || []).join(' / ') || '-' }}</div>
            </div>
          </div>
        </section>

        <section class="panel group-panel">
          <div class="panel-title-row">
            <h2>SKC 分组预览</h2>
          </div>
          <div class="group-list">
            <div v-for="item in preview.groups || []" :key="item.groupKey" class="group-card">
              <div class="group-head">
                <div class="group-name">{{ item.mainFieldValue }}</div>
                <a-tag color="blue">{{ item.skuCount || 0 }} SKU</a-tag>
              </div>
              <div class="group-rows">
                <div v-for="row in item.rows || []" :key="row.id || row.specKey" class="group-row">
                  <span class="mono">{{ row.specKey || row.originSkuId || '-' }}</span>
                  <span>{{ renderFieldSummary(row.normalizedFields) }}</span>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { specMappingApi } from '@/platform/api/specMappings'

const route = useRoute()
const router = useRouter()

const spuId = computed(() => String(route.params.spuId || ''))
const loading = ref(false)
const previewLoading = ref(false)
const saving = ref(false)

const workbench = reactive({
  productName: '',
  sourceCategoryPath: '',
  targetCategoryName: '',
  targetCategoryId: '',
  sourceSignature: '',
  targetParentSpecOptions: [],
  sourceFields: [],
  sourceRows: [],
  profiles: [],
  latestDraft: null
})

const preview = reactive({
  targetParentSpecName: '型号',
  selectedMainField: '',
  selectedSkuFields: [],
  skcCount: 0,
  skuCount: 0,
  publishable: false,
  candidateFields: [],
  normalizedRows: [],
  groups: [],
  validations: []
})

const fallbackParentSpecOptions = [
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

const createFieldMapping = () => ({
  sourceFieldName: '',
  targetFieldName: '',
  role: 'main_candidate',
  enabled: true,
  ignoreBlank: true,
  ignoreAsterisk: true,
  sortOrder: 0
})

const createValueRule = () => ({
  sourceFieldName: '',
  matchType: 'contains',
  matchExpr: '',
  targetFieldName: '',
  targetValue: '',
  enabled: true,
  sortOrder: 0
})

const form = reactive({
  profileId: null,
  targetParentSpecName: '型号',
  selectedMainField: null,
  selectedSkuFields: [],
  fieldMappings: [],
  valueRules: [],
  mainFieldCandidates: [],
  autoIgnoreConstantFields: true
})

const candidateFieldOptions = computed(() => (preview.candidateFields || []).map((item) => ({ label: `${item.fieldName} (${item.distinctCount})`, value: item.fieldName })))
const sourceFieldOptions = computed(() => (workbench.sourceFields || []).map((item) => ({ label: item.sourceFieldName, value: item.sourceFieldName })))
const parentSpecOptions = computed(() => {
  const options = (workbench.targetParentSpecOptions || []).map((item) => ({
    label: item.parentSpecName,
    value: item.parentSpecName
  }))
  return options.length ? options : fallbackParentSpecOptions
})
const skuFieldOptions = computed(() => (preview.candidateFields || [])
  .filter((item) => item.fieldName !== form.selectedMainField)
  .map((item) => ({ label: `${item.fieldName} (${item.distinctCount})`, value: item.fieldName })))

const assignPreview = (data = {}) => {
  preview.targetParentSpecName = data?.targetParentSpecName || '型号'
  preview.selectedMainField = data?.selectedMainField || ''
  preview.selectedSkuFields = Array.isArray(data?.selectedSkuFields) ? [...data.selectedSkuFields] : []
  preview.skcCount = Number(data?.skcCount || 0)
  preview.skuCount = Number(data?.skuCount || 0)
  preview.publishable = !!data?.publishable
  preview.candidateFields = Array.isArray(data?.candidateFields) ? data.candidateFields : []
  preview.normalizedRows = Array.isArray(data?.normalizedRows) ? data.normalizedRows : []
  preview.groups = Array.isArray(data?.groups) ? data.groups : []
  preview.validations = Array.isArray(data?.validations) ? data.validations : []
}

const assignFormFromProfile = (profile) => {
  form.profileId = profile?.id ?? null
  form.targetParentSpecName = profile?.targetParentSpecName || '型号'
  form.selectedMainField = null
  form.selectedSkuFields = []
  form.mainFieldCandidates = Array.isArray(profile?.mainFieldCandidates) ? [...profile.mainFieldCandidates] : []
  form.autoIgnoreConstantFields = profile?.autoIgnoreConstantFields !== false
  form.fieldMappings = Array.isArray(profile?.fieldMappings) && profile.fieldMappings.length
    ? profile.fieldMappings.map((item, index) => ({ ...createFieldMapping(), ...item, sortOrder: index }))
    : (workbench.sourceFields || []).map((item, index) => ({
        ...createFieldMapping(),
        sourceFieldName: item.sourceFieldName,
        targetFieldName: item.sourceFieldName,
        role: index === 0 ? 'main_candidate' : 'sku_dimension',
        sortOrder: index
      }))
  form.valueRules = Array.isArray(profile?.valueRules)
    ? profile.valueRules.map((item, index) => ({ ...createValueRule(), ...item, sortOrder: index }))
    : []
}

const assignFormFromDraft = (draft) => {
  form.profileId = draft?.profileId ?? null
  form.targetParentSpecName = draft?.targetParentSpecName || '型号'
  form.selectedMainField = draft?.selectedMainField || null
  form.selectedSkuFields = Array.isArray(draft?.selectedSkuFields) ? [...draft.selectedSkuFields] : []
  form.fieldMappings = Array.isArray(draft?.fieldMappings)
    ? draft.fieldMappings.map((item, index) => ({ ...createFieldMapping(), ...item, sortOrder: index }))
    : []
  form.valueRules = Array.isArray(draft?.valueRules)
    ? draft.valueRules.map((item, index) => ({ ...createValueRule(), ...item, sortOrder: index }))
    : []
  form.mainFieldCandidates = []
  form.autoIgnoreConstantFields = draft?.autoIgnoreConstantFields !== false
}

const buildPayload = () => ({
  profileId: form.profileId || null,
  targetParentSpecName: String(form.targetParentSpecName || '').trim() || '型号',
  selectedMainField: form.selectedMainField || null,
  selectedSkuFields: (form.selectedSkuFields || []).filter(Boolean),
  fieldMappings: (form.fieldMappings || []).map((item, index) => ({
    ...item,
    sourceFieldName: String(item.sourceFieldName || '').trim(),
    targetFieldName: String(item.targetFieldName || '').trim(),
    role: String(item.role || '').trim() || 'main_candidate',
    enabled: item.enabled !== false,
    ignoreBlank: item.ignoreBlank !== false,
    ignoreAsterisk: item.ignoreAsterisk !== false,
    sortOrder: index
  })).filter((item) => item.sourceFieldName),
  valueRules: (form.valueRules || []).map((item, index) => ({
    ...item,
    sourceFieldName: String(item.sourceFieldName || '').trim(),
    matchType: String(item.matchType || '').trim() || 'contains',
    matchExpr: String(item.matchExpr || '').trim(),
    targetFieldName: String(item.targetFieldName || '').trim(),
    targetValue: String(item.targetValue || '').trim(),
    enabled: item.enabled !== false,
    sortOrder: index
  })).filter((item) => item.sourceFieldName && item.matchExpr && item.targetFieldName && item.targetValue),
  mainFieldCandidates: (form.mainFieldCandidates || []).filter(Boolean),
  autoIgnoreConstantFields: !!form.autoIgnoreConstantFields
})

const applyProfileById = (profileId) => {
  const profile = (workbench.profiles || []).find((item) => item.id === profileId)
  assignFormFromProfile(profile)
  previewNow()
}

const syncSelectedSkuFields = () => {
  form.selectedSkuFields = (form.selectedSkuFields || []).filter((item) => item && item !== form.selectedMainField)
}

const addFieldMapping = () => {
  form.fieldMappings.push({ ...createFieldMapping(), sortOrder: form.fieldMappings.length })
}

const addValueRule = () => {
  form.valueRules.push({ ...createValueRule(), sortOrder: form.valueRules.length })
}

const reload = async () => {
  if (!spuId.value) return
  loading.value = true
  try {
    const res = await specMappingApi.getWorkbench(spuId.value)
    if (!res?.success) {
      message.error(res?.message || '加载失败')
      return
    }

    const data = res.data || {}
    workbench.productName = data.productName || ''
    workbench.sourceCategoryPath = data.sourceCategoryPath || ''
    workbench.targetCategoryName = data.targetCategoryName || ''
    workbench.targetCategoryId = data.targetCategoryId || ''
    workbench.sourceSignature = data.sourceSignature || ''
    workbench.targetParentSpecOptions = Array.isArray(data.targetParentSpecOptions) ? data.targetParentSpecOptions : []
    workbench.sourceFields = Array.isArray(data.sourceFields) ? data.sourceFields : []
    workbench.sourceRows = Array.isArray(data.sourceRows) ? data.sourceRows : []
    workbench.profiles = Array.isArray(data.profiles) ? data.profiles : []
    workbench.latestDraft = data.latestDraft || null

    if (workbench.latestDraft) {
      assignFormFromDraft(workbench.latestDraft)
      assignPreview(workbench.latestDraft.preview || {})
      return
    }

    const firstProfile = workbench.profiles[0] || null
    assignFormFromProfile(firstProfile)
    await previewNow()
  } catch (error) {
    message.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const previewNow = async () => {
  if (!spuId.value) return
  previewLoading.value = true
  try {
    const res = await specMappingApi.preview(spuId.value, buildPayload())
    if (res?.success) {
      assignPreview(res.data || {})
      if (!form.selectedMainField) {
        form.selectedMainField = res.data?.selectedMainField || null
      }
      if (!form.selectedSkuFields?.length) {
        form.selectedSkuFields = Array.isArray(res.data?.selectedSkuFields) ? [...res.data.selectedSkuFields] : []
      }
      return
    }
    message.error(res?.message || '预演失败')
  } catch (error) {
    message.error(error.message || '预演失败')
  } finally {
    previewLoading.value = false
  }
}

const saveDraft = async () => {
  if (!spuId.value) return
  saving.value = true
  try {
    const payload = {
      ...buildPayload(),
      draftName: `${workbench.productName || '商品'} 规格映射草案`,
      active: true
    }
    const res = await specMappingApi.saveDraft(spuId.value, payload)
    if (res?.success) {
      message.success('草案已保存')
      workbench.latestDraft = res.data || null
      assignPreview(res.data?.preview || {})
      return
    }
    message.error(res?.message || '保存失败')
  } catch (error) {
    message.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const renderFieldSummary = (fields) => Object.entries(fields || {}).map(([key, value]) => `${key}:${value}`).join(' / ')
const goBack = () => router.push(`/platform/product-collections/${spuId.value}`)
const goProfiles = () => router.push('/platform/spec-mappings/profiles')

watch(() => form.selectedMainField, () => {
  syncSelectedSkuFields()
  if (spuId.value) {
    previewNow()
  }
})

watch(() => form.targetParentSpecName, () => {
  if (spuId.value) {
    previewNow()
  }
})

onMounted(() => {
  reload()
})
</script>

<style scoped>
.workbench-page {
  display: grid;
  gap: 18px;
}

.top-banner {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 28px;
  border-radius: 24px;
  background:
    radial-gradient(circle at left top, rgba(249, 115, 22, 0.18), transparent 34%),
    radial-gradient(circle at right bottom, rgba(20, 184, 166, 0.16), transparent 32%),
    linear-gradient(135deg, #fff7ed 0%, #ffffff 50%, #ecfeff 100%);
  border: 1px solid rgba(251, 146, 60, 0.14);
}

.banner-kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #c2410c;
}

.top-banner h1 {
  margin: 8px 0;
  font-size: 28px;
}

.top-banner p {
  margin: 0;
  color: #475569;
}

.banner-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.banner-actions {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.panel-grid,
.result-grid {
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  gap: 18px;
}

.panel {
  padding: 20px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.06);
}

.panel-title-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
}

.panel-title-row h2 {
  margin: 0;
  font-size: 18px;
}

.panel-tip,
.editor-tip {
  margin-bottom: 12px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.field-cards,
.candidate-wrap,
.group-list {
  display: grid;
  gap: 12px;
}

.field-card,
.candidate-card,
.group-card {
  padding: 14px 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.field-name,
.candidate-name,
.group-name {
  font-weight: 700;
  color: #0f172a;
}

.field-meta,
.candidate-meta {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.sample-list {
  margin-top: 8px;
  font-size: 13px;
  color: #334155;
  word-break: break-all;
}

.switch-line {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f8fafc;
}

.editor-block {
  margin-top: 18px;
}

.editor-title {
  margin-bottom: 10px;
  font-weight: 700;
}

.edit-grid {
  display: grid;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  margin-bottom: 8px;
}

.field-grid {
  grid-template-columns: 72px 1fr 1fr 140px 92px 92px 60px;
}

.rule-grid {
  grid-template-columns: 72px 0.9fr 120px 1.2fr 0.9fr 0.9fr 60px;
}

.header-row {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  background: transparent;
  border-style: dashed;
}

.editor-btn {
  margin-top: 4px;
}

.validation-list {
  display: grid;
  gap: 10px;
}

.validation-item {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 14px;
}

.validation-item.error {
  background: #fff1f2;
  color: #be123c;
}

.validation-item.warning {
  background: #fff7ed;
  color: #c2410c;
}

.validation-item.info {
  background: #ecfeff;
  color: #0f766e;
}

.group-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 10px;
}

.group-rows {
  display: grid;
  gap: 6px;
}

.group-row {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.mono {
  font-family: 'SFMono-Regular', 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #475569;
}

@media (max-width: 1280px) {
  .panel-grid,
  .result-grid,
  .top-banner {
    grid-template-columns: 1fr;
    flex-direction: column;
  }
}

@media (max-width: 1100px) {
  .field-grid,
  .rule-grid {
    grid-template-columns: 1fr;
  }

  .header-row {
    display: none;
  }
}
</style>