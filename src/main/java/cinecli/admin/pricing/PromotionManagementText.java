package cinecli.admin.pricing;

import cinecli.model.PromoCode;
import java.util.List;

/** Renders complete administrator-visible Promotion-management output blocks. */
final class PromotionManagementText {
    static final String ACCESS_FAILURE_PREFIX = "Unable to access promotion management: ";
    static final String ACTION_ERROR_WITH_PROMPT = "Enter 0, 1, 2, or 3.\nEnter choice:\n";
    static final String EDIT_MENU_ERROR = "Enter 0, 1, 2, or 3.\n";
    static final String EMPTY_EDIT = "There are no promotions to edit.\n";
    static final String EMPTY_DELETE = "There are no promotions to delete.\n";
    static final String ADD_CANCELLED = "Promotion addition cancelled.\n";
    static final String EDIT_CANCELLED = "Promotion edit cancelled.\n";
    static final String DELETE_CANCELLED = "Promotion deletion cancelled.\n";
    static final String CHANGE_NOT_SAVED_PREFIX = "Promotion change was not saved: ";
    static final String CODE_PROMPT = "Enter promotion code (/cancel to cancel):\n";
    static final String CODE_ERROR = "Enter a code of 1 to 32 ASCII letters, digits, underscores, or "
            + "hyphens, beginning with a letter or digit.\n";
    static final String DUPLICATE_CODE_ERROR = "A promotion with that code already exists.\n";
    static final String PERCENTAGE_PROMPT = "Enter discount percentage (1 to 100, /cancel to cancel):\n";
    static final String PERCENTAGE_ERROR = "Enter a whole discount percentage from 1 through 100.\n";
    static final String CODE_UNCHANGED = "The promotion code is unchanged.\n";
    static final String PERCENTAGE_UNCHANGED = "The promotion percentage is unchanged.\n";
    static final String NO_CHANGES = "No promotion changes have been made.\n";
    static final String CONFIRMATION_ERROR = "Enter Y to confirm or N to cancel.\n";

    String management(List<PromoCode> promotions) {
        StringBuilder screen = new StringBuilder("Promotion Management\n\nPromotions\n");
        if (promotions.isEmpty()) {
            screen.append("No promotions are currently available.\n");
        } else {
            for (int i = 0; i < promotions.size(); i++) {
                PromoCode promotion = promotions.get(i);
                screen.append(i + 1).append(". ").append(promotion.code())
                        .append(" - ").append(promotion.discountPercentage()).append("%\n");
            }
        }
        return screen.append("\nActions\n1. Add promotion\n2. Edit promotion\n3. Delete promotion\n"
                + "0. Back\nEnter choice:\n").toString();
    }

    String addPreview(PromoCode promotion) {
        return "Add Promotion Preview\nCode: " + promotion.code() + "\nDiscount: "
                + promotion.discountPercentage() + "%\nConfirm add? (Y/N):\n";
    }

    String editMenu(PromoCode original, String code, int percentage) {
        return "Edit Promotion\nCode: " + code + "\nDiscount: " + percentage + "%\n\n"
                + "1. Change code\n2. Change discount percentage\n3. Review changes\n"
                + "0. Cancel edit\nEnter choice:\n";
    }

    String editPreview(PromoCode original, String code, int percentage) {
        String codeLine = code.equals(original.code())
                ? code + " (unchanged)" : original.code() + " -> " + code;
        String percentageLine = percentage == original.discountPercentage()
                ? percentage + "% (unchanged)" : original.discountPercentage() + "% -> " + percentage + "%";
        return "Edit Promotion Preview\nCode: " + codeLine + "\nDiscount: " + percentageLine
                + "\nConfirm edit? (Y/N):\n";
    }

    String deletePreview(PromoCode promotion) {
        return "Delete Promotion Preview\nCode: " + promotion.code() + "\nDiscount: "
                + promotion.discountPercentage() + "%\nWARNING: This promotion will be removed.\n"
                + "Confirm delete? (Y/N):\n";
    }

    String targetPrompt(String action) {
        return "Enter promotion number to " + action + " (0 to go back):\n";
    }

    String targetError(int count) {
        return "Enter a promotion number from 1 to " + count + ", or 0 to go back.\n";
    }

    String added(PromoCode promotion) {
        return "Promotion added: " + promotion.code() + " (" + promotion.discountPercentage() + "%).\n";
    }

    String updated(PromoCode promotion) {
        return "Promotion updated: " + promotion.code() + " (" + promotion.discountPercentage() + "%).\n";
    }

    String deleted(PromoCode promotion) {
        return "Promotion deleted: " + promotion.code() + " (" + promotion.discountPercentage() + "%).\n";
    }

    String storageFailure(String prefix, Exception exception) {
        return prefix + exception.getMessage() + "\n";
    }
}
