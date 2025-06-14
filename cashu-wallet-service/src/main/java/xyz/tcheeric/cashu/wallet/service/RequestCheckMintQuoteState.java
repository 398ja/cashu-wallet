package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import org.springframework.stereotype.Service;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.PostMintQuoteResponse;

@Nut(4)
@Service
public class RequestCheckMintQuoteState extends AbstractRequestBase<PostMintQuoteResponse, Void> {

    public RequestCheckMintQuoteState(@NonNull String baseUrl, @NonNull String quoteId, @NonNull String paymentMethod) {
        super(baseUrl, "/mint/quote/" + paymentMethod + "/" + quoteId, PostMintQuoteResponse.class);
    }

}
