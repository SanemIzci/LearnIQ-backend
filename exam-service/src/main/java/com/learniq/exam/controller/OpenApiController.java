package com.learniq.exam.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenApiController {

    private final Resource openApiSpec = new ClassPathResource("openapi.yaml");

    @GetMapping(value = {"/v3/api-docs", "/api-docs/openapi.yaml"}, produces = "application/yaml")
    public ResponseEntity<Resource> getOpenApiSpec() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/yaml"))
                .body(openApiSpec);
    }
}

