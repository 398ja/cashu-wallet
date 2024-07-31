package cashu.wallet.db.client;

import cashu.wallet.db.model.MeltQuoteResponseEntity;
import lombok.NonNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

public class MeltQuoteResponseClient extends BaseClient<MeltQuoteResponseEntity> {

    public MeltQuoteResponseClient() {
        super("/melt/quote/response");
    }

    public MeltQuoteResponseEntity getByCorrelationId(@NonNull String correlationId) {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/correlation/" + correlationId)).build();
        ResponseEntity<MeltQuoteResponseEntity> response = restTemplate.exchange(requestEntity, MeltQuoteResponseEntity.class);
        return response.getBody();
    }

    public MeltQuoteResponseEntity getByQuote(@NonNull String quote) {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/quote/" + quote)).build();
        ResponseEntity<MeltQuoteResponseEntity> response = restTemplate.exchange(requestEntity, MeltQuoteResponseEntity.class);
        return response.getBody();
    }

    public List<MeltQuoteResponseEntity> getAllMeltQuoteResponses() {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/")).build();
        ResponseEntity<List<MeltQuoteResponseEntity>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<>() {
        });
        return response.getBody();
    }
}