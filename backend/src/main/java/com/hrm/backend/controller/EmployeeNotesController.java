package com.hrm.backend.controller;

import com.hrm.backend.dto.NoteResponseDTO;
import com.hrm.backend.service.EmployeeService;
import com.hrm.backend.service.NoteService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The signed-in employee's own notes. The owner always comes from the session,
 * never from the request.
 */
@RestController
@RequestMapping("/api/employee/notes")
public class EmployeeNotesController {

    private final NoteService noteService;
    private final EmployeeService employeeService;

    public EmployeeNotesController(NoteService noteService, EmployeeService employeeService) {
        this.noteService = noteService;
        this.employeeService = employeeService;
    }

    @GetMapping
    public ResponseEntity<List<NoteResponseDTO>> list(Authentication authentication) {
        return ResponseEntity.ok(
                noteService.getEmployeeNotes(employeeId(authentication)));
    }

    @PostMapping
    public ResponseEntity<NoteResponseDTO> create(
            @Valid @RequestBody NoteResponseDTO.Request request,
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                noteService.createForEmployee(
                        employeeService.getEmployeeById(employeeId(authentication)),
                        request));
    }

    @PutMapping("/{noteId}")
    public ResponseEntity<NoteResponseDTO> update(
            @PathVariable Long noteId,
            @Valid @RequestBody NoteResponseDTO.Request request,
            Authentication authentication) {

        return ResponseEntity.ok(
                noteService.updateForEmployee(noteId, employeeId(authentication), request));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long noteId,
            Authentication authentication) {

        noteService.deleteForEmployee(noteId, employeeId(authentication));
        return ResponseEntity.noContent().build();
    }

    private Long employeeId(Authentication authentication) {
        return employeeService.getEmployeeIdByEmail(authentication.getName());
    }
}
