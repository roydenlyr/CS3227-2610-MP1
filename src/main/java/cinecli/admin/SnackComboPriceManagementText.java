package cinecli.admin;

import cinecli.model.Pricing;
import cinecli.model.SnackMenuItem;
import java.util.Locale;

/** Renders administrator-visible Snack and Combo Price management output blocks. */
final class SnackComboPriceManagementText {
    static final String ACCESS_FAILURE_PREFIX = "Unable to access snack/combo price management: ";
    static final String CHANGE_NOT_SAVED_PREFIX = "Snack/combo price change was not saved: ";
    static final String ACTION_ERROR_WITH_PROMPT = "Enter 0 or 1.\nEnter choice:\n";
    static final String TARGET_ERROR = "Enter a snack/combo number from 1 to 5, or 0 to cancel.\n";
    static final String PRICE_ERROR = "Enter a price from S$0.01 through S$9,999.99 with exactly two decimal places.\n";
    static final String PRICE_UNCHANGED = "The snack/combo price is unchanged.\n";
    static final String EDIT_CANCELLED = "Snack/combo price edit cancelled.\n";
    static final String CONFIRMATION_ERROR = "Enter Y to confirm or N to cancel.\n";

    String management(Pricing pricing) {
        StringBuilder screen = new StringBuilder("Snack and Combo Price Management\n\nSnack and Combo Prices\n");
        for (SnackMenuItem menuItem : SnackMenuItem.values()) {
            screen.append(menuItem.getMenuNumber()).append(". ")
                    .append(menuItem.getDisplayName()).append(" - ")
                    .append(formatPrice(pricing.snackPriceInCents(menuItem))).append('\n');
        }
        return screen.append("\nActions\n1. Edit snack/combo price\n0. Back\nEnter choice:\n")
                .toString();
    }

    String targetPrompt() {
        return "Enter snack/combo number to edit (0 to cancel):\n";
    }

    String pricePrompt(SnackMenuItem menuItem) {
        return "Enter new price for " + menuItem.getDisplayName()
                + " (S$0.01 to S$9,999.99, /cancel to cancel):\n";
    }

    String editPreview(SnackMenuItem menuItem, int originalPriceInCents, int proposedPriceInCents) {
        return "Edit Snack/Combo Price Preview\n"
                + "Item: " + menuItem.getDisplayName() + "\n"
                + "Price: " + formatPrice(originalPriceInCents) + " -> "
                + formatPrice(proposedPriceInCents) + "\n"
                + "Confirm edit? (Y/N):\n";
    }

    String updated(SnackMenuItem menuItem, int priceInCents) {
        return "Snack/combo price updated: " + menuItem.getDisplayName() + " - "
                + formatPrice(priceInCents) + ".\n";
    }

    String storageFailure(String prefix, Exception exception) {
        return prefix + exception.getMessage() + "\n";
    }

    private String formatPrice(int priceInCents) {
        return String.format(Locale.ROOT, "S$%,d.%02d", priceInCents / 100, priceInCents % 100);
    }
}
