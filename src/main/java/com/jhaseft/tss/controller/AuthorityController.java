package com.jhaseft.tss.controller;

import com.jhaseft.tss.model.Authority;
import com.jhaseft.tss.service.AuthorityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/authorities")
public class AuthorityController {

    private final AuthorityService authorityService;

    public AuthorityController(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    @GetMapping
    public List<Authority> getAll() {
        return authorityService.getAll();
    }

    @PostMapping
    public ResponseEntity<Authority> create(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authorityService.create(body.get("name")));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        authorityService.delete(name);
        return ResponseEntity.noContent().build();
    }
}
