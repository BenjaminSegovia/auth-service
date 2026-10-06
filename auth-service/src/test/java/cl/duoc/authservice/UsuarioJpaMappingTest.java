package cl.duoc.authservice;

import cl.duoc.authservice.model.Role;
import cl.duoc.authservice.model.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el mapeo JPA de la entidad {@link Usuario} (IE6):
 * nombre de la tabla, restricciones de cada columna y guardado del rol como texto.
 *
 * Es un test de seguridad de datos: si alguien borra una anotación,
 * acá se entera en lugar de descubrirlo en producción.
 */
class UsuarioJpaMappingTest {

    @Test
    void laEntidadCorrespondeALaTablaUsuarios() {
        Entity entity = Usuario.class.getAnnotation(Entity.class);
        Table table = Usuario.class.getAnnotation(Table.class);

        assertThat(entity).as("@Entity presente").isNotNull();
        assertThat(table).as("@Table presente").isNotNull();
        assertThat(table.name()).as("nombre de la tabla").isEqualTo("usuarios");
    }

    @Test
    void elIdentificadorEsAutoIncremental() throws Exception {
        Field id = Usuario.class.getDeclaredField("id");

        assertThat(id.getAnnotation(Id.class)).as("@Id en id").isNotNull();

        GeneratedValue generado = id.getAnnotation(GeneratedValue.class);
        assertThat(generado).as("@GeneratedValue en id").isNotNull();
        assertThat(generado.strategy()).isEqualTo(GenerationType.IDENTITY);
    }

    @Test
    void elUsernameEsUnicoYNuloProhibido() throws Exception {
        Column username = columna("username");

        assertThat(username).as("@Column en username").isNotNull();
        assertThat(username.unique()).as("unique = true").isTrue();
        assertThat(username.nullable()).as("nullable = false").isFalse();
    }

    @Test
    void passwordNombreCompletoYRolSonObligatorios() throws Exception {
        assertThat(columna("password").nullable()).as("password no admite null").isFalse();
        assertThat(columna("nombreCompleto").nullable()).as("nombreCompleto no admite null").isFalse();
        assertThat(columna("role").nullable()).as("role no admite null").isFalse();
    }

    @Test
    void elRolSeGuardaComoTextoYNoseDescuadra() throws Exception {
        Field rol = Usuario.class.getDeclaredField("role");
        Enumerated enumerado = rol.getAnnotation(Enumerated.class);

        assertThat(enumerado).as("@Enumerated en role").isNotNull();
        assertThat(enumerado.value())
                .as("EnumType.STRING guarda el nombre y no el ordinal")
                .isEqualTo(EnumType.STRING);

        assertThat(Role.values())
                .as("roles disponibles en la base")
                .containsExactly(Role.USER, Role.MEDICO, Role.FARMACEUTICO, Role.ADMIN);
    }

    private Column columna(String nombre) throws Exception {
        return Usuario.class.getDeclaredField(nombre).getAnnotation(Column.class);
    }
}
