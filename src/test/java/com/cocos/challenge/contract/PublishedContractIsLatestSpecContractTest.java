package com.cocos.challenge.contract;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PublishedContractIsLatestSpecContractTest {

    @Test
    void publishedContractIsByteIdenticalToTheLatestSpecContract() throws IOException {
        Path published = Path.of("src/main/resources/static/openapi/openapi.yaml");
        assertThat(published).exists();
        Map<String, Map<String, String>> document;
        try (InputStream input = Files.newInputStream(published)) {
            document = new Yaml().load(input);
        }
        String version = document.get("info").get("version");
        assertThat(version).matches("0\\.\\d+\\.0");
        String specNumber = "%03d".formatted(Integer.parseInt(version.split("\\.")[1]));

        List<Path> matchingSpecs;
        try (Stream<Path> specDirectories = Files.list(Path.of("specs"))) {
            matchingSpecs = specDirectories.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(specNumber + "-"))
                    .toList();
        }
        assertThat(matchingSpecs).hasSize(1);
        Path contract = matchingSpecs.getFirst().resolve("contracts/openapi.yaml");
        assertThat(contract).exists();
        assertThat(Files.mismatch(published, contract)).isEqualTo(-1L);
    }
}
