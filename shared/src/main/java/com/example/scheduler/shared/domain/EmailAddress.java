package com.example.scheduler.shared.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record EmailAddress(String value) {
    private static final Pattern BASIC_EMAIL = Pattern.compile("^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$", Pattern.CASE_INSENSITIVE);

    public EmailAddress {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Email must not be blank");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!BASIC_EMAIL.matcher(value).matches()) throw new IllegalArgumentException("Email address is invalid");
    }
}
