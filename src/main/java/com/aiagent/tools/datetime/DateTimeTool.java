package com.aiagent.tools.datetime;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Tool: Date and time operations.
 */
@Component
public class DateTimeTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Tool("Get the current date and time. Optionally specify a timezone like 'Asia/Shanghai' or 'America/New_York'. Default is system timezone.")
    public String getCurrentTime(String timezone) {
        try {
            ZoneId zone = (timezone != null && !timezone.isBlank())
                    ? ZoneId.of(timezone.trim())
                    : ZoneId.systemDefault();
            ZonedDateTime now = ZonedDateTime.now(zone);
            return "Current time in " + zone + ": " + now.format(FORMATTER);
        } catch (Exception e) {
            return "Error: invalid timezone '" + timezone + "'. Use format like 'Asia/Shanghai' or 'America/New_York'.";
        }
    }

    @Tool("Convert time between timezones. Input format: '2024-01-15 14:30:00 from=Asia/Shanghai to=America/New_York'")
    public String convertTimezone(String input) {
        try {
            String[] parts = input.split("from=|to=");
            if (parts.length < 3) return "Error: use format '2024-01-15 14:30:00 from=Asia/Shanghai to=America/New_York'";

            String dateTimeStr = parts[0].trim();
            String fromZone = parts[1].trim();
            String toZone = parts[2].trim();

            LocalDateTime ldt = LocalDateTime.parse(dateTimeStr, FORMATTER);
            ZonedDateTime fromTime = ldt.atZone(ZoneId.of(fromZone));
            ZonedDateTime toTime = fromTime.withZoneSameInstant(ZoneId.of(toZone));

            return fromZone + " " + fromTime.format(FORMATTER) + " = " + toZone + " " + toTime.format(FORMATTER);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Calculate the difference between two dates. Input format: '2024-01-01 2024-12-31' (start end)")
    public String calculateDateDifference(String input) {
        try {
            String[] parts = input.trim().split("\\s+");
            if (parts.length < 2) return "Error: use format '2024-01-01 2024-12-31'";

            LocalDate start = LocalDate.parse(parts[0]);
            LocalDate end = LocalDate.parse(parts[1]);

            long days = ChronoUnit.DAYS.between(start, end);
            long months = ChronoUnit.MONTHS.between(start, end);
            long years = ChronoUnit.YEARS.between(start, end);

            return String.format("Difference: %d days, %d months, %d years", days, months, years);
        } catch (Exception e) {
            return "Error: " + e.getMessage() + ". Use format '2024-01-01 2024-12-31'";
        }
    }
}
