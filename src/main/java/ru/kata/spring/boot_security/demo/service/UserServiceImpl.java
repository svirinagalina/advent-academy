package ru.kata.spring.boot_security.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kata.spring.boot_security.demo.dao.UserDao;
import ru.kata.spring.boot_security.demo.model.User;
import ru.kata.spring.boot_security.demo.model.Role;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    private final UserDao userDao;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImpl(UserDao userDao, RoleService roleService, PasswordEncoder passwordEncoder) {
        this.userDao = userDao;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userDao.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с именем " + username + " не найден"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userDao.getAllUsers();
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userDao.getUserById(id);
    }

    @Override
    @Transactional
    public void save(User user) {
        userDao.save(user);
    }

    @Override
    @Transactional
    public void update(User user) {
        userDao.update(user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        userDao.delete(id);
    }

    @Override
    @Transactional
    public void saveWithRoles(User user, List<Long> roleIds) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));

    setRoles(roleIds, user);

        userDao.save(user);
    }

    @Override
    @Transactional
    public void updateWithRoles(User user, List<Long> roleIds) {
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(userDao.getUserById(user.getId()).getPassword());
        }

        setRoles(roleIds, user);

        userDao.update(user);
    }

    private void setRoles(List<Long> roleIds, User user){
        if (user == null) {
            return;
        }
        if (roleIds != null && !roleIds.isEmpty()) {
            Set<Role> roles = new HashSet<>();
            for (Long roleId : roleIds) {
                Role role = roleService.getRoleById(roleId);
                roles.add(role);
            }
            user.setRoles(roles);
        }
    }
}
