package cashu.wallet.service;

import cashu.common.annotation.Nut;
import cashu.common.model.rest.GetActiveKeySetsResponse;
import org.springframework.stereotype.Service;

@Nut(2)
@Service
public class RequestActiveKeysets extends AbstractRequestBase<GetActiveKeySetsResponse, Void> {

    public RequestActiveKeysets() {
        super("/keysets", GetActiveKeySetsResponse.class);
    }
}
