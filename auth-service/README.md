# auth-service — RecetaYa

Microservicio de **autenticación y autorización** de RecetaYa. Expone una API REST
para registrar e iniciar usuarios, y para administrar sus roles, todo protegido con
**JWT (HS256)** y **Spring Security** en modo stateless.

- **Stack:** Java 21 · Maven · Spring Boot 3.2.5 · Spring Security · Spring Data JPA · PostgreSQL
- **Documentación:** springdoc-openapi 2.3.0 (Swagger UI) + Actuator
- **Tests:** 24 (Spring Security Test + H2, no necesitan base de datos)

---

## 1. Descripción

El servicio resuelve tres problemas:

1. **Identidad:** registro y login con contraseña hasheada en BCrypt, y renovación de
   tokens con refresh token rotativo.
2. **Autorización:** cuatro roles (`USER`, `MEDICO`, `FARMACEUTICO`, `ADMIN`); el
   **primer usuario registrado queda como `ADMIN`** (bootstrap) y desde ahí solo un
   ADMIN puede listar, consultar, eliminar usuarios o cambiar roles.
3. **Administración:** CRUD de usuarios completo (`GET`, `PUT`, `DELETE`) pensado para
   que el resto de los microservicios validen el `role` que viene en el JWT.

Todo error responde con el mismo cuerpo (`ErrorResponse`):
`{timestamp, status, error, message, path, fields}`.

---

## 2. Requisitos

| Requisito | Versión mínima | Para qué sirve |
|---|---|---|
| JDK | 21 | compilar y correr (`java -version`) |
| Maven Wrapper | incluido (`mvnw`) | no requiere Maven instalado |
| Docker + Docker Compose | 2.x | base de datos y despliegue completo |
| Postman | cualquiera | probar los endpoints (o Swagger UI / curl) |

> No necesitas instalar PostgreSQL: la imagen `postgres:16-alpine` la levanta Docker.

---

## 3. Clonar el repositorio

```bash
git clone https://github.com/BenjaminSegovia/auth-service.git
cd auth-service
```

Estructura relevante:

```
auth-service/               ← raíz del repositorio
├── docker-compose.yml      ← servicios (bd + microservicio)
├── .env.example            ← plantilla de variables de entorno
├── .gitignore
└── auth-service/           ← código fuente del microservicio (este README)
    ├── pom.xml
    ├── mvnw / mvnw.cmd
    └── src/
```

> ⚠️ Siempre que el comando indique `docker compose`, ejecútalo desde la **raíz del
> repositorio**; cuando indique `./mvnw`, entra antes a `auth-service/`.

---

## 4. Base de datos

Desde la raíz del repositorio:

```bash
# opcional pero recomendado: copiar el archivo de variables de entorno
cp .env.example .env      # Windows PowerShell: Copy-Item .env.example .env

# levantar solo PostgreSQL
docker compose up -d auth-db
```

| Dato | Valor |
|---|---|
| Contenedor | `auth-db-v2` |
| Imagen | `postgres:16-alpine` |
| Base | `auth_db` |
| Puerto en el host | **5433** → 5432 del contenedor |
| Usuario / clave | `postgres` / `postgrespassword` (sobrescribir con `DB_USERNAME`/`DB_PASSWORD`) |
| Persistencia | volumen `auth_db_data` + healthcheck `pg_isready` |

```bash
docker compose ps              # debe verse "healthy"
docker compose down -v         # borrar la base (el primer registro vuelve a ser ADMIN)
```

---

## 5. Ejecutar el servicio

### Opción A — en local (desarrollo)

```bash
cd auth-service
```

**Bash / Git Bash (Linux, macOS, WSL):**

```bash
export DB_URL="jdbc:postgresql://localhost:5433/auth_db"
export DB_USERNAME=postgres
export DB_PASSWORD=postgrespassword
export JWT_SECRET="RecetaYaClaveSecretaMuySeguraParaJWT2026!"
./mvnw spring-boot:run
```

