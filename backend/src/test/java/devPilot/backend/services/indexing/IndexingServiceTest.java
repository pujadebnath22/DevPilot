package devPilot.backend.services.indexing;

import devPilot.backend.entity.IndexStatus;
import devPilot.backend.entity.Repository;
import devPilot.backend.entity.User;
import devPilot.backend.exceptions.BadRequestException;
import devPilot.backend.repository.RepositoryRepository;
import devPilot.backend.services.UserService;
import devPilot.backend.services.github.GitHubRateLimiter;
import devPilot.backend.services.github.GithubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GitHub and the vector store are mocked; this exercises the indexing state machine only. */
class IndexingServiceTest {

    private final UUID repoId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private RepositoryRepository repositoryRepository;
    private UserService userService;
    private GithubApiClient github;
    private VectorStore vectorStore;
    private IndexingService service;
    private Repository repo;

    @BeforeEach
    void setUp() {
        repositoryRepository = mock(RepositoryRepository.class);
        userService = mock(UserService.class);
        github = mock(GithubApiClient.class);
        vectorStore = mock(VectorStore.class);
        CodeFileFilter filter = new CodeFileFilter();
        service = new IndexingService(repositoryRepository, userService, github, filter,
                new CodeChunker(800, filter), new GitHubRateLimiter(0), vectorStore);
        ReflectionTestUtils.setField(service, "maxFileBytes", 102_400L);

        repo = Repository.builder()
                .id(repoId).userId(userId).githubRepoId(1L)
                .owner("o").name("r").fullName("o/r").defaultBranch("main")
                .build();
        User user = User.builder().id(userId).accessToken("enc").build();

        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(repositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(repo));
        when(repositoryRepository.save(any(Repository.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.requiredById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("token");
        when(github.getRepoTree("token", "o", "r", "main")).thenReturn(Map.of("tree", List.of(
                Map.of("type", "blob", "path", "src/A.java", "size", 50),
                Map.of("type", "blob", "path", "src/B.java", "size", 50),
                Map.of("type", "blob", "path", "image.png", "size", 50),
                Map.of("type", "tree", "path", "src", "size", 0))));
    }

    @Test
    void successfulRunMarksReadyAndStoresVectors() {
        when(github.getFileContent(eq("token"), eq("o"), eq("r"), anyString())).thenReturn("class A {}");

        service.indexAsync(repoId, userId);

        assertThat(repo.getIndexStatus()).isEqualTo(IndexStatus.READY);
        assertThat(repo.getFilesTotal()).isEqualTo(2);
        assertThat(repo.getFilesProcessed()).isEqualTo(2);
        assertThat(repo.getChunkCount()).isEqualTo(2);
        assertThat(repo.getIndexedAt()).isNotNull();
        verify(vectorStore).delete(any(Filter.Expression.class));
        verify(vectorStore).add(anyList());
    }

    @Test
    void unreadableFileIsSkippedWithoutFailingTheRun() {
        when(github.getFileContent("token", "o", "r", "src/A.java")).thenThrow(new RuntimeException("404"));
        when(github.getFileContent("token", "o", "r", "src/B.java")).thenReturn("class B {}");

        service.indexAsync(repoId, userId);

        assertThat(repo.getIndexStatus()).isEqualTo(IndexStatus.READY);
        assertThat(repo.getFilesProcessed()).isEqualTo(2);
        assertThat(repo.getChunkCount()).isEqualTo(1);
    }

    @Test
    void embeddingFailureMarksRepositoryFailedInsteadOfReady() {
        when(github.getFileContent(eq("token"), eq("o"), eq("r"), anyString())).thenReturn("class A {}");
        doThrow(new RuntimeException("API key not valid")).when(vectorStore).add(anyList());

        service.indexAsync(repoId, userId);

        assertThat(repo.getIndexStatus()).isEqualTo(IndexStatus.FAILED);
        assertThat(repo.getErrorMessage()).contains("API key not valid");
        assertThat(repo.getIndexedAt()).isNull();
    }

    @Test
    void cannotStartWhileAlreadyIndexing() {
        repo.setIndexStatus(IndexStatus.INDEXING);
        assertThatThrownBy(() -> service.startIndexing(repoId, userId))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void startResetsProgress() {
        repo.setIndexStatus(IndexStatus.FAILED);
        repo.setErrorMessage("old");
        repo.setChunkCount(9);

        Repository started = service.startIndexing(repoId, userId);

        assertThat(started.getIndexStatus()).isEqualTo(IndexStatus.INDEXING);
        assertThat(started.getErrorMessage()).isNull();
        assertThat(started.getChunkCount()).isZero();
    }

    @Test
    void interruptedJobsAreMarkedFailedOnStartup() {
        repo.setIndexStatus(IndexStatus.INDEXING);
        when(repositoryRepository.findByIndexStatus(IndexStatus.INDEXING)).thenReturn(List.of(repo));

        service.recoverInterruptedIndexing();

        assertThat(repo.getIndexStatus()).isEqualTo(IndexStatus.FAILED);
        assertThat(repo.getErrorMessage()).contains("interrupted");
    }
}
