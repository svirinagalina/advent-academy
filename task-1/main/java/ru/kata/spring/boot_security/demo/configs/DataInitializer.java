package ru.kata.spring.boot_security.demo.configs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.kata.spring.boot_security.demo.model.Role;
import ru.kata.spring.boot_security.demo.model.User;
import ru.kata.spring.boot_security.demo.service.RoleService;
import ru.kata.spring.boot_security.demo.service.UserService;

import java.util.HashSet;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserService userService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public DataInitializer(UserService userService, RoleService roleService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Проверяем, есть ли уже данные
        if (!roleService.getAllRoles().isEmpty()) {
            System.out.println("Data already initialized, skipping...");
            return;
        }

        // Создаем роли
        Role roleAdmin = new Role("ROLE_ADMIN");
        Role roleUser = new Role("ROLE_USER");

        roleService.save(roleAdmin);
        roleService.save(roleUser);

        System.out.println("Roles created: ROLE_ADMIN, ROLE_USER");

        // Создаем админа
        User admin = new User("Admin", "Adminov", "admin", passwordEncoder.encode("admin"));
        Set<Role> adminRoles = new HashSet<>();
        adminRoles.add(roleAdmin);
        adminRoles.add(roleUser);
        admin.setRoles(adminRoles);
        userService.save(admin);

        System.out.println("Admin created - username: admin, password: admin");

        // Создаем обычного пользователя
        User user = new User("User", "Userov", "user", passwordEncoder.encode("user"));
        Set<Role> userRoles = new HashSet<>();
        userRoles.add(roleUser);
        user.setRoles(userRoles);
        userService.save(user);

        System.out.println("User created - username: user, password: user");
        System.out.println("=================================");
        System.out.println("Test data initialized successfully!");
        System.out.println("=================================");
    }
}
