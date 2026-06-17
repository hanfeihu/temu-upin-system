import { Alert, App, Button, Card, Descriptions, Form, Input, Space, Switch, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { alibabaImageProxyConfigApi } from '@/api/alibabaImageProxyConfig';
import type { AlibabaImageProxyConfigPayload, AlibabaImageProxyConfigVO } from '@/types/api';
import {
  buildAlibabaImageProxyUrl,
  DEFAULT_ALIBABA_IMAGE_PROXY_HOST_LABELS,
  DEFAULT_ALIBABA_IMAGE_PROXY_PATH,
} from '@/utils/alibabaImageProxy';
import { formatDateTime } from '@/utils/format';

const previewSourceUrl = 'https://img.alicdn.com/imgextra/i1/2201536826314/O1CN01demo.jpg?x-oss-process=image/resize,w_750';
const DEFAULT_CONFIG_NAME = '默认配置';

const initialFormValues: AlibabaImageProxyConfigPayload = {
  configName: DEFAULT_CONFIG_NAME,
  enabled: false,
  proxyBaseUrl: 'http://127.0.0.1:18080',
  imageProxyPath: DEFAULT_ALIBABA_IMAGE_PROXY_PATH,
  allowedHostsText: '',
  remark: '',
};

function toFormValues(config?: AlibabaImageProxyConfigVO | null): AlibabaImageProxyConfigPayload {
  return {
    configName: config?.configName || DEFAULT_CONFIG_NAME,
    enabled: Boolean(config?.enabled),
    proxyBaseUrl: config?.proxyBaseUrl || initialFormValues.proxyBaseUrl,
    imageProxyPath: config?.imageProxyPath || DEFAULT_ALIBABA_IMAGE_PROXY_PATH,
    allowedHostsText: config?.allowedHostsText || '',
    remark: config?.remark || '',
  };
}

function buildSavePayload(values: AlibabaImageProxyConfigPayload): AlibabaImageProxyConfigPayload {
  return {
    configName: String(values.configName || '').trim() || DEFAULT_CONFIG_NAME,
    enabled: Boolean(values.enabled),
    proxyBaseUrl: String(values.proxyBaseUrl || '').trim() || undefined,
    imageProxyPath: String(values.imageProxyPath || '').trim() || DEFAULT_ALIBABA_IMAGE_PROXY_PATH,
    allowedHostsText: String(values.allowedHostsText || '').trim() || undefined,
    remark: String(values.remark || '').trim() || undefined,
  };
}

const AlibabaImageProxyConfigPage = () => {
  const { message } = App.useApp();
  const [form] = Form.useForm<AlibabaImageProxyConfigPayload>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [currentConfig, setCurrentConfig] = useState<AlibabaImageProxyConfigVO | null>(null);
  const enabled = Form.useWatch('enabled', form);
  const proxyBaseUrl = Form.useWatch('proxyBaseUrl', form);
  const imageProxyPath = Form.useWatch('imageProxyPath', form);
  const allowedHostsText = Form.useWatch('allowedHostsText', form);

  const previewUrl = useMemo(
    () => buildAlibabaImageProxyUrl(previewSourceUrl, {
      enabled: Boolean(enabled),
      proxyBaseUrl,
      imageProxyPath,
      allowedHostsText,
    }) || previewSourceUrl,
    [allowedHostsText, enabled, imageProxyPath, proxyBaseUrl],
  );

  async function load() {
    setLoading(true);
    try {
      const res = await alibabaImageProxyConfigApi.current();
      const config = res.data || null;
      setCurrentConfig(config);
      form.setFieldsValue(toFormValues(config));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载阿里图片代理配置失败');
      setCurrentConfig(null);
      form.setFieldsValue(initialFormValues);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    form.setFieldsValue(initialFormValues);
    void load();
  }, [form]);

  async function handleSave() {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const payload = buildSavePayload(values);
      const res = currentConfig?.id
        ? await alibabaImageProxyConfigApi.update(currentConfig.id, payload)
        : await alibabaImageProxyConfigApi.create(payload);
      setCurrentConfig(res.data);
      form.setFieldsValue(toFormValues(res.data));
      message.success('阿里图片代理配置已保存');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存阿里图片代理配置失败');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message="阿里图片代理服务器配置"
        description="这里只控制 1688 选品池页面图片展示时的 URL 改写，不会修改数据库里的原始图片地址。"
      />

      <Card>
        <Space wrap>
          <Button loading={loading} onClick={() => void load()}>
            刷新
          </Button>
          <Button type="primary" loading={saving} onClick={() => void handleSave()}>
            保存配置
          </Button>
        </Space>
      </Card>

      <Card loading={loading} title="代理参数">
        <Form form={form} layout="vertical" initialValues={initialFormValues}>
          <Form.Item label="配置名称" name="configName" extra="默认保存为单条当前配置，用于后台识别。">
            <Input allowClear placeholder={DEFAULT_CONFIG_NAME} />
          </Form.Item>
          <Form.Item label="启用图片代理" name="enabled" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="关闭" />
          </Form.Item>
          <Form.Item
            label="代理服务基地址"
            name="proxyBaseUrl"
            extra="例如 http://127.0.0.1:18080；为空时，选品池页面会直接回退原图 URL。"
          >
            <Input allowClear placeholder="http://127.0.0.1:18080" />
          </Form.Item>
          <Form.Item
            label="图片代理路径"
            name="imageProxyPath"
            extra={`默认 ${DEFAULT_ALIBABA_IMAGE_PROXY_PATH}，会自动规范成 /path 形式。`}
          >
            <Input allowClear placeholder={DEFAULT_ALIBABA_IMAGE_PROXY_PATH} />
          </Form.Item>
          <Form.Item
            label="允许改写的 host 白名单"
            name="allowedHostsText"
            extra={`每行、空格或逗号写一个 host；留空时默认允许 ${DEFAULT_ALIBABA_IMAGE_PROXY_HOST_LABELS.join('、')}。`}
          >
            <Input.TextArea
              rows={5}
              allowClear
              placeholder={'img.alicdn.com\ncbu01.alicdn.com\ngw.alicdn.com'}
            />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={3} allowClear placeholder="可选，用于记录代理用途或对应 nginx 服务说明" />
          </Form.Item>
        </Form>
      </Card>

      <Card size="small" title="改写预览">
        <Descriptions bordered size="small" column={1}>
          <Descriptions.Item label="示例原图 URL">
            <Typography.Paragraph style={{ marginBottom: 0, wordBreak: 'break-all' }}>
              {previewSourceUrl}
            </Typography.Paragraph>
          </Descriptions.Item>
          <Descriptions.Item label="示例展示 URL">
            <Typography.Paragraph style={{ marginBottom: 0, wordBreak: 'break-all' }}>
              {previewUrl}
            </Typography.Paragraph>
          </Descriptions.Item>
          <Descriptions.Item label="当前保存状态">
            {currentConfig
              ? `${currentConfig.configName || DEFAULT_CONFIG_NAME} / ${currentConfig.enabled ? '已启用' : '已关闭'} / 更新时间：${formatDateTime(currentConfig.updatedAt)}`
              : '后端暂未返回已保存配置，当前展示的是默认表单值。'}
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </Space>
  );
};

export default AlibabaImageProxyConfigPage;
