package cashu.wallet.client.service;

import cashu.common.model.BlindedMessage;
import cashu.common.model.PaymentMethod;
import cashu.common.model.PrivateKey;
import cashu.common.model.Proof;
import cashu.common.model.PublicKey;
import cashu.common.model.Secret;
import cashu.common.model.rest.PostMeltRequest;
import cashu.common.model.rest.PostMintRequest;
import cashu.common.model.rest.PostSwapRequest;
import cashu.wallet.client.operation.MeltTokens;
import cashu.wallet.client.operation.MintTokens;
import cashu.wallet.client.operation.SwapTokens;
import cashu.wallet.client.util.ChangeCalculator;
import cashu.wallet.client.util.WalletUtil;
import cashu.wallet.db.client.MeltQuoteRequestClient;
import cashu.wallet.db.client.MeltQuoteResponseClient;
import cashu.wallet.db.client.MintQuoteRequestClient;
import cashu.wallet.db.client.ProofClient;
import cashu.wallet.db.model.MeltQuoteRequestEntity;
import cashu.wallet.db.model.MeltQuoteResponseEntity;
import cashu.wallet.db.model.MintQuoteRequestEntity;
import cashu.wallet.db.model.MintQuoteResponseEntity;
import cashu.wallet.db.model.ProofEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import static cashu.wallet.client.util.WalletUtil.createPostMintRequest;

@Log
@RequiredArgsConstructor
@Service
public class Wallet {

    private final PaymentMethod paymentMethod;
    private final MintQuoteRequestClient mintQuoteRequestClient;
    private final MeltQuoteRequestClient meltQuoteRequestClient;
    private final MintTokens mintTokens;
    private final MeltTokens meltTokens;

    public Wallet(@NonNull PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
        this.mintQuoteRequestClient = new MintQuoteRequestClient();
        this.meltQuoteRequestClient = new MeltQuoteRequestClient();
        this.mintTokens = new MintTokens(paymentMethod);
        this.meltTokens = new MeltTokens(paymentMethod);

    }

    public MintQuoteResponseEntity quoteMint(int amount, @NonNull String unit) {

        MintQuoteRequestEntity mintQuoteRequestEntity = new MintQuoteRequestEntity();
        mintQuoteRequestEntity.setAmount(amount);
        mintQuoteRequestEntity.setUnit(unit);
        mintQuoteRequestEntity.setCorrelationId(UUID.randomUUID());
        mintQuoteRequestEntity.setPaymentMethod(paymentMethod.name().toLowerCase());

        // Persists the mintQuoteRequestEntity
        mintQuoteRequestClient.createEntity(mintQuoteRequestEntity);

        return mintTokens.quote(mintQuoteRequestEntity);
    }

    public MeltQuoteResponseEntity quoteMelt(@NonNull String request, @NonNull String unit) {

        MeltQuoteRequestEntity meltQuoteRequestEntity = new MeltQuoteRequestEntity();
        meltQuoteRequestEntity.setUnit(unit);
        meltQuoteRequestEntity.setRequest(request);
        meltQuoteRequestEntity.setPaymentMethod(paymentMethod.name().toLowerCase());
        meltQuoteRequestEntity.setCorrelationId(UUID.randomUUID());

        // Persists the meltQuoteRequestEntity
        meltQuoteRequestClient.createEntity(meltQuoteRequestEntity);

        return meltTokens.quote(meltQuoteRequestEntity);
    }

    public void mintTokens(@NonNull MintQuoteResponseEntity mintQuoteResponseEntity) {

        MintQuoteRequestClient client = new MintQuoteRequestClient();
        MintQuoteRequestEntity mintQuoteRequestEntity = client.getByCorrelationId(mintQuoteResponseEntity.getCorrelationId().toString()); // Retrieve the mintQuoteRequestEntity

        PostMintRequest postMintRequest = createPostMintRequest(mintQuoteResponseEntity.getQuote(), mintQuoteRequestEntity);

        MintTokens mintTokens = new MintTokens(paymentMethod);
        mintTokens.mint(postMintRequest);
    }

    public void meltTokens(@NonNull MeltQuoteResponseEntity meltQuoteResponseEntity) {

        MeltQuoteRequestClient client = new MeltQuoteRequestClient();
        MeltQuoteRequestEntity meltQuoteRequestEntity = client.getByCorrelationId(meltQuoteResponseEntity.getCorrelationId().toString()); // Retrieve the meltQuoteRequestEntity

        PostMeltRequest postMeltRequest = createPostMeltRequest(meltQuoteResponseEntity.getQuote(), meltQuoteRequestEntity);

        MeltTokens meltTokens = new MeltTokens(paymentMethod);
        meltTokens.melt(postMeltRequest);
    }

