package ai.madpolicy.diplomacy.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.yaml.MappingJackson2YamlHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class YamlWebConfig implements WebMvcConfigurer {
    public static final MediaType APPLICATION_YAML = MediaType.parseMediaType("application/yaml");

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        ObjectMapper yamlMapper = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
            .findAndRegisterModules();
        yamlMapper.setDefaultPropertyInclusion(
            JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL)
        );
        var yamlConverter = new MappingJackson2YamlHttpMessageConverter(yamlMapper);
        yamlConverter.setSupportedMediaTypes(List.of(APPLICATION_YAML));
        converters.add(0, yamlConverter);
    }
}
