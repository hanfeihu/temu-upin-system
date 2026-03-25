package com.tminos.productscene.config;

import com.tminos.productscene.service.TemuParentSpecMappingService;
import org.springframework.stereotype.Component;

@Component
public class TemuParentSpecMappingTable {

    private final TemuParentSpecMappingService mappingService;

    public TemuParentSpecMappingTable(TemuParentSpecMappingService mappingService) {
        this.mappingService = mappingService;
    }

    public String getMappedParentSpecName(String sourceSpecName) {
        return mappingService.findTargetParentSpecName(sourceSpecName);
    }
}