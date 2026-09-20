# LANDrop 🚀

> **Zero-configuration, high-speed P2P file transfers and local network workspace sync.**

LANDrop is a lightweight, serverless peer-to-peer file-sharing engine and subnet collaboration suite crafted in Java SE. Engineered for high-throughput local transfers without relying on external servers, cloud relay infrastructure, or active internet connections, LANDrop brings seamless drop-zone sharing to local meshes.

---

## ✨ Key Capabilities

* **📡 Zero-Conf Subnet Discovery:** Automatic peer detection across local network interfaces using background multicast UDP broadcasts.
* **🔒 Encrypted Streaming Engine:** End-to-end payload security leveraging AES-128 stream wrapping for all local file transfers and session messages.
* **⚡ Resumable Chunking & Checkpoints:** Byte-offset stream tracking backed by an embedded SQLite database—recover interrupted transfers effortlessly.
* **🎛️ Bandwidth Throttling Controls:** Real-time transfer speed capping to preserve network headroom on crowded local routers.
* **👻 Stealth & Trust Matrix:** Toggle network visibility or mark verified peers to enable silent auto-acceptance for incoming transfers.
* **💬 Session Messaging & Radar:** Interactive radar-style UI for local device discovery paired with peer-to-peer chat logs.
* **📦 Automated Directory Zipping:** On-the-fly zip packaging and batch queue processing for complex folder structures.
* **🔔 Native System Tray Integration:** Cross-platform system tray support with desktop notifications and minimized background operations.

---

## 🛠️ Tech Stack & Dependencies

| Layer | Component |
| :--- | :--- |
| **Language & Runtime** | Java 17+ (Java SE / Swing) |
| **User Interface** | FlatLaf Dark Theme Engine |
| **Persistence** | SQLite JDBC (Checkpoints & Session Logs) |
| **Networking** | Java Socket API (Multicast UDP & Stream-wrapped TCP) |
| **Build Pipeline** | Apache Maven |

---

## 📁 Repository Layout

```text
LANDrop/
├── lib/                   # Pre-packaged local library JARs
├── src/com/landrop/
│   ├── db/                # SQLite connection pool & migration helpers
│   ├── model/             # Entities (PeerDevice, TransferMetadata)
│   ├── net/               # Core networking (TCP sockets, UDP discovery, Chat)
│   ├── ui/                # Swing GUI components (MainFrame, RadarPanel, TrayManager)
│   └── util/              # Utilities (Crypto, Hashing, Zip, AppConfig)
└── pom.xml                # Maven build definition & assembly manifest
```

---

## 🏗️ Build & Setup

### Prerequisites
- **Java Development Kit (JDK):** Version 17 or higher
- **Build Tool:** Apache Maven 3.8+

### Compilation & Packaging
Clone the repository and assemble the executable Fat JAR:

```bash
mvn clean package
```

The compiled binary will be placed inside the target output directory:
`target/LANDrop-1.0.0-jar-with-dependencies.jar`

---

## 🚀 Running LANDrop

Launch the application directly using the fat JAR bundle:

```bash
java -jar target/LANDrop-1.0.0-jar-with-dependencies.jar
```

Alternatively, specify the GUI entry point class explicitly from the classpath:

```bash
java -cp target/LANDrop-1.0.0-jar-with-dependencies.jar com.landrop.ui.MainFrame
```

---

## 📄 License

This project is open-source and released under the **MIT License**.