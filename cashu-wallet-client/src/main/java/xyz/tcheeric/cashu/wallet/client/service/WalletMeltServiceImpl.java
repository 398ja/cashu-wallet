package xyz.tcheeric.cashu.wallet.client.service;

import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.crypto.DeterministicKey;
import xyz.tcheeric.cashu.common.BlindedMessage;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.common.nut18.PaymentMethod;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltQuoteResponse;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltRequest;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltResponse;
import xyz.tcheeric.cashu.wallet.client.impl.RequestMeltToken;
import xyz.tcheeric.cashu.wallet.proto.builders.BlankOutputBuilder;
import xyz.tcheeric.cashu.wallet.proto.service.MeltChangeService;
import xyz.tcheeric.cashu.wallet.proto.service.impl.DefaultMeltChangeService;
import xyz.tcheeric.cashu.wallet.proto.tasks.DeriveSecretsTask;

import java.util.List;
import java.util.Objects;

/**
 * Default {@link WalletMeltService}, wiring NUT-08 blank outputs into the NUT-05 melt.
 *
 * <p>Blank output secrets are derived deterministically (NUT-13) from the wallet master
 * key, so the recovered change is also recoverable from the mnemonic alone.
 */
@Nut(8)
@Slf4j
@ToString(exclude = {"mintUrl", "meltClientFactory"})
public class WalletMeltServiceImpl implements WalletMeltService {

    private final String mintUrl;
    private final BlankOutputBuilder blankOutputBuilder;
    private final MeltChangeService meltChangeService;
    private final MeltClientFactory meltClientFactory;

    /**
     * Creates a melt service talking to the given mint over the real REST client.
     *
     * @param mintUrl Mint base URL
     */
    public WalletMeltServiceImpl(@NonNull String mintUrl) {
        this(mintUrl, new BlankOutputBuilder(), new DefaultMeltChangeService(), new DefaultMeltClientFactory());
    }

    public WalletMeltServiceImpl(
            @NonNull String mintUrl,
            @NonNull BlankOutputBuilder blankOutputBuilder,
            @NonNull MeltChangeService meltChangeService,
            @NonNull MeltClientFactory meltClientFactory) {
        this.mintUrl = mintUrl;
        this.blankOutputBuilder = blankOutputBuilder;
        this.meltChangeService = meltChangeService;
        this.meltClientFactory = meltClientFactory;
    }

    @Override
    public <T extends Secret> MeltResult melt(
            @NonNull PostMeltQuoteResponse quote,
            @NonNull List<Proof<T>> inputs,
            @NonNull DeterministicKey masterKey,
            @NonNull KeySet keySet,
            int startCounter,
            @NonNull PaymentMethod paymentMethod) {

        requireInputs(inputs);

        int blankOutputCount = blankOutputBuilder.calculateBlankOutputCount(quote.getFeeReserve());
        log.info("wallet_melt started quote={} fee_reserve={} blank_outputs={}",
            quote.getQuoteId(), quote.getFeeReserve(), blankOutputCount);

        if (blankOutputCount == 0) {
            return meltWithoutChange(quote, inputs, paymentMethod, startCounter);
        }

        DeriveSecretsTask.DeriveSecretsResult derived = deriveBlankOutputSecrets(
            masterKey, KeysetId.fromString(keySet.getId()), startCounter, blankOutputCount);

        try {
            List<BlindedMessage> blankOutputs = blankOutputBuilder.createBlankOutputs(
                derived.getSecrets(), derived.getBlindingFactors());

            PostMeltResponse response = submitMelt(
                new PostMeltRequest<>(quote.getQuoteId(), inputs, blankOutputs), paymentMethod);

            List<Proof<DeterministicSecret>> changeProofs = meltChangeService.recoverChange(
                response, derived.getSecrets(), derived.getBlindingFactors(), keySet);

            MeltResult result = MeltResult.builder()
                .paid(response.isPaid())
                .paymentPreimage(response.getPaymentPreimage())
                .changeProofs(changeProofs)
                .nextCounter(Math.addExact(startCounter, blankOutputCount))
                .build();

            log.info("wallet_melt completed quote={} paid={} change_proofs={} change_amount={}",
                quote.getQuoteId(), result.isPaid(), changeProofs.size(), result.getChangeAmount());

            return result;

        } finally {
            derived.clearSensitiveData();
        }
    }

    private <T extends Secret> MeltResult meltWithoutChange(
            PostMeltQuoteResponse quote,
            List<Proof<T>> inputs,
            PaymentMethod paymentMethod,
            int startCounter) {

        PostMeltResponse response = submitMelt(
            new PostMeltRequest<>(quote.getQuoteId(), inputs), paymentMethod);

        log.info("wallet_melt completed quote={} paid={} change_proofs=0 reason=zero_fee_reserve",
            quote.getQuoteId(), response.isPaid());

        return MeltResult.builder()
            .paid(response.isPaid())
            .paymentPreimage(response.getPaymentPreimage())
            .changeProofs(List.of())
            .nextCounter(startCounter)
            .build();
    }

    private <T extends Secret> PostMeltResponse submitMelt(
            PostMeltRequest<T> request, PaymentMethod paymentMethod) {

        RequestMeltToken<T> client = meltClientFactory.create(mintUrl, paymentMethod, request);
        PostMeltResponse response = client.execute();

        if (response == null) {
            throw new IllegalStateException(
                "Failed to melt against mint " + mintUrl + ". The mint returned an empty body. " +
                    "Suggestion: check the melt quote state before retrying, the payment may have started.");
        }
        return response;
    }

    private DeriveSecretsTask.DeriveSecretsResult deriveBlankOutputSecrets(
            DeterministicKey masterKey, KeysetId keysetId, int startCounter, int count) {

        DeriveSecretsTask.DeriveSecretsResult derived =
            new DeriveSecretsTask(masterKey, keysetId, startCounter, count).execute();
        derived.validate();
        return derived;
    }

    private <T extends Secret> void requireInputs(List<Proof<T>> inputs) {
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("Melt inputs cannot be empty");
        }
    }

    private static final class DefaultMeltClientFactory implements MeltClientFactory {

        @Override
        public <T extends Secret> RequestMeltToken<T> create(
                String mintUrl, PaymentMethod paymentMethod, PostMeltRequest<T> request) {
            Objects.requireNonNull(request, "Melt request cannot be null");
            return new RequestMeltToken<>(mintUrl, paymentMethod, request);
        }
    }
}
