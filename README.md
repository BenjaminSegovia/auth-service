# RecetaYa

Monorepo de microservicios Spring Boot. Este documento funciona como **contexto vivo del proyecto**:
se actualiza cada vez que avanza algo (nuevos endpoints, bugs corregidos, infraestructura, etc.).

> Última actualización: 2026-10-06 — rama `develop` (`267b445`)
> Documentación completa del microservicio: [`auth-service/README.md`](auth-service/README.md)

---

## 1. Qué es

Repositorio `C:\RecetaYa` con microservicios Spring Boot (**Java 21**, **Maven**, **Spring Boot 3.2.5**):

| Servicio | Descripción | Estado |
|---|---|---|
| `auth-service/` | Autenticación y JWT | ✅ Funcionando, **24 tests en verde**, documentado |
| `receta-service/` | Segundo microservicio | **Sin trackear en git** (irá a su propio repo) |

**Archivos en la raíz:** `README.md`, `docker-compose.yml`, `.env.example`, `.gitignore`.

**Estado de git:**
- Rama activa `develop` → **43 commits**, sincronizada con `origin/develop`.
- Cada tarea de la rúbrica se hizo en su rama `feature/*` y se fusionó con **`git merge --no-ff`**.
- `main` recibe el merge final de `develop` con `--no-ff` (ítem **IE9**).
- Repo remoto: `https://github.com/BenjaminSegovia/auth-service.git` (un repo por microservicio).

---

## 2. Stack y dependencias (`auth-service/pom.xml`)

- `spring-boot-starter-web`, `data-jpa`, `security`, `validation`
- `postgresql` (runtime), `h2` (solo tests, hoy con `<scope>runtime</scope>`)
- `lombok`
- `jjwt 0.12.6` (api / impl / jackson) → `${jjwt.version}`
- `spring-boot-starter-test` + `spring-security-test`
- `spring-boot-starter-actuator` y `springdoc-openapi-starter-webmvc-ui` **2.3.0** → `${springdoc.version}`
  (2.5.x exige Boot 3.3+; con 3.2.5 se usa 2.3.0)
- `<properties>` con `java.version` (21), `jjwt.version` y `springdoc.version`, y comentarios de sección.

---

## 3. Estructura (`auth-service/src/main/java/cl/duoc/authservice/`)

- `config/` → `OpenApiConfig` (título/descripción/versionado del documento OpenAPI)
- `controller/` → `AuthController` (10 endpoints, **sin lógica de negocio**, con `@Tag`/`@Operation`/`@ApiResponse`)
- `service/` → `AuthService` (toda la lógica de negocio)
- `dto/` → `AuthResponse`, `LoginRequest`, `RegisterRequest`, `RefreshRequest`, `RoleUpdateRequest`,
  `UserResponse` (`id`, `username`, `nombreCompleto`, `role`), `ErrorResponse`,
  `ChangePasswordRequest` (usado por `PUT /auth/password`)
