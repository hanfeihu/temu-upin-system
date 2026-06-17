package com.tminos.productscene.minio;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MinioProperties.class)
public class MinioSdkConfiguration {

    @Bean
    public MinioStorageClient minioStorageClient(MinioProperties minioProperties) {
        return new MinioStorageClient(minioProperties);
    }
}
