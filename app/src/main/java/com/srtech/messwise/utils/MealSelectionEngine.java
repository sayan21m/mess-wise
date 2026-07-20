package com.srtech.messwise.utils;

import com.srtech.messwise.data_models.MenuItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Selects daily menus to keep the running meal rate near the goal while
 * maximizing variety and balancing meal frequencies.
 */
public final class MealSelectionEngine {

    private static final int NEVER_SERVED_DAYS = 999;

    private MealSelectionEngine() {
    }

    public static double computeRemainingMealRate(
            double currentMealRate,
            double goalMealRate,
            long mealsTaken,
            int futureMeals) {

        if (futureMeals <= 0) {
            return goalMealRate;
        }

        double currentTotal = currentMealRate * mealsTaken;
        long totalMealsAtMonthEnd = mealsTaken + futureMeals;
        double allowedTotalCost = goalMealRate * totalMealsAtMonthEnd;
        double remainingBudget = allowedTotalCost - currentTotal;
        return remainingBudget / futureMeals;
    }

    public static List<MenuItem> generateSchedule(
            List<MenuItem> meals,
            double targetUnitCost,
            int futureMeals,
            MenuItem previousMeal,
            Map<String, Integer> frequency,
            Map<String, Integer> daysSinceLastServed) {

        if (meals == null || meals.isEmpty() || futureMeals <= 0) {
            return new ArrayList<>();
        }

        List<MenuItem> schedule = new ArrayList<>(futureMeals);
        List<Double> runningUnitCosts = new ArrayList<>();
        Map<String, Integer> freq = new HashMap<>(frequency != null ? frequency : new HashMap<>());
        Map<String, Integer> lastServed = new HashMap<>(daysSinceLastServed != null ? daysSinceLastServed : new HashMap<>());

        MenuItem previous = previousMeal;

        for (int i = 0; i < futureMeals; i++) {
            int maxFrequency = maxFrequency(freq, meals);
            MenuItem selected = selectBestMeal(
                    meals,
                    previous,
                    targetUnitCost,
                    runningUnitCosts,
                    freq,
                    lastServed,
                    maxFrequency);

            schedule.add(selected);
            runningUnitCosts.add(toUnitCost(selected));

            freq.put(selected.getId(), freq.getOrDefault(selected.getId(), 0) + 1);
            for (MenuItem meal : meals) {
                if (meal.getId().equals(selected.getId())) {
                    lastServed.put(meal.getId(), 0);
                } else {
                    lastServed.put(meal.getId(), lastServed.getOrDefault(meal.getId(), NEVER_SERVED_DAYS) + 1);
                }
            }
            previous = selected;
        }

        return schedule;
    }

    public static MenuItem selectBestMeal(
            List<MenuItem> meals,
            MenuItem previousMeal,
            double targetUnitCost,
            List<Double> scheduleUnitCostsSoFar,
            Map<String, Integer> frequency,
            Map<String, Integer> daysSinceLastServed,
            int maxFrequency) {

        List<MenuItem> candidates = new ArrayList<>();
        for (MenuItem meal : meals) {
            if (previousMeal == null || !meal.getId().equals(previousMeal.getId())) {
                candidates.add(meal);
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(meals);
        }

        MenuItem best = candidates.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;

        for (MenuItem candidate : candidates) {
            double score = scoreCandidate(
                    candidate,
                    targetUnitCost,
                    scheduleUnitCostsSoFar,
                    frequency,
                    daysSinceLastServed,
                    maxFrequency);

            if (score > bestScore
                    || (score == bestScore && compareMealIds(candidate.getId(), best.getId()) < 0)) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private static int compareMealIds(String leftId, String rightId) {
        if (leftId == null) leftId = "";
        if (rightId == null) rightId = "";
        return leftId.compareTo(rightId);
    }

    private static double scoreCandidate(
            MenuItem candidate,
            double targetUnitCost,
            List<Double> scheduleUnitCostsSoFar,
            Map<String, Integer> frequency,
            Map<String, Integer> daysSinceLastServed,
            int maxFrequency) {

        double unitCost = toUnitCost(candidate);

        List<Double> withCandidate = new ArrayList<>(scheduleUnitCostsSoFar);
        withCandidate.add(unitCost);
        double newAverage = average(withCandidate);
        double budgetPenalty = Math.abs(newAverage - targetUnitCost);

        int varietyScore = daysSinceLastServed.getOrDefault(candidate.getId(), NEVER_SERVED_DAYS);
        int currentFrequency = frequency.getOrDefault(candidate.getId(), 0);
        int frequencyScore = maxFrequency - currentFrequency;

        return varietyScore + frequencyScore - budgetPenalty;
    }

    private static double toUnitCost(MenuItem meal) {
        return meal.getCost();
    }

    /**
     * Menu bank stores cost per plate. Return as-is for rate comparisons.
     */
    public static double toPerPersonUnitCost(MenuItem meal, int takingCount) {
        return meal.getCost();
    }

    public static double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private static int maxFrequency(Map<String, Integer> frequency, List<MenuItem> meals) {
        int max = 0;
        for (MenuItem meal : meals) {
            max = Math.max(max, frequency.getOrDefault(meal.getId(), 0));
        }
        return max;
    }
}
