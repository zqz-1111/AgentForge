package com.aiagent.tools.payment;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * Tool: Payment status lookup.
 * Replace the stub with a real payment service call.
 */
@Component
public class PaymentTools {

    @Tool("Get payment status by transaction ID")
    public String getPaymentStatus(String txnId) {
        // TODO: inject and call real PaymentService
        if (txnId == null || txnId.isBlank()) {
            return "Invalid transaction ID";
        }
        // Stub — replace with actual service call
        return "Transaction " + txnId + ": FAILED - Insufficient funds";
    }
}
