an # RecetaYa

Monorepo de microservicios Spring Boot. Este documento funciona como **contexto vivo del proyecto**:
se actualiza cada vez que avanza algo (nuevos endpoints, bugs corregidos, infraestructura, etc.).

> Última actualización: 2026-10-06 — commit `58e0938` (rama `develop`)

---

## 1. Qué es

Repositorio `C:\RecetaYa` con microservicios Spring Boot (**Java 21**, **Maven**, **Spring Boot 3.2.5**):

| Servicio | Descripción | Estado |
|---|---|---|
| `auth-service/` | Autenticación y JWT | Funcionando, con tests |
| `receta-service/` | Segundo microservicio | **Sin trackear en git**, sin integración con auth |

**Estado de git:** última vista `58e0938 chore(docker): agregar Dockerfile y docker-compose para auth-service`.
Sin commitear: `auth-service/pom.xml` (modificado) y `receta-service/` (sin trackear).

---

## 2. Stack y dependencias (`auth-service/pom.xml`)

- `spring-boot-starter-web`, `data-jpa`, `security`, `validation`
- `postgresql` (runtime), `h2` (solo tests)
- `lombok`
- `jjwt 0.12.6` (api / impl / jackson)
- `spring-boot-starter-test` + `spring-security-test`
- `spring-boot-starter-actuator` y `springdoc-openapi-starter-webmvc-ui 2.5.0` *(recién agregados, sin commitear)*

---

## 3. Estructura (`auth-service/src/main/java/cl/duoc/authservice/`)

- `controller/` → `AuthController`
- `service/` → `AuthService`
- `dto/` → `AuthResponse`, `LoginRequest`, `RegisterRequest`, `RefreshRequest`, `RoleUpdateRequest`,
  `UserResponse`, `ErrorResponse`, `ChangePasswordRequest` *(huérfano, sin endpoint)*
- `model/` → `Usuario` (@Entity), `Role` (`USER`, `MEDICO`, `FARMACEUTICO`, `ADMIN`)
- `repository/` → `UsuarioRepository`
- `security/` → `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `UsuarioUserDetailsService`,
  `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`
- `exception/` → `GlobalExceptionHandler` + `UsernameAlreadyExistsException`, `InvalidCredentialsException`,
  `UsuarioNotFoundException`

---

## 4. API

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/auth/register` | público | Crea usuario. **El rol lo asigna el servidor**: primer usuario = ADMIN (bootstrap), resto = USER. Password 8–72 chars con BCrypt. |
| POST | `/auth/login` | público | Devuelve access + refresh token. |
| POST | `/auth/refresh` | público | Rota el par de tokens (exige claim `type=refresh`). |
| POST | `/auth/logout` | JWT | Stateless: 204, el cliente descarta sus tokens. |
| PUT | `/auth/users/{username}/role` | solo ADMIN | Cambia el rol de un usuario. |

**JWT:** HS256 con claims `role` y `type` (`access` \| `refresh`). Access 15 min, refresh 7 días.
El filtro solo acepta tokens `type=access` para llamar a la API.

**Seguridad:** sesión `STATELESS`, CSRF desactivado, sin CORS configurado.
**Errores:** `GlobalExceptionHandler` responde siempre `ErrorResponse`
`{timestamp, status, error, message, path, fields}`.

---

## 5. Configuración y ejecución

- `application.yaml`: PostgreSQL `localhost:5432/auth_db` (postgres/postgrespassword),
  `ddl-auto: update`, `jwt.secret` hardcodeado, **sin `server.port` → corre en 8080**.
- Tests con H2 (`src/test/resources/application.yaml`): no necesitan base de datos corriendo.
- **Tests:** `./mvnw test` dentro de `auth-service/` → **10 tests, 0 fallos**
  (`AuthApiIntegrationTest` 6, `JwtServiceTest` 3, contexto 1).
- **Docker:** `docker-compose.yml` en la raíz con
  - `auth-db`: `postgres:16-alpine`, puerto host **5433**, volumen persistente + healthcheck.
  - `auth-service`: build desde `auth-service/Dockerfile` (multi-stage Maven + JRE 21),
    mapeo **8081 (host) → 8080 (contenedor)**, espera el healthcheck de la BD antes de arrancar.

