package cashu.wallet.db.client;

import cashu.wallet.db.client.BaseClient;
import cashu.wallet.db.model.MintRequestEntity;
import lombok.NonNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

public class MintRequestClient extends BaseClient<MintRequestEntity> {

    public MintRequestClient() {
        super("/mint/request");
    }

    public List<MintRequestEntity> getByCorrelationId(@NonNull String correlationId) {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/" + correlationId)).build();
        ResponseEntity<List<MintRequestEntity>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<>() {
        });
        return response.getBody();
    }

    public List<MintRequestEntity> getAllMintRequests() {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/")).build();
        ResponseEntity<List<MintRequestEntity>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<>() {
        });
        return response.getBody();
    }

    public MintRequestEntity getByCorrelationIdAndBlindMessage(@NonNull String correlationId, @NonNull String blindMessage) {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/" + correlationId + "/" + blindMessage)).build();
        ResponseEntity<MintRequestEntity> response = restTemplate.exchange(requestEntity, MintRequestEntity.class);
        return response.getBody();
    }

}