package com.hrm.backend.service;

import com.hrm.backend.dto.NoteResponseDTO;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.Note;
import com.hrm.backend.repository.NoteRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Personal notes for employees and administrators.
 *
 * Every operation resolves the owner from the authenticated account and filters
 * the query by that owner, so a note id from another account simply does not
 * exist as far as the caller is concerned (404, never someone else's note).
 */
@Service
public class NoteService {

    private final NoteRepository repository;

    public NoteService(NoteRepository repository) {
        this.repository = repository;
    }

    /* ------------------------------ employee ------------------------------ */

    @Transactional(readOnly = true)
    public List<NoteResponseDTO> getEmployeeNotes(Long employeeId) {
        return repository.findByEmployeeEmployeeIdOrderByUpdatedAtDesc(employeeId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public NoteResponseDTO createForEmployee(Employee employee, NoteResponseDTO.Request request) {
        Note note = new Note();
        note.setEmployee(employee);
        apply(note, request);
        return toDTO(repository.save(note));
    }

    @Transactional
    public NoteResponseDTO updateForEmployee(Long noteId, Long employeeId, NoteResponseDTO.Request request) {
        Note note = repository.findByNoteIdAndEmployeeEmployeeId(noteId, employeeId)
                .orElseThrow(() -> notFound());
        apply(note, request);
        return toDTO(repository.save(note));
    }

    @Transactional
    public void deleteForEmployee(Long noteId, Long employeeId) {
        Note note = repository.findByNoteIdAndEmployeeEmployeeId(noteId, employeeId)
                .orElseThrow(() -> notFound());
        repository.delete(note);
    }

    /* -------------------------------- admin -------------------------------- */

    @Transactional(readOnly = true)
    public List<NoteResponseDTO> getAdminNotes(Long adminId) {
        return repository.findByAdminAdminIdOrderByUpdatedAtDesc(adminId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public NoteResponseDTO createForAdmin(Admin admin, NoteResponseDTO.Request request) {
        Note note = new Note();
        note.setAdmin(admin);
        apply(note, request);
        return toDTO(repository.save(note));
    }

    @Transactional
    public NoteResponseDTO updateForAdmin(Long noteId, Long adminId, NoteResponseDTO.Request request) {
        Note note = repository.findByNoteIdAndAdminAdminId(noteId, adminId)
                .orElseThrow(() -> notFound());
        apply(note, request);
        return toDTO(repository.save(note));
    }

    @Transactional
    public void deleteForAdmin(Long noteId, Long adminId) {
        Note note = repository.findByNoteIdAndAdminAdminId(noteId, adminId)
                .orElseThrow(() -> notFound());
        repository.delete(note);
    }

    /* ------------------------------- helpers ------------------------------- */

    private void apply(Note note, NoteResponseDTO.Request request) {

        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write something in the note first");
        }

        String title = request.title() == null || request.title().isBlank()
                ? null
                : request.title().trim();

        if (title != null && title.length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Title must be at most 150 characters");
        }

        String content = request.content().trim();

        if (content.length() > 5000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Note must be at most 5000 characters");
        }

        note.setTitle(title);
        note.setContent(content);
    }

    private NoteResponseDTO toDTO(Note note) {
        return new NoteResponseDTO(
                note.getNoteId(),
                note.getTitle(),
                note.getContent(),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found");
    }
}
