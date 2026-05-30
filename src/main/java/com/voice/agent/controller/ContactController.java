package com.voice.agent.controller;

import com.voice.agent.model.entity.ContactEntity;
import com.voice.agent.model.vo.ApiResponse;
import com.voice.agent.service.ContactService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {
    private final ContactService service;

    public ContactController(ContactService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<ContactEntity>> list(@RequestParam Long userId, @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(userId, keyword));
    }

    @PostMapping
    public ApiResponse<ContactEntity> save(@RequestBody ContactEntity contact) {
        return ApiResponse.ok(service.save(contact));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id, @RequestParam Long userId) {
        return ApiResponse.ok(service.delete(id, userId));
    }
}
