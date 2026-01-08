package com.svirina.project.dao;

import com.svirina.project.model.User;
import java.util.List;

public interface UserDao {

    List<User> listUsers();

    void add(User user);

    User getById(int id);

    void update(User user);

    void delete(int id);
}
