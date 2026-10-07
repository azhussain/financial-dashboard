package com.example.dashboard.agent;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * One exchange's local trading session. A region is considered open when the
 * current instant falls inside ANY of its windows.
 */
public record TradingWindow(
        ZoneId zone,
        LocalTime open,
        LocalTime close,
        Set<DayOfWeek> days
) {
    private static final Set<DayOfWeek> WEEKDAYS =
            Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    public static TradingWindow weekdays(ZoneId zone, LocalTime open, LocalTime close) {
        return new TradingWindow(zone, open, close, WEEKDAYS);
    }

    public boolean isOpen(Instant now) {
        var t = now.atZone(zone);
        var time = t.toLocalTime();
        return days.contains(t.getDayOfWeek())
                && !time.isBefore(open)
                && time.isBefore(close);
    }
}
