package com.hrm.backend.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** A personal note as returned to its owner. */
public record NoteResponseDTO(
        Long noteId,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** Request body for creating or editing a note. */
    public record Request(
            @Size(max = 150, message = "Title must be at most 150 characters")
            String title,

            @jakarta.validation.constraints.NotBlank(message = "Write something in the note first")
            @Size(max = 5000, message = "Note must be at most 5000 characters")
            String content
    ) {
    }
}
