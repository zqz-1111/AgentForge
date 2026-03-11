package com.aiagent.guardrails;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects common prompt injection patterns.
 * These are attempts to override the system prompt or extract internal instructions.
 */
@Component
public class PromptInjectionDetector {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore (all |previous |above )?instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard (your |the )?system prompt",        Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now",                                  Pattern.CASE_INSENSITIVE),
            Pattern.compile("act as (a |an )?",                            Pattern.CASE_INSENSITIVE),
            Pattern.compile("jailbreak",                                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal (your |the )?(prompt|instructions)",   Pattern.CASE_INSENSITIVE)
    );

    public boolean isInjectionAttempt(String input) {
        return INJECTION_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(input).find());
    }
}
