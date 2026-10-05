package cl.duoc.authservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleUpdateRequest {

    @NotNull(message = "el rol es obligatorio")
    private cl.duoc.authservice.model.Role role;
}
