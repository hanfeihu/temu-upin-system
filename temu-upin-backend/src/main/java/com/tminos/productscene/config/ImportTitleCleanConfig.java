package com.tminos.productscene.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "import.title-clean")
@Getter
@Setter
public class ImportTitleCleanConfig {

    /**
     * Keywords to remove from imported product titles.
     * Example: ["亚马逊", "跨境", "TEMU", "厂家直销"]
     */
    private List<String> removeKeywords = new ArrayList<>();
}
