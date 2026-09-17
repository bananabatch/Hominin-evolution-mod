package dev.hominin.evolution.stage;

import java.util.Locale;

/**
 * Deep time, written the way palaeoanthropology writes it.
 *
 * <p>Millions of years ago for everything up to the early Homo, thousands for the
 * later hominins - "0.3 MYA" reads like a typo where "300 KYA" reads like a date.
 */
public final class StageAge {
    private static final int MILLION = 1_000_000;
    private static final int THOUSAND = 1_000;

    /** "3.3 MYA", "780 KYA"; empty when the stage gives no age. */
    public static String ago(int yearsAgo) {
        if (yearsAgo <= 0) {
            return "";
        }
        if (yearsAgo >= MILLION) {
            return trim(yearsAgo / (double) MILLION) + " MYA";
        }
        return Math.round(yearsAgo / (double) THOUSAND) + " KYA";
    }

    /** "50,000 years back", "1.4 million years back" - the clock run the other way. */
    public static String rewound(int years) {
        if (years <= 0) {
            return "the same ground, a poorer footing";
        }
        if (years >= MILLION) {
            return trim(years / (double) MILLION) + " million years back";
        }
        return String.format(java.util.Locale.ROOT, "%,d", Math.round(years / (double) THOUSAND) * THOUSAND)
                + " years back";
    }

    /** "900,000 years later", "1.4 million years later". */
    public static String later(int fromYearsAgo, int toYearsAgo) {
        int elapsed = fromYearsAgo - toYearsAgo;
        if (elapsed <= 0) {
            return "Some time later";
        }
        if (elapsed >= MILLION) {
            return trim(elapsed / (double) MILLION) + " million years later";
        }
        return String.format(Locale.ROOT, "%,d years later", elapsed);
    }

    /** One decimal place, and none at all when it would be ".0". */
    private static String trim(double value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    private StageAge() {
    }
}
