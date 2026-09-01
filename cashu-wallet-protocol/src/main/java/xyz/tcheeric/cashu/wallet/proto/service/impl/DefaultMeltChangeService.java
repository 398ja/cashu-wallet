package xyz.tcheeric.cashu.wallet.proto.service.impl;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.BlindSignature;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.crypto.BDHKEUtils;
import xyz.tcheeric.cashu.crypto.util.Utils;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltResponse;
import xyz.tcheeric.cashu.wallet.proto.service.BDHKEUtilsService;
import xyz.tcheeric.cashu.wallet.proto.service.DLEQVerificationService;
import xyz.tcheeric.cashu.wallet.proto.service.MeltChangeService;
import xyz.tcheeric.cashu.wallet.proto.tasks.UnblindSignatureTask;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default {@link MeltChangeService}, unblinding NUT-08 melt change against the wallet's
 * own secrets.
 *
 * <p>The mint omits zero-value signatures, so the change list is usually shorter than the
 * blank output list. Because the surviving signatures keep their original relative order,
 * matching them positionally to the leading secrets is correct.
 */
@Nut(8)
@Slf4j
public final class DefaultMeltChangeService implements MeltChangeService {

    private final BDHKEUtilsService bdhkeUtilsService;
    private final DLEQVerificationService dleqVerificationService;

    /** Creates a service with the default BDHKE and DLEQ collaborators. */
    public DefaultMeltChangeService() {
        this(new BDHKEUtilsServiceImpl(), new DefaultDLEQVerificationService());
    }

    public DefaultMeltChangeService(
            BDHKEUtilsService bdhkeUtilsService,
            DLEQVerificationService dleqVerificationService) {
        this.bdhkeUtilsService = Objects.requireNonNull(bdhkeUtilsService, "BDHKE utils service cannot be null");
        this.dleqVerificationService =
            Objects.requireNonNull(dleqVerificationService, "DLEQ verification service cannot be null");
    }

    @Override
    public List<Proof<DeterministicSecret>> recoverChange(
            @NonNull PostMeltResponse response,
            @NonNull List<DeterministicSecret> secrets,
            @NonNull List<byte[]> blindingFactors,
            @NonNull KeySet keySet) {

        requireMatchingSizes(secrets, blindingFactors);

        List<BlindSignature> change = response.getChange();
        if (change == null || change.isEmpty()) {
            log.info("melt_change no_change_returned keyset={}", keySet.getId());
            return List.of();
        }

        requireChangeFitsBlankOutputs(change, secrets, keySet);

        List<Proof<DeterministicSecret>> changeProofs = new ArrayList<>(change.size());
        for (int index = 0; index < change.size(); index++) {
            changeProofs.add(unblindChange(change.get(index), secrets.get(index), blindingFactors.get(index), keySet));
        }

        log.info("melt_change recovered keyset={} proofs={} amount={}",
            keySet.getId(), changeProofs.size(), totalAmount(changeProofs));

        return changeProofs;
    }

    private Proof<DeterministicSecret> unblindChange(
            BlindSignature blindSignature,
            DeterministicSecret secret,
            byte[] blindingFactor,
            KeySet keySet) {

        PublicKey mintPublicKey = requireMintPublicKey(keySet, blindSignature.getAmount());

        byte[] blindedBytes = BDHKEUtils.blindMessage(secret.getData(), blindingFactor);
        dleqVerificationService.verifyBlindSignature(
            blindSignature, PublicKey.fromBytes(blindedBytes, false), mintPublicKey);

        BigInteger r = Utils.bigIntFromBytes(blindingFactor);
        Proof<DeterministicSecret> proof = new UnblindSignatureTask<>(
            blindSignature, r, mintPublicKey, secret, bdhkeUtilsService).execute();

        return dleqVerificationService.addDLEQToProof(proof, blindingFactor, blindSignature.getDleq());
    }

    private PublicKey requireMintPublicKey(KeySet keySet, int amount) {
        PublicKey mintPublicKey = keySet.getKeys().get(amount);
        if (mintPublicKey == null) {
            throw new IllegalStateException(
                "Failed to unblind melt change for amount " + amount +
                    ". The keyset " + keySet.getId() + " has no public key for that amount. " +
                    "Suggestion: refresh the mint keyset and retry recovering the change.");
        }
        return mintPublicKey;
    }

    private void requireMatchingSizes(List<DeterministicSecret> secrets, List<byte[]> blindingFactors) {
        if (secrets.size() != blindingFactors.size()) {
            throw new IllegalArgumentException(String.format(
                "Secrets and blinding factors count mismatch: secrets=%d, blindingFactors=%d",
                secrets.size(), blindingFactors.size()
            ));
        }
    }

    private void requireChangeFitsBlankOutputs(
            List<BlindSignature> change,
            List<DeterministicSecret> secrets,
            KeySet keySet) {

        if (change.size() > secrets.size()) {
            throw new IllegalArgumentException(String.format(
                "Mint returned %d change signatures for %d blank outputs on keyset %s. " +
                    "The response cannot be matched to wallet secrets. " +
                    "Suggestion: discard the change and reconcile the melt with the mint.",
                change.size(), secrets.size(), keySet.getId()
            ));
        }
    }

    private int totalAmount(List<Proof<DeterministicSecret>> proofs) {
        return proofs.stream().mapToInt(Proof::getAmount).sum();
    }
}
