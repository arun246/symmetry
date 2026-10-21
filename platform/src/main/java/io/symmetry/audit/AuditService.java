package io.symmetry.audit;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.symmetry.identity.Actor;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes append-only audit entries. Serialization failures degrade to {@code null}
 * rather than blocking the business action that is being audited.
 */
@Service
public class AuditService {

    private final AuditLogRepository logs;
    private final ObjectMapper mapper;

    public AuditService(AuditLogRepository logs, ObjectMapper mapper) {
        this.logs = logs;
        this.mapper = mapper;
    }

    @Transactional
    public void record(Actor actor, String action, String targetType, String targetId,
            Object before, Object after) {
        UUID actorId = actor == null ? null : actor.id();
        String email = actor == null ? "system" : actor.email();
        String role = actor == null ? "SYSTEM" : actor.role().name();
        HttpServletRequest request = currentRequest();
        logs.save(new AuditLog(actorId, email, role, action, targetType, targetId,
                json(before), json(after), ip(request), userAgent(request)));
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet
                ? servlet.getRequest()
                : null;
    }

    private static String ip(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }
}
