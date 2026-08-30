package cinecli.admin.pricing;

import cinecli.admin.AdminInputRules;
import cinecli.admin.AdminInputRules.Confirmation;
import cinecli.admin.AdminWorkflowInput;
import cinecli.admin.AdminWorkflowInteraction;
import cinecli.admin.AdminWorkflowOutcome;
import cinecli.admin.ui.AdminTerminal;
import cinecli.model.Pricing;
import cinecli.model.PromoCode;
import cinecli.storage.exception.PricingStorageException;
import cinecli.storage.pricing.PricingStorage;
import java.util.List;
import java.util.Objects;

/** Coordinates administrator Promotion-management workflows. */
public final class PromotionManagementApplication {
    private final PricingStorage pricingStorage;
    private final AdminWorkflowInteraction interaction;
    private final AdminInputRules inputRules = new AdminInputRules();
    private final PricingInputRules pricingInputRules = new PricingInputRules();
    private final PromotionManagementText text = new PromotionManagementText();

    /** Creates a promotion-management workflow using the supplied pricing storage and terminal. */
    public PromotionManagementApplication(PricingStorage pricingStorage, AdminTerminal terminal) {
        this.pricingStorage = Objects.requireNonNull(pricingStorage);
        this.interaction = new AdminWorkflowInteraction(terminal);
    }

    /** Runs Promotion management until a typed navigation or termination outcome occurs. */
    public AdminWorkflowOutcome run() {
        while (true) {
            Pricing pricing;
            try {
                pricing = pricingStorage.load();
            } catch (PricingStorageException exception) {
                return reportStorageFailure(PromotionManagementText.ACCESS_FAILURE_PREFIX, exception);
            }
            if (!write(text.management(pricing.promotions()))) {
                return AdminWorkflowOutcome.TERMINATED;
            }
            ActionResult action = requestAction();
            if (action.outcome != null) {
                return action.outcome;
            }
            AdminWorkflowOutcome outcome;
            if (action.choice == 0) {
                outcome = AdminWorkflowOutcome.BACK;
            } else if (action.choice == 1) {
                outcome = addPromotion(pricing);
            } else if (action.choice == 2) {
                outcome = pricing.promotions().isEmpty()
                        ? showEmptyAction(PromotionManagementText.EMPTY_EDIT) : editPromotion(pricing);
            } else {
                outcome = pricing.promotions().isEmpty()
                        ? showEmptyAction(PromotionManagementText.EMPTY_DELETE) : deletePromotion(pricing);
            }
            if (outcome != null) {
                return outcome;
            }
        }
    }

