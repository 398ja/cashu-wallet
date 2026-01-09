package xyz.tcheeric.cashu.wallet.proto.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.util.JsonUtils;
import xyz.tcheeric.cashu.entities.rest.PostCheckStateRequest;
import xyz.tcheeric.cashu.entities.rest.PostCheckStateResponse;
import xyz.tcheeric.cashu.wallet.proto.service.CheckStateClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Default HTTP client for calling the mint's /checkstate endpoint.
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultCheckStateClient implements CheckStateClient {

    private static final String CHECK_STATE_PATH = "/checkstate";

    private final HttpClient httpClient;

    public DefaultCheckStateClient() {
        this(HttpClient.newHttpClient());
    }

    @Override
    public PostCheckStateResponse checkState(String mintUrl, PostCheckStateRequest request) {
        Objects.requireNonNull(mintUrl, "Mint URL cannot be null");
        Objects.requireNonNull(request, "PostCheckStateRequest cannot be null");

        String normalizedBase = mintUrl.endsWith("/") ? mintUrl.substring(0, mintUrl.length() - 1) : mintUrl;
        URI uri = URI.create(normalizedBase + CHECK_STATE_PATH);

        try {
            String payload = JsonUtils.JSON_MAPPER.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

            log.debug("check_state http_request_started uri={} secrets_count={}",
                uri, request.getHashToCurveSecrets() == null ? 0 : request.getHashToCurveSecrets().size());

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(String.format(
                    "Mint /checkstate returned HTTP %d. Suggestion: retry later or switch mint.",
                    response.statusCode()
                ));
            }

            PostCheckStateResponse parsed = JsonUtils.JSON_MAPPER.readValue(
                response.body(),
                PostCheckStateResponse.class
            );

            log.debug("check_state http_request_completed uri={} states_count={}",
                uri, parsed.getStates() == null ? 0 : parsed.getStates().size());

            return parsed;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Failed to call mint /checkstate endpoint. Suggestion: verify mint availability and retry.",
                e
            );
        }
    }
}
