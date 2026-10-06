package com.smartmed.service;

import com.smartmed.dto.response.AnalyticsSummaryResponse;
import com.smartmed.entity.NotificationRelatedEntityType;
import com.smartmed.entity.NotificationType;
import com.smartmed.entity.User;
import com.smartmed.exception.ResourceNotFoundException;
import com.smartmed.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.time.YearMonth;

@Service
public class AdherenceAttentionEvaluator {

    public static final int ATTENTION_THRESHOLD_PERCENT = 70;
    public static final int MIN_RECORDED_DOSES = 3;

    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public AdherenceAttentionEvaluator(AnalyticsService analyticsService,
                                       UserRepository userRepository,
                                       NotificationService notificationService, Clock clock) {
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public void evaluateMonthToDate(Long patientId) {
        LocalDate today = LocalDate.now(clock);
        YearMonth reportingPeriod = YearMonth.from(today);
        AnalyticsSummaryResponse summary = analyticsService.summaryForPatient(
                patientId, reportingPeriod.atDay(1), today);
        long recordedDoses = summary.taken() + summary.missed() + summary.skipped();
        if (recordedDoses < MIN_RECORDED_DOSES
                || summary.adherencePercentage().compareTo(BigDecimal.valueOf(ATTENTION_THRESHOLD_PERCENT)) >= 0) {
            return;
        }

        User patient = userRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        notificationService.createIfAbsent(patient, NotificationType.ADHERENCE_ATTENTION,
                "Adherence needs attention",
                "Your month-to-date medication adherence is below the SmartMed attention threshold.",
                NotificationRelatedEntityType.ADHERENCE_PERIOD, patientId,
                "adherence-attention:" + reportingPeriod);
    }
}
