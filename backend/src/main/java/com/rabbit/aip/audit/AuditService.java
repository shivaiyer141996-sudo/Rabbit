package com.rabbit.aip.audit;

import com.rabbit.aip.common.web.RequestMetadata;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.user.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditEventRepository events;
    private final CurrentSession session;
    private final RequestMetadata requestMetadata;

    public AuditService(
            AuditEventRepository events,
            CurrentSession session,
            RequestMetadata requestMetadata
    ) {
        this.events = events;
        this.session = session;
        this.requestMetadata = requestMetadata;
    }

    public void record(
            String module,
            String action,
            String entityType,
            UUID entityId,
            String beforeValue,
            String afterValue
    ) {
        events.save(new AuditEvent(
                session.organisationId(),
                session.userId(),
                module,
                action,
                entityType,
                entityId,
                beforeValue,
                afterValue,
                session.email(),
                session.role().name(),
                requestMetadata.ipAddress(),
                requestMetadata.traceId()
        ));
    }

    public void recordAuthentication(
            UUID organisationId,
            UUID actorUserId,
            String actorEmail,
            UserRole actorRole,
            String action,
            String beforeValue,
            String afterValue
    ) {
        events.save(new AuditEvent(
                organisationId,
                actorUserId,
                "AUTH",
                action,
                "UserSession",
                actorUserId,
                beforeValue,
                afterValue,
                actorEmail,
                actorRole.name(),
                requestMetadata.ipAddress(),
                requestMetadata.traceId()
        ));
    }

    public void recordSystem(
            UUID organisationId,
            UUID relatedUserId,
            String module,
            String action,
            String entityType,
            UUID entityId,
            String beforeValue,
            String afterValue
    ) {
        events.save(new AuditEvent(
                organisationId,
                relatedUserId,
                module,
                action,
                entityType,
                entityId,
                beforeValue,
                afterValue,
                "system@rabbit.local",
                "SYSTEM",
                "server",
                null
        ));
    }

    public List<AuditEventResponse> search(
            String module,
            String action,
            String actor,
            Instant from,
            Instant to
    ) {
        String normalizedActor = normalized(actor);
        return events.findAllByOrganisationIdOrderByCreatedAtDesc(
                        session.organisationId()
                ).stream()
                .filter(event -> module == null
                        || module.isBlank()
                        || event.getModule().equalsIgnoreCase(module))
                .filter(event -> action == null
                        || action.isBlank()
                        || event.getAction().equalsIgnoreCase(action))
                .filter(event -> normalizedActor.isBlank()
                        || normalized(event.getActorEmail()).contains(normalizedActor)
                        || event.getActorUserId().toString().contains(normalizedActor))
                .filter(event -> from == null || !event.getCreatedAt().isBefore(from))
                .filter(event -> to == null || !event.getCreatedAt().isAfter(to))
                .limit(1000)
                .map(AuditEventResponse::from)
                .toList();
    }

    public List<AuditEventResponse> entityHistory(String entityType, UUID entityId) {
        return events
                .findAllByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        session.organisationId(), entityType, entityId
                ).stream()
                .map(AuditEventResponse::from)
                .toList();
    }

    public String exportCsv(
            String module,
            String action,
            String actor,
            Instant from,
            Instant to
    ) {
        StringBuilder csv = new StringBuilder(
                "event_id,timestamp,actor_email,actor_role,ip_address,module,action,"
                        + "entity_type,entity_id,status,before_value,after_value,trace_id\n"
        );
        search(module, action, actor, from, to).forEach(event -> csv
                .append(cell(event.id())).append(',')
                .append(cell(event.timestamp())).append(',')
                .append(cell(event.actorEmail())).append(',')
                .append(cell(event.actorRole())).append(',')
                .append(cell(event.ipAddress())).append(',')
                .append(cell(event.module())).append(',')
                .append(cell(event.action())).append(',')
                .append(cell(event.entityType())).append(',')
                .append(cell(event.entityId())).append(',')
                .append(cell(event.status())).append(',')
                .append(cell(event.beforeValue())).append(',')
                .append(cell(event.afterValue())).append(',')
                .append(cell(event.traceId())).append('\n'));
        return csv.toString();
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String cell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    public record AuditEventResponse(
            UUID id,
            Instant timestamp,
            UUID actorUserId,
            String actorEmail,
            String actorRole,
            String ipAddress,
            String module,
            String action,
            String entityType,
            UUID entityId,
            String status,
            String beforeValue,
            String afterValue,
            String traceId
    ) {
        static AuditEventResponse from(AuditEvent event) {
            return new AuditEventResponse(
                    event.getId(),
                    event.getCreatedAt(),
                    event.getActorUserId(),
                    event.getActorEmail(),
                    event.getActorRole(),
                    event.getIpAddress(),
                    event.getModule(),
                    event.getAction(),
                    event.getEntityType(),
                    event.getEntityId(),
                    event.getStatus(),
                    event.getBeforeValue(),
                    event.getAfterValue(),
                    event.getTraceId()
            );
        }
    }
}
