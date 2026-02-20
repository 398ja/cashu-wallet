package xyz.tcheeric.cashu.wallet.client.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.nut04.PostMintQuoteResponse;
import xyz.tcheeric.cashu.wallet.client.AbstractRequestBase;

@Nut(4)
public class RequestCheckMintQuoteState extends AbstractRequestBase<PostMintQuoteResponse, Void> {

    public RequestCheckMintQuoteState(@NonNull String baseUrl, @NonNull String quoteId, @NonNull String paymentMethod) {
        super(baseUrl, "/mint/quote/" + paymentMethod + "/" + quoteId, PostMintQuoteResponse.class);
    }

}
