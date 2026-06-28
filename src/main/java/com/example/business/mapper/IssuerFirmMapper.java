package com.example.business.mapper;

import com.example.business.adapter.IssuerFirmAdapter.IssuerFirmRow;

import java.util.List;

public final class IssuerFirmMapper {

    public static List<com.example.openapi.model.IssuerFirm> mapToIssuerFirms(List<IssuerFirmRow> rows) {
        return rows.stream().map(IssuerFirmMapper::mapToIssuerFirm).toList();
    }

    public static com.example.openapi.model.IssuerFirm mapToIssuerFirm(IssuerFirmRow row) {
        return new com.example.openapi.model.IssuerFirm(
                Math.toIntExact(row.id()), Math.toIntExact(row.carId()), row.firmName())
            .carMark(row.carMark())
            .carModel(row.carModel());
    }
}
