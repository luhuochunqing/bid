# Technology Stack

## Frontend
- **Framework:** Vue 3 (Composition API)
- **Build Tool:** Vite 5
- **UI Library:** Element Plus
- **State Management:** Pinia
- **Routing:** Vue Router 4
- **HTTP Client:** Axios
- **Data Visualization:** ECharts
- **Styling:** Sass / CSS Variables
- **Testing:** Vitest, Playwright

## Backend
- **Language:** Java 21
- **Framework:** Spring Boot 3.2
- **Data Visibility:** AOP-based Data Scope filtering (Self/Dept/All) + Collaboration ACL + CRM Mirroring
- **Security:** Spring Security + JWT
- **Data Access:** Spring Data JPA
- **Database:** MySQL 8.0
- **Caching:** Redis
- **Migrations:** Flyway

## Architecture
- **Type:** Hybrid Monorepo (Frontend in root, Java Backend in `backend/`, Python Sidecar in `document-converter-sidecar/`)
- **Sidecar:** Python 3.11+, FastAPI, MarkItDown (for document-to-markdown conversion)