package xyz.tcheeric.cashu.wallet.client.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.nut18.PaymentMethod;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.nut04.PostMintQuoteRequest;
import xyz.tcheeric.cashu.entities.rest.nut04.PostMintQuoteResponse;
import xyz.tcheeric.cashu.wallet.client.AbstractRequestBase;

@Nut(4)
public class RequestMintQuote extends AbstractRequestBase<PostMintQuoteResponse, PostMintQuoteRequest> {

    public RequestMintQuote(@NonNull String baseUrl, @NonNull PaymentMethod paymentMethod, @NonNull PostMintQuoteRequest postMintQuoteRequest) {
        super(baseUrl, "/mint/quote/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMintQuoteRequest,PostMintQuoteResponse.class);
    }
}
