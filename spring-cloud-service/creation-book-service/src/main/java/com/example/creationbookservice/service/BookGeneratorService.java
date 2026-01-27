package com.example.creationbookservice.service;

import com.example.creationbookservice.model.Book;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Service
@Slf4j
public class BookGeneratorService {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final Random random = new Random();

    private final String[] bookNames = {
            "Effective Java", "Clean Code", "Design Patterns",
            "Spring in Action", "Java Concurrency", "Microservices Patterns"
    };

    @Bean
    public Supplier<Book> bookSupplier() {
        return () -> {
            Book book = Book.builder()
                    .id(idGenerator.getAndIncrement())
                    .name(bookNames[random.nextInt(bookNames.length)])
                    .description("Auto-generated book description")
                    .status("unchecked")
                    .price(random.nextDouble() * 50 + 10)
                    .build();

            log.info("Generated and sent book: {}", book);
            return book;
        };
    }
}