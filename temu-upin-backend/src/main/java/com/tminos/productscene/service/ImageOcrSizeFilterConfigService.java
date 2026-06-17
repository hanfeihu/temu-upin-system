package com.tminos.productscene.service;

import com.tminos.productscene.dto.ImageOcrSizeFilterConfigDTO;
import com.tminos.productscene.entity.ImageOcrSizeFilterConfig;
import com.tminos.productscene.repository.ImageOcrSizeFilterConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ImageOcrSizeFilterConfigService {

    private final ImageOcrSizeFilterConfigRepository repo;

    public ImageOcrSizeFilterConfigService(ImageOcrSizeFilterConfigRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<ImageOcrSizeFilterConfigDTO.Response> list() {
        return repo.findAllByOrderByImageWidthAscImageHeightAsc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ImageOcrSizeFilterConfigDTO.Response create(ImageOcrSizeFilterConfigDTO.Request req) {
        validate(req);
        ImageOcrSizeFilterConfig item = repo.findByImageWidthAndImageHeight(req.getImageWidth(), req.getImageHeight())
                .orElseGet(ImageOcrSizeFilterConfig::new);
        item.setImageWidth(req.getImageWidth());
        item.setImageHeight(req.getImageHeight());
        item.setEnabled(req.getEnabled() == null || req.getEnabled());
        item.setRemark(StringUtils.hasText(req.getRemark()) ? req.getRemark().trim() : null);
        return toResponse(repo.save(item));
    }

    @Transactional
    public ImageOcrSizeFilterConfigDTO.Response update(Long id, ImageOcrSizeFilterConfigDTO.Request req) {
        validate(req);
        ImageOcrSizeFilterConfig item = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("size filter config not found: " + id));
        repo.findByImageWidthAndImageHeight(req.getImageWidth(), req.getImageHeight())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("该图片尺寸过滤配置已存在");
                });
        item.setImageWidth(req.getImageWidth());
        item.setImageHeight(req.getImageHeight());
        item.setEnabled(req.getEnabled() == null || req.getEnabled());
        item.setRemark(StringUtils.hasText(req.getRemark()) ? req.getRemark().trim() : null);
        return toResponse(repo.save(item));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new EntityNotFoundException("size filter config not found: " + id);
        }
        repo.deleteById(id);
    }

    private void validate(ImageOcrSizeFilterConfigDTO.Request req) {
        if (req == null || req.getImageWidth() == null || req.getImageHeight() == null
                || req.getImageWidth() <= 0 || req.getImageHeight() <= 0) {
            throw new IllegalArgumentException("图片宽高必须大于 0");
        }
    }

    private ImageOcrSizeFilterConfigDTO.Response toResponse(ImageOcrSizeFilterConfig item) {
        return ImageOcrSizeFilterConfigDTO.Response.builder()
                .id(item.getId())
                .imageWidth(item.getImageWidth())
                .imageHeight(item.getImageHeight())
                .enabled(item.getEnabled())
                .remark(item.getRemark())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
