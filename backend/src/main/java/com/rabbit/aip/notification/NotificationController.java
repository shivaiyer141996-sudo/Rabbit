package com.rabbit.aip.notification;

import com.rabbit.aip.notification.NotificationDtos.NotificationInbox;
import com.rabbit.aip.notification.NotificationDtos.NotificationResponse;
import com.rabbit.aip.notification.NotificationDtos.PreferenceRequest;
import com.rabbit.aip.notification.NotificationDtos.PreferenceResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    NotificationInbox inbox() {
        return service.inbox();
    }

    @PatchMapping("/{id}/read")
    NotificationResponse markRead(@PathVariable UUID id) {
        return service.markRead(id);
    }

    @PatchMapping("/read-all")
    ResponseEntity<Void> markAllRead() {
        service.markAllRead();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    PreferenceResponse preference() {
        return service.preference();
    }

    @PutMapping("/preferences")
    PreferenceResponse updatePreference(@RequestBody PreferenceRequest request) {
        return service.updatePreference(request);
    }
}
