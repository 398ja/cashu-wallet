package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import org.springframework.stereotype.Service;
import xyz.tcheeric.cashu.common.annotation.Nut;
import xyz.tcheeric.cashu.common.model.rest.GetActiveKeySetsResponse;

@Nut(2)
@Service
public class RequestActiveKeysets extends AbstractRequestBase<GetActiveKeySetsResponse, Void> {

    public RequestActiveKeysets(@NonNull String baseUrl) {
        super(baseUrl, "/keysets", GetActiveKeySetsResponse.class);
    }
}
