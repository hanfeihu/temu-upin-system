import { App, Button, Input, Modal, Select, Space, Spin, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { productCollectionsApi } from '@/api/productCollections';
import type { ProductCollectionRow, TemuAttrRuleVO } from '@/types/api';

interface TemuAttributesModalProps {
  open: boolean;
  record: ProductCollectionRow | null;
  onClose: () => void;
}

interface TemuAttributeValueOption {
  vid?: string | number | null;
  value?: string | null;
}

interface TemuAttributeParentRule {
  parentVidList?: Array<string | number>;
  vidList?: Array<string | number>;
}

interface TemuAttributeShowCondition {
  parentRefPid?: string | number | null;
  parentVids?: Array<string | number>;
}

interface TemuAttributeProperty {
  pid?: string | number | null;
  templatePid?: string | number | null;
  refPid?: string | number | null;
  parentTemplatePid?: string | number | null;
  name?: string | null;
  required?: boolean | null;
  values?: TemuAttributeValueOption[];
  templatePropertyValueParentList?: TemuAttributeParentRule[];
  showCondition?: TemuAttributeShowCondition[];
  chooseMaxNum?: number | null;
  numberInputTitle?: string | null;
  controlType?: number | null;
  valueUnit?: Array<string | number>;
  __rule?: TemuAttrRuleVO | null;
}

interface TemuAttributesPayloadProperty {
  pid?: string | number | null;
  templatePid?: string | number | null;
  refPid?: string | number | null;
  freeText?: string | null;
  selectedVids?: Array<string | number>;
  numberInputValue?: string | number | null;
}

interface TemuAttributesSavedPayload {
  values?: Record<string, string | number | null>;
  properties?: TemuAttributesPayloadProperty[];
}

interface TemuAiFillResponse {
  success?: boolean;
  errorMsg?: string;
  properties?: TemuAttributesPayloadProperty[];
  missingRequiredPids?: Array<string | number>;
  warnings?: string[];
}

type AttrValueState = Record<string, string | string[]>;
type AttrNumberState = Record<string, string>;

function safeJsonParse<T>(value?: string | null): T | null {
  if (!value || typeof value !== 'string') {
    return null;
  }

  try {
    return JSON.parse(value) as T;
  } catch {
    return null;
  }
}

function attrKey(attr?: TemuAttributeProperty | null) {
  if (!attr) {
    return '';
  }
  return `tp:${attr.templatePid ?? ''}|pid:${attr.pid ?? ''}`;
}

function isChildAttr(attr?: TemuAttributeProperty | null) {
  return Array.isArray(attr?.templatePropertyValueParentList) && attr!.templatePropertyValueParentList!.length > 0;
}

function hasShowCondition(attr?: TemuAttributeProperty | null) {
  return Array.isArray(attr?.showCondition) && attr!.showCondition!.length > 0;
}

function isConditionalAttr(attr?: TemuAttributeProperty | null) {
  return isChildAttr(attr) || hasShowCondition(attr);
}

function hasAttrSelectableValues(attr?: TemuAttributeProperty | null) {
  return Array.isArray(attr?.values) && attr!.values!.length > 0;
}

function hasAttrNumberInput(attr?: TemuAttributeProperty | null) {
  if (!hasAttrSelectableValues(attr)) {
    return false;
  }

  if (String(attr?.numberInputTitle || '').trim()) {
    return true;
  }

  return Number(attr?.controlType || 0) === 16;
}

function attrNumberInputPlaceholder(attr?: TemuAttributeProperty | null) {
  const title = String(attr?.numberInputTitle || '').trim();
  return title ? `请输入${title}` : '请输入数值';
}

function attrUnitText(attr?: TemuAttributeProperty | null) {
  if (!Array.isArray(attr?.valueUnit) || !attr.valueUnit.length) {
    return '';
  }
  return String(attr.valueUnit[0] ?? '').trim();
}

function collectSelectedVids(groups: TemuAttributeProperty[], values: AttrValueState) {
  const selected = new Set<string>();
  groups.forEach((group) => {
    const raw = values[attrKey(group)];
    const list = Array.isArray(raw) ? raw : raw ? [raw] : [];
    list.forEach((item) => {
      const text = String(item || '').trim();
      if (text) {
        selected.add(text);
      }
    });
  });
  return selected;
}

function findParentPropOfChild(groups: TemuAttributeProperty[], child?: TemuAttributeProperty | null) {
  const templatePid = child?.parentTemplatePid;
  if (templatePid == null || templatePid === '') {
    return null;
  }
  return groups.find((item) => String(item?.templatePid ?? '') === String(templatePid)) || null;
}

function findParentPropByRefPid(groups: TemuAttributeProperty[], refPid?: string | number | null) {
  if (refPid == null || refPid === '') {
    return null;
  }
  return groups.find((item) => String(item?.refPid ?? '') === String(refPid)) || null;
}

function collectSelectedVidsByRefPid(groups: TemuAttributeProperty[], values: AttrValueState) {
  const selectedByRefPid = new Map<string, Set<string>>();
  groups.forEach((group) => {
    const refPid = group?.refPid;
    if (refPid == null || refPid === '') {
      return;
    }

    const raw = values[attrKey(group)];
    const list = Array.isArray(raw) ? raw : raw ? [raw] : [];
    list.forEach((item) => {
      const text = String(item || '').trim();
      if (!text) {
        return;
      }
      const key = String(refPid);
      if (!selectedByRefPid.has(key)) {
        selectedByRefPid.set(key, new Set<string>());
      }
      selectedByRefPid.get(key)?.add(text);
    });
  });
  return selectedByRefPid;
}

function buildShowConditionHint(groups: TemuAttributeProperty[], conditions?: TemuAttributeShowCondition[]) {
  for (const condition of conditions || []) {
    const parentProp = findParentPropByRefPid(groups, condition?.parentRefPid);
    const expectVids = new Set((Array.isArray(condition?.parentVids) ? condition.parentVids : []).map((item) => String(item)));
    if (parentProp?.values?.length) {
      const labels = parentProp.values
        .filter((item) => expectVids.has(String(item?.vid)))
        .map((item) => item?.value)
        .filter(Boolean);
      return `请先选择父属性：${parentProp.name || '父属性'}${labels.length ? `（可选：${labels.slice(0, 6).join(' / ')}${labels.length > 6 ? ' ...' : ''}）` : ''}`;
    }
  }

  return '请先选择父属性后再填写此项';
}

function getAttrApplicabilityInfo(groups: TemuAttributeProperty[], values: AttrValueState, attr?: TemuAttributeProperty | null) {
  const all = (attr?.values || []).map((item) => ({
    value: String(item?.vid),
    label: String(item?.value || ''),
  }));

  let allowed = all;
  let parentHint = '';
  let parentRuleActive = true;

  if (isChildAttr(attr)) {
    const selectedParents = collectSelectedVids(groups, values);
    const allowedSet = new Set<string>();
    let hasParentSelected = false;

    (attr?.templatePropertyValueParentList || []).forEach((rule) => {
      const parents = Array.isArray(rule?.parentVidList) ? rule.parentVidList : [];
      const vids = Array.isArray(rule?.vidList) ? rule.vidList : [];
      parents.forEach((parentVid) => {
        if (selectedParents.has(String(parentVid))) {
          hasParentSelected = true;
          vids.forEach((vid) => allowedSet.add(String(vid)));
        }
      });
    });

    parentRuleActive = hasParentSelected;
    allowed = hasParentSelected ? all.filter((item) => allowedSet.has(String(item.value))) : [];

    if (!hasParentSelected) {
      const parentProp = findParentPropOfChild(groups, attr);
      if (parentProp?.values?.length) {
        const parentEnableVids = new Set<string>();
        (attr?.templatePropertyValueParentList || []).forEach((rule) => {
          (Array.isArray(rule?.parentVidList) ? rule.parentVidList : []).forEach((parentVid) => {
            parentEnableVids.add(String(parentVid));
          });
        });
        const labels = (parentProp.values || [])
          .filter((item) => parentEnableVids.has(String(item?.vid)))
          .map((item) => item?.value)
          .filter(Boolean);
        parentHint = `请先选择父属性：${parentProp.name || '父属性'}${labels.length ? `（可选：${labels.slice(0, 6).join(' / ')}${labels.length > 6 ? ' ...' : ''}）` : ''}`;
      } else {
        parentHint = '请先选择父属性后再填写此项';
      }
    }
  }

  let showConditionActive = true;
  if (hasShowCondition(attr)) {
    const selectedByRefPid = collectSelectedVidsByRefPid(groups, values);
    for (const condition of attr?.showCondition || []) {
      const refPid = String(condition?.parentRefPid ?? '');
      const expectVids = new Set((Array.isArray(condition?.parentVids) ? condition.parentVids : []).map((item) => String(item)));
      const selected = selectedByRefPid.get(refPid) || new Set<string>();
      const matched = Array.from(selected).some((item) => expectVids.has(String(item)));
      if (!matched) {
        showConditionActive = false;
        if (!parentHint) {
          parentHint = buildShowConditionHint(groups, attr?.showCondition);
        }
        break;
      }
    }
  }

  return {
    allowed,
    isActive: parentRuleActive && showConditionActive,
    parentHint,
  };
}

function cleanupInvalidSelections(groups: TemuAttributeProperty[], values: AttrValueState, numberValues: AttrNumberState) {
  const nextValues: AttrValueState = { ...values };
  const nextNumberValues: AttrNumberState = { ...numberValues };

  groups.forEach((group) => {
    if (!isConditionalAttr(group)) {
      return;
    }

    const key = attrKey(group);
    const raw = nextValues[key];
    if (raw === undefined || raw === null) {
      return;
    }

    const info = getAttrApplicabilityInfo(groups, nextValues, group);
    if (!info.isActive) {
      delete nextValues[key];
      delete nextNumberValues[key];
      return;
    }

    if (!isChildAttr(group)) {
      return;
    }

    const allowedSet = new Set((info.allowed || []).map((item) => String(item.value)));
    const selected = (Array.isArray(raw) ? raw : [raw]).map((item) => String(item));
    const filtered = selected.filter((item) => allowedSet.has(item));

    if (!filtered.length) {
      delete nextValues[key];
      delete nextNumberValues[key];
      return;
    }

    nextValues[key] = filtered.length > 1 ? filtered : filtered[0];
  });

  return {
    values: nextValues,
    numberValues: nextNumberValues,
  };
}

function buildSavedValueState(saved?: TemuAttributesSavedPayload | null) {
  const values: AttrValueState = {};
  const numberValues: AttrNumberState = {};

  if (!saved || typeof saved !== 'object') {
    return { values, numberValues };
  }

  const props = Array.isArray(saved.properties) ? saved.properties : [];
  if (props.length) {
    props.forEach((item) => {
      const pid = item?.pid;
      if (pid == null || pid === '') {
        return;
      }

      const key = item?.templatePid ? `tp:${item.templatePid}|pid:${pid}` : `pid:${pid}`;
      if (item?.freeText) {
        values[key] = String(item.freeText);
      } else {
        const vids = Array.isArray(item?.selectedVids) ? item.selectedVids.map((vid) => String(vid)) : [];
        if (vids.length > 1) {
          values[key] = vids;
        } else if (vids.length === 1) {
          values[key] = vids[0];
        }
      }

      if (item?.numberInputValue != null && String(item.numberInputValue).trim() !== '') {
        numberValues[key] = String(item.numberInputValue);
      }
    });
    return { values, numberValues };
  }

  Object.entries(saved.values || {}).forEach(([pid, value]) => {
    values[`pid:${pid}`] = value == null ? '' : String(value);
  });

  return { values, numberValues };
}

function applyRules(groups: TemuAttributeProperty[], values: AttrValueState, numberValues: AttrNumberState) {
  const nextValues: AttrValueState = { ...values };
  const nextNumberValues: AttrNumberState = { ...numberValues };

  groups.forEach((group) => {
    const rule = group?.__rule;
    if (!rule) {
      return;
    }

    const key = attrKey(group);
    if (rule.fillMode === 'FORCE_EMPTY') {
      nextValues[key] = '';
      nextNumberValues[key] = '';
    }

    if (rule.fillMode === 'FIXED_VALUE') {
      nextValues[key] = String(rule.fixedValue || '');
    }
  });

  return {
    values: nextValues,
    numberValues: nextNumberValues,
  };
}

const TemuAttributesModal = ({ open, record, onClose }: TemuAttributesModalProps) => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [aiFilling, setAiFilling] = useState(false);
  const [groups, setGroups] = useState<TemuAttributeProperty[]>([]);
  const [values, setValues] = useState<AttrValueState>({});
  const [numberValues, setNumberValues] = useState<AttrNumberState>({});

  function applyStateUpdate(nextValues: AttrValueState, nextNumberValues: AttrNumberState) {
    const cleaned = cleanupInvalidSelections(groups, nextValues, nextNumberValues);
    setValues(cleaned.values);
    setNumberValues(cleaned.numberValues);
  }

  async function loadData() {
    if (!record?.id) {
      return;
    }

    setLoading(true);
    setGroups([]);
    setValues({});
    setNumberValues({});

    const savedState = { values: {} as AttrValueState, numberValues: {} as AttrNumberState };
    try {
      const detailResponse = await productCollectionsApi.get(record.id);
      const saved = safeJsonParse<TemuAttributesSavedPayload>(detailResponse.data?.temuAttributes || undefined);
      Object.assign(savedState, buildSavedValueState(saved));
    } catch {
      // ignore saved value loading failure
    }

    try {
      let rules: TemuAttrRuleVO[] = [];
      try {
        const leafCatId = String((record.temuCatid || '').split(',').slice(-1)[0] || '').trim();
        const response = await productCollectionsApi.listTemuAttrRules({ enabled: true });
        const list = Array.isArray(response.data) ? response.data : [];
        rules = list
          .filter((item) => item?.enabled)
          .filter(
            (item) =>
              item.ruleType === 'GENERAL' ||
              (item.ruleType === 'FIXED_CATEGORY' && String(item.leafCatId || '').trim() === leafCatId),
          );
      } catch {
        // ignore rules loading failure
      }

      const response = await productCollectionsApi.getTemuCategoryAttributes(record.id);
      const raw = safeJsonParse<{ result?: { properties?: TemuAttributeProperty[] } }>(response.data);
      const sourceGroups = Array.isArray(raw?.result?.properties) ? raw?.result?.properties || [] : [];
      const ruleMap = new Map<string, TemuAttrRuleVO>();
      rules.forEach((item) => {
        const name = String(item?.attrName || '').trim();
        if (name) {
          ruleMap.set(name, item);
        }
      });

      const nextGroups = sourceGroups
        .filter((item) => item && String(item.name || '').trim())
        .map((item) => ({
          ...item,
          __rule: ruleMap.get(String(item.name || '').trim()) || null,
        }))
        .filter((item) => item.__rule?.fillMode !== 'SKIP');

      const ruledState = applyRules(nextGroups, savedState.values, savedState.numberValues);
      const cleaned = cleanupInvalidSelections(nextGroups, ruledState.values, ruledState.numberValues);
      setGroups(nextGroups);
      setValues(cleaned.values);
      setNumberValues(cleaned.numberValues);

      if (!nextGroups.length) {
        message.warning('未返回可填写属性');
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '获取属性失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!open) {
      return;
    }
    void loadData();
  }, [open, record?.id]);

  function updateAttrValue(attr: TemuAttributeProperty, nextValue: string | string[] | undefined) {
    const key = attrKey(attr);
    const state: AttrValueState = {
      ...values,
    };

    if (nextValue == null || (Array.isArray(nextValue) && nextValue.length === 0) || String(nextValue).trim() === '') {
      delete state[key];
    } else {
      state[key] = nextValue;
    }

    applyStateUpdate(state, { ...numberValues });
  }

  function updateAttrNumberValue(attr: TemuAttributeProperty, nextValue: string) {
    const key = attrKey(attr);
    const state: AttrNumberState = {
      ...numberValues,
    };
    if (!String(nextValue || '').trim()) {
      delete state[key];
    } else {
      state[key] = nextValue;
    }
    setNumberValues(state);
  }

  async function aiFill() {
    if (!record?.id) {
      return;
    }

    if (!groups.length) {
      message.warning('请先获取属性模板');
      return;
    }

    setAiFilling(true);
    try {
      message.loading({ content: 'AI 正在填写（可能需要 1-3 分钟）...', key: 'temu-attr-ai-fill', duration: 0 });
      const response = await productCollectionsApi.aiFillTemuAttributes(record.id);
      const data = (response.data || {}) as TemuAiFillResponse;
      if (!data.success) {
        throw new Error(data.errorMsg || 'AI 填写失败');
      }

      const nextValues: AttrValueState = { ...values };
      const nextNumberValues: AttrNumberState = { ...numberValues };
      const groupByPid = new Map<string, TemuAttributeProperty[]>();
      groups.forEach((group) => {
        const pid = group?.pid;
        if (pid == null || pid === '') {
          return;
        }
        const key = String(pid);
        if (!groupByPid.has(key)) {
          groupByPid.set(key, []);
        }
        groupByPid.get(key)?.push(group);
      });

      (Array.isArray(data.properties) ? data.properties : []).forEach((item) => {
        const pid = item?.pid;
        if (pid == null || pid === '') {
          return;
        }

        const group = (groupByPid.get(String(pid)) || [])[0];
        const key = group ? attrKey(group) : `pid:${pid}`;
        if (item?.freeText) {
          nextValues[key] = String(item.freeText);
        } else {
          const vids = Array.isArray(item?.selectedVids) ? item.selectedVids.map((vid) => String(vid)) : [];
          if (vids.length > 1) {
            nextValues[key] = vids;
          } else if (vids.length === 1) {
            nextValues[key] = vids[0];
          }
        }

        if (item?.numberInputValue != null && String(item.numberInputValue).trim() !== '') {
          nextNumberValues[key] = String(item.numberInputValue);
        }
      });

      const ruled = applyRules(groups, nextValues, nextNumberValues);
      applyStateUpdate(ruled.values, ruled.numberValues);

      const missing = Array.isArray(data.missingRequiredPids) ? data.missingRequiredPids : [];
      const warnings = Array.isArray(data.warnings) ? data.warnings : [];
      if (missing.length) {
        message.warning({ content: `AI 未能填写部分必填项：${missing.join(', ')}`, key: 'temu-attr-ai-fill' });
      } else if (warnings.length) {
        message.info({ content: warnings[0], key: 'temu-attr-ai-fill' });
      } else {
        message.success({ content: 'AI 填写完成', key: 'temu-attr-ai-fill' });
      }
    } catch (error) {
      message.error({
        content: error instanceof Error ? error.message : 'AI 填写失败/超时',
        key: 'temu-attr-ai-fill',
      });
    } finally {
      setAiFilling(false);
    }
  }

  async function save() {
    if (!record?.id) {
      return;
    }

    const isSkipByRule = (attr: TemuAttributeProperty) => attr.__rule?.fillMode === 'SKIP';
    const isForceEmptyByRule = (attr: TemuAttributeProperty) => attr.__rule?.fillMode === 'FORCE_EMPTY';
    const isFixedValueByRule = (attr: TemuAttributeProperty) => attr.__rule?.fillMode === 'FIXED_VALUE';

    for (const group of groups) {
      if (!group?.required) {
        continue;
      }
      if (isConditionalAttr(group) && !getAttrApplicabilityInfo(groups, values, group).isActive) {
        continue;
      }
      if (isSkipByRule(group) || isForceEmptyByRule(group)) {
        continue;
      }

      const value = values[attrKey(group)];
      if (value === undefined || value === null || String(value).trim() === '') {
        message.error(`请填写必填属性：${group.name}`);
        return;
      }

      if (hasAttrNumberInput(group)) {
        const numberValue = numberValues[attrKey(group)];
        if (numberValue === undefined || numberValue === null || String(numberValue).trim() === '') {
          message.error(`请填写属性数值：${group.name}`);
          return;
        }
      }
    }

    setSaving(true);
    try {
      const out: Record<string, unknown> = {
        leafCatId: String((record.temuCatid || '').split(',').slice(-1)[0] || '').trim(),
        savedAt: new Date().toISOString(),
        properties: [] as Record<string, unknown>[],
      };

      for (const group of groups) {
        const pid = String(group?.pid ?? '');
        if (!pid || isSkipByRule(group)) {
          continue;
        }

        const key = attrKey(group);
        const rawValue = values[key];

        if (isForceEmptyByRule(group)) {
          (out.properties as Record<string, unknown>[]).push({
            pid: group.pid,
            templatePid: group.templatePid,
            refPid: group.refPid,
            valueUnit: Array.isArray(group.valueUnit) && group.valueUnit.length ? String(group.valueUnit[0] ?? '') : '',
            name: group.name,
            required: !!group.required,
            selectedVids: [],
            selectedValues: [],
            freeText: '',
            numberInputValue: '',
          });
          continue;
        }

        let effectiveValue = rawValue;
        if (isFixedValueByRule(group)) {
          effectiveValue = String(group.__rule?.fixedValue || '');
        }

        let effectiveNumber = numberValues[key];
        if (isConditionalAttr(group) && !getAttrApplicabilityInfo(groups, values, group).isActive) {
          continue;
        }

        const isEmpty =
          effectiveValue === undefined ||
          effectiveValue === null ||
          (Array.isArray(effectiveValue) ? effectiveValue.length === 0 : String(effectiveValue).trim() === '');
        if (isEmpty) {
          continue;
        }

        const selectable = hasAttrSelectableValues(group);
        const selectedVids = selectable
          ? (Array.isArray(effectiveValue) ? effectiveValue : [String(effectiveValue)]).map((item) => String(item))
          : [];

        let finalSelectedVids = selectedVids;
        if (isChildAttr(group)) {
          const selectedParents = collectSelectedVids(groups, values);
          const allowed = new Set<string>();
          let hasParentSelected = false;
          (group.templatePropertyValueParentList || []).forEach((rule) => {
            const parents = Array.isArray(rule?.parentVidList) ? rule.parentVidList : [];
            const vids = Array.isArray(rule?.vidList) ? rule.vidList : [];
            parents.forEach((parentVid) => {
              if (selectedParents.has(String(parentVid))) {
                hasParentSelected = true;
                vids.forEach((vid) => allowed.add(String(vid)));
              }
            });
          });

          if (hasParentSelected) {
            finalSelectedVids = selectedVids.filter((item) => allowed.has(String(item)));
            if (!finalSelectedVids.length) {
              continue;
            }
          } else {
            continue;
          }
        }

        const valueTextMap = new Map((group.values || []).map((item) => [String(item?.vid), item?.value || '']));
        const selectedValues = selectable
          ? finalSelectedVids.map((vid) => ({
              vid: Number(vid),
              value: valueTextMap.get(String(vid)) || String(vid),
            }))
          : [];

        (out.properties as Record<string, unknown>[]).push({
          pid: group.pid,
          templatePid: group.templatePid,
          refPid: group.refPid,
          valueUnit: Array.isArray(group.valueUnit) && group.valueUnit.length ? String(group.valueUnit[0] ?? '') : '',
          name: group.name,
          required: !!group.required,
          selectedVids: finalSelectedVids,
          selectedValues,
          freeText: selectable ? null : Array.isArray(effectiveValue) ? effectiveValue.join(',') : String(effectiveValue),
          numberInputValue: selectable && hasAttrNumberInput(group) ? String(effectiveNumber == null ? '' : effectiveNumber) : '',
        });
      }

      await productCollectionsApi.saveTemuAttributes(record.id, out);
      message.success('已保存 TEMU 属性');
      onClose();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  function renderAttrItem(group: TemuAttributeProperty) {
    const key = attrKey(group);
    const ruleLocked = group.__rule?.fillMode === 'FORCE_EMPTY' || group.__rule?.fillMode === 'FIXED_VALUE';
    const info = getAttrApplicabilityInfo(groups, values, group);
    if (isConditionalAttr(group) && !info.isActive) {
      return null;
    }

    return (
      <div
        key={key}
        style={{
          border: '1px solid #eef2f7',
          borderRadius: 14,
          padding: 14,
          background: '#fff',
          display: 'grid',
          gap: 10,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, minHeight: 24 }}>
          {group.required ? (
            <span
              title="必填"
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: '#ef4444',
                display: 'inline-block',
              }}
            />
          ) : null}
          <Typography.Text strong ellipsis={{ tooltip: String(group.name || '') }}>
            {group.name || '-'}
          </Typography.Text>
        </div>

        {hasAttrSelectableValues(group) ? (
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <Select
              value={values[key]}
              mode={group.chooseMaxNum && group.chooseMaxNum > 1 ? 'multiple' : undefined}
              allowClear
              disabled={ruleLocked}
              placeholder={
                ruleLocked
                  ? '已锁定'
                  : isConditionalAttr(group) && !info.isActive
                    ? info.parentHint || '请先选择父属性'
                    : '请选择'
              }
              options={(info.allowed || []).map((item) => ({
                value: item.value,
                label: item.label,
              }))}
              onChange={(nextValue) => updateAttrValue(group, nextValue)}
            />
            {hasAttrNumberInput(group) ? (
              <Input
                value={numberValues[key] || ''}
                allowClear
                disabled={ruleLocked}
                placeholder={attrNumberInputPlaceholder(group)}
                suffix={attrUnitText(group) || undefined}
                onChange={(event) => updateAttrNumberValue(group, event.target.value)}
              />
            ) : null}
          </Space>
        ) : (
          <Input
            value={typeof values[key] === 'string' ? values[key] : ''}
            allowClear
            disabled={ruleLocked}
            placeholder="请输入"
            onChange={(event) => updateAttrValue(group, event.target.value)}
          />
        )}
      </div>
    );
  }

  return (
    <Modal
      open={open}
      title="填写 TEMU 商品属性"
      width={920}
      maskClosable={!saving && !aiFilling}
      keyboard={!saving && !aiFilling}
      onCancel={onClose}
      footer={
        <Space>
          <Button disabled={saving || aiFilling} onClick={onClose}>
            取消
          </Button>
          <Button loading={aiFilling} disabled={saving} onClick={() => void aiFill()}>
            AI 填写
          </Button>
          <Button type="primary" loading={saving} disabled={aiFilling} onClick={() => void save()}>
            保存
          </Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <div
            style={{
              borderRadius: 14,
              padding: '14px 16px',
              background: 'linear-gradient(135deg, rgba(14,165,233,0.08), rgba(34,197,94,0.06))',
              border: '1px solid rgba(14,165,233,0.12)',
            }}
          >
            <Typography.Text type="secondary">类目</Typography.Text>
            <div style={{ marginTop: 6, fontWeight: 600 }}>{record?.temuCatname || '-'}</div>
          </div>

          {groups.length ? (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
                gap: 12,
                alignItems: 'start',
              }}
            >
              {groups.map((group) => renderAttrItem(group))}
            </div>
          ) : (
            <Typography.Text type="secondary">未获取到可填写属性（请先匹配 TEMU 类目）</Typography.Text>
          )}
        </Space>
      </Spin>
    </Modal>
  );
};

export default TemuAttributesModal;
