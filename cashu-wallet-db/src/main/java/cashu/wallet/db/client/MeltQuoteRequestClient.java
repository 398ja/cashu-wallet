package cashu.wallet.db.client;

import cashu.wallet.db.model.MeltQuoteRequestEntity;
import lombok.NonNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

public class MeltQuoteRequestClient extends BaseClient<MeltQuoteRequestEntity> {

    public MeltQuoteRequestClient() {
        super("/melt/quote/request");
    }

    public MeltQuoteRequestEntity getByCorrelationId(@NonNull String correlationId) {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/correlation/" + correlationId)).build();
        ResponseEntity<MeltQuoteRequestEntity> response = restTemplate.exchange(requestEntity, MeltQuoteRequestEntity.class);
        return response.getBody();
    }

    public List<MeltQuoteRequestEntity> getAllMeltQuoteRequests() {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/")).build();
        ResponseEntity<List<MeltQuoteRequestEntity>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<>() {
        });
        return response.getBody();
    }
}