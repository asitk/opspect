# 🌀 Opscog

Orchestration for Cloud Apps: Modern application architectures are complex
with many interconnected components. User requests and underlying business
workflows have multiple touchpoints across the app topology. It is critical
to be able to view performance of the system as a whole and react to
business flow disruption in realtime. Opscog takes a new approach towards
instrumentation of systems and flows using AI. Opscog allows you to setup
hooks and then leverage ML/AI to prevent downtime and take custom actions.

## 🚀 Key Characteristics

- No need to setup specific screens or alerts for observability
- Anomaly Detection & Correlation
- Monitor service health, Flag potential issues/downtime
- Quick Deployment, No code level instrumentation required
- Agentic framework for custom actions (WIP)

## ✨ Features

- 🔍 **Adaptive Error Detection and Root Cause Analysis**
- 📈 **Trends**
- 📊 **Predictive Analytics**
  - Mean time to failure
  - Performance bottlenecks based on Seasonal Variations
  - Errors by Severity
- ⚡ **Systemic Performance Analysis**
  - Web Servers (Apache/NgInx)
  - App Servers (Java and .Net)
    - Top time taking methods
    - Memory leaks and GC health
  - Databases (SQL)
    - Top Slow Queries

## 🏗️ Project Structure

```
Opscog/
├── apps/                       # Application components
│   ├── simulator/
│   └── statserver/
├── signals/                    # Core signal processing
│   ├── inputs/
│   │   ├── net/                # Network packet capture
│   │   └── plugins/            # Input plugins (system, mysql, nginx, apache, docker, etc.)
│   └── outputs/                # Output plugins (kafka, file, etc.)
├── config/                     # Configuration management
├── models/                     # Data models
│   ├── client/                 # Client implementations
│   ├── kafka/                  # Kafka models
│   └── opentsdb/               # OpenTSDB models
├── visual/                     # Web UI components
├── util/                       # Utility packages (discovery, escape, test)
├── deploy/                     # Deployment scripts
├── docs/                       # Documentation
├── logstash/                   # Logstash configuration
├── main.go                     # Entry point
├── Makefile                    # Build automation
├── go.mod                      # Go module dependencies
└── go.sum                      # Go module checksums
```

## 🔧 Work in Progress

- [ ] Installation
- [ ] Quickstart Guide
- [ ] Maintenance

## 📜 License

**GNU Affero General Public License v3.0 (AGPL-3.0)** - All source code and
components