    private ActionResult requestAction() {
        while (true) {
            ReadResult input = read();
            if (input.outcome != null) {
                return ActionResult.outcome(input.outcome);
            }
            Integer choice = inputRules.parseNumber(input.line, 0, 3);
            if (choice != null) {
                return ActionResult.choice(choice);
            }
            if (!write(PromotionManagementText.ACTION_ERROR_WITH_PROMPT)) {
                return ActionResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
        }
    }

    private AdminWorkflowOutcome showEmptyAction(String message) {
        return write(message) ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private AdminWorkflowOutcome addPromotion(Pricing pricing) {
        ValueResult<PromoCode> code = requestCode(pricing.promotions(), -1, 1);
        if (code.outcome != null) {
            return code.outcome;
        }
        if (code.isCancelled) {
            return cancel(PromotionManagementText.ADD_CANCELLED);
        }
        ValueResult<Integer> percentage = requestPercentage();
        if (percentage.outcome != null) {
            return percentage.outcome;
        }
        if (percentage.isCancelled) {
            return cancel(PromotionManagementText.ADD_CANCELLED);
        }
        PromoCode promotion = new PromoCode(code.value.code(), percentage.value);
        Pricing intended = PricingReplacements.withPromotions(
                pricing, PricingReplacements.promotionsWithAdded(pricing, promotion));
        ConfirmationResult confirmation = confirm(text.addPreview(promotion));
        if (confirmation.outcome != null) {
            return confirmation.outcome;
        }
        if (!confirmation.isConfirmed) {
            return cancel(PromotionManagementText.ADD_CANCELLED);
        }
        try {
            pricingStorage.save(intended);
        } catch (PricingStorageException exception) {
            return reportStorageFailure(PromotionManagementText.CHANGE_NOT_SAVED_PREFIX, exception);
        }
        return write(text.added(promotion)) ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private AdminWorkflowOutcome editPromotion(Pricing pricing) {
        TargetResult target = requestTarget(pricing.promotions().size(), "edit");
        if (target.outcome != null) {
            return target.outcome;
        }
        if (target.isBack) {
            return target.isCancelled ? cancel(PromotionManagementText.EDIT_CANCELLED) : null;
        }
        int promotionIndex = target.index;
        PromoCode original = pricing.promotions().get(promotionIndex);
        String proposedCode = original.code();
        int proposedPercentage = original.discountPercentage();
        while (true) {
            if (!write(text.editMenu(original, proposedCode, proposedPercentage))) {
                return AdminWorkflowOutcome.TERMINATED;
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return input.outcome;
            }
            if (inputRules.isCancel(input.line)) {
                return cancel(PromotionManagementText.EDIT_CANCELLED);
            }
            Integer choice = inputRules.parseNumber(input.line, 0, 3);
            if (choice == null) {
                if (!write(PromotionManagementText.EDIT_MENU_ERROR)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            if (choice == 0) {
                return cancel(PromotionManagementText.EDIT_CANCELLED);
            }
            if (choice == 1) {
                ValueResult<PromoCode> code = requestCode(
                        pricing.promotions(), promotionIndex, proposedPercentage);
                if (code.outcome != null) {
                    return code.outcome;
                }
                if (code.isCancelled) {
                    return cancel(PromotionManagementText.EDIT_CANCELLED);
                }
                proposedCode = code.value.code();
                if (proposedCode.equals(original.code())
                        && !write(PromotionManagementText.CODE_UNCHANGED)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            if (choice == 2) {
                ValueResult<Integer> percentage = requestPercentage();
                if (percentage.outcome != null) {
                    return percentage.outcome;
                }
                if (percentage.isCancelled) {
                    return cancel(PromotionManagementText.EDIT_CANCELLED);
                }
                proposedPercentage = percentage.value;
                if (proposedPercentage == original.discountPercentage()
                        && !write(PromotionManagementText.PERCENTAGE_UNCHANGED)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            if (proposedCode.equals(original.code())
                    && proposedPercentage == original.discountPercentage()) {
                if (!write(PromotionManagementText.NO_CHANGES)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            PromoCode proposed = new PromoCode(proposedCode, proposedPercentage);
            Pricing intended = PricingReplacements.withPromotions(pricing,
                    PricingReplacements.promotionsWithReplaced(pricing, promotionIndex, proposed));
            ConfirmationResult confirmation = confirm(text.editPreview(
                    original, proposed.code(), proposed.discountPercentage()));
            if (confirmation.outcome != null) {
                return confirmation.outcome;
            }
            if (!confirmation.isConfirmed) {
                return cancel(PromotionManagementText.EDIT_CANCELLED);
            }
            try {
                pricingStorage.save(intended);
            } catch (PricingStorageException exception) {
                return reportStorageFailure(PromotionManagementText.CHANGE_NOT_SAVED_PREFIX, exception);
            }
            return write(text.updated(proposed)) ? null : AdminWorkflowOutcome.TERMINATED;
        }
    }

    private AdminWorkflowOutcome deletePromotion(Pricing pricing) {
        TargetResult target = requestTarget(pricing.promotions().size(), "delete");
        if (target.outcome != null) {
            return target.outcome;
        }
        if (target.isBack) {
            return target.isCancelled ? cancel(PromotionManagementText.DELETE_CANCELLED) : null;
        }
        PromoCode promotion = pricing.promotions().get(target.index);
        Pricing intended = PricingReplacements.withPromotions(pricing,
                PricingReplacements.promotionsWithRemoved(pricing, target.index));
        ConfirmationResult confirmation = confirm(text.deletePreview(promotion));
        if (confirmation.outcome != null) {
            return confirmation.outcome;
        }
        if (!confirmation.isConfirmed) {
            return cancel(PromotionManagementText.DELETE_CANCELLED);
        }
        try {
            pricingStorage.save(intended);
        } catch (PricingStorageException exception) {
            return reportStorageFailure(PromotionManagementText.CHANGE_NOT_SAVED_PREFIX, exception);
        }
        return write(text.deleted(promotion)) ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private ValueResult<PromoCode> requestCode(
            List<PromoCode> promotions, int editedIndex, int percentageForValidation) {
        while (true) {
            if (!write(PromotionManagementText.CODE_PROMPT)) {
                return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return ValueResult.outcome(input.outcome);
            }
            if (inputRules.isCancel(input.line)) {
                return ValueResult.cancelled();
            }
            PromoCode promotion;
            try {
                promotion = new PromoCode(input.line, percentageForValidation);
            } catch (IllegalArgumentException exception) {
                if (!write(PromotionManagementText.CODE_ERROR)) {
                    return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
                }
                continue;
            }
            if (hasCollision(promotions, editedIndex, promotion.code())) {
                if (!write(PromotionManagementText.DUPLICATE_CODE_ERROR)) {
                    return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
                }
                continue;
            }
            return ValueResult.value(promotion);
        }
    }

    private boolean hasCollision(List<PromoCode> promotions, int editedIndex, String code) {
        for (int index = 0; index < promotions.size(); index++) {
            if (index != editedIndex && promotions.get(index).code().equals(code)) {
                return true;
            }
        }
        return false;
    }

    private ValueResult<Integer> requestPercentage() {
        while (true) {
            if (!write(PromotionManagementText.PERCENTAGE_PROMPT)) {
                return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return ValueResult.outcome(input.outcome);
            }
            if (inputRules.isCancel(input.line)) {
                return ValueResult.cancelled();
            }
            Integer percentage = pricingInputRules.parsePercentage(input.line);
            if (percentage != null) {
                return ValueResult.value(percentage);
            }
            if (!write(PromotionManagementText.PERCENTAGE_ERROR)) {
                return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
        }
    }

    private TargetResult requestTarget(int count, String action) {
        while (true) {
            if (!write(text.targetPrompt(action))) {
                return TargetResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return TargetResult.outcome(input.outcome);
            }
            if (inputRules.isCancel(input.line)) {
                return TargetResult.cancelled();
            }
            Integer target = inputRules.parseNumber(input.line, 0, count);
            if (target == null) {
                if (!write(text.targetError(count))) {
                    return TargetResult.outcome(AdminWorkflowOutcome.TERMINATED);
                }
                continue;
            }
            return target == 0 ? TargetResult.back() : TargetResult.index(target - 1);
        }
    }

    private ConfirmationResult confirm(String preview) {
        if (!write(preview)) {
            return ConfirmationResult.outcome(AdminWorkflowOutcome.TERMINATED);
        }
        while (true) {
            ReadResult input = read();
            if (input.outcome != null) {
                return ConfirmationResult.outcome(input.outcome);
            }
            Confirmation answer = inputRules.parseConfirmation(input.line);
            if (answer == Confirmation.CONFIRMED) {
                return ConfirmationResult.confirmed();
            }
            if (answer == Confirmation.CANCELLED) {
                return ConfirmationResult.cancelled();
            }
            if (!write(PromotionManagementText.CONFIRMATION_ERROR)) {
                return ConfirmationResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
        }
    }

    private ReadResult read() {
        AdminWorkflowInput input = interaction.read();
        return new ReadResult(input.line(), input.outcome());
    }

    private boolean write(String output) {
        return interaction.write(output);
    }

    private AdminWorkflowOutcome cancel(String message) {
        return write(message) ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private AdminWorkflowOutcome reportStorageFailure(String prefix, Exception exception) {
        return write(text.storageFailure(prefix, exception))
                ? AdminWorkflowOutcome.BACK : AdminWorkflowOutcome.TERMINATED;
    }

    /** Carries either a submitted input line or a terminal workflow outcome. */
    private record ReadResult(String line, AdminWorkflowOutcome outcome) {
    }

    /** Carries a selected management action or a terminal workflow outcome. */
    private record ActionResult(int choice, AdminWorkflowOutcome outcome) {
        private static ActionResult choice(int choice) {
            return new ActionResult(choice, null);
        }

        private static ActionResult outcome(AdminWorkflowOutcome outcome) {
            return new ActionResult(-1, outcome);
        }
    }

    /** Carries a requested value, cancellation state, or terminal workflow outcome. */
    private static final class ValueResult<T> {
        private final T value;
        private final boolean isCancelled;
        private final AdminWorkflowOutcome outcome;

        private ValueResult(T value, boolean isCancelled, AdminWorkflowOutcome outcome) {
            this.value = value;
            this.isCancelled = isCancelled;
            this.outcome = outcome;
        }

        private static <T> ValueResult<T> value(T value) {
            return new ValueResult<>(value, false, null);
        }

        private static <T> ValueResult<T> cancelled() {
            return new ValueResult<>(null, true, null);
        }

        private static <T> ValueResult<T> outcome(AdminWorkflowOutcome outcome) {
            return new ValueResult<>(null, false, outcome);
        }
    }

    /** Carries a selected promotion index, cancellation state, or terminal workflow outcome. */
    private record TargetResult(
            int index, boolean isBack, boolean isCancelled, AdminWorkflowOutcome outcome) {
        private static TargetResult index(int index) {
            return new TargetResult(index, false, false, null);
        }

        private static TargetResult back() {
            return new TargetResult(-1, true, false, null);
        }

        private static TargetResult cancelled() {
            return new TargetResult(-1, true, true, null);
        }

        private static TargetResult outcome(AdminWorkflowOutcome outcome) {
            return new TargetResult(-1, false, false, outcome);
        }
    }

    /** Carries a confirmation response or a terminal workflow outcome. */
    private record ConfirmationResult(boolean isConfirmed, AdminWorkflowOutcome outcome) {
        private static ConfirmationResult confirmed() {
            return new ConfirmationResult(true, null);
        }

        private static ConfirmationResult cancelled() {
            return new ConfirmationResult(false, null);
        }

        private static ConfirmationResult outcome(AdminWorkflowOutcome outcome) {
            return new ConfirmationResult(false, outcome);
        }
    }
}
