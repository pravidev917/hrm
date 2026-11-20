package com.hrm.backend.controller;

import com.hrm.backend.dto.NoteResponseDTO;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.NoteService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The signed-in administrator's own notepad (dashboard "thoughts" and the
 * dedicated notepad screen use this same data — there is no second note system).
 */
@RestController
@RequestMapping("/api/admin/notes")
public class AdminNotesController {

    private final NoteService noteService;
    private final AdminService adminService;

    public AdminNotesController(NoteService noteService, AdminService adminService) {
        this.noteService = noteService;
        this.adminService = adminService;
    }

    @GetMapping
    public ResponseEntity<List<NoteResponseDTO>> list(Authentication authentication) {
        return ResponseEntity.ok(
                noteService.getAdminNotes(adminId(authentication)));
    }

    @PostMapping
    public ResponseEntity<NoteResponseDTO> create(
            @Valid @RequestBody NoteResponseDTO.Request request,
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                noteService.createForAdmin(
                        adminService.getAdminRecord(adminId(authentication)),
                        request));
    }

    @PutMapping("/{noteId}")
    public ResponseEntity<NoteResponseDTO> update(
            @PathVariable Long noteId,
            @Valid @RequestBody NoteResponseDTO.Request request,
            Authentication authentication) {

        return ResponseEntity.ok(
                noteService.updateForAdmin(noteId, adminId(authentication), request));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long noteId,
            Authentication authentication) {

        noteService.deleteForAdmin(noteId, adminId(authentication));
        return ResponseEntity.noContent().build();
    }

    private Long adminId(Authentication authentication) {
        return adminService.getAdminIdByEmail(authentication.getName());
    }
}
