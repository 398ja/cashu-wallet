package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.PostMintQuoteResponse;

@Nut(5)
public class RequestCheckMeltQuoteState extends AbstractRequestBase<PostMintQuoteResponse, Void> {

    public RequestCheckMeltQuoteState(@NonNull String baseUrl, @NonNull String quoteId, @NonNull String paymentMethod) {
        super(baseUrl, "/melt/quote/" + paymentMethod + "/" + quoteId, PostMintQuoteResponse.class);
    }
}
