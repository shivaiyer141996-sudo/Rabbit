package com.rabbit.aip.organisation;

import com.rabbit.aip.organisation.OrganisationBrandingService.LogoMetadata;
import com.rabbit.aip.organisation.OrganisationBrandingService.StoredLogo;
import com.rabbit.aip.security.CurrentSession;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organisation-branding")
public class OrganisationBrandingController {
    private final OrganisationBrandingService service;
    private final CurrentSession session;

    public OrganisationBrandingController(OrganisationBrandingService service, CurrentSession session) {
        this.service = service;
        this.session = session;
    }

    @GetMapping("/current")
    LogoMetadata metadata() { return service.currentMetadata(); }

    @GetMapping("/organisations/{organisationId}/logo")
    ResponseEntity<byte[]> logo(@PathVariable UUID organisationId) {
        StoredLogo logo = service.download(organisationId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(logo.fileName()).build().toString())
                .body(logo.bytes());
    }

    @PutMapping(path = "/current/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')")
    LogoMetadata uploadCurrent(@RequestPart("file") MultipartFile file) {
        return service.upload(session.organisationId(), file);
    }

    @PutMapping(path = "/organisations/{organisationId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    LogoMetadata upload(@PathVariable UUID organisationId, @RequestPart("file") MultipartFile file) {
        return service.upload(organisationId, file);
    }

    @DeleteMapping("/current/logo")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')")
    ResponseEntity<Void> removeCurrent() {
        service.remove(session.organisationId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/organisations/{organisationId}/logo")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    ResponseEntity<Void> remove(@PathVariable UUID organisationId) {
        service.remove(organisationId);
        return ResponseEntity.noContent().build();
    }
}