---

## 6. Pendientes / bugs conocidos

1. **BUG:** `UserResponse` declara `id` y `nombreCompleto`, pero `AuthService.assignRole()` solo llena
   `username` y `role` → esos campos llegan `null`. Corregir el builder (o quitarlos del DTO).
2. **INCONCLUSO:** `ChangePasswordRequest` existe pero no tiene endpoint ni servicio →
   no se puede cambiar la contraseña. Falta `PUT /auth/change-password` con validación
   (actual correcta + nueva distinta de la actual) y su test.
3. **Actuator y Swagger responden 401:** `SecurityConfig` usa `.anyRequest().authenticated()`.
   Falta `permitAll` para `/actuator/health`, `/actuator/info`, `/swagger-ui/**`,
   `/swagger-ui.html`, `/v3/api-docs/**`.
4. `pom.xml` (actuator + springdoc) modificado y **sin commitear**. springdoc 2.5.x apunta a
   Boot 3.3+; con 3.2.5 compila y arranca bien, si falla bajar a 2.3.x.
5. **Seguridad/config:** secret del JWT y password de BD hardcodeados (pasar a variables de entorno),
   sin CORS, sin Flyway/Liquibase (`ddl-auto: update`), sin rate limiting en login.
6. **Infra:** sin `.dockerignore`, sin healthcheck del servicio en el compose, sin CI
   (`.github` solo tiene hooks de `modernize`), sin `.gitignore` de proyecto.
7. **Funcionalidad futura:** `GET /auth/me`, listado/eliminación de usuarios para ADMIN,
   olvido de contraseña (requiere email), revocación de tokens (blacklist/`jti`) para logout real.

---

## 7. Convenciones del repo

- Commits **conventional commits en español**: `feat(auth): ...`, `config(auth): ...`,
  `test(auth): ...`, `chore(docker): ...`.
- Javadoc y comentarios en español en clases y métodos públicos.
- DTOs con Lombok `@Data` + `@Builder` / `@NoArgsConstructor` / `@AllArgsConstructor`.
- Mensajes de error en español hacia el usuario final.
- Cada feature se cierra completa: **DTO + controller + service + test**.
- Al agregar un campo a un DTO, el test debe verificar que viene poblado (evita nulls silenciosos).

---

## 8. Changelog breve

| Fecha | Commit | Qué se hizo |
|---|---|---|
| 2026-10-06 | `58e0938` | `chore(docker)`: Dockerfile multi-stage + docker-compose (Postgres 16, red, volúmenes). |
| 2026-10-06 | — | Dependencias nuevas: actuator y springdoc (sin commitear). |
| 2026-10-05 | `cbeafc3` | `test(auth)`: pruebas de integración del flujo de autenticación y de `JwtService`. |
| 2026-10-05 | `da2d685` | `feat(auth)`: rol asignado por el servidor, refresh token, logout y cambio de roles por ADMIN. |
| 2026-10-05 | `5a6e07f` | `feat(auth)`: manejo uniforme de errores y filtro de autenticación JWT. |
| 2026-10-05 | `9db62c1` | `config(auth)`: secret JWT de 32 bytes y conexión a PostgreSQL. |
| 2026-10-05 | `49834e7` | `feat(auth)`: `SecurityConfig`, `AuthController` y `GlobalExceptionHandler`. |
| 2026-10-05 | `78ddaa0` | `feat(auth)`: `AuthService` con excepciones personalizadas y DTOs. |
| 2026-10-05 | `0b6f1d6` | `feat(auth)`: paquete `dto`. |
| 2026-10-05 | `363a8f5` | `feat(auth)`: paquete `repository`. |
| 2026-10-05 | `f3d21ff` | `feat`: enum `Role` y modelo `Usuario`. |
| 2026-10-05 | `d18c9fa` | `feat`: configuración inicial de `auth-service` y modelos base. |
| 2026-10-05 | `270c28f` | `feat(auth)`: estructura inicial para autenticación JWT. |
| 2026-10-05 | `695a670` | `feat(init)`: inicializar microservicio `auth-service`. |
