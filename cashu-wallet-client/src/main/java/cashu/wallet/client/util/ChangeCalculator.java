package cashu.wallet.client.util;

import lombok.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChangeCalculator {

    public static Map<Integer, Integer> withSmallestDenomination(@NonNull Integer amount, @NonNull String unit) {
        // Initialize the result map
        Map<Integer, Integer> result = new HashMap<>();

        // Get the smallest denomination
        MintRestClient mintRestClient = new MintRestClient(unit);
        List<Integer> denominations = mintRestClient.getDenominations(); // Get the denominations
        Integer denomination = Collections.min(denominations);

        if (amount >= denomination) {
            int count = amount / denomination; // Determine how many of this denomination fit into the amount
            result.put(denomination, count); // Store the count in the result map
            amount -= denomination * count; // Update the remaining amount
            if (amount > 0) {
                result.put(amount, 1);
            }
            return result;
        }
        throw new IllegalArgumentException("Amount is less than denomination");
    }

    public static Map<Integer, Integer> withDenomination(@NonNull Integer amount, @NonNull Integer denomination) {
        // Initialize the result map
        Map<Integer, Integer> result = new HashMap<>();
        if (amount >= denomination) {
            int count = amount / denomination; // Determine how many of this denomination fit into the amount
            result.put(denomination, count); // Store the count in the result map
            amount -= denomination * count; // Update the remaining amount
            if (amount > 0) {
                result.put(amount, 1);
            }
            return result;
        }
        throw new IllegalArgumentException("Amount is less than denomination");
    }

    public static Map<Integer, Integer> withAllDenominations(@NonNull Integer amount, @NonNull String unit) {
        MintRestClient mintRestClient = new MintRestClient(unit);
        List<Integer> denominations = mintRestClient.getDenominations(); // Get the denominations

        // Sort the denominations in descending order
        Collections.sort(denominations, Collections.reverseOrder());

        // Initialize the result map
        Map<Integer, Integer> result = new HashMap<>();

        // Iterate through the denominations
        for (int denomination : denominations) {
            if (amount >= denomination) {
                int count = amount / denomination; // Determine how many of this denomination fit into the amount
                result.put(denomination, count); // Store the count in the result map
                amount -= denomination * count; // Update the remaining amount
            }
        }

        // Not required, as long as the smallest denomination is 1
        if (amount > 0) {
            Map<Integer, Integer> smallestChange = withSmallestDenomination(amount, unit);
            result.putAll(smallestChange);
        }

        return result;
    }

}
