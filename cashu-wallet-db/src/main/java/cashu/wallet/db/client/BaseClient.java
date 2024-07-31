package cashu.wallet.db.client;

import cashu.util.Configuration;
import lombok.NonNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BaseClient<T> {

    protected final RestTemplate restTemplate;
    private final String path;
    private String serverAddress;
    private String serverPort;


    public BaseClient(@NonNull String path) {
        this.restTemplate = new RestTemplate();
        this.path = path;
        this.setConfigAttributes();
    }


    public T createEntity(@NonNull T entity) {
        HttpEntity<T> request = new HttpEntity<>(entity);
        ResponseEntity<T> response = restTemplate.exchange(
                getUrl(),
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<T>() {}
        );
        return response.getBody();
    }

    public void deleteEntity(@NonNull Integer id) {
        restTemplate.delete(getUrl() + "/" + id);
    }

    public void deleteAll() {
        restTemplate.delete(getUrl() + "/");
    }

    public List<T> getAll() {
        try {
            RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/")).build();
            ResponseEntity<List<T>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<>() {});
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return new ArrayList<>();
        }
    }


    public String getBaseUrl() {
        String address = System.getProperty("server.address") != null ? System.getProperty("server.address") : serverAddress;
        String port = System.getProperty("server.port") != null ? System.getProperty("server.port") : (serverPort != null ? serverPort : "8080");
        return "http://" + address + ":" + port;
    }

    protected String getUrl() {
        return getBaseUrl() + path;
    }

    private void setConfigAttributes() {
        InputStream inputStream = BaseClient.class.getResourceAsStream("/application.properties");
        Configuration configuration = Configuration.load(Objects.requireNonNull(inputStream));
        serverAddress = configuration.getValue("server.address");
        serverPort = configuration.getValue("server.port");
    }


}
