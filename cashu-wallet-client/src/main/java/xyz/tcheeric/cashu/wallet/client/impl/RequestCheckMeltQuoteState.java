package xyz.tcheeric.cashu.wallet.client.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltQuoteResponse;
import xyz.tcheeric.cashu.wallet.client.AbstractRequestBase;

@Nut(5)
public class RequestCheckMeltQuoteState extends AbstractRequestBase<PostMeltQuoteResponse, Void> {

    public RequestCheckMeltQuoteState(@NonNull String baseUrl, @NonNull String quoteId, @NonNull String paymentMethod) {
        super(baseUrl, "/melt/quote/" + paymentMethod + "/" + quoteId, PostMeltQuoteResponse.class);
    }
}
