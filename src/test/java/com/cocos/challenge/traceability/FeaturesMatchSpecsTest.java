package com.cocos.challenge.traceability;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class FeaturesMatchSpecsTest {

    @Test
    void copiedFeaturesAreByteIdenticalToTheirSpecSources() throws IOException {
        try (var paths = Files.walk(Path.of("src/test/resources/features"))) {
            List<Path> copies = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".feature"))
                    .filter(this::containsCuTag)
                    .toList();
            for (Path copy : copies) {
                List<Path> sources = specSources(copy.getFileName().toString());
                assertThat(sources).hasSize(1);
                assertThat(Files.readAllBytes(copy)).isEqualTo(Files.readAllBytes(sources.getFirst()));
            }
        }
    }

    private boolean containsCuTag(Path path) {
        try {
            return Files.readString(path).contains("@CU-");
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static List<Path> specSources(String fileName) throws IOException {
        try (var paths = Files.walk(Path.of("specs"))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .filter(path -> path.getParent().getFileName().toString().equals("features"))
                    .toList();
        }
    }
}
