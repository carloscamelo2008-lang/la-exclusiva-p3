package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.StockRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaleRequestFingerprintTest {
    @Test
    void canonicalRequestIsStableButDifferentBusinessInputsHaveDifferentFingerprints() {
        List<StockRequest> request = List.of(new StockRequest("P1", SalePresentation.UNIT, 2));
        String original = SaleRequestFingerprint.calculate(request, "S1", "C1", new BigDecimal("10.00"));

        assertEquals(original, SaleRequestFingerprint.calculate(request, "S1", "C1", new BigDecimal("10.0")));
        assertNotEquals(original, SaleRequestFingerprint.calculate(request, "S1", "C2", new BigDecimal("10.00")));
        assertNotEquals(original, SaleRequestFingerprint.calculate(
                List.of(new StockRequest("P1", SalePresentation.UNIT, 3)), "S1", "C1", new BigDecimal("10.00")));
        assertNotEquals(original, SaleRequestFingerprint.calculate(request, "S1", "C1", new BigDecimal("11.00")));
    }
}