    public void swapTokens(@NonNull List<ProofEntity> proofEntities, @NonNull Map<Integer, Integer> change, @NonNull String unit) {

        // Ensure the proof amount and the change match
        Integer totalChange = change.entrySet().stream().map(e -> e.getKey() * e.getValue()).reduce(0, Integer::sum);
        Integer totalProofs = proofEntities.stream().map(ProofEntity::getAmount).reduce(0, Integer::sum);
        if (!totalProofs.equals(totalChange)) {
            throw new IllegalArgumentException("The proof amount and the change do not match");
        }

        // Ensure all proofEntities have the same keySetId
        String keysetId = proofEntities.get(0).getKeysetId();
        if (!proofEntities.stream().allMatch(p -> p.getKeysetId().equals(keysetId))) {
            throw new IllegalArgumentException("All proofEntities must have the same keySetId");
        }

        // Create the blind messages for the new proofs
        List<BlindedMessage> blindedMessages = new ArrayList<>();
        ProofSecretRecordList proofSecretRecordList = new ProofSecretRecordList();

        for (Map.Entry<Integer, Integer> entry : change.entrySet()) {
            int amount = entry.getKey();
            int count = entry.getValue();
            for (int i = 0; i < count; i++) {

                Secret secret = Secret.create();
                byte[] r = PrivateKey.generateRandom().toBytes();
                PublicKey bMsg = WalletUtil.createBlindMessage(secret.toBytes(), r);

                proofSecretRecordList.addRecord(secret, r);

                BlindedMessage blindedMessage = new BlindedMessage();
                blindedMessage.setAmount(amount);
                blindedMessage.setKeySetId(keysetId);
                blindedMessage.setBlindedMessage(bMsg);

                blindedMessages.add(blindedMessage);
            }
        }

        // Create the post swap request
        PostSwapRequest postSwapRequest = new PostSwapRequest();
        postSwapRequest.setProofs(proofEntities.stream().map(ProofEntity::toProof).toList());
        postSwapRequest.setBlindedMessages(blindedMessages);

        // Swap the tokens
        SwapTokens swapTokens = new SwapTokens(paymentMethod, unit);
        swapTokens.swap(postSwapRequest, proofSecretRecordList);
    }

    public void swapTokens(@NonNull ProofEntity proofEntity, @NonNull Map<Integer, Integer> change, @NonNull String unit) {
        swapTokens(List.of(proofEntity), change, unit);
    }

    public void checkState(List<Secret> secrets) {
        // TODO - Implement this method
    }

    private PostMeltRequest createPostMeltRequest(@NonNull String quote, @NonNull MeltQuoteRequestEntity meltQuoteRequestEntity) {

        PostMeltRequest postMeltRequest = new PostMeltRequest();
        postMeltRequest.setQuoteId(quote);
        List<Proof> proofs = getProofsToMelt(meltQuoteRequestEntity);
        postMeltRequest.setProofs(proofs);

        return postMeltRequest;
    }

    private List<Proof> getProofsToMelt(@NonNull MeltQuoteRequestEntity meltQuoteRequestEntity) {

        MeltQuoteResponseEntity meltQuoteResponseEntity = new MeltQuoteResponseClient().getByCorrelationId(meltQuoteRequestEntity.getCorrelationId().toString());

        if (meltQuoteResponseEntity != null) {
            Integer amount = meltQuoteResponseEntity.getAmount();
            String unit = meltQuoteRequestEntity.getUnit();

            // Swap the tokens for the required denomination
            ProofEntity proofToSwap = findProofToSwap(amount, unit);
            if (proofToSwap != null) {
                log.log(Level.FINE, "Proof to swap: {0}", proofToSwap);
                Map<Integer, Integer> change = ChangeCalculator.withSmallestDenomination(proofToSwap.getAmount(), unit);
                swapTokens(proofToSwap, change, unit);
            }

            // Get the proofs to melt
            String keysetId = WalletUtil.getKeysetId(unit);
            var feeReserve = meltQuoteResponseEntity.getFeeReserve();
            List<ProofEntity> allProofs = new ProofClient().getByKeySetId(keysetId);

            return Objects.requireNonNull(findProofsSubsetForTotalAmount(allProofs, amount + feeReserve)).stream().map(ProofEntity::toProof).toList();
        }

        throw new IllegalStateException("MeltQuoteResponseEntity not found");
    }

