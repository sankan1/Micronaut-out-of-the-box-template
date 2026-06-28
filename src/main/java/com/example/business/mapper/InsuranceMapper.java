package com.example.business.mapper;

import com.example.business.adapter.InsuranceAdapter.InsuranceRow;
import com.example.jooq.insurance.tables.pojos.Insurance;
import com.example.openapi.model.InsuranceCreateRequest;
import com.example.openapi.model.InsuranceUpdateRequest;

import java.math.BigDecimal;
import java.util.List;

public final class InsuranceMapper {

    public static List<com.example.openapi.model.Insurance> mapToInsurances(List<InsuranceRow> rows) {
        return rows.stream().map(InsuranceMapper::mapToInsurance).toList();
    }

    public static com.example.openapi.model.Insurance mapToInsurance(InsuranceRow row) {
        return new com.example.openapi.model.Insurance(
                Math.toIntExact(row.id()),
                Math.toIntExact(row.personId()),
                Math.toIntExact(row.carId()),
                row.insurerName(),
                row.plan(),
                row.amount() == null ? null : row.amount().doubleValue(),
                row.expiryDate())
            .personName(row.personName())
            .carMark(row.carMark())
            .carModel(row.carModel())
            .daysLeft(row.daysLeft());
    }

    public static Insurance mapToNewInsurance(InsuranceCreateRequest request) {
        return new Insurance()
            .setPersonId(request.getPersonId().longValue())
            .setCarId(request.getCarId().longValue())
            .setInsurerName(request.getInsurerName())
            .setPlan(request.getPlan())
            .setAmount(BigDecimal.valueOf(request.getAmount()))
            .setExpiryDate(request.getExpiryDate());
    }

    public static void applyUpdate(Insurance existing, InsuranceUpdateRequest update) {
        existing.setInsurerName(update.getInsurerName());
        existing.setPlan(update.getPlan());
        existing.setAmount(BigDecimal.valueOf(update.getAmount()));
        existing.setExpiryDate(update.getExpiryDate());
    }
}
