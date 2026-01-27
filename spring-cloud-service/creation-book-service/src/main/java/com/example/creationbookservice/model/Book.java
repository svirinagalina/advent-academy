package com.example.creationbookservice.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class Book implements Serializable {
    private Long id;
    private String name;
    private String description;
    @Builder.Default
    private String status = "unchecked";
    private Double price;
}