    /**
     * Finds a subset of proofs that sum up to a given total amount.
     *
     * @param allProofs The list of all available proofs.
     * @param totalAmount The total amount to match.
     * @return A list of proofs that sum up to the given total amount, or an empty list if no such subset exists.
     */
    private List<ProofEntity> findProofsSubsetForTotalAmount(List<ProofEntity> allProofs, int totalAmount) {
        List<ProofEntity> subset = new ArrayList<>();
        int currentSum = 0;

        // Sort proofs in descending order of amount
        allProofs.sort(Comparator.comparingInt(ProofEntity::getAmount).reversed());

        for (ProofEntity proof : allProofs) {
            if (currentSum + proof.getAmount() <= totalAmount) {
                subset.add(proof);
                currentSum += proof.getAmount();
                if (currentSum == totalAmount) {
                    return subset; // Found a matching subset
                }
            }
        }

        return subset.isEmpty() ? null : subset; // Return null or subset if no exact match is found
    }


    /**
     * The `findProofToSwap` method is designed to identify a specific `ProofEntity` that can be swapped
     * based on a given amount and unit of currency.
     *
     * @param amount The total amount to melt
     * @param unit   The unit of currency
     */
    private static ProofEntity findProofToSwap(@NonNull Integer amount, @NonNull String unit) {

        ProofClient proofClient = new ProofClient();
        String keySetId = WalletUtil.getKeysetId(unit);

        List<ProofEntity> allProofs = proofClient.getByKeySetId(keySetId);
        List<ProofEntity> tmpAllProofs = new ArrayList<>(allProofs);
        tmpAllProofs.sort((proof1, proof2) -> proof2.getAmount().compareTo(proof1.getAmount()));

        Map<Integer, Integer> change = ChangeCalculator.withSmallestDenomination(amount, unit);

        Integer totalChange = allProofs.stream().map(p -> p.getAmount()).reduce(0, Integer::sum);
        AtomicBoolean missingProofFlag = new AtomicBoolean(false);
        if (totalChange >= amount) {
            change.keySet().stream().sorted().forEach(denominationAmount -> {
                ProofEntity proofEntity = allProofs.stream().filter(p -> p.getAmount().equals(denominationAmount) && p.getKeysetId().equals(keySetId)).findFirst().orElse(null);
                if (proofEntity != null) {
                    tmpAllProofs.remove(proofEntity);
                } else {
                    missingProofFlag.set(true);
                }
            });

            return missingProofFlag.get() ? findNearestProof(amount, unit) : null;

        }
        throw new IllegalStateException("Denomination not found");
    }

    private static ProofEntity findNearestProof(@NonNull Integer targetAmount, @NonNull String unit) {

        String keySetId = WalletUtil.getKeysetId(unit);
        ProofClient proofClient = new ProofClient();
        List<ProofEntity> allProofs = proofClient.getByKeySetId(keySetId);

        return allProofs.stream()
                .filter(proof -> proof.getAmount() > targetAmount)
                .min(Comparator.comparingInt(proof -> proof.getAmount() - targetAmount))
                .orElse(null);
    }

    public static class ProofSecretRecordList {

        @Getter
        private final List<ProofSecretRecord> proofSecretRecordList;

        public ProofSecretRecordList() {
            this.proofSecretRecordList = new ArrayList<>();
        }

        public void addRecord(@NonNull Secret secret, byte[] r) {
            this.proofSecretRecordList.add(new ProofSecretRecord(secret, r));
        }

        public Secret getSecret(int index) {
            return this.getProofSecretRecord(index).getSecret();
        }

        public byte[] getR(int index) {
            return this.getProofSecretRecord(index).getR();
        }

        public int size() {
            return this.proofSecretRecordList.size();
        }

        private ProofSecretRecord getProofSecretRecord(int counter) {
            if (counter < 0 || counter >= this.proofSecretRecordList.size()) {
                throw new IllegalArgumentException("Invalid counter");
            }
            return this.proofSecretRecordList.get(counter);
        }

        @Data
        @AllArgsConstructor
        private static class ProofSecretRecord {
            private final Secret secret;
            private final byte[] r;
        }
    }
}
