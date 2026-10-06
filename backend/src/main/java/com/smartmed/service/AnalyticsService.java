package com.smartmed.service;

import com.smartmed.dto.response.AnalyticsSummaryResponse;
import com.smartmed.dto.response.DailyAnalyticsResponse;
import com.smartmed.dto.response.MedicationAnalyticsResponse;
import com.smartmed.entity.DoseStatus;
import com.smartmed.entity.Role;
import com.smartmed.exception.InvalidDateRangeException;
import com.smartmed.repository.DoseRecordRepository;
import com.smartmed.repository.projection.DailyDoseCountProjection;
import com.smartmed.repository.projection.DoseStatusCountProjection;
import com.smartmed.repository.projection.MedicationDoseCountProjection;
import com.smartmed.security.SmartMedUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private static final int DEFAULT_RANGE_DAYS = 30;
    private final DoseRecordRepository doseRecordRepository;

    public AnalyticsService(DoseRecordRepository doseRecordRepository) {
        this.doseRecordRepository = doseRecordRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(LocalDate from, LocalDate to, SmartMedUserDetails principal) {
        requirePatient(principal);
        return summaryForPatient(principal.getId(), from, to);
    }

    @Transactional(readOnly = true)
    public List<DailyAnalyticsResponse> daily(LocalDate from, LocalDate to, SmartMedUserDetails principal) {
        requirePatient(principal);
        return dailyForPatient(principal.getId(), from, to);
    }

    // Internal target-patient methods are called by MonitoringService only after relationship authorization.
    AnalyticsSummaryResponse summaryForPatient(Long patientId, LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        Counts counts = counts(doseRecordRepository.aggregateStatuses(patientId, range.from(), range.to()));
        return new AnalyticsSummaryResponse(range.from(), range.to(), counts.total(), counts.taken,
                counts.missed, counts.skipped, counts.pending, counts.percentage());
    }

    List<DailyAnalyticsResponse> dailyForPatient(Long patientId, LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        Map<LocalDate, Counts> countsByDay = new LinkedHashMap<>();
        List<DailyDoseCountProjection> rows = doseRecordRepository
                .aggregateDailyStatuses(patientId, range.from(), range.to());
        for (DailyDoseCountProjection row : rows) {
            countsByDay.computeIfAbsent(row.getScheduledDate(), ignored -> new Counts())
                    .add(row.getStatus(), row.getDoseCount());
        }
        List<DailyAnalyticsResponse> result = new ArrayList<>(countsByDay.size());
        countsByDay.forEach((date, counts) -> result.add(new DailyAnalyticsResponse(
                date, counts.taken, counts.missed, counts.skipped, counts.pending, counts.percentage())));
        return result;
    }

    @Transactional(readOnly = true)
    public List<MedicationAnalyticsResponse> medications(LocalDate from, LocalDate to,
                                                          SmartMedUserDetails principal) {
        requirePatient(principal);
        return medicationsForPatient(principal.getId(), from, to);
    }

    List<MedicationAnalyticsResponse> medicationsForPatient(Long patientId, LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        Map<Long, MedicationCounts> countsByMedication = new LinkedHashMap<>();
        List<MedicationDoseCountProjection> rows = doseRecordRepository
                .aggregateMedicationStatuses(patientId, range.from(), range.to());
        for (MedicationDoseCountProjection row : rows) {
            MedicationCounts counts = countsByMedication.computeIfAbsent(row.getMedicationId(),
                    ignored -> new MedicationCounts(row.getMedicationName()));
            counts.add(row.getStatus(), row.getDoseCount());
        }
        List<MedicationAnalyticsResponse> result = new ArrayList<>(countsByMedication.size());
        countsByMedication.forEach((id, counts) -> result.add(new MedicationAnalyticsResponse(
                id, counts.name, counts.taken(), counts.missed(), counts.skipped(), counts.pending(), counts.percentage())));
        return result;
    }

    private static DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate effectiveFrom = from;
        LocalDate effectiveTo = to;
        if (effectiveFrom == null && effectiveTo == null) {
            effectiveTo = today;
            effectiveFrom = today.minusDays(DEFAULT_RANGE_DAYS - 1L);
        } else if (effectiveFrom != null && effectiveTo == null) {
            effectiveTo = today;
        } else if (effectiveFrom == null) {
            effectiveFrom = effectiveTo.minusDays(DEFAULT_RANGE_DAYS - 1L);
        }
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new InvalidDateRangeException("from date cannot be after to date");
        }
        return new DateRange(effectiveFrom, effectiveTo);
    }

    private static Counts counts(List<DoseStatusCountProjection> rows) {
        Counts counts = new Counts();
        rows.forEach(row -> counts.add(row.getStatus(), row.getDoseCount()));
        return counts;
    }

    private static void requirePatient(SmartMedUserDetails principal) {
        if (principal.getRole() != Role.PATIENT) {
            throw new AccessDeniedException("Only patients can access adherence analytics");
        }
    }

    private record DateRange(LocalDate from, LocalDate to) { }

    private static class Counts {
        private long taken;
        private long missed;
        private long skipped;
        private long pending;

        void add(DoseStatus status, Long count) {
            long value = count == null ? 0 : count;
            switch (status) {
                case TAKEN -> taken += value;
                case MISSED -> missed += value;
                case SKIPPED -> skipped += value;
                case PENDING -> pending += value;
            }
        }

        long total() { return taken + missed + skipped + pending; }
        long taken() { return taken; }
        long missed() { return missed; }
        long skipped() { return skipped; }
        long pending() { return pending; }

        BigDecimal percentage() {
            long completedOrNoncompliant = taken + missed + skipped;
            if (completedOrNoncompliant == 0) return BigDecimal.ZERO.setScale(1);
            return BigDecimal.valueOf(taken).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(completedOrNoncompliant), 1, RoundingMode.HALF_UP);
        }
    }

    private static final class MedicationCounts extends Counts {
        private final String name;

        private MedicationCounts(String name) {
            this.name = name;
        }
    }
}
