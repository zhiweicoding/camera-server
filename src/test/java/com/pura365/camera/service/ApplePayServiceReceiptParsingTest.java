package com.pura365.camera.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplePayServiceReceiptParsingTest {

    @Test
    void selectPurchase_prefersLatestReceiptInfoForExpectedProduct() throws Exception {
        ApplePayService service = new ApplePayService();
        JsonNode response = new ObjectMapper().readTree("{"
                + "\"latest_receipt_info\":["
                + "{\"product_id\":\"cloud.motion.month\",\"transaction_id\":\"tx-old\",\"purchase_date_ms\":\"1000\"},"
                + "{\"product_id\":\"cloud.motion.month\",\"transaction_id\":\"tx-new\",\"purchase_date_ms\":\"3000\"}"
                + "],"
                + "\"receipt\":{\"in_app\":["
                + "{\"product_id\":\"cloud.motion.month\",\"transaction_id\":\"tx-receipt-first\",\"purchase_date_ms\":\"2000\"}"
                + "]}"
                + "}");

        JsonNode purchase = ReflectionTestUtils.invokeMethod(
                service,
                "selectPurchase",
                response,
                "cloud.motion.month",
                null
        );

        assertEquals("tx-new", purchase.get("transaction_id").asText());
    }
}
