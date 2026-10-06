package com.smartmed.entity;

public enum ScheduleFrequency {
    ONCE_DAILY(1),
    TWICE_DAILY(2),
    THREE_TIMES_DAILY(3),
    FOUR_TIMES_DAILY(4);

    private final int slotsPerDay;

    ScheduleFrequency(int slotsPerDay) {
        this.slotsPerDay = slotsPerDay;
    }

    public int slotsPerDay() {
        return slotsPerDay;
    }
}
