package com.srtech.messwise.utils;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.srtech.messwise.admin_ui.MealSlot;
import com.srtech.messwise.data_models.MenuItem;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class MenuPlanner {

    public static final class SlotMenuResult {
        private final MenuItem menuItem;
        private final MealSlot slot;
        private final double targetUnitCost;
        private final double perPersonCost;

        public SlotMenuResult(MenuItem menuItem, MealSlot slot, double targetUnitCost, double perPersonCost) {
            this.menuItem = menuItem;
            this.slot = slot;
            this.targetUnitCost = targetUnitCost;
            this.perPersonCost = perPersonCost;
        }

        public MenuItem getMenuItem() {
            return menuItem;
        }

        public MealSlot getSlot() {
            return slot;
        }

        public double getTargetUnitCost() {
            return targetUnitCost;
        }

        public double getPerPersonCost() {
            return perPersonCost;
        }
    }

    public static final class NextSlotContext {
        private final Calendar scheduleDay;
        private final MealSlot slot;
        private final boolean tomorrow;

        public NextSlotContext(Calendar scheduleDay, MealSlot slot, boolean tomorrow) {
            this.scheduleDay = scheduleDay;
            this.slot = slot;
            this.tomorrow = tomorrow;
        }

        public Calendar getScheduleDay() {
            return scheduleDay;
        }

        public MealSlot getSlot() {
            return slot;
        }

        public boolean isTomorrow() {
            return tomorrow;
        }
    }

    private MenuPlanner() {
    }

    public static List<MealSlot> parseAndSortSlots(DataSnapshot slotsSnap) {
        List<MealSlot> slots = new ArrayList<>();
        if (slotsSnap == null || !slotsSnap.exists()) {
            return slots;
        }

        for (DataSnapshot ds : slotsSnap.getChildren()) {
            MealSlot slot = ds.getValue(MealSlot.class);
            if (slot != null) {
                slot.setId(ds.getKey());
                slots.add(slot);
            }
        }

        Collections.sort(slots, Comparator.comparingInt(
                slot -> DateUtils.parseSlotTimeMinutes(slot.getTime())));
        return slots;
    }

    public static NextSlotContext resolveNextSlot(List<MealSlot> sortedSlots, Calendar now) {
        if (sortedSlots == null || sortedSlots.isEmpty()) {
            return null;
        }

        int currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        MealSlot next = null;
        int minDiff = Integer.MAX_VALUE;

        for (MealSlot slot : sortedSlots) {
            int slotMins = DateUtils.parseSlotTimeMinutes(slot.getTime());
            if (slotMins < 0) continue;
            int diff = slotMins - currentMins;
            if (diff > 0 && diff < minDiff) {
                minDiff = diff;
                next = slot;
            }
        }

        if (next != null) {
            return new NextSlotContext((Calendar) now.clone(), next, false);
        }

        Calendar tomorrow = (Calendar) now.clone();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        return new NextSlotContext(tomorrow, sortedSlots.get(0), true);
    }

    public static MealSlot findNextSlot(List<MealSlot> sortedSlots, Calendar now) {
        NextSlotContext ctx = resolveNextSlot(sortedSlots, now);
        return ctx != null ? ctx.getSlot() : null;
    }

    public static int indexOfSlot(List<MealSlot> sortedSlots, MealSlot target) {
        if (target == null || sortedSlots == null) {
            return 0;
        }
        for (int i = 0; i < sortedSlots.size(); i++) {
            if (target.getId() != null && target.getId().equals(sortedSlots.get(i).getId())) {
                return i;
            }
        }
        return 0;
    }

    public static int countMessMembers(DataSnapshot messSnapshot) {
        if (messSnapshot == null || !messSnapshot.child("member").exists()) {
            return 1;
        }
        long count = messSnapshot.child("member").getChildrenCount();
        return Math.max(1, (int) count);
    }

    /**
     * Reads the shared mess menu for the upcoming slot from Firebase only.
     * All members should display this result — never a locally planned menu.
     */
    public static SlotMenuResult readScheduledNextSlot(
            DataSnapshot messSnapshot,
            Calendar today,
            int displayTakingCount) {

        List<MenuItem> meals = parseMenuBank(messSnapshot.child("menu_bank"));
        if (meals.isEmpty()) {
            return null;
        }

        Map<String, MenuItem> mealsById = indexById(meals);
        List<MealSlot> sortedSlots = parseAndSortSlots(messSnapshot.child("meal_slots"));
        String todayKey = DateUtils.formatMealDay(today.getTime());
        DataSnapshot scheduleSnap = messSnapshot.child("menu_schedule");

        if (sortedSlots.isEmpty()) {
            return buildResultFromEntry(
                    scheduleSnap.child(todayKey),
                    mealsById,
                    null,
                    displayTakingCount,
                    messSnapshot,
                    today,
                    0,
                    countMessMembers(messSnapshot));
        }

        NextSlotContext ctx = resolveNextSlot(sortedSlots, today);
        if (ctx == null) {
            return null;
        }
        String dayKey = DateUtils.formatMealDay(ctx.getScheduleDay().getTime());
        DataSnapshot entrySnap = resolveScheduleEntry(scheduleSnap, dayKey, ctx.getSlot());
        if (!entrySnap.exists() || entrySnap.child("menuId").getValue(String.class) == null) {
            return null;
        }

        return buildResultFromEntry(
                entrySnap,
                mealsById,
                ctx.getSlot(),
                displayTakingCount,
                messSnapshot,
                ctx.getScheduleDay(),
                indexOfSlot(sortedSlots, ctx.getSlot()),
                countMessMembers(messSnapshot));
    }

    /**
     * Commits missing slot menus one at a time using Firebase transactions.
     * Re-reads the mess before each write so every member converges on the same schedule.
     */
    public static void ensureSchedulesCommitted(
            FirebaseDatabase db,
            String messId,
            Calendar today,
            Runnable onComplete) {

        if (messId == null || db == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        commitNextMissingSlot(db, messId, today, onComplete);
    }

    private static void commitNextMissingSlot(
            FirebaseDatabase db,
            String messId,
            Calendar today,
            Runnable onComplete) {

        db.getReference().child(messId).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                if (onComplete != null) onComplete.run();
                return;
            }

            int memberCount = countMessMembers(snapshot);
            List<MealSlot> sortedSlots = parseAndSortSlots(snapshot.child("meal_slots"));
            String todayKey = DateUtils.formatMealDay(today.getTime());

            if (sortedSlots.isEmpty()) {
                commitLegacyDayIfMissing(db, messId, snapshot, today, todayKey, memberCount, onComplete);
                return;
            }

            NextSlotContext ctx = resolveNextSlot(sortedSlots, today);
            String dayKey = DateUtils.formatMealDay(ctx.getScheduleDay().getTime());
            DataSnapshot entrySnap = resolveScheduleEntry(snapshot.child("menu_schedule"), dayKey, ctx.getSlot());
            if (entrySnap.exists() && entrySnap.child("menuId").getValue(String.class) != null) {
                if (onComplete != null) onComplete.run();
                return;
            }

            List<SlotMenuResult> pending = planTodaySlotsUpToNext(snapshot, memberCount, today);
            if (pending.isEmpty()) {
                if (onComplete != null) onComplete.run();
                return;
            }

            Map<String, Object> batch = new HashMap<>();
            for (SlotMenuResult toWrite : pending) {
                MealSlot slot = toWrite.getSlot();
                if (slot == null || slot.getId() == null) {
                    continue;
                }
                batch.put(
                        slot.getId(),
                        toScheduleEntry(
                                toWrite.getMenuItem(),
                                slot,
                                toWrite.getTargetUnitCost(),
                                memberCount));
            }

            if (batch.isEmpty()) {
                if (onComplete != null) onComplete.run();
                return;
            }

            db.getReference()
                    .child(messId)
                    .child("menu_schedule")
                    .child(dayKey)
                    .runTransaction(new Transaction.Handler() {
                        @NonNull
                        @Override
                        public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            for (Map.Entry<String, Object> entry : batch.entrySet()) {
                                if (currentData.child(entry.getKey()).getValue() == null) {
                                    currentData.child(entry.getKey()).setValue(entry.getValue());
                                }
                            }
                            return Transaction.success(currentData);
                        }

                        @Override
                        public void onComplete(
                                @Nullable DatabaseError error,
                                boolean committed,
                                @Nullable DataSnapshot currentData) {
                            if (onComplete != null) onComplete.run();
                        }
                    });
        }).addOnFailureListener(e -> {
            if (onComplete != null) onComplete.run();
        });
    }

    private static void commitLegacyDayIfMissing(
            FirebaseDatabase db,
            String messId,
            DataSnapshot snapshot,
            Calendar today,
            String todayKey,
            int memberCount,
            Runnable onComplete) {

        if (snapshot.child("menu_schedule").child(todayKey).exists()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        List<SlotMenuResult> pending = planTodaySlotsUpToNext(snapshot, memberCount, today);
        if (pending.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        SlotMenuResult toWrite = pending.get(0);
        Map<String, Object> entry = toScheduleEntry(
                toWrite.getMenuItem(),
                toWrite.getTargetUnitCost(),
                memberCount);

        db.getReference()
                .child(messId)
                .child("menu_schedule")
                .child(todayKey)
                .runTransaction(new Transaction.Handler() {
                    @NonNull
                    @Override
                    public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                        if (currentData.getValue() != null) {
                            return Transaction.abort();
                        }
                        currentData.setValue(entry);
                        return Transaction.success(currentData);
                    }

                    @Override
                    public void onComplete(
                            @Nullable DatabaseError error,
                            boolean committed,
                            @Nullable DataSnapshot currentData) {
                        if (onComplete != null) onComplete.run();
                    }
                });
    }

    private static SlotMenuResult buildResultFromEntry(
            DataSnapshot entrySnap,
            Map<String, MenuItem> mealsById,
            MealSlot slot,
            int displayTakingCount,
            DataSnapshot messSnapshot,
            Calendar today,
            int slotIndex,
            int planningMemberCount) {

        MenuItem menuItem = readScheduledMenu(entrySnap, mealsById);
        if (menuItem == null) {
            return null;
        }

        Double storedTarget = entrySnap.child("targetRate").getValue(Double.class);
        double targetUnitCost = storedTarget != null
                ? storedTarget
                : computeTargetRate(
                        messSnapshot,
                        mealsTaken(messSnapshot, today),
                        parseAndSortSlots(messSnapshot.child("meal_slots")),
                        today,
                        slotIndex,
                        planningMemberCount);

        if (slot == null) {
            String slotName = entrySnap.child("slotName").getValue(String.class);
            String slotTime = entrySnap.child("slotTime").getValue(String.class);
            String slotId = entrySnap.child("slotId").getValue(String.class);
            if (slotName != null) {
                slot = new MealSlot(slotId, slotName, slotTime != null ? slotTime : "");
            }
        }

        return new SlotMenuResult(
                menuItem,
                slot,
                targetUnitCost,
                MealSelectionEngine.toPerPersonUnitCost(menuItem, displayTakingCount));
    }

    /**
     * @deprecated Local planning is not shared across members. Use
     * {@link #readScheduledNextSlot} with {@link #ensureSchedulesCommitted} instead.
     */
    @Deprecated
    public static SlotMenuResult planForNextSlot(
            DataSnapshot messSnapshot,
            int planningMemberCount,
            Calendar today) {
        return readScheduledNextSlot(messSnapshot, today, planningMemberCount);
    }

    /**
     * Plans missing slots using the mess member count so every device computes the same menu.
     */
    public static List<SlotMenuResult> planTodaySlotsUpToNext(
            DataSnapshot messSnapshot,
            int planningMemberCount,
            Calendar today) {

        List<MenuItem> meals = parseMenuBank(messSnapshot.child("menu_bank"));
        if (meals.isEmpty()) {
            return new ArrayList<>();
        }

        List<MealSlot> sortedSlots = parseAndSortSlots(messSnapshot.child("meal_slots"));
        if (sortedSlots.isEmpty()) {
            SlotMenuResult legacy = planLegacyDayMenu(messSnapshot, meals, planningMemberCount, today);
            if (legacy == null) {
                return new ArrayList<>();
            }
            List<SlotMenuResult> single = new ArrayList<>();
            single.add(legacy);
            return single;
        }

        NextSlotContext ctx = resolveNextSlot(sortedSlots, today);
        Calendar scheduleDay = ctx.getScheduleDay();
        MealSlot targetSlot = ctx.getSlot();
        int targetIndex = indexOfSlot(sortedSlots, targetSlot);
        String dayKey = DateUtils.formatMealDay(scheduleDay.getTime());
        Map<String, MenuItem> mealsById = indexById(meals);
        DataSnapshot scheduleSnap = messSnapshot.child("menu_schedule");

        Map<String, Integer> frequency = new HashMap<>();
        Map<String, Integer> daysSinceLastServed = new HashMap<>();
        initVarietyState(scheduleSnap, mealsById, scheduleDay, frequency, daysSinceLastServed);

        List<MenuItem> unitMeals = toUnitCostMeals(meals, planningMemberCount);
        long mealsTaken = countMealsTakenThisMonth(messSnapshot.child("member"), scheduleDay);
        double targetUnitCost = computeTargetRate(
                messSnapshot, mealsTaken, sortedSlots, scheduleDay, targetIndex, planningMemberCount);

        MenuItem previousMeal = getPreviousSlotMeal(
                scheduleSnap, mealsById, scheduleDay, sortedSlots, 0);

        List<SlotMenuResult> pending = new ArrayList<>();

        for (int i = 0; i <= targetIndex; i++) {
            MealSlot slot = sortedSlots.get(i);
            MenuItem cached = readScheduledMenu(
                    resolveScheduleEntry(scheduleSnap, dayKey, slot),
                    mealsById);
            if (cached != null) {
                previousMeal = cached;
                continue;
            }

            MenuItem selectedUnit = MealSelectionEngine.selectBestMeal(
                    unitMeals,
                    previousMeal != null ? toUnitCostMeal(previousMeal, planningMemberCount) : null,
                    targetUnitCost,
                    new ArrayList<>(),
                    frequency,
                    daysSinceLastServed,
                    maxFrequency(frequency, unitMeals));

            MenuItem selected = mealsById.get(selectedUnit.getId());
            if (selected == null) {
                selected = selectedUnit;
            }

            pending.add(new SlotMenuResult(
                    selected,
                    slot,
                    targetUnitCost,
                    MealSelectionEngine.toPerPersonUnitCost(selected, planningMemberCount)));

            frequency.put(selected.getId(), frequency.getOrDefault(selected.getId(), 0) + 1);
            for (MenuItem meal : unitMeals) {
                if (meal.getId().equals(selected.getId())) {
                    daysSinceLastServed.put(meal.getId(), 0);
                } else {
                    daysSinceLastServed.put(
                            meal.getId(),
                            daysSinceLastServed.getOrDefault(meal.getId(), 999) + 1);
                }
            }
            previousMeal = selected;
        }

        return pending;
    }

    private static SlotMenuResult planSlotMenu(
            DataSnapshot messSnapshot,
            List<MenuItem> meals,
            List<MealSlot> sortedSlots,
            int planningMemberCount,
            Calendar today,
            MealSlot targetSlot) {

        List<SlotMenuResult> pending = planTodaySlotsUpToNext(messSnapshot, planningMemberCount, today);
        if (pending.isEmpty()) {
            return readScheduledNextSlot(messSnapshot, today, planningMemberCount);
        }

        return pending.get(pending.size() - 1);
    }

    private static int maxFrequency(Map<String, Integer> frequency, List<MenuItem> meals) {
        int max = 0;
        for (MenuItem meal : meals) {
            max = Math.max(max, frequency.getOrDefault(meal.getId(), 0));
        }
        return max;
    }

    private static SlotMenuResult planLegacyDayMenu(
            DataSnapshot messSnapshot,
            List<MenuItem> meals,
            int planningMemberCount,
            Calendar today) {

        String todayKey = DateUtils.formatMealDay(today.getTime());
        Map<String, MenuItem> mealsById = indexById(meals);

        MenuItem cached = readScheduledMenu(messSnapshot.child("menu_schedule").child(todayKey), mealsById);
        if (cached != null) {
            double target = computeTargetRate(
                    messSnapshot,
                    mealsTaken(messSnapshot, today),
                    Collections.emptyList(),
                    today,
                    0,
                    planningMemberCount);
            return new SlotMenuResult(
                    cached,
                    null,
                    target,
                    MealSelectionEngine.toPerPersonUnitCost(cached, planningMemberCount));
        }

        Map<String, Integer> frequency = new HashMap<>();
        Map<String, Integer> daysSinceLastServed = new HashMap<>();
        initVarietyState(messSnapshot.child("menu_schedule"), mealsById, today, frequency, daysSinceLastServed);

        Calendar yesterday = (Calendar) today.clone();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        MenuItem previousMeal = readScheduledMenu(
                messSnapshot.child("menu_schedule").child(DateUtils.formatMealDay(yesterday.getTime())),
                mealsById);

        long mealsTaken = countMealsTakenThisMonth(messSnapshot.child("member"), today);
        int futureMeals = DateUtils.daysRemainingInMonth(today) * planningMemberCount;
        double targetUnitCost = MealSelectionEngine.computeRemainingMealRate(
                readMealRate(messSnapshot, DateUtils.formatMonthKey(today.getTime())),
                readGoalRate(messSnapshot, readMealRate(messSnapshot, DateUtils.formatMonthKey(today.getTime()))),
                mealsTaken,
                futureMeals);

        List<MenuItem> schedule = MealSelectionEngine.generateSchedule(
                toUnitCostMeals(meals, planningMemberCount),
                targetUnitCost,
                Math.max(1, DateUtils.daysRemainingInMonth(today)),
                previousMeal != null ? toUnitCostMeal(previousMeal, planningMemberCount) : null,
                frequency,
                daysSinceLastServed);

        if (schedule.isEmpty()) {
            return null;
        }

        MenuItem selected = mealsById.get(schedule.get(0).getId());
        if (selected == null) {
            selected = schedule.get(0);
        }
        return new SlotMenuResult(
                selected,
                null,
                targetUnitCost,
                MealSelectionEngine.toPerPersonUnitCost(selected, planningMemberCount));
    }

    private static long mealsTaken(DataSnapshot messSnapshot, Calendar today) {
        return countMealsTakenThisMonth(messSnapshot.child("member"), today);
    }

    private static double computeTargetRate(
            DataSnapshot messSnapshot,
            long mealsTaken,
            List<MealSlot> sortedSlots,
            Calendar today,
            int slotIndex,
            int planningMemberCount) {

        String monthKey = DateUtils.formatMonthKey(today.getTime());
        double currentMealRate = readMealRate(messSnapshot, monthKey);
        double goalMealRate = readGoalRate(messSnapshot, currentMealRate);
        int futureMeals = countFutureSlotInstances(today, sortedSlots, slotIndex, planningMemberCount);
        if (sortedSlots.isEmpty()) {
            futureMeals = DateUtils.daysRemainingInMonth(today) * planningMemberCount;
        }
        return MealSelectionEngine.computeRemainingMealRate(
                currentMealRate, goalMealRate, mealsTaken, futureMeals);
    }

    public static int countFutureSlotInstances(
            Calendar fromDay,
            List<MealSlot> sortedSlots,
            int fromSlotIndex,
            int planningMemberCount) {

        if (sortedSlots.isEmpty()) {
            return Math.max(1, DateUtils.daysRemainingInMonth(fromDay));
        }

        int slotsPerDay = sortedSlots.size();
        int memberFactor = Math.max(1, planningMemberCount);
        int count = 0;
        Calendar cal = (Calendar) fromDay.clone();
        int month = cal.get(Calendar.MONTH);
        int year = cal.get(Calendar.YEAR);
        int slotIdx = fromSlotIndex;

        while (cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year) {
            count += (slotsPerDay - slotIdx) * memberFactor;
            slotIdx = 0;
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        return Math.max(1, count);
    }

    private static MenuItem getPreviousSlotMeal(
            DataSnapshot scheduleSnap,
            Map<String, MenuItem> mealsById,
            Calendar day,
            List<MealSlot> sortedSlots,
            int slotIndex) {

        String dayKey = DateUtils.formatMealDay(day.getTime());

        for (int i = slotIndex - 1; i >= 0; i--) {
            String previousSlotId = sortedSlots.get(i).getId();
            MenuItem sameDayPrevious = readScheduledMenu(
                    scheduleSnap.child(dayKey).child(previousSlotId),
                    mealsById);
            if (sameDayPrevious != null) {
                return sameDayPrevious;
            }
        }

        Calendar yesterday = (Calendar) day.clone();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        String yesterdayKey = DateUtils.formatMealDay(yesterday.getTime());
        DataSnapshot yesterdaySnap = scheduleSnap.child(yesterdayKey);

        if (yesterdaySnap.exists()) {
            MealSlot lastSlot = sortedSlots.get(sortedSlots.size() - 1);
            MenuItem lastSlotMeal = readScheduledMenu(yesterdaySnap.child(lastSlot.getId()), mealsById);
            if (lastSlotMeal != null) {
                return lastSlotMeal;
            }
            return readScheduledMenu(yesterdaySnap, mealsById);
        }

        return null;
    }

    public static Map<String, Object> toScheduleEntry(MenuItem item, MealSlot slot, double targetUnitCost, int planningMemberCount) {
        Map<String, Object> entry = toScheduleEntry(item, targetUnitCost, planningMemberCount);
        if (slot != null) {
            entry.put("slotId", slot.getId());
            entry.put("slotName", slot.getName());
            entry.put("slotTime", slot.getTime());
        }
        return entry;
    }

    public static Map<String, Object> toScheduleEntry(MenuItem item, double targetUnitCost, int planningMemberCount) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("menuId", item.getId());
        entry.put("menuName", item.getName());
        entry.put("description", item.getDescription());
        entry.put("cost", item.getCost());
        entry.put("unitCost", MealSelectionEngine.toPerPersonUnitCost(item, planningMemberCount));
        entry.put("memberCount", planningMemberCount);
        entry.put("targetRate", targetUnitCost);
        entry.put("timestamp", System.currentTimeMillis());
        return entry;
    }

    public static String schedulePath(String dayKey, MealSlot slot) {
        if (slot != null && slot.getId() != null) {
            return dayKey + "/" + slot.getId();
        }
        return dayKey;
    }

    private static List<MenuItem> parseMenuBank(DataSnapshot bankNode) {
        List<MenuItem> meals = new ArrayList<>();
        if (!bankNode.exists()) {
            return meals;
        }

        for (DataSnapshot ds : bankNode.getChildren()) {
            String name = ds.child("menuName").getValue(String.class);
            if (name == null || name.isEmpty()) {
                continue;
            }
            String description = ds.child("description").getValue(String.class);
            Double cost = ds.child("cost").getValue(Double.class);
            meals.add(new MenuItem(
                    ds.getKey(),
                    name,
                    description,
                    cost != null ? cost : 0));
        }

        Collections.sort(meals, Comparator.comparing(menu -> menu.getId() != null ? menu.getId() : ""));
        return meals;
    }

    private static double readMealRate(DataSnapshot messSnapshot, String monthKey) {
        DataSnapshot rateSnap = messSnapshot.child("meal_rate_history").child(monthKey);
        if (!rateSnap.exists()) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(rateSnap.getValue()));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static double readGoalRate(DataSnapshot messSnapshot, double fallback) {
        Double goal = messSnapshot.child("config").child("goal_meal_rate").getValue(Double.class);
        if (goal != null && goal > 0) {
            return goal;
        }
        return fallback > 0 ? fallback : 0;
    }

    private static long countMealsTakenThisMonth(DataSnapshot membersSnap, Calendar today) {
        if (!membersSnap.exists()) {
            return 0;
        }

        int month = today.get(Calendar.MONTH);
        int year = today.get(Calendar.YEAR);
        long total = 0;

        for (DataSnapshot memberSnap : membersSnap.getChildren()) {
            DataSnapshot history = memberSnap.child("meal_count_history");
            for (DataSnapshot entry : history.getChildren()) {
                Date entryDate = DateUtils.parseMealDay(entry.getKey());
                if (DateUtils.isSameMonthYear(entryDate, month, year)) {
                    Integer val = entry.getValue(Integer.class);
                    if (val != null) {
                        total += val;
                    }
                }
            }
        }
        return total;
    }

    private static Map<String, MenuItem> indexById(List<MenuItem> meals) {
        Map<String, MenuItem> map = new HashMap<>();
        for (MenuItem meal : meals) {
            map.put(meal.getId(), meal);
        }
        return map;
    }

    private static void initVarietyState(
            DataSnapshot scheduleSnap,
            Map<String, MenuItem> mealsById,
            Calendar today,
            Map<String, Integer> frequency,
            Map<String, Integer> daysSinceLastServed) {

        int month = today.get(Calendar.MONTH);
        int year = today.get(Calendar.YEAR);

        for (MenuItem meal : mealsById.values()) {
            daysSinceLastServed.put(meal.getId(), 999);
        }

        if (!scheduleSnap.exists()) {
            return;
        }

        for (DataSnapshot daySnap : scheduleSnap.getChildren()) {
            Date servedDate = DateUtils.parseMealDay(daySnap.getKey());
            if (!DateUtils.isSameMonthYear(servedDate, month, year)) {
                continue;
            }

            if (daySnap.hasChild("menuId")) {
                trackServing(daySnap, servedDate, today, frequency, daysSinceLastServed);
                continue;
            }

            for (DataSnapshot slotSnap : daySnap.getChildren()) {
                trackServing(slotSnap, servedDate, today, frequency, daysSinceLastServed);
            }
        }
    }

    private static void trackServing(
            DataSnapshot entrySnap,
            Date servedDate,
            Calendar today,
            Map<String, Integer> frequency,
            Map<String, Integer> daysSinceLastServed) {

        String menuId = entrySnap.child("menuId").getValue(String.class);
        if (menuId == null) {
            return;
        }

        frequency.put(menuId, frequency.getOrDefault(menuId, 0) + 1);

        if (servedDate != null) {
            Calendar servedCal = Calendar.getInstance(Locale.ENGLISH);
            servedCal.setTime(servedDate);
            int dayDiff = DateUtils.daysBetween(servedCal, today);
            if (dayDiff > 0) {
                int existing = daysSinceLastServed.getOrDefault(menuId, 999);
                daysSinceLastServed.put(menuId, Math.min(existing, dayDiff));
            } else if (dayDiff == 0) {
                daysSinceLastServed.put(menuId, 0);
            }
        }
    }

    private static DataSnapshot resolveScheduleEntry(
            DataSnapshot scheduleSnap,
            String dayKey,
            MealSlot slot) {

        DataSnapshot daySnap = scheduleSnap.child(dayKey);
        if (slot == null || slot.getId() == null) {
            return daySnap;
        }

        DataSnapshot slotSnap = daySnap.child(slot.getId());
        if (slotSnap.exists()) {
            return slotSnap;
        }

        if (isLegacyDayEntry(daySnap) && !dayHasSlotEntries(daySnap)) {
            return daySnap;
        }

        return slotSnap;
    }

    private static boolean isLegacyDayEntry(DataSnapshot daySnap) {
        return daySnap.child("menuId").exists();
    }

    private static boolean dayHasSlotEntries(DataSnapshot daySnap) {
        for (DataSnapshot child : daySnap.getChildren()) {
            String key = child.getKey();
            if ("menuId".equals(key) || "menuName".equals(key) || "description".equals(key)
                    || "cost".equals(key) || "targetRate".equals(key) || "memberCount".equals(key)
                    || "timestamp".equals(key) || "unitCost".equals(key)) {
                continue;
            }
            if (child.child("menuId").exists()) {
                return true;
            }
        }
        return false;
    }

    private static MenuItem readScheduledMenu(DataSnapshot entrySnap, Map<String, MenuItem> mealsById) {
        if (entrySnap == null || !entrySnap.exists()) {
            return null;
        }

        String menuId = entrySnap.child("menuId").getValue(String.class);
        if (menuId != null && mealsById.containsKey(menuId)) {
            return mealsById.get(menuId);
        }

        String name = entrySnap.child("menuName").getValue(String.class);
        if (name == null) {
            return null;
        }

        String description = entrySnap.child("description").getValue(String.class);
        Double cost = entrySnap.child("cost").getValue(Double.class);
        return new MenuItem(menuId != null ? menuId : name, name, description, cost != null ? cost : 0);
    }

    private static List<MenuItem> toUnitCostMeals(List<MenuItem> meals, int takingCount) {
        List<MenuItem> converted = new ArrayList<>(meals.size());
        for (MenuItem meal : meals) {
            converted.add(toUnitCostMeal(meal, takingCount));
        }
        return converted;
    }

    private static MenuItem toUnitCostMeal(MenuItem meal, int takingCount) {
        return new MenuItem(
                meal.getId(),
                meal.getName(),
                meal.getDescription(),
                MealSelectionEngine.toPerPersonUnitCost(meal, takingCount));
    }
}
