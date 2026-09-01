#!/usr/bin/env bash
# Tracks the cashu-lib secret encoding story documented in
# docs/explanation/cashu-lib-0.22.0-secret-encoding-audit.md (issues #39 and #40).
#
# The wallet blinds a proof with DeterministicSecret.getData() while the mint verifies against the
# wire string DeterministicSecret.toString(). Two independent properties are probed:
#
#   checkstate MATCH?       the Y the proof commits to equals the Y the wallet reports to the mint
#   spec-only mint accepts? a SPEC-only (UTF-8 hashing) mint such as Nutshell accepts the proof
#
# Run with no arguments to compare all three library versions:
#     ./scripts/probe-secret-encoding.sh
#
# Expected results:
#   0.21.0  MATCH true,  spec-only false  (self-consistent, but never spec-conforming)
#   0.22.0  MATCH false, spec-only true   (the fund-loss regression of #40)
#   0.23.0  MATCH true,  spec-only true   (getData() returns the UTF-8 wire bytes; both hold)
#
# Note: a locally installed 0.22.0 artifact may have been overwritten by a later build carrying
# the fix, in which case the 0.22.0 row reports the fixed behaviour rather than the released one.
# Check for DeterministicSecret.getDerivedBytes in the jar before trusting that row.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

cat > "${WORK_DIR}/SecretEncodingProbe.java" <<'JAVA'
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;
import org.bouncycastle.util.encoders.Hex;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.common.util.SecretUtil;
import xyz.tcheeric.cashu.crypto.BDHKEUtils;

import java.math.BigInteger;

/** Compares the Y a wallet-issued proof commits to against the Y the wallet reports to the mint. */
public final class SecretEncodingProbe {

    private static final String DERIVED_SECRET_HEX =
            "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";
    private static final String BLINDING_FACTOR_HEX =
            "1122334455667788990011223344556677889900112233445566778899001122";
    private static final String MINT_PRIVATE_KEY_HEX =
            "00000000000000000000000000000000000000000000000000000000000000ff";
    private static final String KEYSET_ID = "009a1f293253e41e";

    public static void main(String[] args) {
        byte[] derivedBytes = Hex.decode(DERIVED_SECRET_HEX);
        DeterministicSecret secret =
                DeterministicSecret.create(derivedBytes, KeysetId.fromString(KEYSET_ID), 0);

        String yProofCommitsTo = BDHKEUtils.pointToHex(BDHKEUtils.hashToCurve(secret.getData()));
        String yWalletReports = SecretUtil.toY(secret);

        System.out.println("wire secret                 = " + secret);
        System.out.println("Y the proof commits to      = " + yProofCommitsTo);
        System.out.println("Y the wallet sends to mint  = " + yWalletReports);
        System.out.println("checkstate MATCH?           = " + yWalletReports.equals(yProofCommitsTo));
        System.out.println("spec-only mint accepts?     = " + specOnlyMintAccepts(secret));
    }

    /** Mints, unblinds and then verifies C = k*Y the way a SPEC-only mint such as Nutshell does. */
    private static boolean specOnlyMintAccepts(DeterministicSecret secret) {
        BigInteger mintPrivateKey = new BigInteger(1, Hex.decode(MINT_PRIVATE_KEY_HEX));
        byte[] blindingFactor = Hex.decode(BLINDING_FACTOR_HEX);

        byte[] blindedMessage = BDHKEUtils.blindMessage(secret.getData(), blindingFactor);
        byte[] blindSignature = BDHKEUtils.signBlindedMessage(blindedMessage, Hex.decode(MINT_PRIVATE_KEY_HEX));
        byte[] mintPublicKey = ECNamedCurveTable.getParameterSpec("secp256k1")
                .getG().multiply(mintPrivateKey).getEncoded(true);
        byte[] unblindedSignature = BDHKEUtils.unblindSignature(blindSignature, blindingFactor, mintPublicKey);

        ECPoint expected = BDHKEUtils.hashToCurve(secret.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .multiply(mintPrivateKey);
        return expected.equals(new SecP256K1Curve().decodePoint(unblindedSignature));
    }
}
JAVA

probe_version() {
    local library_version="$1"
    local classpath_file="${WORK_DIR}/cp-${library_version}.txt"
    local classes_dir="${WORK_DIR}/classes-${library_version}"

    echo "=== cashu-lib ${library_version} ==="
    if ! (cd "${REPO_ROOT}" && mvn -o -q dependency:build-classpath \
            -Dmdep.outputFile="${classpath_file}" \
            -pl cashu-wallet-protocol \
            -Dcashu-lib.version="${library_version}" >/dev/null 2>&1); then
        echo "could not resolve cashu-lib ${library_version}; is it installed locally?"
        return
    fi

    local classpath
    classpath="$(cat "${classpath_file}")"
    mkdir -p "${classes_dir}"
    javac -proc:none -nowarn -cp "${classpath}" \
        "${WORK_DIR}/SecretEncodingProbe.java" -d "${classes_dir}" 2>/dev/null
    java -cp "${classpath}:${classes_dir}" SecretEncodingProbe 2>/dev/null
    echo
}

probe_version "0.21.0"
probe_version "0.22.0"
probe_version "0.23.0"
