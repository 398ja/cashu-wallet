package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import org.springframework.stereotype.Service;
import xyz.tcheeric.cashu.common.annotation.Nut;
import xyz.tcheeric.cashu.common.model.rest.GetKeySetsResponse;

@Nut(1)
@Service
public class RequestKeySetPublicKey extends AbstractRequestBase<GetKeySetsResponse, Void> {

    public RequestKeySetPublicKey(@NonNull String baseUrl) {
        super(baseUrl, "/keys", GetKeySetsResponse.class);
    }

    public RequestKeySetPublicKey(@NonNull String baseUrl, @NonNull String keySetId) {
        super(baseUrl, "/keys/" + keySetId, GetKeySetsResponse.class);
    }
}
