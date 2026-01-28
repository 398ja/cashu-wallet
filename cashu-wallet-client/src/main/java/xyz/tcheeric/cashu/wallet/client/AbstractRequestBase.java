package xyz.tcheeric.cashu.wallet.client;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

@SuppressWarnings("ALL")
@Getter
@Slf4j
public abstract class AbstractRequestBase<T, U> {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String path;
    private final String httpMethod;
    private final U requestObject;
    private final Class<T> responseType;

    private String serverAddress;
    private String serverPort;
    private String version;

    protected final static String HTTP_METHOD_GET = "GET";
    protected final static String HTTP_METHOD_POST = "POST";

    // Request ID header for tracing
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    // Timeout configuration
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Lazy initialization holder for the shared RestTemplate.
     * Uses initialization-on-demand holder idiom for thread-safe lazy initialization.
     */
    private static class RestTemplateHolder {
        private static final RestTemplate INSTANCE = createConfiguredRestTemplate();

        private static RestTemplate createConfiguredRestTemplate() {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(CONNECT_TIMEOUT);
            factory.setReadTimeout(READ_TIMEOUT);

            RestTemplate template = new RestTemplateBuilder()
                    .requestFactory(() -> factory)
                    .build();

            log.info("request_base shared_rest_template_created connect_timeout={}s read_timeout={}s",
                    CONNECT_TIMEOUT.toSeconds(), READ_TIMEOUT.toSeconds());
            return template;
        }
    }

    /**
     * Gets the shared RestTemplate instance. Lazily initialized on first access.
     */
    private static RestTemplate getSharedRestTemplate() {
        return RestTemplateHolder.INSTANCE;
    }

    public AbstractRequestBase(@NonNull String baseUrl, @NonNull String path, Class<T> responseType) {
        this(baseUrl, path, HTTP_METHOD_GET, null, responseType);
    }

    public AbstractRequestBase(@NonNull String baseUrl, @NonNull String path, @NonNull String httpMethod, U requestObject, Class<T> responseType) {
        this.restTemplate = getSharedRestTemplate();  // Use shared instance (lazy initialized)
        this.baseUrl = baseUrl;
        this.path = path;
        this.httpMethod = httpMethod;
        this.requestObject = requestObject;
        this.responseType = responseType;
    }

    public T execute() {
        // Generate unique request ID for tracing
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String url = baseUrl + path;

        return switch (httpMethod) {
            case HTTP_METHOD_GET -> {
                log.debug("request_base dispatching_request method={} target={} request_id={} reason=execute_invoked",
                    HTTP_METHOD_GET, url, requestId);

                HttpHeaders headers = new HttpHeaders();
                headers.set(REQUEST_ID_HEADER, requestId);
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                long startTime = System.currentTimeMillis();
                ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
                long duration = System.currentTimeMillis() - startTime;

                log.info("request_base request_completed method={} target={} request_id={} result=success duration_ms={}",
                    HTTP_METHOD_GET, url, requestId, duration);
                yield response.getBody();
            }
            case HTTP_METHOD_POST -> {
                log.debug("request_base dispatching_request method={} target={} request_id={} reason=execute_invoked",
                    HTTP_METHOD_POST, url, requestId);

                HttpHeaders headers = new HttpHeaders();
                headers.set(REQUEST_ID_HEADER, requestId);
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                HttpEntity<U> entity = new HttpEntity<>(this.requestObject, headers);

                long startTime = System.currentTimeMillis();
                ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.POST, entity, responseType);
                long duration = System.currentTimeMillis() - startTime;

                log.info("request_base request_completed method={} target={} request_id={} result=success duration_ms={}",
                    HTTP_METHOD_POST, url, requestId, duration);
                yield response.getBody();
            }
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + httpMethod);
        };
    }
}
