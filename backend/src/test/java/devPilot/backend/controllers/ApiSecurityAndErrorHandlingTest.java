package devPilot.backend.controllers;

import devPilot.backend.config.CorsConfig;
import devPilot.backend.config.SecurityConfig;
import devPilot.backend.dto.ChatSessionResponse;
import devPilot.backend.entity.User;
import devPilot.backend.exceptions.BadRequestException;
import devPilot.backend.exceptions.NotFoundException;
import devPilot.backend.security.AppUserPrincipal;
import devPilot.backend.security.CurrentUser;
import devPilot.backend.security.GithubOAuth2UserService;
import devPilot.backend.services.ChatService;
import devPilot.backend.services.RepoService;
import devPilot.backend.services.indexing.IndexingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Service layer is mocked; exercises SecurityConfig, CORS, validation and GlobalExceptionHandler. */
@WebMvcTest(controllers = {AuthController.class, RepoController.class, ChatController.class})
@Import({SecurityConfig.class, CorsConfig.class, CurrentUser.class})
@TestPropertySource(properties = {
        "app.frontend-url=http://localhost:3000",
        "app.cors.allowed-origins=http://localhost:3000",
        "spring.security.oauth2.client.registration.github.client-id=test-client",
        "spring.security.oauth2.client.registration.github.client-secret=test-secret",
})
class ApiSecurityAndErrorHandlingTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    GithubOAuth2UserService githubOAuth2UserService;
    @MockitoBean
    RepoService repoService;
    @MockitoBean
    IndexingService indexingService;
    @MockitoBean
    ChatService chatService;

    private static RequestPostProcessor signedIn() {
        User user = User.builder().id(USER_ID).githubId(42L).githubUsername("octo")
                .displayName("Octo Cat").avatarUrl(null).accessToken("enc").build();
        AppUserPrincipal principal = new AppUserPrincipal(user, Map.of("id", 42));
        return authentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "github"));
    }

    @Test
    void protectedEndpointsRejectAnonymousUsersWith401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/repos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chat/sessions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repoService, chatService, indexingService);
    }

    @Test
    void loginUrlIsPublicAndOauthEntryRedirectsToGithub() throws Exception {
        mvc.perform(get("/api/auth/login-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/oauth2/authorization/github"));
        mvc.perform(get("/oauth2/authorization/github"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("https://github.com/login/oauth/authorize")));
    }

    @Test
    void meReturnsProfileWithoutAccessToken() throws Exception {
        mvc.perform(get("/api/auth/me").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubUsername").value("octo"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void corsAllowsConfiguredFrontendWithCredentialsOnly() throws Exception {
        mvc.perform(options("/api/repos")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/repos")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidUuidIs400NotServerError() throws Exception {
        mvc.perform(get("/api/repos/not-a-uuid").with(signedIn()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
    }

    @Test
    void createSessionValidatesBody() throws Exception {
        mvc.perform(post("/api/chat/sessions").with(signedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("repositoryId")));
        mvc.perform(post("/api/chat/sessions").with(signedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
        verifyNoInteractions(chatService);
    }

    @Test
    void sendMessageRejectsBlankContentBeforeStreaming() throws Exception {
        mvc.perform(post("/api/chat/sessions/" + UUID.randomUUID() + "/messages").with(signedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(chatService);
    }

    @Test
    void createSessionReturnsSessionForOwner() throws Exception {
        UUID repoId = UUID.randomUUID();
        when(chatService.createSession(eq(USER_ID), any()))
                .thenReturn(new ChatSessionResponse(UUID.randomUUID(), repoId, "Chat", Instant.now()));
        mvc.perform(post("/api/chat/sessions").with(signedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryId\":\"" + repoId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryId").value(repoId.toString()));
    }

    @Test
    void domainErrorsMapToMatchingStatusCodes() throws Exception {
        UUID repoId = UUID.randomUUID();
        when(repoService.requireOwned(repoId, USER_ID)).thenThrow(new NotFoundException("Repository not found"));
        mvc.perform(get("/api/repos/" + repoId).with(signedIn()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Repository not found"));

        when(indexingService.startIndexing(repoId, USER_ID))
                .thenThrow(new BadRequestException("Repository is already being indexed"));
        mvc.perform(post("/api/repos/" + repoId + "/index").with(signedIn()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Repository is already being indexed"));
    }

    @Test
    void revokedGithubTokenIs401WithActionableMessage() throws Exception {
        when(repoService.syncAndListRepos(USER_ID)).thenThrow(
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));
        mvc.perform(get("/api/repos").with(signedIn()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("sign in again")));
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() throws Exception {
        when(repoService.listStored(USER_ID)).thenThrow(new IllegalStateException("password=hunter2 at jdbc://db"));
        mvc.perform(get("/api/repos?refresh=false").with(signedIn()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected server error"));
    }

    @Test
    void indexReturns202Accepted() throws Exception {
        UUID repoId = UUID.randomUUID();
        when(repoService.listStored(USER_ID)).thenReturn(List.of());
        devPilot.backend.entity.Repository repo = devPilot.backend.entity.Repository.builder()
                .id(repoId).userId(USER_ID).githubRepoId(1L).owner("o").name("r").fullName("o/r")
                .defaultBranch("main").build();
        when(indexingService.startIndexing(repoId, USER_ID)).thenReturn(repo);
        when(repoService.toResponse(repo)).thenCallRealMethod();
        mvc.perform(post("/api/repos/" + repoId + "/index").with(signedIn()))
                .andExpect(status().isAccepted());
    }

    @Test
    void fullIndexingQueueIs503AndDoesNotLeaveRepoStuckIndexing() throws Exception {
        UUID repoId = UUID.randomUUID();
        devPilot.backend.entity.Repository repo = devPilot.backend.entity.Repository.builder()
                .id(repoId).userId(USER_ID).githubRepoId(1L).owner("o").name("r").fullName("o/r")
                .defaultBranch("main").build();
        when(indexingService.startIndexing(repoId, USER_ID)).thenReturn(repo);
        org.mockito.Mockito.doThrow(new org.springframework.core.task.TaskRejectedException("queue full"))
                .when(indexingService).indexAsync(repoId, USER_ID);

        mvc.perform(post("/api/repos/" + repoId + "/index").with(signedIn()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("busy")));
        org.mockito.Mockito.verify(indexingService).markFailed(eq(repoId), any());
    }

    @Test
    void logoutOnlyAcceptsPost() throws Exception {
        mvc.perform(post("/api/auth/logout").with(signedIn()))
                .andExpect(status().isNoContent());
        // A GET (e.g. an <img src> or link on another page) must not end the session.
        mvc.perform(get("/api/auth/logout").with(signedIn()))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getStatus()).isNotEqualTo(204));
    }
}
