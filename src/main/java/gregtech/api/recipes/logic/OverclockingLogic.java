package gregtech.api.recipes.logic;

import gregtech.common.ConfigHolder;

import javax.annotation.Nonnull;

/**
 * A class for holding all the various Overclocking logics
 */
public class OverclockingLogic {

    public static final double STANDARD_OVERCLOCK_VOLTAGE_MULTIPLIER = 4.0;
    public static final double STANDARD_OVERCLOCK_DURATION_DIVISOR = ConfigHolder.machines.overclockDivisor;
    public static final double PERFECT_OVERCLOCK_DURATION_DIVISOR = 4.0;

    public static final int COIL_EUT_DISCOUNT_TEMPERATURE = 900;

    /**
     * applies standard logic for overclocking, where each overclock modifies energy and duration
     *
     * @param recipeEUt         the EU/t of the recipe to overclock
     * @param maxVoltage        the maximum voltage the recipe is allowed to be run at
     * @param recipeDuration    the duration of the recipe to overclock
     * @param durationDivisor   the value to divide the duration by for each overclock
     * @param voltageMultiplier the value to multiply the voltage by for each overclock
     * @param numberOfOCs       the maximum amount of overclocks allowed
     * @return an int array of {OverclockedEUt, OverclockedDuration}
     */
    @Nonnull
    public static int[] standardOverclockingLogic(int recipeEUt, long maxVoltage, int recipeDuration, int numberOfOCs, double durationDivisor, double voltageMultiplier) {
        double resultDuration = recipeDuration;
        double resultVoltage = recipeEUt;

        for (; numberOfOCs > 0; numberOfOCs--) {
            // it is important to do voltage first,
            // so overclocking voltage does not go above the limit before changing duration

            double potentialVoltage = resultVoltage * voltageMultiplier;
            // do not allow voltage to go above maximum
            if (potentialVoltage > maxVoltage) break;

            double potentialDuration = resultDuration / durationDivisor;
            // do not allow duration to go below one tick
            if (potentialDuration < 1) break;
            // update the duration for the next iteration
            resultDuration = potentialDuration;

            // update the voltage for the next iteration after everything else
            // in case duration overclocking would waste energy
            resultVoltage = potentialVoltage;
        }

        return new int[]{(int) resultVoltage, (int) resultDuration};
    }

    /**
     * @param providedTemp the temperate provided by the machine
     * @param requiredTemp the required temperature of the recipe
     * @return the amount of EU/t discounts to apply
     */
    public static int calculateAmountCoilEUtDiscount(int providedTemp, int requiredTemp) {
        return Math.max(0, (providedTemp - requiredTemp) / COIL_EUT_DISCOUNT_TEMPERATURE);
    }

    /**
     * @param recipeEUt the EU/t of the recipe
     * @param amountEUtDiscount the amount of discounts to apply
     * @return the discounted EU/t
     */
    public static int applyCoilEUtDiscount(int recipeEUt, int amountEUtDiscount) {
        if (amountEUtDiscount < 1) return recipeEUt;
        return (int) (recipeEUt * Math.min(1, Math.pow(0.95, amountEUtDiscount)));
    }

    @Nonnull
    public static int[] heatingCoilOverclockingLogic(int recipeEUt, long maximumVoltage, int recipeDuration, int maxOverclocks, int currentTemp, int recipeRequiredTemp) {
        int amountEUtDiscount = calculateAmountCoilEUtDiscount(currentTemp, recipeRequiredTemp);
        int amountPerfectOC = amountEUtDiscount / 2;

        // apply a multiplicative 95% energy multiplier for every 900k over recipe temperature
        recipeEUt = applyCoilEUtDiscount(recipeEUt, amountEUtDiscount);

        // perfect overclock for every 1800k over recipe temperature
        if (amountPerfectOC > 0) {
            // use the normal overclock logic to do perfect OCs up to as many times as calculated
            int[] overclock = standardOverclockingLogic(recipeEUt, maximumVoltage, recipeDuration, amountPerfectOC, PERFECT_OVERCLOCK_DURATION_DIVISOR, STANDARD_OVERCLOCK_VOLTAGE_MULTIPLIER);

            // overclock normally as much as possible after perfects are exhausted
            return standardOverclockingLogic(overclock[0], maximumVoltage, overclock[1], maxOverclocks - amountPerfectOC, STANDARD_OVERCLOCK_DURATION_DIVISOR, STANDARD_OVERCLOCK_VOLTAGE_MULTIPLIER);
        }

        // no perfects are performed, do normal overclocking
        return standardOverclockingLogic(recipeEUt, maximumVoltage, recipeDuration, maxOverclocks, STANDARD_OVERCLOCK_DURATION_DIVISOR, STANDARD_OVERCLOCK_VOLTAGE_MULTIPLIER);
    }
}
