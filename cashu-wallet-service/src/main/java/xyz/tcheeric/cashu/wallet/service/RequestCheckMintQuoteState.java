package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import org.springframework.stereotype.Service;
import xyz.tcheeric.cashu.common.annotation.Nut;
import xyz.tcheeric.cashu.common.model.rest.PostMintQuoteResponse;

@Nut(4)
@Service
public class RequestCheckMintQuoteState extends AbstractRequestBase<PostMintQuoteResponse, Void> {

    public RequestCheckMintQuoteState(@NonNull String baseUrl, @NonNull String quoteId, @NonNull String paymentMethod) {
        super(baseUrl, "/mint/quote/" + paymentMethod + "/" + quoteId, PostMintQuoteResponse.class);
    }

}
