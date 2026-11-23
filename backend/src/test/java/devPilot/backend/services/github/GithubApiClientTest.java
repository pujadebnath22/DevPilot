package devPilot.backend.services.github;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Uses a mocked HTTP server; no real GitHub calls are made. */
class GithubApiClientTest {

    @Test
    void eachRequestCarriesOnlyItsOwnUsersToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GithubApiClient client = new GithubApiClient(builder);

        server.expect(requestTo(containsString("/user/repos")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertThat(request.getHeaders().get(HttpHeaders.AUTHORIZATION))
                        .containsExactly("Bearer token-alice"))
                .andRespond(withSuccess("[{\"id\":1,\"full_name\":\"alice/a\"}]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(containsString("/user/repos")))
                .andExpect(request -> assertThat(request.getHeaders().get(HttpHeaders.AUTHORIZATION))
                        .containsExactly("Bearer token-bob"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.listUserRepos("token-alice")).hasSize(1);
        assertThat(client.listUserRepos("token-bob")).isEmpty();
        server.verify();
    }

    @Test
    void sharedBuilderIsNotMutatedWithGithubDefaults() {
        RestClient.Builder builder = RestClient.builder();
        new GithubApiClient(builder);

        MockRestServiceServer other = MockRestServiceServer.bindTo(builder).build();
        RestClient plain = builder.build();
        other.expect(requestTo("https://example.test/ping"))
                .andExpect(request -> {
                    assertThat(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
                    assertThat(request.getHeaders().containsHeader("X-GitHub-Api-Version")).isFalse();
                })
                .andRespond(withSuccess());
        plain.get().uri("https://example.test/ping").retrieve().toBodilessEntity();
        other.verify();
    }

    @Test
    void decodesBase64FileContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GithubApiClient client = new GithubApiClient(builder);

        String encoded = Base64.getEncoder().encodeToString("class A {}\n".getBytes(StandardCharsets.UTF_8));
        // GitHub wraps base64 content with newlines; the client must strip whitespace.
        String wrapped = encoded.substring(0, 4) + "\\n" + encoded.substring(4);
        server.expect(requestTo(containsString("/repos/o/r/contents/")))
                .andRespond(withSuccess("{\"encoding\":\"base64\",\"content\":\"" + wrapped + "\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.getFileContent("t", "o", "r", "A.java")).isEqualTo("class A {}\n");
        server.verify();
    }

    @Test
    void stopsPagingOnShortPage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GithubApiClient client = new GithubApiClient(builder);
        server.expect(requestTo(containsString("page=1")))
                .andRespond(withSuccess("[{\"id\":1},{\"id\":2}]", MediaType.APPLICATION_JSON));

        assertThat(client.listUserRepos("t")).hasSize(2);
        server.verify();
    }
}
