package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import org.springframework.stereotype.Service;
import xyz.tcheeric.cashu.common.annotation.Nut;
import xyz.tcheeric.cashu.common.model.PaymentMethod;
import xyz.tcheeric.cashu.common.model.rest.PostMintQuoteRequest;
import xyz.tcheeric.cashu.common.model.rest.PostMintQuoteResponse;

@Nut(4)
@Service
public class RequestMintQuote extends AbstractRequestBase<PostMintQuoteResponse, PostMintQuoteRequest> {

    public RequestMintQuote(@NonNull String baseUrl, @NonNull PaymentMethod paymentMethod, @NonNull PostMintQuoteRequest postMintQuoteRequest) {
        super(baseUrl, "/mint/quote/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMintQuoteRequest,PostMintQuoteResponse.class);
    }
}
