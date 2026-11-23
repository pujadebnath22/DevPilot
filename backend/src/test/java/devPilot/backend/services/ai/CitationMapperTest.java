package devPilot.backend.services.ai;

import devPilot.backend.dto.CitationDto;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CitationMapperTest {

    private final CitationMapper mapper = new CitationMapper(JsonMapper.builder().build());

    @Test
    void mapsDocumentMetadata() {
        Document doc = new Document("x", Map.of(
                "filePath", "src/A.java", "language", "java", "startLine", "3", "endLine", 9));
        assertThat(mapper.fromDocument(doc)).isEqualTo(new CitationDto("src/A.java", 3, 9, "java"));
    }

    @Test
    void roundTripsJson() {
        List<CitationDto> citations = List.of(new CitationDto("a.ts", null, null, "ts"));
        assertThat(mapper.fromJson(mapper.toJson(citations))).isEqualTo(citations);
    }

    @Test
    void toleratesMissingOrCorruptJson() {
        assertThat(mapper.fromJson(null)).isEmpty();
        assertThat(mapper.fromJson("")).isEmpty();
        assertThat(mapper.fromJson("{not json")).isEmpty();
    }
}
