package com.petfoster.aspect;

import com.petfoster.annotation.ResourceType;

public interface OwnershipResolver {

    boolean supports(ResourceType resource, String role);

    Long resolveOwnerId(Long resourceId);

    default boolean isOwnerMatch(Long resourceId, Long userId) {
        Long ownerId = resolveOwnerId(resourceId);
        return ownerId != null && ownerId.equals(userId);
    }
}
