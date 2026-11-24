package com.laexclusiva.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Set;

/** Política conservadora de horario y días hábiles de contacto en America/Bogota. */
public final class ColombiaContactWindow {
    private final Set<LocalDate> holidays;
    private final boolean holidayCalendarConfigured;

    /** Una lista de festivos ausente bloquea el envío real hasta configurar el calendario anual. */
    public ColombiaContactWindow(Set<LocalDate> holidays, boolean holidayCalendarConfigured) {
        this.holidays = holidays == null ? Set.of() : Set.copyOf(holidays);
        this.holidayCalendarConfigured = holidayCalendarConfigured;
    }

    /** Indica si el instante cae dentro del horario legal del artículo 3, sin operar en festivo. */
    public boolean allows(ZonedDateTime localTime) {
        if (!holidayCalendarConfigured || localTime == null || holidays.contains(localTime.toLocalDate())) return false;
        DayOfWeek day = localTime.getDayOfWeek();
        LocalTime time = localTime.toLocalTime();
        if (day == DayOfWeek.SUNDAY) return false;
        if (day == DayOfWeek.SATURDAY) {
            return !time.isBefore(LocalTime.of(8, 0)) && time.isBefore(LocalTime.of(15, 0));
        }
        return !time.isBefore(LocalTime.of(7, 0)) && time.isBefore(LocalTime.of(19, 0));
    }

    /** Explica un bloqueo sin revelar configuración interna ni datos del cliente. */
    public String blockedReason(ZonedDateTime localTime) {
        if (!holidayCalendarConfigured) return "HOLIDAY_CALENDAR_NOT_CONFIGURED";
        if (localTime == null || holidays.contains(localTime.toLocalDate())) return "COLOMBIAN_HOLIDAY";
        return "OUTSIDE_COLOMBIA_CONTACT_WINDOW";
    }
}
