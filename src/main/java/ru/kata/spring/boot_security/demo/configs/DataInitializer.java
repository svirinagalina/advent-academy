package ru.kata.spring.boot_security.demo.configs;

import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import ru.kata.spring.boot_security.demo.model.Role;
import ru.kata.spring.boot_security.demo.model.User;
import ru.kata.spring.boot_security.demo.service.RoleService;
import ru.kata.spring.boot_security.demo.service.UserService;

import java.util.Set;

@Component
public class DataInitializer {

    private final UserService userService;
    private final RoleService roleService;

    public DataInitializer(UserService userService, RoleService roleService) {
        this.userService = userService;
        this.roleService = roleService;
    }

    @PostConstruct
    public void init() {

        if (!roleService.getAllRoles().isEmpty()) {
            return;
        }

        Role roleAdmin = new Role("ROLE_ADMIN");
        Role roleUser = new Role("ROLE_USER");

        roleService.save(roleAdmin);
        roleService.save(roleUser);

        User admin = new User("Admin", "Adminov", "admin", "admin");
        admin.setRoles(Set.of(roleAdmin, roleUser));
        userService.save(admin);

        User user = new User("User", "Userov", "user", "user");
        user.setRoles(Set.of(roleUser));
        userService.save(user);
    }
}
