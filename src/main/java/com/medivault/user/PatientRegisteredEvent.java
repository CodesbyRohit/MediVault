package com.medivault.user;

/**
 * Published (synchronously, inside the registration transaction) after a new patient is saved.
 */
public record PatientRegisteredEvent(Long userId) {
}
