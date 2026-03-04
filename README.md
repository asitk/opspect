# 🔭 Opspect

Orchestration for Cloud Apps: Modern application architectures are complex
with many interconnected systems. User requests and the underlying business
workflows have multiple touchpoints across the ecosystem. It is critical to
be able to view performance of the system and react to business flow
disruption due to anomalies in realtime. Opspect takes a new approach to
instrument systems and application flows using AI. Opspect allows you to
setup custom hooks for flows and then leverage ML/AI to prevent downtime and
take custom actions.

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
opspect/
├── apps/                       # Application components
│   ├── demo-rest-jersey-spring/
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
├── main.go                     # Entry point
└── main                        # Compiled binary
```

## 🔧 Work in Progress

- [ ] Installation
- [ ] Quickstart Guide
- [ ] Maintenance

## 📜 License

**GNU Affero General Public License v3.0 (AGPL-3.0)** - All source code and
components
