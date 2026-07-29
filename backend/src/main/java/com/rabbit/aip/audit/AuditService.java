package com.rabbit.aip.audit;

import com.rabbit.aip.security.CurrentSession;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditEventRepository events;
    private final CurrentSession session;

    public AuditService(AuditEventRepository events, CurrentSession session) {
        this.events = events;
        this.session = session;
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
                afterValue
        ));
    }
}
