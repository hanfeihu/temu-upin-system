package com.tminos.productscene.service;

import com.tminos.productscene.dto.AlibabaImageProxyConfigDTO;
import com.tminos.productscene.entity.AlibabaImageProxyConfig;
import com.tminos.productscene.repository.AlibabaImageProxyConfigRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlibabaImageProxyConfigServiceTest {

    @Test
    void currentShouldReturnEmptyViewWhenNoConfigExists() {
        AlibabaImageProxyConfigRepository repository = mock(AlibabaImageProxyConfigRepository.class);
        when(repository.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        AlibabaImageProxyConfigService service = new AlibabaImageProxyConfigService(repository);
        AlibabaImageProxyConfigDTO.View view = service.current();

        assertNotNull(view);
        assertNull(view.getId());
        assertEquals("", view.getConfigName());
        assertFalse(Boolean.TRUE.equals(view.getEnabled()));
        assertEquals("", view.getProxyBaseUrl());
        assertEquals("", view.getImageProxyPath());
        assertEquals("", view.getAllowedHostsText());
        assertEquals("", view.getRemark());
        assertNull(view.getCreatedAt());
        assertNull(view.getUpdatedAt());
    }

    @Test
    void saveCurrentShouldReuseLatestConfigRecord() {
        AlibabaImageProxyConfigRepository repository = mock(AlibabaImageProxyConfigRepository.class);
        AlibabaImageProxyConfig existing = AlibabaImageProxyConfig.builder()
                .id(12L)
                .configName("旧配置")
                .enabled(true)
                .createdAt(LocalDateTime.of(2026, 4, 20, 10, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 20, 10, 0, 0))
                .build();
        when(repository.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(existing));
        when(repository.save(any(AlibabaImageProxyConfig.class))).thenAnswer(invocation -> {
            AlibabaImageProxyConfig entity = invocation.getArgument(0);
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(LocalDateTime.of(2026, 4, 20, 10, 0, 0));
            }
            entity.setUpdatedAt(LocalDateTime.of(2026, 4, 23, 12, 30, 0));
            return entity;
        });

        AlibabaImageProxyConfigService service = new AlibabaImageProxyConfigService(repository);
        AlibabaImageProxyConfigDTO.UpsertRequest request = new AlibabaImageProxyConfigDTO.UpsertRequest();
        request.setConfigName("新配置");
        request.setEnabled(false);
        request.setProxyBaseUrl("https://proxy.example.com");
        request.setImageProxyPath("/image-proxy");
        request.setAllowedHostsText("cbu01.alicdn.com\nimg.alicdn.com");
        request.setRemark("测试备注");

        AlibabaImageProxyConfigDTO.View view = service.saveCurrent(request);

        assertNotNull(view);
        assertEquals(12L, view.getId());
        assertEquals("新配置", view.getConfigName());
        assertFalse(Boolean.TRUE.equals(view.getEnabled()));
        assertEquals("https://proxy.example.com", view.getProxyBaseUrl());
        assertEquals("/image-proxy", view.getImageProxyPath());
        assertEquals("cbu01.alicdn.com\nimg.alicdn.com", view.getAllowedHostsText());
        assertEquals("测试备注", view.getRemark());
        verify(repository).findTopByOrderByUpdatedAtDescIdDesc();
        verify(repository).save(any(AlibabaImageProxyConfig.class));
    }
}
