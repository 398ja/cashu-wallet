package cashu.wallet.service;

import cashu.util.Configuration;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@SuppressWarnings("ALL")
@Getter
@Service
@Log
public abstract class AbstractRequestBase<T, U> {

    private final RestTemplate restTemplate;
    private final String path;
    private final String httpMethod;
    private final U requestObject;
    private final Class<T> responseType;  // Add this field

    private String serverAddress;
    private String serverPort;
    private String version;

    protected final static String HTTP_METHOD_GET = "GET";
    protected final static String HTTP_METHOD_POST = "POST";

    public AbstractRequestBase(@NonNull String path, Class<T> responseType) {
        this(path, HTTP_METHOD_GET, null, responseType);
    }

    public AbstractRequestBase(@NonNull String path, @NonNull String httpMethod, U requestObject, Class<T> responseType) {
        this.restTemplate = new RestTemplateBuilder().build();
        this.path = path;
        this.httpMethod = httpMethod;
        this.requestObject = requestObject;
        this.responseType = responseType;  // Initialize the field
        this.initConfigVariables();
    }


    public T execute() {
        final String url = getUrl();
        return switch (httpMethod) {
            case HTTP_METHOD_GET -> restTemplate.getForObject(url + path, responseType);
            case HTTP_METHOD_POST -> restTemplate.postForObject(url + path, this.requestObject, responseType);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + httpMethod);
        };
    }

    // TODO - use HTTPS instead of HTTP
    public String getUrl() {
        String address = System.getProperty("server.address") != null ? System.getProperty("server.address") : serverAddress;
        String port = System.getProperty("server.port") != null ? System.getProperty("server.port") : (serverPort != null ? serverPort : "8080");
        String version = System.getProperty("mint.version") != null ? System.getProperty("mint.version") : "v1";
        return "http://" + address + ":" + port + "/" + version;
    }

    private void initConfigVariables() {
        try (InputStream inputStream = AbstractRequestBase.class.getResourceAsStream("/mint.properties")) {
            Configuration configuration = Configuration.load(Objects.requireNonNull(inputStream));
            serverAddress = configuration.getValue("server.address");
            serverPort = configuration.getValue("server.port");
            version = configuration.getValue("mint.version");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration", e);
        }
    }
}
