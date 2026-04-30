# Shorten URL Observability Stack

This directory runs the local observability stack for the Spring Boot URL shortener.

## Architecture

```text
Spring Boot app
  |-- metrics: /actuator/prometheus
  |      Prometheus scrapes host.docker.internal:8080
  |
  |-- logs: logs/shorten-url-service.log
         Alloy tails logs/*.log and pushes to Loki

Grafana reads both Prometheus and Loki.
```

## Components

| Component | Role |
| --- | --- |
| Alloy | Collector. Reads application log files and forwards them to Loki. |
| Loki | Log storage and query engine. Query language is LogQL. |
| Prometheus | Metrics scraper and time-series database. Query language is PromQL. |
| Grafana | UI for dashboards, Explore, and alerts. |

## Start

From the repository root:

```powershell
docker compose -f monitoring/docker-compose.yml up -d
```

Then run the Spring Boot app locally on port `8080`.

Useful URLs:

| URL | Purpose |
| --- | --- |
| http://localhost:3000 | Grafana. Login: `admin` / `admin` |
| http://localhost:9090 | Prometheus |
| http://localhost:3100/ready | Loki readiness |
| http://localhost:12345 | Alloy debug UI |
| http://localhost:8080/actuator/prometheus | Spring Boot metrics |

## Stop

```powershell
docker compose -f monitoring/docker-compose.yml down
```

To delete stored Grafana, Prometheus, Loki, and Alloy data:

```powershell
docker compose -f monitoring/docker-compose.yml down -v
```

## Grafana Explore Queries

Loki logs:

```logql
{app="shorten-url-service"}
```

Warnings and errors:

```logql
{app="shorten-url-service", level=~"WARN|ERROR"}
```

Request logs for one endpoint:

```logql
{app="shorten-url-service"} |= "/shortenUrl"
```

Prometheus target health:

```promql
up{job="shorten-url-service"}
```

HTTP request rate:

```promql
sum(rate(http_server_requests_seconds_count{job="shorten-url-service"}[1m]))
```

5xx request rate:

```promql
sum(rate(http_server_requests_seconds_count{job="shorten-url-service", status=~"5.."}[1m]))
```

## Notes

- Keep labels low-cardinality. Good labels: `app`, `env`, `job`, `level`.
- Do not use `requestId`, URL values, user IDs, or short URL keys as Loki labels or Prometheus labels.
- Put high-cardinality values in the log body instead.
- The current compose setup assumes the app runs on the host machine at port `8080`.
- If the app is later containerized, change the Prometheus target from `host.docker.internal:8080` to the app service name, for example `app:8080`.
