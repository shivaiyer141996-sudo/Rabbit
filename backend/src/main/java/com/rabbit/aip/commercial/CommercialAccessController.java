package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialDtos.CommercialAccessResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commercial-access")
public class CommercialAccessController {

    private final CommercialService service;

    public CommercialAccessController(CommercialService service) {
        this.service = service;
    }

    @GetMapping
    CommercialAccessResponse access() {
        return service.access();
    }
}
