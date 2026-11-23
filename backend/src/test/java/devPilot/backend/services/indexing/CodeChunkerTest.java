package devPilot.backend.services.indexing;

import devPilot.backend.services.ai.RagSettings;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeChunkerTest {

    private final CodeChunker chunker = new CodeChunker(800, new CodeFileFilter());

    @Test
    void blankContentProducesNoChunks() {
        assertThat(chunker.chunkFile("r1", "A.java", "  ")).isEmpty();
        assertThat(chunker.chunkFile("r1", "A.java", null)).isEmpty();
    }

    @Test
    void chunksCarryRepositoryScopedMetadataWithSequentialIndexes() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            content.append("public int method").append(i).append("() { return ").append(i).append("; }\n");
        }

        List<Document> chunks = chunker.chunkFile("repo-123", "src/Big.java", content.toString());

        assertThat(chunks).hasSizeGreaterThan(1);
        for (int i = 0; i < chunks.size(); i++) {
            var meta = chunks.get(i).getMetadata();
            assertThat(meta.get(RagSettings.METADATA_REPO_ID)).isEqualTo("repo-123");
            assertThat(meta.get("filePath")).isEqualTo("src/Big.java");
            assertThat(meta.get("language")).isEqualTo("java");
            assertThat(meta.get("chunkIndex")).isEqualTo(i);
        }
        assertThat(chunks.get(0).getText()).startsWith("// File: src/Big.java");
    }
}
