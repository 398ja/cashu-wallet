package cashu.wallet.service;

import cashu.common.annotation.Nut;
import cashu.common.model.rest.GetKeySetsResponse;
import org.springframework.stereotype.Service;

@Nut(1)
@Service
public class RequestKeySetPublicKey extends AbstractRequestBase<GetKeySetsResponse, Void> {

    public RequestKeySetPublicKey() {
        super("/keys", GetKeySetsResponse.class);
    }
}
