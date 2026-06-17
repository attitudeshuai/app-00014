package com.petfoster.common.aspect;

public interface OwnershipChecker {
    ResourceType getResourceType();

    void checkOwnership(Long resourceId, Long userId, OwnershipRole role);
}
