package cashu.wallet.client.util;

import cashu.common.model.KeySet;
import cashu.common.model.rest.ActiveKeySetResponse;
import cashu.common.model.rest.KeySetResponse;
import cashu.util.Configuration;
import cashu.wallet.service.AbstractRequestBase;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Getter
public class MintRestClient {

    private final String keysetId;
    private final List<Integer> denominations;
    private final RestTemplate restTemplate;

    public MintRestClient() {
        this(null);
    }

    public MintRestClient(String unit) {
        restTemplate = new RestTemplate();
        if (unit != null) {
            Map<String, List<Integer>> denominationsMap = getDenominations(unit);
            this.keysetId = denominationsMap.keySet().iterator().next();
            this.denominations = denominationsMap.get(keysetId);
        } else {
            this.keysetId = null;
            this.denominations = null;
        }
    }

    public List<KeySet> keys() {
        String url = getBaseUrl() + "/keys";
        KeySetResponse response = restTemplate.getForObject(url, KeySetResponse.class);
        return Objects.requireNonNull(response).getKeysets();
    }

    public List<KeySet> keys(String keysetId) {
        String url = getBaseUrl() + "/keys/" + keysetId;
        KeySetResponse response = restTemplate.getForObject(url, KeySetResponse.class);
        return Objects.requireNonNull(response).getKeysets();
    }


    public ActiveKeySetResponse keysets() {
        String url = getBaseUrl() + "/keysets";
        return restTemplate.getForObject(url, ActiveKeySetResponse.class);
    }


    private Map<String, List<Integer>> getDenominations(@NonNull String unit) {
        Map<String, List<Integer>> result = new HashMap<>();
        List<Integer> denominations = new ArrayList<>();

        String baseUrl = getBaseUrl();
        CompletableFuture<Void> future = CompletableFuture
                // TODO - Mint url is hardcoded
                .supplyAsync(() -> restTemplate.getForEntity(baseUrl + "/keys", KeySetResponse.class).getBody())
                .thenAccept(keySetResponse -> {
                    List<KeySet> keysets = keySetResponse.getKeysets();

                    keysets.stream()
                            .filter(keyset -> keyset.getUnit().equals(unit))
                            .forEach(keyset -> {
                                result.put(keyset.getId(), denominations);
                                keyset.getKeys().getValues()
                                        .forEach((bigInteger, publicKey) -> denominations.add(bigInteger.intValue()));
                            });
                });

        future.join();  // Wait for the CompletableFuture to complete

        return result;
    }

    private String getBaseUrl() {
        try (InputStream inputStream = AbstractRequestBase.class.getResourceAsStream("/application.properties")) {
            Configuration configuration = Configuration.load(Objects.requireNonNull(inputStream));
            return configuration.getValue("mint.base_url");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration", e);
        }

    }
}
