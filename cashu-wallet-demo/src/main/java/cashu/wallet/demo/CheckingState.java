package cashu.wallet.demo;

import cashu.common.model.PaymentMethod;
import cashu.common.model.Secret;
import cashu.wallet.client.service.Wallet;
import lombok.AllArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
public class CheckingState {

    private final Wallet wallet;

    public CheckingState() {
        this.wallet = new Wallet(PaymentMethod.MOCK);
    }

    private void checkState(List<Secret> secrets) {
        wallet.checkState(secrets);
    }

    public static void main(String[] args) {
        CheckingState checkingState = new CheckingState();
        List<Secret> secrets = Arrays.stream(args)
                .map(s -> Secret.fromString(s))
                .collect(Collectors.toList());
        checkingState.checkState(secrets);
    }
}