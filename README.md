# 🔭 Opspect

Deep Inspection System for Cloud Apps

Application architectures are growing in complexity. Even basic apps have many interconnected micro-services. They use a wide variety of closed and open software stacks with multiple points of failure. Interconnections results in ripple effects through the application topology based on external fluctuations. Instances and services frequently come up and go down dynamically based on load. Data is flowing in and out of the application to the internet and intra node interactions also keep changing

This trend is expected to grow in the future. This has made it hard to track health, prevent downtime and debug issues using current tools and has business performance and security implications as well. Nuvidata builds specialized products to address these issues

Most products in the monitoring space focus on instance specific screens. They are not intelligent, context aware and lack debug capabilities. Also human beings are not able to perform correlations manually beyond a few screens/variables. Usually these tools are simply the ability to create visualizations from data provided by customers themselves and do not have the required depth for automated causality analysis

Opspect brings Intelligent Root Cause Isolation that is built on Application Topology Awareness. The product combines service specific metrics and context along with big data and machine learning. Isolates anomalies using proprietary algorithms, suppresses noise, predicts downtime/failure events and reveals important attributes about the application stack

## 🚀  Key Characterics

- No need to setup specific montoring screens and/or alerts
- Anomaly Detection & Correlation (App Server - MySQL)
- UI: App Topology Health
- UI: Time series graph and heatmap, stats charts
- Monitor the health of a service, Flag potential issues/downtime
- Root Cause Analysis for specific service clusters
- Quick Deployment, No code level instrumentation required

## ✨ Features

- 🔍 **Adaptive Error Detection and Root Cause Analysis**
- 📈 **Trends**
- 📊 **Predictive Analytics**
  - Mean time to failure 
  - Performance bottlenecks based on Seasonal Variations
  - Errors by Severity 
- ⚡ **Performance Analysis**
  - Web Server (Apache/NgInx)
  - App Server (Java and .Net)
    - Top time taking methods
    - Memory leaks and GC health
  - Database (SQL)
    - Top Slow Queries 
- 💰 **Capacity Planning and Cost Projection**  
- ☁️ **Cloud Debugger**
  - Sets capture points based on scenarios
  - Gets thread, method and stack level info from application topology using a JIT engine
  - Search and debug results using intelligent tooling

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

**GNU Affero General Public License v3.0 (AGPL-3.0)** - All source code and components
