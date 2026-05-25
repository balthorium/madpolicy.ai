package ai.madpolicy.diplomacy.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;

public final class TestYaml {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
        .findAndRegisterModules();

    private TestYaml() {
    }

    public static <T> T load(Path path, Class<T> type) {
        try {
            return YAML.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse YAML: " + path, e);
        }
    }

    public static <T> T load(String content, Class<T> type) {
        try {
            return YAML.readValue(content, type);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse YAML content", e);
        }
    }

    public static String write(Object value) {
        try {
            return YAML.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write YAML content", e);
        }
    }
}
