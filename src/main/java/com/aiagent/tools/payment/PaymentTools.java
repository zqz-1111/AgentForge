package com.aiagent.tools.payment;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 查询支付状态的工具。
 * 可用真实支付服务调用替换占位实现。
 */
@Component
public class PaymentTools {

    @Tool("Get payment status by transaction ID")
    public String getPaymentStatus(String txnId) {
        // TODO: 注入并调用真实的 PaymentService
        if (txnId == null || txnId.isBlank()) {
            return "Invalid transaction ID";
        }
        // 占位实现，可替换为真实服务调用
        return "Transaction " + txnId + ": FAILED - Insufficient funds";
    }
}
