package com.tminos.productscene;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.BucketInfo;
import com.aliyun.oss.model.ObjectMetadata;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class OssDebugMainTest {

    public static void main(String[] args) throws Exception {
        //响应数据: {"result":{"categoryPathDTOS":[{"cat9DTO":null,"cat8DTO":null,"cat6DTO":null,"cat7DTO":null,"cat10DTO":null,"cat2DTO":{"catId":11730,"catName":"浴室用品","parentCatId":9711,"catType":0,"isLeaf":false,"hiddenType":0,"catLevel":2,"isHidden":false},"cat5DTO":null,"cat3DTO":{"catId":11731,"catName":"浴室配件","parentCatId":11730,"catType":0,"isLeaf":false,"hiddenType":0,"catLevel":3,"isHidden":false},"cat4DTO":{"catId":11800,"catName":"化妆品收纳盒","parentCatId":11731,"catType":0,"isLeaf":true,"hiddenType":0,"catLevel":4,"isHidden":false},"cat1DTO":{"catId":9711,"catName":"家居、厨房用品","parentCatId":0,"catType":0,"isLeaf":false,"hiddenType":0,"catLevel":1,"isHidden":false}},{"cat9DTO":null,"cat8DTO":null,"cat6DTO":null,"cat7DTO":null,"cat10DTO":null,"cat2DTO":{"catId":19503,"catName":"工具和配件","parentCatId":18768,"catType":0,"isLeaf":false,"hiddenType":0,"catLevel":2,"isHidden":false},"cat5DTO":null,"cat3DTO":{"catId":19668,"catName":"化妆袋和化妆包","parentCatId":19503,"catType":0,"isLeaf":false,"hiddenType":0,"catLevel":3,"isHidden":false},"cat4DTO":{"catId":19671,"catName":"化妆旅行盒","parentCatId":19668,"catType":0,"isLeaf":true,"hiddenType":0,"catLevel":4,"isHidden":false},"cat1DTO":{"catId":18768,"catName":"美容和个人护理","parentCatId":0,"catType":0,"isLeaf":false,"hiddenType":0,"catLevel":1,"isHidden":false}},{"cat9DTO":null,"cat8DTO":null,"cat6DTO":null,"cat7DTO":null,"cat10DTO":null,"cat2DTO":{"catId":19503,"catName":"工具和配件","parentCatId":18768,"catType":0,"isLeaf":false,"hiddenType":0,"catLevel":2,"isHidden":false},"cat5DTO":null,"cat3DTO":{"catId":19668,"catName":"化妆袋和化妆包","parentCatId":19503,"catType":0,"isLeaf":false,"hiddenType":0,"catLevel":3,"isHidden":false},"cat4DTO":{"catId":19673,"catName":"化妆品陈列箱","parentCatId":19668,"catType":0,"isLeaf":true,"hiddenType":0,"catLevel":4,"isHidden":false},"cat1DTO":{"catId":18768,"catName":"美容和个人护理","parentCatId":0,"catType":0,"isLeaf":false,"hiddenType":0,"catLevel":1,"isHidden":false}}]},"success":true,"requestId":"cn-54568412-35ed-45a9-bde9-083efcbd015e","errorCode":1000000,"errorMsg":""}
        // Temu SDK call removed from this OSS debug main.

//        // Fill these values for local testing
//        String endpoint = "oss-cn-hangzhou.aliyuncs.com";
//        String bucket = "hanfeihutemuimage";
//
//        String accessKeyId = "REDACTED_ACCESS_KEY_ID";
//        String accessKeySecret = "REDACTED_ACCESS_KEY_SECRET";
//
//        // Optional: your CDN/custom domain. Leave empty to use default OSS domain.
//        String publicDomain = "https://oss.tminos.com";
//
//        OSS client = null;
//        try {
//            client = new OSSClientBuilder().build(endpoint, accessKeyId.trim(), accessKeySecret.trim());
//
//            // Authenticated call to validate signature and permission
//            BucketInfo info = client.getBucketInfo(bucket);
//            System.out.println("Bucket OK: name=" + info.getBucket().getName() + ", region=" + info.getBucket().getLocation());
//
//            // Upload a tiny text file
//            String key = "sku-test/" + Instant.now().toEpochMilli() + "/test.txt";
//            byte[] data = ("hello oss " + Instant.now()).getBytes(StandardCharsets.UTF_8);
//
//            ObjectMetadata meta = new ObjectMetadata();
//            meta.setContentLength(data.length);
//            meta.setContentType("text/plain; charset=utf-8");
//
//            client.putObject(bucket, key, new ByteArrayInputStream(data), meta);
//            System.out.println("Upload OK: key=" + key);
//
//            String url;
//            if (publicDomain != null && !publicDomain.isBlank()) {
//                url = publicDomain.replaceAll("/+$", "") + "/" + key;
//            } else {
//                url = "https://" + bucket + "." + endpoint + "/" + key;
//            }
//            System.out.println("Public URL: " + url);
//
//        } catch (OSSException e) {
//            System.err.println("OSSException:");
//            System.err.println("  errorCode=" + e.getErrorCode());
//            System.err.println("  message=" + e.getMessage());
//            System.err.println("  requestId=" + e.getRequestId());
//            System.err.println("  hostId=" + e.getHostId());
//            throw e;
//        } catch (ClientException e) {
//            System.err.println("ClientException: " + e.getMessage());
//            throw e;
//        } finally {
//            if (client != null) {
//                client.shutdown();
//            }
//        }
    }
}
