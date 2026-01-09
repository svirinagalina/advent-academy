package ru.kata.spring.boot_security.demo.dao;

import ru.kata.spring.boot_security.demo.model.User;
import java.util.List;
import java.util.Optional;

public interface UserDao {
    Optional<User> findByUsername(String username);    List<User> getAllUsers();
    User getUserById(Long id);
    void save(User user);
    void update(User user);
    void delete(Long id);
}
