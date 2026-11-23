package devPilot.backend.services.indexing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeFileFilterTest {

    private final CodeFileFilter filter = new CodeFileFilter();

    @Test
    void acceptsSourceFiles() {
        assertThat(filter.isEligible("src/main/java/App.java", 100, 1000)).isTrue();
        assertThat(filter.isEligible("client/app/page.tsx", 100, 1000)).isTrue();
        assertThat(filter.isEligible("Dockerfile", 100, 1000)).isTrue();
        assertThat(filter.isEligible("docs\\README.md", 100, 1000)).isTrue();
    }

    @Test
    void rejectsGeneratedVendoredHiddenLockBinaryAndLargeFiles() {
        assertThat(filter.isEligible("client/node_modules/x/index.js", 10, 1000)).isFalse();
        assertThat(filter.isEligible("backend/target/classes/A.java", 10, 1000)).isFalse();
        assertThat(filter.isEligible("client/package-lock.json", 10, 1000)).isFalse();
        assertThat(filter.isEligible(".env", 10, 1000)).isFalse();
        assertThat(filter.isEligible("logo.png", 10, 1000)).isFalse();
        assertThat(filter.isEligible("LICENSE", 10, 1000)).isFalse();
        assertThat(filter.isEligible("src/Big.java", 2000, 1000)).isFalse();
        assertThat(filter.isEligible("", 1, 1000)).isFalse();
        assertThat(filter.isEligible(null, 1, 1000)).isFalse();
    }

    @Test
    void detectsLanguageFromExtension() {
        assertThat(filter.detectLanguage("a/b/C.Java")).isEqualTo("java");
        assertThat(filter.detectLanguage("Makefile")).isEqualTo("makefile");
        assertThat(filter.detectLanguage("bin/run")).isEqualTo("text");
    }
}
