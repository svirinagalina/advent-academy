package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.adapter.rest.dto.CatalogResponse;
import com.adventacademy.contentservice.application.service.GetCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
@Tag(name = "Catalog", description = "Public catalog: directions with published courses")
public class CatalogController {

    private final GetCatalogService catalogService;

    public CatalogController(GetCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @Operation(summary = "Get the catalog of directions with their published courses")
    public CatalogResponse getCatalog() {
        GetCatalogService.Catalog catalog = catalogService.getCatalog();
        return CatalogResponse.from(catalog.directions(), catalog.coursesById());
    }
}