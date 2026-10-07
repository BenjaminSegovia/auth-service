# RecetaYa

Proyecto académico de **microservicios** para la gestión de recetas médicas y despacho de
medicamentos. Cada microservicio vive en **su propio repositorio de GitHub**; este repo
(`auth-service`) contiene el microservicio de autenticación y los archivos compartidos del
proyecto (documentación general, Docker Compose y variables de entorno).

> Documentación completa de este microservicio: [`auth-service/README.md`](auth-service/README.md)

---

## 1. Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | **Java 21** |
| Build | **Maven** (wrapper `mvnw` incluido) |
| Framework | **Spring Boot** (Web, Data JPA, Security, Validation, Actuator) |
| Persistencia | **PostgreSQL 16** (`ddl-auto: update`, `open-in-view: false`) |
| Seguridad | **JWT HS256** con access (15 min) y refresh token (7 días) |
| API docs | **springdoc-openapi** (Swagger UI) |
| Tests | JUnit 5 + Spring Security Test + **H2** (no necesitan base de datos) |
| Infraestructura | **Docker** / **docker-compose** con red y volúmenes persistentes |

---

## 2. Servicios (un repositorio por microservicio)

| Servicio | Responsabilidad | Repositorio | Estado |
|---|---|---|---|
| `auth-service` | Registro, login, roles y administración de usuarios (JWT) | [BenjaminSegovia/auth-service](https://github.com/BenjaminSegovia/auth-service) | ✅ Completo: 10 endpoints, **24 tests en verde**, Dockerfile y README propio |
| `receta-service` | Gestión de recetas médicas | [BenjaminSegovia/receta-service](https://github.com/BenjaminSegovia/receta-service) | 🔨 Estructura base + datasource con `${VAR:default}` y tests sobre H2 |
| `inventory-service` | Inventario de medicamentos | [BenjaminSegovia/inventory-service](https://github.com/BenjaminSegovia/inventory-service) | 🔨 Estructura base + datasource con `${VAR:default}` y tests sobre H2 |
| `dispensing-service` | Despacho/dispensación de medicamentos | [BenjaminSegovia/dispensing-service](https://github.com/BenjaminSegovia/dispensing-service) | 🔨 Estructura base + datasource con `${VAR:default}` y tests sobre H2 |
| `notification-service` | Notificaciones del sistema | [BenjaminSegovia/notification-service](https://github.com/BenjaminSegovia/notification-service) | 🔨 Estructura base + datasource con `${VAR:default}` y tests sobre H2 |

Todos comparten el mismo patrón: paquete `cl.duoc.<servicio>`, `application.yaml` con
variables `${VAR:valor_por_defecto}` y Actuator exponiendo `health` e `info`.

---

## 3. Estructura de este repositorio

```
auth-service/              ← nombre del repo en GitHub
├── README.md              # este archivo (resumen general del proyecto)
├── docker-compose.yml     # red, volúmenes y servicios (auth-db + auth-service)
├── .env.example           # plantilla de variables de entorno
├── .gitignore
└── auth-service/          # el microservicio (ver su README.md)
```

---

## 4. Requisitos y ejecución

- **Docker Desktop** con el demonio en marcha (para levantar BD + servicio con compose).
- **Java 21** para correr en local con Maven.

```bash
# 1. Variables de entorno (el .env real nunca se commitea)
cp .env.example .env

# 2. Levantar base de datos + auth-service
docker compose up --build

# 3. Servicio disponible en http://localhost:8081
#    Swagger UI:  http://localhost:8081/swagger-ui.html
#    Health:      http://localhost:8081/actuator/health
```

Para correr el servicio en local sin Docker:

```bash
cd auth-service
./mvnw spring-boot:run     # mvnw.cmd spring-boot:run en Windows PowerShell
```

**Tests** (usan H2, no requieren PostgreSQL):

```bash
./mvnw clean test
```

---

## 5. Configuración

- Cada servicio lee su configuración de variables de entorno con valor por defecto, por eso
  corre en local sin tocar archivos:
  - `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` → base de datos del servicio.
  - `JWT_SECRET` (mínimo 32 caracteres), `JWT_EXPIRATION_MS`, `JWT_REFRESH_EXPIRATION_MS`
    → solo `auth-service`.
- `docker-compose.yml` define la red `recetaya-net`, volúmenes para cada base de datos y
  healthchecks; dentro de la red los servicios se resuelven **por nombre**
  (`jdbc:postgresql://auth-db:5432/auth_db`).
- `.env` está en `.gitignore`; `.env.example` documenta cada variable.

---

## 6. Convenciones del proyecto

- **Un repositorio por microservicio** (IE9): cada servicio se clona por separado y se
  documenta con su propio `README.md`.
- **Commits convencionales en español:** `feat(auth): ...`, `fix(auth): ...`,
  `test(auth): ...`, `docs(auth): ...`, `chore(env): ...`.
- Cada tarea en su rama `feature/*` y se fusiona a `develop` con **`git merge --no-ff`**;
  `main` recibe el merge final.
- Javadoc y mensajes de error en español; DTOs con Lombok (`@Data`, `@Builder`).
- Cada feature se cierra completa: **DTO + controller + service + test**.

---

## 7. Estado actual y pendientes

**Hecho**

- `auth-service` funcional: JWT, roles, CRUD de usuarios, Swagger y Actuator abiertos,
  errores uniformes (`ErrorResponse`), 24 tests en verde y despliegue con Docker Compose.
- Los cuatro servicios restantes tienen su repo propio con datasource parametrizado
  (`${VAR:default}`), H2 para tests y Actuator.

**Problema detectado y corregido (2026-10-07)**

> Los commits `d30cfaa`, `3bc46d2`, `4d6b652` y `fd072ff` incorporaron `receta`,
> `inventory`, `dispensing` y `notification` a **este** repositorio, borrando su `.git`
> anidado, pese a que **cada microservicio tiene su propio repo en GitHub**. Eso dejaba
> este repo (llamado `auth-service`) con cinco servicios y con la portada de GitHub
> mostrando un README que no correspondía.
>
> - Se **eliminaron esos 4 commits de la historia** de `develop` (reescritura + force push).
> - Respaldo local del histórico: rama **`backup/servicios-20261007`**.
> - Los fixes que solo vivían ahí (datasource + H2 + actuator) **se subieron a cada repo
>   propio** antes de borrarlos:
>   `receta` → `906445f` · `inventory` → `4684c1a` · `dispensing` → `bbab28d` ·
>   `notification` → `1ff121f`.

**Pendiente**

1. Desarrollar la lógica de `receta`, `inventory`, `dispensing` y `notification`.
2. Definir un puerto por servicio y completar el `docker-compose.yml` compartido.
3. Seguridad: secretos solo por variables de entorno, CORS y rate limiting en `auth-service`.
4. Migraciones con Flyway/Liquibase en lugar de `ddl-auto: update` y CI con los tests.
