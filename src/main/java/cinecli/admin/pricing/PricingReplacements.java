package cinecli.admin.pricing;

import cinecli.model.Pricing;
import cinecli.model.PromoCode;
import cinecli.model.SnackMenuItem;
import cinecli.model.TicketType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Creates complete immutable replacement pricing states for administrator mutations. */
final class PricingReplacements {
    private PricingReplacements() {
    }

    /**
     * Returns a pricing state with one ticket price replaced.
     *
     * @param pricing Existing complete pricing state.
     * @param ticketType Ticket type whose price is replaced.
     * @param priceInCents Replacement price in cents.
     * @return Complete replacement pricing state.
     */
    static Pricing withTicketPrice(Pricing pricing, TicketType ticketType, int priceInCents) {
        EnumMap<TicketType, Integer> ticketPrices = ticketPrices(pricing);
        ticketPrices.put(ticketType, priceInCents);
        return new Pricing(ticketPrices, snackPrices(pricing), pricing.promotions());
    }

    /**
     * Returns a pricing state with one snack or combo price replaced.
     *
     * @param pricing Existing complete pricing state.
     * @param menuItem Snack or combo whose price is replaced.
     * @param priceInCents Replacement price in cents.
     * @return Complete replacement pricing state.
     */
    static Pricing withSnackPrice(Pricing pricing, SnackMenuItem menuItem, int priceInCents) {
        EnumMap<SnackMenuItem, Integer> snackPrices = snackPrices(pricing);
        snackPrices.put(menuItem, priceInCents);
        return new Pricing(ticketPrices(pricing), snackPrices, pricing.promotions());
    }

    /**
     * Returns a pricing state with its complete promotion list replaced.
     *
     * @param pricing Existing complete pricing state.
     * @param promotions Replacement promotions.
     * @return Complete replacement pricing state.
     */
    static Pricing withPromotions(Pricing pricing, List<PromoCode> promotions) {
        return new Pricing(ticketPrices(pricing), snackPrices(pricing), promotions);
    }

    /**
     * Returns the promotions with one promotion appended.
     *
     * @param pricing Existing complete pricing state.
     * @param promotion Promotion to append.
     * @return Immutable promotion list containing the appended promotion.
     */
    static List<PromoCode> promotionsWithAdded(Pricing pricing, PromoCode promotion) {
        List<PromoCode> promotions = new ArrayList<>(pricing.promotions());
        promotions.add(promotion);
        return List.copyOf(promotions);
    }

    /**
     * Returns the promotions with one position replaced.
     *
     * @param pricing Existing complete pricing state.
     * @param promotionIndex Zero-based promotion position to replace.
     * @param promotion Replacement promotion.
     * @return Immutable promotion list containing the replacement.
     */
    static List<PromoCode> promotionsWithReplaced(
            Pricing pricing, int promotionIndex, PromoCode promotion) {
        List<PromoCode> promotions = new ArrayList<>(pricing.promotions());
        promotions.set(promotionIndex, promotion);
        return List.copyOf(promotions);
    }

    /**
     * Returns the promotions with one position removed.
     *
     * @param pricing Existing complete pricing state.
     * @param promotionIndex Zero-based promotion position to remove.
     * @return Immutable promotion list without the removed promotion.
     */
    static List<PromoCode> promotionsWithRemoved(Pricing pricing, int promotionIndex) {
        List<PromoCode> promotions = new ArrayList<>(pricing.promotions());
        promotions.remove(promotionIndex);
        return List.copyOf(promotions);
    }

    private static EnumMap<TicketType, Integer> ticketPrices(Pricing pricing) {
        EnumMap<TicketType, Integer> prices = new EnumMap<>(TicketType.class);
        for (TicketType ticketType : TicketType.values()) {
            prices.put(ticketType, pricing.ticketPriceInCents(ticketType));
        }
        return prices;
    }

    private static EnumMap<SnackMenuItem, Integer> snackPrices(Pricing pricing) {
        EnumMap<SnackMenuItem, Integer> prices = new EnumMap<>(SnackMenuItem.class);
        for (SnackMenuItem menuItem : SnackMenuItem.values()) {
            prices.put(menuItem, pricing.snackPriceInCents(menuItem));
        }
        return prices;
    }
}
