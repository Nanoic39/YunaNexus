package cc.nanoic.yunanexus.user.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.util.Collections;

@Configuration
public class FastJsonConfiguration implements WebMvcConfigurer {

    // 保留全局静态注册，确保非 HTTP 场景（如直接 JSON.toJSONString）也生效
    static {
        JSON.register(LocalDateTime.class, new LocalDateTimeSerializer());
        JSON.register(LocalDateTime.class, new LocalDateTimeDeserializer());
    }

    @Bean
    public HttpMessageConverter<?> fastJsonHttpMessageConverter() {
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
        FastJsonConfig config = new FastJsonConfig();

        // 只保留基础配置即可，不需要 WriteMillisAsTicks
        config.setWriterFeatures(
                JSONWriter.Feature.WriteMapNullValue,
                JSONWriter.Feature.BrowserCompatible // 防止大整数前端精度丢失
        );

        converter.setFastJsonConfig(config);
        converter.setSupportedMediaTypes(Collections.singletonList(MediaType.APPLICATION_JSON));
        return converter;
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.addCustomConverter(fastJsonHttpMessageConverter());
    }
}
