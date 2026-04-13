package com.wealthwise.util;

/**
 * Single source of truth for transaction type classification.
 *
 * Key distinction:
 *   isPurchase()            — adds units, includes DIVIDEND_REINVEST (for lot/unit tracking)
 *   isPurchaseCashOutflow() — real cash the investor deposited, excludes DIVIDEND_REINVEST
 *                             (used for XIRR cash flows and "invested amount" calculations)
 *   isRedemption()          — removes units
 */
public final class TransactionTypeUtil {

    private TransactionTypeUtil() {} // utility class

    /**
     * Returns true for any transaction type that increases unit balance.
     * Includes DIVIDEND_REINVEST because it creates new units even though
     * no fresh cash was deposited by the investor.
     */
    public static boolean isPurchase(String type) {
        return type != null && (
            type.equals("PURCHASE_LUMPSUM") ||
            type.equals("PURCHASE_SIP")     ||
            type.equals("SWITCH_IN")        ||
            type.equals("STP_IN")           ||
            type.equals("DIVIDEND_REINVEST")
        );
    }

    /**
     * Returns true only for transactions representing real cash outflows from the investor.
     * DIVIDEND_REINVEST is excluded because no cash was put in — it's an internal NAV event.
     * Use this for: XIRR cash flows, cumulative "invested amount" in growth timeline.
     */
    public static boolean isPurchaseCashOutflow(String type) {
        return type != null && (
            type.equals("PURCHASE_LUMPSUM") ||
            type.equals("PURCHASE_SIP")     ||
            type.equals("SWITCH_IN")        ||
            type.equals("STP_IN")
        );
    }

    /**
     * Returns true for any transaction type that decreases unit balance.
     */
    public static boolean isRedemption(String type) {
        return type != null && (
            type.equals("REDEMPTION")  ||
            type.equals("SWITCH_OUT")  ||
            type.equals("STP_OUT")     ||
            type.equals("SWP")
        );
    }
}
