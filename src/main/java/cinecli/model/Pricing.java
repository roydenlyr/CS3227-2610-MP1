package cinecli.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents the complete immutable prices and promotions available to a customer session.
 */
public final class Pricing {
    private static final int MINIMUM_PRICE_IN_CENTS = 1;
    private static final int MAXIMUM_PRICE_IN_CENTS = 999_999;

    private final Map<TicketType, Integer> ticketPricesInCents;
    private final Map<SnackMenuItem, Integer> snackPricesInCents;
    private final List<PromoCode> promotions;

    /**
     * Creates a complete immutable pricing state.
     *
     * @param ticketPricesInCents Price for every ticket type, in cents.
     * @param snackPricesInCents Price for every snack or combo, in cents.
     * @param promotions Available promotions.
     * @throws NullPointerException If a supplied collection, key, value, or promotion is null.
     * @throws IllegalArgumentException If identities, prices, or promotions are invalid.
     */
    public Pricing(
            Map<TicketType, Integer> ticketPricesInCents,
            Map<SnackMenuItem, Integer> snackPricesInCents,
            List<PromoCode> promotions) {
        this.ticketPricesInCents = immutablePrices(
                ticketPricesInCents, TicketType.class, "ticket");
        this.snackPricesInCents = immutablePrices(
                snackPricesInCents, SnackMenuItem.class, "snack");
        this.promotions = immutablePromotions(promotions);
    }

    /**
     * Returns the pricing state that preserves the application's existing customer-facing values.
     *
     * @return Complete default pricing state.
     */
    public static Pricing defaults() {
        Map<TicketType, Integer> ticketPrices = new EnumMap<>(TicketType.class);
        ticketPrices.put(TicketType.ADULT, 1100);
        ticketPrices.put(TicketType.SENIOR, 450);
        ticketPrices.put(TicketType.STUDENT, 700);

        Map<SnackMenuItem, Integer> snackPrices = new EnumMap<>(SnackMenuItem.class);
        snackPrices.put(SnackMenuItem.POPCORN, 500);
        snackPrices.put(SnackMenuItem.NACHOS, 600);
        snackPrices.put(SnackMenuItem.SOFT_DRINK, 300);
        snackPrices.put(SnackMenuItem.POPCORN_COMBO, 700);
        snackPrices.put(SnackMenuItem.NACHOS_COMBO, 800);

        return new Pricing(ticketPrices, snackPrices, List.of(
                new PromoCode("CS2103", 20),
                new PromoCode("CS3227", 99)));
    }

    /**
     * Returns the price for a ticket type in cents.
     *
     * @param ticketType Selected ticket type.
     * @return Exact price in cents.
     */
    public int ticketPriceInCents(TicketType ticketType) {
        return ticketPricesInCents.get(Objects.requireNonNull(ticketType, "ticketType"));
    }

    /**
     * Returns the price for a snack or combo in cents.
     *
     * @param menuItem Selected snack or combo.
     * @return Exact price in cents.
     */
    public int snackPriceInCents(SnackMenuItem menuItem) {
        return snackPricesInCents.get(Objects.requireNonNull(menuItem, "menuItem"));
    }

    /**
     * Returns the immutable promotions in their persisted order.
     *
     * @return Available promotion snapshots.
     */
    public List<PromoCode> promotions() {
        return promotions;
    }

    /**
     * Finds a promotion with an already-normalized uppercase code.
     *
     * @param normalizedUppercaseCode Promotion code to locate.
     * @return The matching promotion, if one exists.
     */
    public Optional<PromoCode> findPromotion(String normalizedUppercaseCode) {
        Objects.requireNonNull(normalizedUppercaseCode, "normalizedUppercaseCode");
        return promotions.stream()
                .filter(promotion -> promotion.code().equals(normalizedUppercaseCode))
                .findFirst();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Pricing pricing)) {
            return false;
        }
        return ticketPricesInCents.equals(pricing.ticketPricesInCents)
                && snackPricesInCents.equals(pricing.snackPricesInCents)
                && promotions.equals(pricing.promotions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticketPricesInCents, snackPricesInCents, promotions);
    }

    private static <T extends Enum<T>> Map<T, Integer> immutablePrices(
            Map<T, Integer> pricesInCents, Class<T> identityType, String identityDescription) {
        Objects.requireNonNull(pricesInCents, "pricesInCents");
        EnumMap<T, Integer> copiedPrices = new EnumMap<>(identityType);
        for (Map.Entry<T, Integer> entry : pricesInCents.entrySet()) {
            T identity = Objects.requireNonNull(entry.getKey(), identityDescription + " identity");
            Integer priceInCents = Objects.requireNonNull(entry.getValue(), "priceInCents");
            validatePrice(priceInCents, identityDescription + " price");
            copiedPrices.put(identity, priceInCents);
        }
        if (!copiedPrices.keySet().equals(EnumSet.allOf(identityType))) {
            throw new IllegalArgumentException(
                    "pricing must define exactly one price for every " + identityDescription + " identity");
        }
        return Map.copyOf(copiedPrices);
    }

    private static List<PromoCode> immutablePromotions(List<PromoCode> promotions) {
        Objects.requireNonNull(promotions, "promotions");
        List<PromoCode> copiedPromotions = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (PromoCode promotion : promotions) {
            PromoCode nonNullPromotion = Objects.requireNonNull(promotion, "promotion");
            if (!codes.add(nonNullPromotion.code())) {
                throw new IllegalArgumentException(
                        "pricing must not contain duplicate promotion code '"
                                + nonNullPromotion.code() + "'");
            }
            copiedPromotions.add(nonNullPromotion);
        }
        return List.copyOf(copiedPromotions);
    }

    private static void validatePrice(int priceInCents, String description) {
        if (priceInCents < MINIMUM_PRICE_IN_CENTS || priceInCents > MAXIMUM_PRICE_IN_CENTS) {
            throw new IllegalArgumentException(description + " must be from 0.01 through 9999.99");
        }
    }
}
