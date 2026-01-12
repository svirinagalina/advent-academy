package ru.kata.spring.boot_security.demo.service;

import ru.kata.spring.boot_security.demo.model.User;
import java.util.List;

public interface UserService {
    User findByUsername(String username);
    List<User> getAllUsers();
    User getUserById(Long id);
    void save(User user);
    void update(User user);
    void delete(Long id);
}
