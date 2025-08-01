package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.PaymentMethod;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.PostMintQuoteRequest;
import xyz.tcheeric.cashu.entities.rest.PostMintQuoteResponse;

@Nut(4)
public class RequestMintQuote extends AbstractRequestBase<PostMintQuoteResponse, PostMintQuoteRequest> {

    public RequestMintQuote(@NonNull String baseUrl, @NonNull PaymentMethod paymentMethod, @NonNull PostMintQuoteRequest postMintQuoteRequest) {
        super(baseUrl, "/mint/quote/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMintQuoteRequest,PostMintQuoteResponse.class);
    }
}
