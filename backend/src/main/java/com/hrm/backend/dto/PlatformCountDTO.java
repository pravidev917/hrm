package com.hrm.backend.dto;

/** One platform row on a daily report: platform name plus profiles sourced. */
public record PlatformCountDTO(String platform, Integer profileCount) {
}
