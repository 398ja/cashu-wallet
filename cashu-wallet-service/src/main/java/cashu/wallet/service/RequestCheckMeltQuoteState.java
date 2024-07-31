package cashu.wallet.service;

import cashu.common.annotation.Nut;
import cashu.common.model.rest.PostMintQuoteResponse;
import lombok.NonNull;
import org.springframework.stereotype.Service;

@Nut(5)
@Service
public class RequestCheckMeltQuoteState extends AbstractRequestBase<PostMintQuoteResponse, Void> {

    public RequestCheckMeltQuoteState(@NonNull String quoteId, @NonNull String paymentMethod) {
        super("/melt/quote/" + paymentMethod + "/" + quoteId, PostMintQuoteResponse.class);
    }
}
