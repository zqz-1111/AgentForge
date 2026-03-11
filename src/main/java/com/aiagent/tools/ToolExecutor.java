package com.aiagent.tools;

import com.aiagent.tools.database.DatabaseTools;
import com.aiagent.tools.logs.LogSearchTools;
import com.aiagent.tools.payment.PaymentTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Dispatches tool calls from the ReAct loop to the correct tool implementation.
 * Add new tools here as the system grows.
 */
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final PaymentTools paymentTools;
    private final LogSearchTools logSearchTools;
    private final DatabaseTools databaseTools;

    public String execute(String toolName, String input) {
        return switch (toolName.trim().toLowerCase()) {
            case "getpaymentstatus"  -> paymentTools.getPaymentStatus(input);
            case "searchlogs"        -> logSearchTools.searchLogs(input);
            case "querydb"           -> databaseTools.query(input);
            default                  -> "Unknown tool: " + toolName;
        };
    }
}
