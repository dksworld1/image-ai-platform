package com.imageaiplatform.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "datasource.app.url=jdbc:h2:mem:h2-console-app;DB_CLOSE_DELAY=-1",
        "datasource.audit.url=jdbc:h2:mem:h2-console-audit;DB_CLOSE_DELAY=-1",
        "datasource.catalog.url=jdbc:h2:mem:h2-console-catalog;DB_CLOSE_DELAY=-1",
        "datasource.archive.url=jdbc:h2:mem:h2-console-archive;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("local")
class H2ConsoleTest {
    @Value("${local.server.port}")
    private Integer port;

    @Test
    void servesConsoleWithSameOriginFramesWhileKeepingOtherPathsProtectedFromFraming() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var console = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/h2-console/"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(console.statusCode()).isEqualTo(200);
            assertThat(console.body()).contains("H2 Console");
            assertThat(console.headers().firstValue("X-Frame-Options")).contains("SAMEORIGIN");

            var health = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/health"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.headers().firstValue("X-Frame-Options")).contains("DENY");
        }
    }
}
