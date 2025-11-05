package xyz.tcheeric.cashu.wallet.client;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

@SuppressWarnings("ALL")
@Getter
@Slf4j
public abstract class AbstractRequestBase<T, U> {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String path;
    private final String httpMethod;
    private final U requestObject;
    private final Class<T> responseType;  // Add this field

    private String serverAddress;
    private String serverPort;
    private String version;

    protected final static String HTTP_METHOD_GET = "GET";
    protected final static String HTTP_METHOD_POST = "POST";

    public AbstractRequestBase(@NonNull String baseUrl, @NonNull String path, Class<T> responseType) {
        this(baseUrl, path, HTTP_METHOD_GET, null, responseType);
    }

    public AbstractRequestBase(@NonNull String baseUrl, @NonNull String path, @NonNull String httpMethod, U requestObject, Class<T> responseType) {
        this.restTemplate = new RestTemplateBuilder().build();
        this.baseUrl = baseUrl;
        this.path = path;
        this.httpMethod = httpMethod;
        this.requestObject = requestObject;
        this.responseType = responseType;  // Initialize the field
    }


    public T execute() {
        return switch (httpMethod) {
            case HTTP_METHOD_GET -> {
                log.debug("request_base dispatching_request method={} target={} reason=execute_invoked",
                    HTTP_METHOD_GET, baseUrl + path);
                T response = restTemplate.getForObject(baseUrl + path, responseType);
                log.info("request_base request_completed method={} target={} result=success",
                    HTTP_METHOD_GET, baseUrl + path);
                yield response;
            }
            case HTTP_METHOD_POST -> {
                log.debug("request_base dispatching_request method={} target={} reason=execute_invoked",
                    HTTP_METHOD_POST, baseUrl + path);
                T response = restTemplate.postForObject(baseUrl + path, this.requestObject, responseType);
                log.info("request_base request_completed method={} target={} result=success",
                    HTTP_METHOD_POST, baseUrl + path);
                yield response;
            }
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + httpMethod);
        };
    }
}
