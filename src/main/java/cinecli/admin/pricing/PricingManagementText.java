package cinecli.admin.pricing;

import cinecli.model.Pricing;
import cinecli.model.PromoCode;
import cinecli.model.SnackMenuItem;
import cinecli.model.TicketType;
import java.util.Locale;

/** Renders the administrator Pricing Management home screen. */
final class PricingManagementText {
    static final String ACCESS_FAILURE_PREFIX = "Unable to access pricing management: ";
    static final String ACTION_ERROR_WITH_PROMPT = "Enter 0, 1, 2, or 3.\nEnter choice:\n";

    /**
     * Returns the pricing-and-promotions management screen.
     *
     * @param pricing Current complete pricing state.
     * @return Complete management screen.
     */
    String management(Pricing pricing) {
        StringBuilder screen = new StringBuilder("Pricing and Promotions Management\n\nTicket Prices\n");
        for (TicketType ticketType : TicketType.values()) {
            screen.append(ticketType.getMenuNumber()).append(". ")
                    .append(ticketType.getDisplayName()).append(" - ")
                    .append(formatPrice(pricing.ticketPriceInCents(ticketType))).append('\n');
        }
        screen.append("\nSnack and Combo Prices\n");
        for (SnackMenuItem menuItem : SnackMenuItem.values()) {
            screen.append(menuItem.getMenuNumber()).append(". ")
                    .append(menuItem.getDisplayName()).append(" - ")
                    .append(formatPrice(pricing.snackPriceInCents(menuItem))).append('\n');
        }
        screen.append("\nPromotions\n");
        if (pricing.promotions().isEmpty()) {
            screen.append("No promotions are currently available.\n");
        } else {
            for (int index = 0; index < pricing.promotions().size(); index++) {
                PromoCode promotion = pricing.promotions().get(index);
                screen.append(index + 1).append(". ").append(promotion.code())
                        .append(" - ").append(promotion.discountPercentage()).append("% off\n");
            }
        }
        return screen.append("\nSections\n1. Ticket Prices\n2. Snack/Combo Prices\n3. Promotions\n"
                + "0. Back\nEnter choice:\n").toString();
    }

    /**
     * Returns a storage-failure message with the supplied context and cause message.
     *
     * @param prefix Contextual failure prefix.
     * @param exception Storage failure to describe.
     * @return Complete failure message.
     */
    String storageFailure(String prefix, Exception exception) {
        return prefix + exception.getMessage() + "\n";
    }

    private String formatPrice(int priceInCents) {
        return String.format(
                Locale.ROOT, "S$%,d.%02d", priceInCents / 100, priceInCents % 100);
    }
}
