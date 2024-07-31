package cashu.wallet.demo;

import cashu.common.model.PrivateKey;
import cashu.common.model.PublicKey;
import cashu.crypto.BDHKEUtils;
import cashu.util.Configuration;
import cashu.util.Utils;

import java.util.Objects;

public class CryptoChecks {

    private final Configuration configuration;

    public CryptoChecks() {
        configuration = Configuration.load(Objects.requireNonNull(CryptoChecks.class.getResourceAsStream("/crypto.properties")));
    }

    private boolean checkKeypair() {
        String publicKey = configuration.getValue("publicKey");
        String privateKey = configuration.getValue("privateKey");
        String derivedPublicKey = PrivateKey.derivePublicKey(PrivateKey.fromString(privateKey)).toString();
        if (!derivedPublicKey.equals(publicKey)) {
            System.out.println("Check failed:");
            System.out.println("Expected public key: " + derivedPublicKey);
            System.out.println("Provided public key: " + publicKey);
            return false;
        }
        return true;
    }

    private boolean checkSecret() {
        String secret = configuration.getValue("secret");
        String Y = configuration.getValue("Y");
        String Y_ = Utils.bytesToHexString(BDHKEUtils.hashToCurve(secret));
        if(!Y.equals(Y_)) {
            System.out.println("Check failed:");
            System.out.println("Expected Y: " + Y_);
            System.out.println("Provided Y: " + Y);
            return false;
        }
        return true;
    }

    private boolean signatureCheck() {
        boolean result = true;

        String x = configuration.getValue("secret");
        String B_= configuration.getValue("B_");
        String r = configuration.getValue("r");
        String C_ = configuration.getValue("C_");
        String C = configuration.getValue("C");
        String K = configuration.getValue("publicKey");
        String k = configuration.getValue("privateKey");

        var b_ = BDHKEUtils.blindMessage(Utils.hexStringToBytes(x), Utils.hexStringToBytes(r));
        var c_ = BDHKEUtils.signBlindedMessage(Utils.hexStringToBytes(B_), Utils.hexStringToBytes(k));
        var c = BDHKEUtils.unblindSignature(c_, Utils.hexStringToBytes(r), PublicKey.fromString(K).toBytes());

        if(!B_.equals(Utils.bytesToHexString(b_))) {
            System.out.println("Blinded Message Check failed:");
            System.out.println("Expected B_: " + Utils.bytesToHexString(b_));
            System.out.println("Provided B_: " + B_);
            result = false;
        } else {
            System.out.println("Blinded Message Check passed.");
        }

        if (!C_.equals(Utils.bytesToHexString(c_))) {
            System.out.println("Blinded Signature Check failed:");
            System.out.println("Expected C_: " + Utils.bytesToHexString(c_));
            System.out.println("Provided C_: " + C_);
            result = false;
        } else {
            System.out.println("Blinded Signature Check passed.");
        }

        if (!C.equals(Utils.bytesToHexString(c))) {
            System.out.println("Unblinded Signature Check failed:");
            System.out.println("Expected C: " + Utils.bytesToHexString(c));
            System.out.println("Provided C: " + C);
            result = false;
        } else {
            System.out.println("Unblinded Signature Check passed.");
        }

        return result;
    }

    private boolean proofVerify() {
        String x = configuration.getValue("secret");
        String C = configuration.getValue("C");
        String k = configuration.getValue("privateKey");

        return BDHKEUtils.verify(x, Utils.hexStringToBytes(k), Utils.hexStringToBytes(C));
    }

    private boolean checkR() {
        String x = configuration.getValue("secret");
        var bm = BDHKEUtils.blindMessage(Utils.hexStringToBytes(x));
        var b_ = bm[0];
        var r = bm[1];

        var b__ = BDHKEUtils.blindMessage(Utils.hexStringToBytes(x), r);
        if(!Utils.bytesToHexString(b__).equals(Utils.bytesToHexString(b_))) {
            System.out.println("Check failed:");
            System.out.println("Expected b_: " + Utils.bytesToHexString(b__));
            System.out.println("Provided b_: " + Utils.bytesToHexString(b_));
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        CryptoChecks cryptoChecks = new CryptoChecks();

        System.out.println("Public key matches the derived public key from the private key: " + cryptoChecks.checkKeypair());
        //System.out.println("Y matches the hash to curve of the secret: " + cryptoChecks.checkSecret());
        //System.out.println("Signature checks: " + cryptoChecks.signatureCheck());
        //System.out.println("Proof verification: " + cryptoChecks.proofVerify());
        //System.out.println("r check: " + cryptoChecks.checkR());
    }
}