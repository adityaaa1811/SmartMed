package com.smartmed.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * A full medication update with presence tracking for the optional low-stock threshold.
 * An omitted threshold preserves the current value; an explicit JSON null disables alerts.
 */
public final class UpdateMedicationRequest {

    @NotBlank @Size(max = 160)
    private String name;

    @NotBlank @Size(max = 120)
    private String dosage;

    @NotBlank @Size(max = 120)
    private String frequency;

    @Size(max = 2000)
    private String instructions;

    @NotNull
    private LocalDate startDate;

    private LocalDate endDate;

    @PositiveOrZero(message = "Quantity cannot be negative")
    private long quantity;

    @Size(max = 20)
    private String unit;

    @PositiveOrZero(message = "Low stock threshold cannot be negative")
    private Long lowStockThreshold;

    private boolean lowStockThresholdSpecified;

    public String name() { return name; }
    public String dosage() { return dosage; }
    public String frequency() { return frequency; }
    public String instructions() { return instructions; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public long quantity() { return quantity; }
    public String unit() { return unit; }
    public Long lowStockThreshold() { return lowStockThreshold; }
    public boolean lowStockThresholdSpecified() { return lowStockThresholdSpecified; }

    public void setName(String name) { this.name = name; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
    public void setUnit(String unit) { this.unit = unit; }

    @JsonSetter(value = "lowStockThreshold", nulls = Nulls.SET)
    public void setLowStockThreshold(Long lowStockThreshold) {
        this.lowStockThresholdSpecified = true;
        this.lowStockThreshold = lowStockThreshold;
    }

    @AssertTrue(message = "endDate cannot be before startDate")
    public boolean isEndDateValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
