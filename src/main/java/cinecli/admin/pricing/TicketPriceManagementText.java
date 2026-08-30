package cinecli.admin.pricing;

import cinecli.model.Pricing;
import cinecli.model.TicketType;
import java.util.Locale;

/** Renders administrator-visible Ticket Price management output blocks. */
final class TicketPriceManagementText {
    static final String ACCESS_FAILURE_PREFIX = "Unable to access ticket price management: ";
    static final String CHANGE_NOT_SAVED_PREFIX = "Ticket price change was not saved: ";
    static final String ACTION_ERROR_WITH_PROMPT = "Enter 0 or 1.\nEnter choice:\n";
    static final String TARGET_ERROR = "Enter a ticket number from 1 to 3, or 0 to cancel.\n";
    static final String PRICE_ERROR = "Enter a price from S$0.01 through S$9,999.99 with exactly two decimal places.\n";
    static final String PRICE_UNCHANGED = "The ticket price is unchanged.\n";
    static final String EDIT_CANCELLED = "Ticket price edit cancelled.\n";
    static final String CONFIRMATION_ERROR = "Enter Y to confirm or N to cancel.\n";

    /**
     * Returns the Ticket Price management screen.
     *
     * @param pricing Current complete pricing state.
     * @return Complete management screen.
     */
    String management(Pricing pricing) {
        StringBuilder screen = new StringBuilder("Ticket Price Management\n\nTicket Prices\n");
        for (TicketType ticketType : TicketType.values()) {
            screen.append(ticketType.getMenuNumber()).append(". ")
                    .append(ticketType.getDisplayName()).append(" - ")
                    .append(formatPrice(pricing.ticketPriceInCents(ticketType))).append('\n');
        }
        return screen.append("\nActions\n1. Edit ticket price\n0. Back\nEnter choice:\n").toString();
    }

    /**
     * Returns the prompt for selecting a ticket type to edit.
     *
     * @return Ticket-selection prompt.
     */
    String targetPrompt() {
        return "Enter ticket number to edit (0 to cancel):\n";
    }

    /**
     * Returns the prompt for entering a ticket type's replacement price.
     *
     * @param ticketType Ticket type being edited.
     * @return Price-entry prompt.
     */
    String pricePrompt(TicketType ticketType) {
        return "Enter new price for " + ticketType.getDisplayName()
                + " (S$0.01 to S$9,999.99, /cancel to cancel):\n";
    }

    /**
     * Returns the confirmation preview for a ticket-price change.
     *
     * @param ticketType Ticket type being edited.
     * @param originalPriceInCents Current price in cents.
     * @param proposedPriceInCents Tentative price in cents.
     * @return Complete edit preview.
     */
    String editPreview(TicketType ticketType, int originalPriceInCents, int proposedPriceInCents) {
        return "Edit Ticket Price Preview\n"
                + "Ticket: " + ticketType.getDisplayName() + "\n"
                + "Price: " + formatPrice(originalPriceInCents) + " -> "
                + formatPrice(proposedPriceInCents) + "\n"
                + "Confirm edit? (Y/N):\n";
    }

    /**
     * Returns the success message for a ticket-price update.
     *
     * @param ticketType Updated ticket type.
     * @param priceInCents Updated price in cents.
     * @return Update-success message.
     */
    String updated(TicketType ticketType, int priceInCents) {
        return "Ticket price updated: " + ticketType.getDisplayName() + " - "
                + formatPrice(priceInCents) + ".\n";
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
        return String.format(Locale.ROOT, "S$%,d.%02d", priceInCents / 100, priceInCents % 100);
    }
}
