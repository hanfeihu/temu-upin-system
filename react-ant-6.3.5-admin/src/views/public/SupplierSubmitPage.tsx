import {
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Result,
  Space,
  Typography,
  Upload,
} from 'antd';
import type { UploadFile, UploadProps } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { supplierProductSubmissionsApi } from '@/api/supplierProductSubmissions';
import type { SupplierProductSubmissionPayload } from '@/types/api';

type UploadRequestOption = Parameters<NonNullable<UploadProps['customRequest']>>[0];

const initialValues: SupplierProductSubmissionPayload = {
  supplierName: '',
  supplierPhone: '',
  supplierAddress: '',
  productName: '',
  imageUrls: [],
  status: 'PENDING',
  remark: '',
};

function toImageUrls(files: UploadFile[]) {
  return files
    .map((file) => file.url || (file.response as { url?: string } | undefined)?.url)
    .filter(Boolean) as string[];
}

function cleanPayload(values: SupplierProductSubmissionPayload, imageUrls: string[]): SupplierProductSubmissionPayload {
  return {
    supplierName: String(values.supplierName || '').trim(),
    supplierPhone: String(values.supplierPhone || '').trim() || undefined,
    supplierAddress: String(values.supplierAddress || '').trim() || undefined,
    productName: String(values.productName || '').trim(),
    supplyPrice: values.supplyPrice,
    weightG: values.weightG,
    lengthCm: values.lengthCm,
    widthCm: values.widthCm,
    heightCm: values.heightCm,
    imageUrls,
    status: 'PENDING',
    remark: String(values.remark || '').trim() || undefined,
  };
}

const SupplierSubmitPage = () => {
  const { message } = App.useApp();
  const [form] = Form.useForm<SupplierProductSubmissionPayload>();
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  async function handleUpload(options: UploadRequestOption) {
    const file = options.file as File & { uid: string };
    try {
      const res = await supplierProductSubmissionsApi.publicUploadImage(file);
      const url = res.data?.url;
      if (!url) {
        throw new Error('上传成功但没有返回图片地址');
      }
      options.onSuccess?.({ url }, file);
      setFileList((current) => current.map((item) => (
        item.uid === file.uid
          ? { ...item, status: 'done', url, thumbUrl: url }
          : item
      )));
    } catch (error) {
      options.onError?.(error as Error);
      message.error(error instanceof Error ? error.message : '图片上传失败');
    }
  }

  async function handleSubmit() {
    let values: SupplierProductSubmissionPayload;
    try {
      values = await form.validateFields();
    } catch {
      message.warning('请先填写必填信息');
      return;
    }
    const imageUrls = toImageUrls(fileList);
    if (imageUrls.length === 0) {
      message.warning('请至少上传一张产品实拍图');
      return;
    }

    setSubmitting(true);
    try {
      await supplierProductSubmissionsApi.publicCreate(cleanPayload(values, imageUrls));
      setSubmitted(true);
      form.resetFields();
      setFileList([]);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '提交失败');
    } finally {
      setSubmitting(false);
    }
  }

  if (submitted) {
    return (
      <div style={{ minHeight: '100vh', background: '#f5f7fb', padding: 16 }}>
        <Result
          status="success"
          title="提交成功"
          subTitle="我们已经收到产品资料，审核后会联系你确认。"
          extra={[
            <Button key="again" type="primary" onClick={() => setSubmitted(false)}>
              继续提交
            </Button>,
          ]}
        />
      </div>
    );
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f5f7fb', padding: 12 }}>
      <Card
        bordered={false}
        style={{ maxWidth: 680, margin: '0 auto', borderRadius: 8 }}
        styles={{ body: { padding: 18 } }}
      >
        <Space direction="vertical" size={4} style={{ width: '100%', marginBottom: 16 }}>
          <Typography.Title level={3} style={{ margin: 0 }}>
            供应商提品
          </Typography.Title>
          <Typography.Text type="secondary">
            请填写真实产品资料，图片可以上传一张或多张。
          </Typography.Text>
        </Space>

        <Form form={form} layout="vertical" initialValues={initialValues}>
          <Form.Item
            label="供货商名称"
            name="supplierName"
            rules={[{ required: true, message: '请输入供货商名称' }]}
          >
            <Input size="large" allowClear placeholder="例如：张三工厂 / 李姐档口" />
          </Form.Item>

          <Form.Item label="手机号/微信" name="supplierPhone">
            <Input size="large" allowClear placeholder="方便后面联系发货" />
          </Form.Item>

          <Form.Item label="供货商地址" name="supplierAddress">
            <Input.TextArea rows={3} allowClear placeholder="发货地址、档口地址或仓库地址" />
          </Form.Item>

          <Form.Item
            label="产品名称"
            name="productName"
            rules={[{ required: true, message: '请输入产品名称' }]}
          >
            <Input size="large" allowClear placeholder="产品中文名称" />
          </Form.Item>

          <Space wrap style={{ width: '100%' }}>
            <Form.Item label="供货价" name="supplyPrice" style={{ width: 150 }}>
              <InputNumber min={0} precision={2} addonAfter="元" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="重量" name="weightG" style={{ width: 150 }}>
              <InputNumber min={0} precision={2} addonAfter="g" style={{ width: '100%' }} />
            </Form.Item>
          </Space>

          <Space wrap style={{ width: '100%' }}>
            <Form.Item label="长" name="lengthCm" style={{ width: 120 }}>
              <InputNumber min={0} precision={2} addonAfter="cm" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="宽" name="widthCm" style={{ width: 120 }}>
              <InputNumber min={0} precision={2} addonAfter="cm" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="高" name="heightCm" style={{ width: 120 }}>
              <InputNumber min={0} precision={2} addonAfter="cm" style={{ width: '100%' }} />
            </Form.Item>
          </Space>

          <Form.Item label="产品实拍图" required>
            <Upload
              listType="picture-card"
              multiple
              accept="image/*"
              fileList={fileList}
              customRequest={(options) => void handleUpload(options)}
              onChange={({ fileList: nextFileList }) => setFileList(nextFileList.map((file) => {
                const responseUrl = (file.response as { url?: string } | undefined)?.url;
                return responseUrl ? { ...file, url: responseUrl, thumbUrl: responseUrl } : file;
              }))}
              onRemove={(file) => {
                setFileList((current) => current.filter((item) => item.uid !== file.uid));
                return true;
              }}
            >
              <div>
                <PlusOutlined />
                <div style={{ marginTop: 8 }}>上传</div>
              </div>
            </Upload>
          </Form.Item>

          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={3} allowClear placeholder="库存、颜色、规格、发货说明等" />
          </Form.Item>

          <Button
            type="primary"
            size="large"
            block
            loading={submitting}
            onClick={() => void handleSubmit()}
          >
            提交产品
          </Button>
        </Form>
      </Card>
    </div>
  );
};

export default SupplierSubmitPage;
