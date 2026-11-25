package com.aiagent.agent.workflow;

import com.aiagent.rag.HybridRetrievalService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

@Configuration
public class BillingWorkflow {
    private static final Pattern RATE = Pattern.compile(
            "(?m)^billing-rate\\|([A-Za-z0-9_-]{1,64})\\|([A-Za-z0-9_-]{1,16})\\|([A-Z]{3})\\|([0-9]{1,12}(?:\\.[0-9]{1,6})?)[ \\t]*\\r?$");
    private static final Pattern PRODUCT = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Bean
    WorkflowWorker lookupRate(HybridRetrievalService retrieval) {
        return new BillingWorker("lookup_rate", "Find a product's unit price in the knowledge base. Use the exact product code.",
                Map.of("product", String.class), Rate.class,
                (inputs, request) -> {
                    String product = (String) inputs.get("product");
                    if (!PRODUCT.matcher(product).matches()) throw new UnavailableData("商品码格式无效");
                    Rate rate = null;
                    for (String document : retrieval.retrieve("billing-rate " + product, 5)) {
                        var candidates = RATE.matcher(document);
                        while (candidates.find()) {
                            if (!product.equals(candidates.group(1))) continue;
                            var candidate = new Rate(product, candidates.group(2), candidates.group(3),
                                    new BigDecimal(candidates.group(4)).stripTrailingZeros());
                            if (rate != null && !rate.equals(candidate)) {
                                throw new UnavailableData("知识库中有冲突的单价记录");
                            }
                            rate = candidate;
                        }
                    }
                    if (rate == null) throw new UnavailableData("知识库中未找到该商品的单价");
                    return rate;
                });
    }

    @Bean
    WorkflowWorker lookupUsage() {
        return new BillingWorker("lookup_usage", "Find usage in the request's structured data. Use product code and YYYY-MM.",
                Map.of("product", String.class, "month", String.class), WorkflowRequest.UsageRow.class,
                (inputs, request) -> {
                    var matches = request.usage().stream()
                            .filter(row -> row.product().equals(inputs.get("product")) && row.month().equals(inputs.get("month")))
                            .limit(2).toList();
                    if (matches.size() != 1) throw new UnavailableData("没有唯一匹配该商品和月份的用量记录");
                    return matches.getFirst();
                });
    }

    @Bean
    WorkflowWorker calculateCost() {
        return new BillingWorker("calculate_cost", "Multiply a verified rate and usage. Reference the outputs of lookup_rate and lookup_usage.",
                Map.of("rate", Rate.class, "usage", WorkflowRequest.UsageRow.class), Cost.class,
                (inputs, request) -> {
                    var rate = (Rate) inputs.get("rate");
                    var usage = (WorkflowRequest.UsageRow) inputs.get("usage");
                    if (!Objects.equals(rate.product(), usage.product()) || !Objects.equals(rate.unit(), usage.unit())) {
                        throw new UnavailableData("单价和用量的商品或计量单位不一致");
                    }
                    return new Cost(usage.product(), usage.month(), rate.currency(),
                            rate.unitPrice().multiply(usage.quantity()).stripTrailingZeros());
                });
    }

    public record Rate(String product, String unit, String currency, BigDecimal unitPrice) {}
    public record Cost(String product, String month, String currency, BigDecimal amount) {}

    static final class UnavailableData extends RuntimeException {
        UnavailableData(String message) {
            super(message);
        }
    }

    private record BillingWorker(String name, String description, Map<String, Class<?>> inputs, Class<?> outputType,
                                 BiFunction<Map<String, Object>, WorkflowRequest, Object> action) implements WorkflowWorker {
        @Override
        public Object execute(Map<String, Object> values, WorkflowRequest request) {
            return action.apply(values, request);
        }
    }
}
