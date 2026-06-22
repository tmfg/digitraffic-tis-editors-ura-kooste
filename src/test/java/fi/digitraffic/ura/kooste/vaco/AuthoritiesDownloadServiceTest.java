package fi.digitraffic.ura.kooste.vaco;

import io.quarkus.oidc.client.Tokens;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthoritiesDownloadServiceTest {

    @Test
    void resolvesAuthoritiesEndpointCorrectly() {
        AuthoritiesDownloadService service = new AuthoritiesDownloadService(
            "https://vaco.example.com",
            null // tokens not needed for URI resolution test
        );
        assertThat(
            service.resolveAuthoritiesUri().toString(),
            is(equalTo("https://vaco.example.com/api/exports/netex/authorities"))
        );
    }
    
    @Test
    void returnsNullWhenTokenFails() {
        Tokens tokens = mock(Tokens.class);
        when(tokens.getAccessToken()).thenThrow(new RuntimeException("token refresh failed"));

        AuthoritiesDownloadService service = new AuthoritiesDownloadService(
            "https://vaco.example.com",
            tokens
        );

        assertThat(service.fetchAuthoritiesXml(), is(nullValue()));
    }
}