**PowerShell (Windows):**

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5433/auth_db"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="postgrespassword"
$env:JWT_SECRET="RecetaYaClaveSecretaMuySeguraParaJWT2026!"
.\mvnw.cmd spring-boot:run
```

Todos los valores tienen **defecto en `application.yaml`** (`${VARIABLE:valor_por_defecto}`),
así que `./mvnw spring-boot:run` también arranca solo si PostgreSQL está en `localhost:5432`.

### Opción B — con Docker (recomendada para la demo)

Desde la raíz del repositorio:

```bash
docker compose up -d --build
docker compose logs -f auth-service   # opcional: ver los logs
```

### Variables de entorno (`.env`)

| Variable | Ejemplo | Descripción |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/auth_db` | URL JDBC de PostgreSQL |
| `DB_USERNAME` | `postgres` | usuario de la BD |
| `DB_PASSWORD` | `postgrespassword` | contraseña de la BD |
| `JWT_SECRET` | 32+ caracteres | clave de firma HS256 (mínimo 32 caracteres) |
| `JWT_EXPIRATION_MS` | `900000` | vida del access token (15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | `604800000` | vida del refresh token (7 días) |

`.env` está en `.gitignore` y **nunca se sube al repositorio**; `.env.example` sí se commitea
y no contiene credenciales reales.

---

## 6. URLs de prueba

| URL | Local | Docker |
|---|---|---|
| API base | `http://localhost:8080` | `http://localhost:8081` |
| Swagger UI | <http://localhost:8080/swagger-ui/index.html> | <http://localhost:8081/swagger-ui/index.html> |
| OpenAPI JSON | <http://localhost:8080/v3/api-docs> | <http://localhost:8081/v3/api-docs> |
| Health | <http://localhost:8080/actuator/health> | <http://localhost:8081/actuator/health> |
| Info | <http://localhost:8080/actuator/info> | <http://localhost:8081/actuator/info> |

> `/swagger-ui.html` redirige a `/swagger-ui/index.html`.
> `/v3/api-docs/**`, `/swagger-ui/**` y `/actuator/health` están **abiertos sin token**.

---

## 7. Endpoints

| # | Método | Ruta | Acceso | Éxito | Descripción |
|---|---|---|---|---|---|
| 1 | POST | `/auth/register` | público | **201** | Crea usuario. El rol lo asigna el servidor: primer usuario = `ADMIN`, resto = `USER`. Contraseña 8–72 caracteres (BCrypt). |
| 2 | POST | `/auth/login` | público | **200** | Devuelve `token`, `refreshToken`, `username` y `role`. |
| 3 | POST | `/auth/refresh` | público | **200** | Rota el par de tokens (exige un refresh token). |
| 4 | POST | `/auth/logout` | JWT | **204** | Operación stateless: el cliente descarta sus tokens. |
| 5 | PUT | `/auth/password` | JWT | **204** | Cambia la contraseña del usuario autenticado (`currentPassword` + `newPassword`). |
| 6 | GET | `/auth/me` | JWT | **200** | Perfil del usuario autenticado: `id`, `username`, `nombreCompleto`, `role`. |
| 7 | PUT | `/auth/users/{username}/role` | ADMIN | **200** | Cambia el rol (USER, MEDICO, FARMACEUTICO, ADMIN). |
| 8 | GET | `/auth/users` | ADMIN | **200** | Lista todos los usuarios. |
| 9 | GET | `/auth/users/{username}` | ADMIN | **200** | Consulta un usuario por username. |
| 10 | DELETE | `/auth/users/{username}` | ADMIN | **204** | Elimina un usuario. `400` si un ADMIN intenta eliminarse a sí mismo. |

**Codigos de error comunes**

| Código | Cuándo ocurre |
|---|---|
| `400` | validación (`fields` por campo), password débil o auto-eliminación |
| `401` | sin token, token expirado, credenciales incorrectas |
| `403` | usuario autenticado sin rol `ADMIN` en una ruta restringida |
| `404` | username inexistente |
| `409` | username ya registrado |

Ejemplo de respuesta de error:

```json
{
  "timestamp": "2026-10-06T12:00:00.000+00:00",
  "status": 409,
  "error": "Conflict",
  "message": "El username 'benja' ya está en uso",
  "path": "/auth/register",
  "fields": null
}
```

---

## 8. Cómo probar la API con Postman

### 8.1 Importar la colección

Si ya tienes una colección exportada (`docs/postman/auth-service.postman_collection.json`):

1. Postman → **Import** → **Upload Files** → selecciona el archivo JSON.
2. Abre la colección y revisa las **variables**.

Si aún no existe el archivo, se crea desde Postman:

1. **New Collection** → name `auth-service`.
2. Agrega las peticiones de la tabla anterior (carpetas sugeridas: *Casos exitosos* y *Casos de error*).
3. **⋯ → Export** → guárdala en `docs/postman/auth-service.postman_collection.json` y haz commit.

### 8.2 Variables de la colección

| Variable | Valor sugerido | Nota |
|---|---|---|
| `baseUrl` | `http://localhost:8080` (local) o `http://localhost:8081` (Docker) | URL base |
| `token` | — | se llena con la respuesta de `login`/`register` |
| `refreshToken` | — | se llena con la respuesta de `register`/`login` |
| `username` | — | username del usuario de prueba |

Sugerencia de pruebas (`Tests` en cada respuesta) para auto-llenarlas:

```javascript
pm.test("200 OK", () => pm.response.to.have.status(200));
const r = pm.response.json();
pm.collectionVariables.set("token", r.token);
pm.collectionVariables.set("refreshToken", r.refreshToken);
pm.collectionVariables.set("username", r.username);
```

### 8.3 Secuencia recomendada

1. `POST /auth/register` → 201 (guarda tokens y username)
2. `POST /auth/login` → 200
3. `POST /auth/refresh` → 200
4. `GET /auth/me` → 200 (header `Authorization: Bearer {{token}}`)
5. `PUT /auth/password` → 204
6. `PUT /auth/users/{username}/role` → 200 (token de ADMIN)
7. `GET /auth/users` → 200 (token de ADMIN)
8. `DELETE /auth/users/{username}` → 204 (token de ADMIN)
9. Casos de error: 400 (password de 3 caracteres), 401 (sin header), 403 (USER en `/auth/users`),
   404 (`/auth/users/noExiste`), 409 (username repetido).

> 💡 **Bootstrap:** si la base de datos está vacía, el **primer registro queda como ADMIN**.
> Usa ese usuario para los pasos 6–8. Si vacías la base (`docker compose down -v`), vuelve a ocurrir.

---

## 9. Tests

```bash
cd auth-service
./mvnw clean test          # Windows: .\mvnw.cmd clean test
```

| Clase | Tests | Qué cubre |
|---|---|---|
| `AuthApiIntegrationTest` | 10 | registro, login, refresh, logout, me, password, rol, listar, eliminar y errores |
| `SwaggerActuatorIntegrationTest` | 5 | Swagger UI, `/v3/api-docs` y Actuator abiertos sin token |
| `UsuarioJpaMappingTest` | 5 | mapeo JPA de la entidad `Usuario` (columnas, enums, restricciones) |
| `JwtServiceTest` | 3 | generación/validación y expiración de los JWT |
| `AuthServiceApplicationTests` | 1 | que el contexto de Spring arranca |

**Total: 24 tests, 0 fallos.** Usan H2 (`src/test/resources/application.yaml`), por lo que
**no necesitan la base de datos corriendo**.

Empaquetado y ejecución del JAR:

```bash
./mvnw clean package
java -jar target/auth-service-0.0.1-SNAPSHOT.jar
```

---

## 10. Docker

Desde la raíz del repositorio:

```bash
docker compose up -d --build    # bd + microservicio
docker compose ps               # ambos "healthy"/"Up"
docker compose down             # detener (conserva la base)
docker compose down -v          # detener y borrar la base
```

| Servicio | Contenedor | Puerto (host → contenedor) |
|---|---|---|
| `auth-db` | `auth-db-v2` | 5433 → 5432 |
| `auth-service` | `auth-service-v2` | 8081 → 8080 |

El `Dockerfile` (`auth-service/Dockerfile`) es multi-stage: compila con
`maven:3.9.6-eclipse-temurin-21-alpine` (`mvn clean package -DskipTests`) y corre con
`eclipse-temurin:21-jre-alpine`. El servicio espera el healthcheck de la BD
(`condition: service_healthy`) antes de arrancar.
