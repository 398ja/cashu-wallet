package cashu.wallet.proto.nut;

import cashu.common.model.BlindSignature;
import cashu.common.model.Proof;
import cashu.common.model.PublicKey;
import cashu.common.model.Secret;
import cashu.common.model.Signature;
import cashu.crypto.BDHKEUtils;
import cashu.util.Utils;
import lombok.NonNull;
import lombok.extern.java.Log;

import java.math.BigInteger;

@Log
public class NUT04 {

    public static Proof unblindingSignature(@NonNull BlindSignature blindSignature, @NonNull BigInteger r, @NonNull PublicKey K, @NonNull Secret secret) {
        Signature C_ = blindSignature.getBlindedSignature();
        //log.log(Level.INFO, "Unblinding signature: {0}", C_.toString());
        byte[] C = BDHKEUtils.unblindSignature(C_.getBytes(), Utils.bytesFromBigInteger(r), K.getBytes());
        return Proof.builder().secret(secret).amount(blindSignature.getAmount()).keySetId(blindSignature.getKeySetId()).unblindedSignature(Signature.fromBytes(C)).build();
    }
}
