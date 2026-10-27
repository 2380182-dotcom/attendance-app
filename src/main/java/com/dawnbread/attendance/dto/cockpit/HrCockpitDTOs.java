package com.dawnbread.attendance.dto.cockpit;

import com.dawnbread.attendance.dto.cockpit.SalesCockpitDTOs.Alert;
import com.dawnbread.attendance.dto.cockpit.SalesCockpitDTOs.DayValue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Response shapes for the HR cockpit. Every count comes from the database. */
public final class HrCockpitDTOs {

    private HrCockpitDTOs() {
    }

    /**
     * One highlight card. kind = "count" or "time" (value in seconds since
     * midnight, Pakistan time). delta is a % for counts and MINUTES for the
     * time card (deltaUnit says which); null = nothing to compare with.
     */
    public record HrCard(String key, String label, String kind, Double value, Double secondaryValue, String context,
                         List<DayValue> sparkline, Double delta, String deltaUnit, String deltaLabel,
                         Boolean higherIsBetter, String meaning, String section) {
    }

    /** holiday = today's company holiday name, null on a normal day. */
    public record HrHighlights(LocalDateTime generatedAt, LocalDate today, String holiday, List<HrCard> cards) {
    }

    public record HrAttention(LocalDateTime generatedAt, List<Alert> alerts) {
    }

    public record HrFilterOptions(List<SalesCockpitDTOs.Option> marts, List<SalesCockpitDTOs.Option> departments) {
    }
}
