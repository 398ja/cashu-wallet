package cashu.wallet.db.client;

import cashu.wallet.db.model.MintQuoteRequestEntity;
import lombok.NonNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

public class MintQuoteRequestClient extends BaseClient<MintQuoteRequestEntity> {

    public MintQuoteRequestClient() {
        super("/mint/quote/request");
    }

    public MintQuoteRequestEntity getByCorrelationId(@NonNull String correlationId) {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/correlation/" + correlationId)).build();
        ResponseEntity<MintQuoteRequestEntity> response = restTemplate.exchange(requestEntity, MintQuoteRequestEntity.class);
        return response.getBody();
    }

    public List<MintQuoteRequestEntity> getAllMintQuoteRequests() {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/")).build();
        ResponseEntity<List<MintQuoteRequestEntity>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<>() {
        });
        return response.getBody();
    }
}