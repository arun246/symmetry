package io.symmetry.exercise;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.github.javaparser.StaticJavaParser;

@Service
public class StaticCheckService {

    private static final int MAX_CONTENT_BYTES = 512_000;
    private static final List<String> FORBIDDEN = List.of(
            "java.net.",
            "java.io.File",
            "java.nio.file.",
            "ProcessBuilder",
            "Runtime.getRuntime",
            "System.exit",
            "System.load",
            "java.lang.reflect.",
            "javax.script.",
            "java.rmi.");

    public StaticCheckResult check(Exercise exercise, String content) {
        List<String> findings = new ArrayList<>();
        if (content == null || content.isBlank()) {
            findings.add("Code is empty.");
            return new StaticCheckResult(false, findings);
        }
        if (content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            findings.add("Code is larger than " + MAX_CONTENT_BYTES + " bytes.");
        }
        if ("java".equals(exercise.getLanguage())) {
            try {
                StaticJavaParser.parse(content);
            } catch (RuntimeException e) {
                findings.add("Java syntax error: " + e.getMessage());
            }
            for (String forbidden : FORBIDDEN) {
                if (content.contains(forbidden)) {
                    findings.add("Forbidden API: " + forbidden);
                }
            }
        } else if ("python".equals(exercise.getLanguage())) {
            for (String forbidden : List.of("import socket", "import subprocess", "import os", "open(")) {
                if (content.contains(forbidden)) {
                    findings.add("Forbidden API: " + forbidden);
                }
            }
        } else if ("rust".equals(exercise.getLanguage())) {
            for (String forbidden : List.of("std::fs", "std::net", "std::process", "unsafe")) {
                if (content.contains(forbidden)) {
                    findings.add("Forbidden API: " + forbidden);
                }
            }
        }
        return new StaticCheckResult(findings.isEmpty(), List.copyOf(findings));
    }
}
