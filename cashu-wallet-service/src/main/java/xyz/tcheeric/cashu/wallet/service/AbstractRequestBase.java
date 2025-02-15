package xyz.tcheeric.cashu.wallet.service;

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
            case HTTP_METHOD_GET -> restTemplate.getForObject(baseUrl + path, responseType);
            case HTTP_METHOD_POST -> restTemplate.postForObject(baseUrl + path, this.requestObject, responseType);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + httpMethod);
        };
    }
}
