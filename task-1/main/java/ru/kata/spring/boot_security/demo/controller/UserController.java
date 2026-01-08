package com.svirina.project.controller;

import com.svirina.project.model.User;
import com.svirina.project.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/users";
    }

    @GetMapping("/users")
    public String listUsers(Model model) {
        List<User> users = userService.listUsers();
        model.addAttribute("users", users);
        return "users";
    }

    @GetMapping("/users/add")
    public String showAddForm() {
        return "user-add";
    }

    @PostMapping("/users/add")
    public String addUser(@RequestParam String name, @RequestParam String surname) {

        userService.add(name, surname);
        return "redirect:/users";
    }

    @GetMapping("/users/edit")
    public String showEditForm(@RequestParam int id, Model model) {
        User user = userService.getById(id);
        model.addAttribute("user", user);
        return "user-edit";
    }

    @PostMapping("/users/edit")
    public String editUser(@RequestParam int id, @RequestParam String name, @RequestParam String surname) {

        userService.update(id, name, surname);
        return "redirect:/users";
    }

    @PostMapping("/users/delete")
    public String deleteUser(@RequestParam int id) {
        userService.delete(id);
        return "redirect:/users";
    }
}
