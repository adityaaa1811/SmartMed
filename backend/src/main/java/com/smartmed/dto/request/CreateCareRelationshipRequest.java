package com.smartmed.dto.request;

import com.smartmed.entity.RelationshipType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCareRelationshipRequest(
        @NotBlank @Email @Size(max = 255) String relatedUserEmail,
        @NotNull RelationshipType relationshipType
) {
}
