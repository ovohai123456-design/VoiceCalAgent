package com.voice.agent.controller;

import com.voice.agent.auth.AuthContext;
import com.voice.agent.model.entity.ContactEntity;
import com.voice.agent.model.vo.ApiResponse;
import com.voice.agent.service.ContactService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {
    private final ContactService service;

    public ContactController(ContactService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<ContactEntity>> list(@RequestParam(required = false) String keyword, HttpServletRequest request) {
        return ApiResponse.ok(service.list(AuthContext.requireUserId(request), keyword));
    }

    @PostMapping
    public ApiResponse<ContactEntity> save(@RequestBody ContactEntity contact, HttpServletRequest request) {
        contact.setUserId(AuthContext.requireUserId(request));
        return ApiResponse.ok(service.save(contact));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(service.delete(id, AuthContext.requireUserId(request)));
    }
}
