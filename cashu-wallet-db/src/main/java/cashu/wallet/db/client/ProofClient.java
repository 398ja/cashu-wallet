package cashu.wallet.db.client;

import cashu.wallet.db.client.BaseClient;
import cashu.wallet.db.model.ProofEntity;
import lombok.NonNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class ProofClient extends BaseClient<ProofEntity> {

    public ProofClient() {
        super("/proof");
    }

    public ProofEntity getBySignature(@NonNull String signature) {
        RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/signature/" + signature)).build();
        ResponseEntity<ProofEntity> response = restTemplate.exchange(requestEntity, ProofEntity.class);
        return response.getBody();
    }

    public List<ProofEntity> getAllProofs() {
        try {
            RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/")).build();
            ResponseEntity<List<ProofEntity>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<List<ProofEntity>>() {});
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return new ArrayList<>();
        }
    }

    public List<ProofEntity> getByKeySetId(@NonNull String keysetId) {
        try {
            RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/keyset/" + keysetId)).build();
            ResponseEntity<List<ProofEntity>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<List<ProofEntity>>() {});
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return new ArrayList<>();
        }
    }

    public ProofEntity getByAmountAndKeysetId(@NonNull Integer amount, @NonNull String keysetId) {
        try {
            RequestEntity<Void> requestEntity = RequestEntity.get(URI.create(getUrl() + "/amount/" + amount + "/keyset/" + keysetId)).build();
            ResponseEntity<ProofEntity> response = restTemplate.exchange(requestEntity, ProofEntity.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        }
    }
}