- `model/` → `Usuario` (@Entity con Javadoc y mapeo completo), `Role` (`USER`, `MEDICO`, `FARMACEUTICO`, `ADMIN`)
- `repository/` → `UsuarioRepository`
- `security/` → `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `UsuarioUserDetailsService`,
  `RestAuthenticationEntryPoint` (401 JSON), `RestAccessDeniedHandler` (403 JSON)
- `exception/` → `GlobalExceptionHandler` (12 `@ExceptionHandler`) + `UsernameAlreadyExistsException` (409),
  `InvalidCredentialsException` (401), `UsuarioNotFoundException` (404),
  `OperacionInvalidaException` (400)

---

## 4. API

| Método | Ruta | Acceso | Éxito | Descripción |
|---|---|---|---|---|
| POST | `/auth/register` | público | **201** | Crea usuario. **El rol lo asigna el servidor**: primer usuario = ADMIN (bootstrap), resto = USER. Password 8–72 chars con BCrypt. |
| POST | `/auth/login` | público | **200** | Devuelve access + refresh token, `username` y `role`. |
| POST | `/auth/refresh` | público | **200** | Rota el par de tokens (exige claim `type=refresh`). |
| POST | `/auth/logout` | JWT | **204** | Stateless: el cliente descarta sus tokens. |
| PUT | `/auth/password` | JWT | **204** | Cambia la contraseña del usuario autenticado. |
| GET | `/auth/me` | JWT | **200** | Perfil completo: `id`, `username`, `nombreCompleto`, `role`. |
| PUT | `/auth/users/{username}/role` | solo ADMIN | **200** | Cambia el rol de un usuario. |
| GET | `/auth/users` | solo ADMIN | **200** | Lista todos los usuarios. |
| GET | `/auth/users/{username}` | solo ADMIN | **200** | Consulta un usuario por username. |
| DELETE | `/auth/users/{username}` | solo ADMIN | **204** | Elimina un usuario (400 si un ADMIN se elimina a sí mismo). |

**Errores uniformes** (`ErrorResponse` `{timestamp, status, error, message, path, fields}`):
`400` validación / `fields` / auto-eliminación · `401` sin token o credenciales malas ·
`403` USER en ruta ADMIN · `404` username inexistente · `409` username duplicado.

**JWT:** HS256 con claims `role` y `type` (`access` \| `refresh`). Access 15 min, refresh 7 días.
El filtro solo acepta tokens `type=access` para llamar a la API.

**Seguridad:** sesión `STATELESS`, CSRF desactivado, sin CORS configurado.
Rutas abiertas: `POST /auth/register|login|refresh`, `/actuator/health|info`,
`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`; el resto exige ADMIN o JWT.

---

## 5. Configuración y ejecución

- `application.yaml` **parametrizado** con `${VARIABLE:default}`:
  `DB_URL` (default `localhost:5432/auth_db`), `DB_USERNAME`, `DB_PASSWORD`,
  `JWT_SECRET`, `JWT_EXPIRATION_MS=900000`, `JWT_REFRESH_EXPIRATION_MS=604800000`.
- `ddl-auto: update`, `open-in-view: false`, `show-sql: true`, **sin `server.port` → corre en 8080**.
- Actuator expone `health` e `info`.
- **Variables de entorno:** `.env.example` en la raíz (`.env` está en `.gitignore` y nunca se sube).
- Tests con H2 (`src/test/resources/application.yaml`): no necesitan base de datos corriendo.
- **Tests:** `./mvnw clean test` dentro de `auth-service/` → **24 tests, 0 fallos**
  (`AuthApiIntegrationTest` 10, `SwaggerActuatorIntegrationTest` 5, `UsuarioJpaMappingTest` 5,
  `JwtServiceTest` 3, contexto 1).
- **Empaquetado:** `./mvnw clean package` → `target/auth-service-0.0.1-SNAPSHOT.jar`;
  se verificó su arranque con `java -jar` (sin advertencias, health 200, register 201).
- **Docker:** `docker-compose.yml` en la raíz con
  - `auth-db`: `postgres:16-alpine`, puerto host **5433**, volumen persistente + healthcheck
    (`pg_isready -U $$POSTGRES_USER`).
  - `auth-service`: build desde `auth-service/Dockerfile` (multi-stage Maven + JRE 21),
    mapeo **8081 (host) → 8080 (contenedor)**, espera el healthcheck de la BD antes de arrancar;
    la URL de la BD usa el **nombre del servicio** (`auth-db`) dentro de la red de compose.
- Las variables usan la sintaxis de compose `${VAR:-valor}` (defecto si no existe o está vacía).

---

## 6. Pendientes / bugs conocidos

**Ya corregidos (por completitud):**
- ~~`UserResponse` con `id`/`nombreCompleto` en `null`~~ → completado en `AuthService.assignRole`.
- ~~`ChangePasswordRequest` sin endpoint~~ → existe `PUT /auth/password`.
- ~~Swagger/Actuator respondían 401~~ → `permitAll` en `SecurityConfig`.
- ~~`pom.xml` sin commitear y springdoc 2.5.x~~ → commiteado con **2.3.0**.
- ~~Sin `.gitignore`~~ → creado en la raíz.

**Pendientes:**
1. **Seguridad:** el `JWT_SECRET` por defecto está commiteado en `application.yaml` y en el compose;
   sin CORS, sin rate limiting en login, sin revocación de refresh tokens (logout stateless).
2. **Posible *race condition*** en el bootstrap del primer ADMIN (`count() == 0`).
3. **Validación de username:** sin `trim()` ni minúsculas; un username de más de 255 caracteres
   produce **500** (falta `@Size(max = 255)` en `RegisterRequest`).
4. **`h2` con `<scope>runtime</scope>`** → debería ser `test`.
5. **Infra:** sin Flyway/Liquibase (`ddl-auto: update`), sin `.dockerignore`, sin healthcheck del
   servicio en el compose, sin CI, imagen `auth-service-v2` no reconstruida con el código nuevo.
6. **`receta-service/`** sigue sin trackear → crear su **propio repositorio** (IE9: un repo por microservicio).
7. **Postman (IE3):** la colección exportada se quitó del repo; las peticiones se ejecutan directamente
   desde la aplicación. Si la pauta exige el archivo, exportarla a
   `docs/postman/auth-service.postman_collection.json`.
8. **Funcionalidad futura:** olvido de contraseña (requiere email) y blacklist/`jti` para logout real.

---

## 7. Convenciones del repo

- Commits **conventional commits en español**: `feat(auth): ...`, `fix(auth): ...`, `config(auth): ...`,
  `test(auth): ...`, `docs(auth): ...`, `chore(env): ...`, `chore(docker): ...`.
- Un **merge `--no-ff`** por tarea, con prefijo `merge(auth): ...`.
- Javadoc y comentarios en español en clases y métodos públicos.
- DTOs con Lombok `@Data` + `@Builder` / `@NoArgsConstructor` / `@AllArgsConstructor`.
- Mensajes de error en español hacia el usuario final.
- Cada feature se cierra completa: **DTO + controller + service + test**.
- Al agregar un campo a un DTO, el test debe verificar que viene poblado (evita nulls silenciosos).

---

## 8. Changelog breve

| Fecha | Commit | Qué se hizo |
|---|---|---|
| 2026-10-06 | `267b445` | `chore(docker)`: sintaxis `${VAR:-default}` y DNS por nombre de servicio (`auth-db`). |
| 2026-10-06 | `d2d1b47` | `docs(auth)`: **README del microservicio** (IE10) + corrección del puerto en `.env.example`. |
| 2026-10-06 | `502ecca` | `docs(auth)`: se quitó la colección de Postman exportada (IE3 se corre desde la app). |
| 2026-10-06 | `34a7a29` | `docs(auth)`: colección de Postman con casos de éxito y de error (IE3). |
| 2026-10-06 | `1c5c4cd` | `docs(auth)`: README de contexto del proyecto (este archivo). |
| 2026-10-06 | `f37bcbd` | `config(auth)`: arranque **sin advertencias** (`open-in-view: false`, sin dialecto explícito). |
| 2026-10-06 | `fc3f491` | `test(auth)` + `docs(auth)`: mapeo JPA de `Usuario` documentado y testeado (IE6). |
| 2026-10-06 | `16c66f4` | `docs(auth)` + `config(auth)`: documentación OpenAPI de los 10 endpoints (IE1). |
| 2026-10-06 | `fdc4c5c` | `chore(env)`: variables de entorno `${VAR:default}` + `.env.example` + `.gitignore` (IE4). |
| 2026-10-06 | `0b04f97` | `feat(auth)` + `test(auth)`: CRUD de usuarios, `/me`, `/password` y sus tests (IE5). |
| 2026-10-06 | `82fe79b` | `fix(auth)`: `UserResponse` completo en el cambio de rol (IE2). |
| 2026-10-06 | `dd720fd` | `feat(auth)` + `test(auth)`: Swagger y Actuator abiertos sin token (IE1/E8). |
| 2026-10-05 | `cbeafc3` | `test(auth)`: pruebas de integración del flujo de autenticación y de `JwtService`. |
| 2026-10-05 | `da2d685` | `feat(auth)`: rol asignado por el servidor, refresh token, logout y cambio de roles por ADMIN. |
| 2026-10-05 | `5a6e07f` | `feat(auth)`: manejo uniforme de errores y filtro de autenticación JWT. |
| 2026-10-05 | `9db62c1` | `config(auth)`: secret JWT de 32 bytes y conexión a PostgreSQL. |
| 2026-10-05 | `49834e7` | `feat(auth)`: `SecurityConfig`, `AuthController` y `GlobalExceptionHandler`. |
| 2026-10-05 | `78ddaa0` | `feat(auth)`: `AuthService` con excepciones personalizadas y DTOs. |
| 2026-10-05 | — | Paquetes `dto`, `repository`, `model` (`Usuario`, `Role`) y estructura inicial. |
| 2026-10-05 | `695a670` | `feat(init)`: inicializar microservicio `auth-service`. |
