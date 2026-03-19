package com.tminos.productscene.config;

import com.tminos.productscene.entity.AIChannel;
import com.tminos.productscene.repository.AIChannelRepository;
import com.tminos.productscene.service.ChannelCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AIChannelRepository channelRepository;
    private final ChannelCredentialService channelCredentialService;

    @Override
    public void run(String... args) {
        if (channelRepository.count() == 0) {
            log.info("Initializing default AI channels...");

            List<AIChannel> channels = List.of(
            AIChannel.builder().name("Stability AI (SD3)").platform("stability").model("sd3")
                .baseUrl("https://api.stability.ai").enabled(true).description("Stability AI SD3 文生图").sortOrder(1).build(),

            AIChannel.builder().name("豆包-动漫风").platform("volcengine").model("img2img_cartoon_style")
                .baseUrl("https://visual.volcengineapi.com").enabled(true).description("火山引擎-动漫风格化").sortOrder(2).build(),
            AIChannel.builder().name("豆包-3D风").platform("volcengine").model("img2img_disney_3d_style")
                .baseUrl("https://visual.volcengineapi.com").enabled(true).description("火山引擎-3D风格化").sortOrder(3).build(),
            AIChannel.builder().name("豆包-写实风").platform("volcengine").model("img2img_real_mix_style")
                .baseUrl("https://visual.volcengineapi.com").enabled(true).description("火山引擎-写实风格化").sortOrder(4).build(),
            AIChannel.builder().name("豆包-日漫风").platform("volcengine").model("img2img_makoto_style")
                .baseUrl("https://visual.volcengineapi.com").enabled(true).description("火山引擎-日漫风格化").sortOrder(5).build(),
            AIChannel.builder().name("豆包-梦幻风").platform("volcengine").model("img2img_blueline_style")
                .baseUrl("https://visual.volcengineapi.com").enabled(true).description("火山引擎-梦幻风格化").sortOrder(6).build(),
            AIChannel.builder().name("豆包-水墨风").platform("volcengine").model("img2img_water_ink_style")
                .baseUrl("https://visual.volcengineapi.com").enabled(true).description("火山引擎-水墨风格化").sortOrder(7).build(),
            AIChannel.builder().name("豆包-陶瓷娃娃").platform("volcengine").model("img2img_ceramics_style")
                .baseUrl("https://visual.volcengineapi.com").enabled(true).description("火山引擎-陶瓷娃娃风格").sortOrder(8).build(),
            AIChannel.builder().name("豆包-中国红").platform("volcengine").model("img2img_chinese_style")
                .baseUrl("https://visual.volcengineapi.com").enabled(true).description("火山引擎-中国红风格").sortOrder(9).build(),
            AIChannel.builder().name("豆包-丑萌粘土").platform("volcengine").model("img2img_clay_style")
                .baseUrl("https://visual.volcengineapi.com").enabled(true).description("火山引擎-丑萌粘土风格").sortOrder(10).build(),

            AIChannel.builder().name("即梦-图生图3.0").platform("jimeng_i2i").model("jimeng_i2i_v30")
                .baseUrl("https://visual.volcengineapi.com").enabled(true).description("即梦图生图3.0智能参考").sortOrder(11).build(),
            AIChannel.builder().name("即梦-文生图3.1").platform("jimeng_t2i").model("jimeng_t2i_v31")
                .baseUrl("https://visual.volcengineapi.com").enabled(true).description("即梦文生图3.1").sortOrder(12).build()
            );

            channelRepository.saveAll(Objects.requireNonNull(channels));
            log.info("Initialized {} AI channels", channels.size());
        } else {
            log.info("Channels already initialized, skipping seeding...");
        }

        channelCredentialService.fillCredentialsFromEnvironmentIfMissing();
    }
}
