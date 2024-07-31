package cashu.wallet.db.client;

import cashu.wallet.db.model.MintQuoteResponseEntity;
import lombok.NonNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

public class MintQuoteResponseClient extends BaseClient<MintQuoteResponseEntity> {

    public MintQuoteResponseClient() {
        super("/mint/quote/response");
    }

    public MintQuoteResponseEntity getByQuoteId(@NonNull String quoteId) {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/quote/" + quoteId)).build();
        ResponseEntity<MintQuoteResponseEntity> response = restTemplate.exchange(requestEntity, MintQuoteResponseEntity.class);
        return response.getBody();
    }

    public List<MintQuoteResponseEntity> getAllMintQuoteResponses() {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/")).build();
        ResponseEntity<List<MintQuoteResponseEntity>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<>() {
        });
        return response.getBody();
    }

    public MintQuoteResponseEntity getByCorrelationId(@NonNull String correlationId) {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/correlation/" + correlationId)).build();
        ResponseEntity<MintQuoteResponseEntity> response = restTemplate.exchange(requestEntity, MintQuoteResponseEntity.class);
        return response.getBody();
    }
}