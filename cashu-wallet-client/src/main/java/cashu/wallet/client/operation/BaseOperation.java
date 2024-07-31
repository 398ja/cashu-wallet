package cashu.wallet.client.operation;

import cashu.common.model.PaymentMethod;
import cashu.common.model.rest.PostMeltQuoteMockRequest;
import cashu.common.model.rest.PostMeltQuoteRequest;
import cashu.gateway.Gateway;
import cashu.gateway.mock.MockMeltGateway;
import cashu.gateway.mock.MockMintGateway;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

@AllArgsConstructor
public abstract class BaseOperation {

    @Getter
    private final PaymentMethod paymentMethod;

    protected Gateway createGateway(@NonNull String operation) {
        switch (paymentMethod) {
            case PaymentMethod.MOCK -> {
                if (operation.equals("mint"))
                    return new MockMintGateway("mock");
                else if (operation.equals("melt"))
                    return new MockMeltGateway("mock");
                else throw new IllegalArgumentException("Invalid operation");
            }
            default -> throw new IllegalArgumentException("Invalid payment method");
        }
    }

    protected PostMeltQuoteRequest createPostMeltQuoteRequest() {
        switch (paymentMethod) {
            case PaymentMethod.MOCK:
                return new PostMeltQuoteMockRequest();
            default:
                throw new IllegalArgumentException("Invalid payment method: " + paymentMethod);
        }
    }

}
