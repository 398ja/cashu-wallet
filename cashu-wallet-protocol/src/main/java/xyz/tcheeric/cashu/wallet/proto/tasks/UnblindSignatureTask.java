package xyz.tcheeric.cashu.wallet.proto.tasks;

import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.BlindSignature;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.common.Signature;
import xyz.tcheeric.cashu.common.util.Task;
import xyz.tcheeric.cashu.crypto.util.Utils;
import xyz.tcheeric.cashu.wallet.proto.service.BDHKEUtilsService;
import xyz.tcheeric.cashu.wallet.proto.service.impl.BDHKEUtilsServiceImpl;
import xyz.tcheeric.cashu.entities.annotation.Nut;

import java.math.BigInteger;

@Nut(4)
@Slf4j
@AllArgsConstructor
@ToString
public class UnblindSignatureTask<T extends Secret> implements Task<Proof<T>> {

    private final BlindSignature blindSignature;
    private final BigInteger r;
    private final PublicKey K;
    private final T secret;
    private final BDHKEUtilsService utilsService;

    public UnblindSignatureTask(BlindSignature blindSignature, BigInteger r, PublicKey K, T secret) {
        this(blindSignature, r, K, secret, new BDHKEUtilsServiceImpl());
    }

    @Override
    public Proof<T> execute() {
        log.debug("unblind_signature task_started keyset={} amount={} secret_type={}",
            blindSignature.getKeySetId(), blindSignature.getAmount(), secret.getClass().getSimpleName());
        Signature C_ = blindSignature.getBlindedSignature();
        byte[] C = utilsService.unblindSignature(C_.getBytes(), Utils.bytesFromBigInteger(r), K.getBytes());
        Proof<T> proof = Proof.<T>builder()
            .secret(secret)
            .amount(blindSignature.getAmount())
            .keySetId(blindSignature.getKeySetId().toString())
            .unblindedSignature(Signature.fromBytes(C))
            .build();
        log.debug("unblind_signature task_completed keyset={} amount={} secret_type={}",
            blindSignature.getKeySetId(), blindSignature.getAmount(), secret.getClass().getSimpleName());
        return proof;
    }
}
