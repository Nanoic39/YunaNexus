package cc.nanoic.yunanexus.common.web.starter;

import cc.nanoic.yunanexus.common.web.config.LocalDateTimeDeserializer;
import cc.nanoic.yunanexus.common.web.config.LocalDateTimeSerializer;
import cc.nanoic.yunanexus.common.web.config.TimeProperties;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

@Slf4j
@AutoConfiguration
@ConditionalOnClass(FastJsonHttpMessageConverter.class)
@EnableConfigurationProperties(TimeProperties.class)
public class YunaNexusWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LocalDateTimeSerializer.class)
    public LocalDateTimeSerializer localDateTimeSerializer(TimeProperties timeProperties) {
        return new LocalDateTimeSerializer(resolveZoneId(timeProperties.getTimeZone()));
    }

    @Bean
    @ConditionalOnMissingBean(LocalDateTimeDeserializer.class)
    public LocalDateTimeDeserializer localDateTimeDeserializer(TimeProperties timeProperties) {
        return new LocalDateTimeDeserializer(resolveZoneId(timeProperties.getTimeZone()));
    }

    @Bean
    @ConditionalOnMissingBean(name = "fastJsonHttpMessageConverter")
    public HttpMessageConverter<?> fastJsonHttpMessageConverter(
            LocalDateTimeSerializer serializer,
            LocalDateTimeDeserializer deserializer) {

        JSON.register(LocalDateTime.class, serializer);
        JSON.register(LocalDateTime.class, deserializer);

        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
        FastJsonConfig config = new FastJsonConfig();
        config.setWriterFeatures(
                JSONWriter.Feature.WriteMapNullValue,
                JSONWriter.Feature.BrowserCompatible
        );
        converter.setFastJsonConfig(config);
        converter.setSupportedMediaTypes(Collections.singletonList(MediaType.APPLICATION_JSON));
        return converter;
    }


    private ZoneId resolveZoneId(String zoneText) {
        try {
            return ZoneId.of(zoneText);
        } catch (Exception ex) {
            log.warn("非法时区配置 custom.time.time-zone='{}'，已回退 Asia/Shanghai", zoneText);
            return ZoneId.of("Asia/Shanghai");
        }
    }
}
