![Build](https://github.com/open-report-engine/jrxml-renderer/workflows/CI/badge.svg)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

# JRXML Renderer

**HTTP microservice for rendering JasperReports JRXML templates to PDF and XLSX.**

A lightweight, stateless, open-source alternative to JasperReports Server — for teams that just need to render reports, not run a full BI platform.

---

## Why JRXML Renderer?

| Feature | JRXML Renderer | JasperReports Server |
|---|---|---|
| **Architecture** | Single JAR, embedded HTTP server | Tomcat + Spring + Teiid + Mondrian |
| **JRXML format** | Jackson format (Studio 7.x native) | Digester only (Legacy 6.x) |
| **Dependencies** | Minimal: JasperReports Lib + Undertow | 300+ JARs, Teiid, JBoss, proprietary |
| **Image size** | ~180 MB | ~800 MB |
| **Startup time** | < 1 second | 30-60 seconds |
| **License** | Apache 2.0 | AGPL / Commercial |
| **Grafana/Borders** | Full support (JasperReports 7.0.6) | Limited (JasperReports 6.20.3) |

### Key benefits

- **✅ Studio 7.x compatibility** — works with JRXML saved from Jaspersoft Studio 7.0.6
- **✅ Borders in tables** — full support via `<style>` and `<box>` (not available in JasperReports 6.x)
- **✅ Cyrillic & Unicode** — DejaVu fonts with Identity-H encoding
- **✅ ClickHouse & PostgreSQL** — built-in JDBC support
- **✅ Stateless** — each request is independent, no session, no repository
- **✅ Auth** — optional Bearer token authentication

---

## API

### POST /api/render

```
POST /api/render
Content-Type: application/json
Authorization: Bearer <token>    # optional
```

#### Request body

```json
{
  "jrxml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>...",
  "format": "pdf",
  "data_source": {
    "type": "sql",
    "url": "jdbc:clickhouse://host:8123/db?compress=0",
    "user": "default",
    "password": "",
    "query": "SELECT ..."
  },
  "parameters": {
    "period": "2026-Q1"
  }
}
```

#### Response

```
200 OK
Content-Type: application/pdf
Content-Disposition: attachment; filename="report.pdf"

<PDF binary data>
```

#### data_source types

| Type | Description | Required fields |
|---|---|---|
| `sql` | Execute SQL query against JDBC datasource | `url`, `user`, `password`, `query` |
| `none` | Use JRXML's embedded query or empty data | — (no data_source block needed) |

#### Formats

| Format | Content-Type | Exporter |
|---|---|---|
| `pdf` | `application/pdf` | JasperReports PDF (iText/OpenPDF) |
| `xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | Apache POI |

---

## Quick start

### Docker

```bash
docker run -p 8080:8080 ghcr.io/open-report-engine/jrxml-renderer:latest
```

### Test

```bash
curl -X POST http://localhost:8080/api/render \
  -H "Content-Type: application/json" \
  -d '{
    "jrxml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?><jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\" name=\"Hello\" pageWidth=\"595\" pageHeight=\"842\"><detail><band height=\"20\"><textField><reportElement x=\"0\" y=\"0\" width=\"300\" height=\"20\"/><textElement><font fontName=\"DejaVu Sans\" size=\"12\"/></textElement><textFieldExpression><![CDATA[\"Hello, World!\"]]></textFieldExpression></textField></band></detail></jasperReport>",
    "format": "pdf"
  }' \
  -o hello.pdf
```

---

## Configuration

| Environment variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | HTTP server port |
| `AUTH_TOKEN` | — | Bearer token for auth (empty = disabled) |

---

## Build from source

```bash
git clone https://github.com/open-report-engine/jrxml-renderer.git
cd jrxml-renderer
mvn clean package -DskipTests
docker build -t jrxml-renderer .
docker run -p 8080:8080 jrxml-renderer
```

### Prerequisites

- JDK 21+
- Maven 3.9+
- Docker (optional)

---

## Dependencies

| Library | Version | License | Purpose |
|---|---|---|---|
| JasperReports | 7.0.6 | [LGPL 3.0](https://www.gnu.org/licenses/lgpl-3.0.html) | Core reporting engine |
| JasperReports PDF | 7.0.6 | LGPL 3.0 | PDF export |
| JasperReports Excel POI | 7.0.6 | LGPL 3.0 | XLS/XLSX export |
| JasperReports Fonts | 7.0.6 | LGPL 3.0 | DejaVu fonts |
| LJP (Legacy JRXML Parser) | 7.0.6 | [EPL 2.0](https://www.eclipse.org/legal/epl-2.0/) | Digester-based JRXML parser for legacy compatibility |
| Undertow | 2.3 | Apache 2.0 | Embedded HTTP server |
| ClickHouse JDBC | 0.5.0 | Apache 2.0 | ClickHouse connectivity |
| PostgreSQL JDBC | 42.7 | BSD 2-Clause | PostgreSQL connectivity |
| HikariCP | 6.2 | Apache 2.0 | Connection pooling |

---

## Architecture

```
┌──────────────┐      POST /api/render       ┌──────────────────┐
│   curl / app  │  ─────────────────────────→ │  jrxml-renderer  │
│              │  {                          │                  │
│              │    "jrxml": "...",          │  JacksonParser   │
│              │    "format": "pdf",         │    ↓             │
│              │    "data_source": { ... }   │  CompileReport   │
│              │    "parameters": { ... }    │    ↓             │
│              │  }                          │  FillReport      │
│              │                             │    ↓             │
│              │  ←───────────────────────── │  Export → PDF    │
│              │  200 OK, application/pdf    │       or XLSX    │
└──────────────┘                             └──────────────────┘
```

### Loader selection

When a request comes in, `jrxml-renderer` tries two JRXML loaders in order:

1. **JacksonReportLoader** — for JasperReports 7.x Jackson format (Studio 7.x)
2. **LegacyXmlLoader** (LJP) — for legacy Digester format (Studio 6.x)

The first loader that accepts the format is used. This ensures compatibility with both old and new templates.

---

## Project structure

```
jrxml-renderer/
├── src/main/java/io/github/openreportengine/
│   ├── App.java                    # Main entry point
│   ├── api/RenderHandler.java      # HTTP handler for POST /api/render
│   ├── render/
│   │   ├── RenderRequest.java      # Request DTO + parser
│   │   └── RenderService.java      # Core rendering logic
│   └── datasource/
│       └── DataSourceFactory.java  # JDBC datasource pool
├── Dockerfile                      # Alpine + JRE 21 image
└── pom.xml                         # Maven project
```

---

## License

**JRXML Renderer** — Apache 2.0

**LJP (Legacy JRXML Parser)** — EPL 2.0 (fork of JasperReports legacy module with license check removed)

JasperReports Library and its companion modules are licensed under LGPL 3.0.
