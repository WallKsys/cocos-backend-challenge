package com.cocos.challenge.contract;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PublishedContractIsLatestSpecContractTest {

    @Test
    void publishedContractIsByteIdenticalToTheLatestSpecContract() throws IOException {
        Path latestContract;
        try (Stream<Path> specDirectories = Files.list(Path.of("specs"))) {
            latestContract = specDirectories.filter(Files::isDirectory)
                    .map(path -> path.resolve("contracts/openapi.yaml"))
                    .filter(Files::isRegularFile)
                    .max(Comparator.comparingInt(PublishedContractIsLatestSpecContractTest::specNumber))
                    .orElseThrow();
        }
        Path published = Path.of("src/main/resources/static/openapi/openapi.yaml");
        assertThat(published).exists();
        assertThat(Files.mismatch(published, latestContract)).isEqualTo(-1L);
    }

    private static int specNumber(Path contract) {
        return Integer.parseInt(contract.getParent().getParent().getFileName().toString().substring(0, 3));
    }
}
