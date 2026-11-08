# KEC (R2024) Student Academic & Continuous Evaluation System
## Architecture Verification & Cloud Deployment Guide (OCI Distributed Environment)

This document details the complete deployment architecture, network configurations, and execution verification procedures for **Project Assignment Set 1**.

---

## 1. System & Team Architecture Overview

The system is deployed across two independent Oracle Cloud Infrastructure (OCI) Linux Virtual Machines (VMs) to satisfy the distributed enterprise architecture requirement:

```
+-------------------------------------------------------------+          +-------------------------------------------------------------+
|               STUDENT 1: APPLICATION HOST                   |          |                 STUDENT 2: DATABASE HOST                    |
|                (OCI Linux VM Instance 1)                    |          |                 (OCI Linux VM Instance 2)                   |
|                                                             |          |                                                             |
|  - Runtime: Java 21 LTS                                     |          |  - DBMS: MySQL 8.0+ / MariaDB / PostgreSQL                  |
|  - Multithreading: Java ExecutorService Fixed Pool          |          |  - Database Schema: kec_schema.sql                          |
|  - File I/O: BufferedReader & BufferedWriter                |          |  - Listening Port: 3306 TCP (Remote Ingress)                |
|  - Modules: MainApp, BatchResultWorker, FileProcessor, DAO  |          |  - Dedicated User: kec_user                                 |
|                                                             |          |                                                             |
|              [Public IP: VM_APP_PUBLIC_IP]                  |          |              [Public IP: VM_DB_PUBLIC_IP]                   |
+------------------------------+------------------------------+          +------------------------------+------------------------------+
                               |                                                                        ^
                               |                                                                        |
                               +-------------------- JDBC Connection (Port 3306) -----------------------+
                                                     Security List Ingress & Firewall
```

---

## 2. Student 2 (Database Host) Setup Instructions

### Step 2.1: Install & Configure MySQL on OCI VM
Connect via SSH to Student 2's VM:
```bash
# Ubuntu / Debian
sudo apt update && sudo apt install -y mysql-server

# Oracle Linux 8/9 / RHEL
sudo dnf install -y mysql-server
sudo systemctl enable --now mysqld
```

### Step 2.2: Configure MySQL to Accept Remote JDBC Connections
By default, MySQL only listens on `127.0.0.1`. Update the configuration to bind to all interfaces:
Edit `/etc/mysql/mysql.conf.d/mysqld.cnf` (Ubuntu) or `/etc/my.cnf.d/mysql-server.cnf` (Oracle Linux):
```ini
[mysqld]
bind-address = 0.0.0.0
```
Restart MySQL:
```bash
sudo systemctl restart mysql   # or sudo systemctl restart mysqld
```

### Step 2.3: Execute `kec_schema.sql` and Create Remote User
Run the MySQL console:
```bash
sudo mysql -u root -p
```
Execute the following SQL commands:
```sql
-- 1. Run the project schema script
SOURCE kec_schema.sql;

-- 2. Create remote user restricted to Student 1's Public IP (or '%' for any IP)
CREATE USER 'kec_user'@'%' IDENTIFIED BY 'KecPassword2024!';
GRANT ALL PRIVILEGES ON kec_eval_db.* TO 'kec_user'@'%';
FLUSH PRIVILEGES;

-- 3. Verify user privileges
SELECT user, host FROM mysql.user WHERE user = 'kec_user';
```

### Step 2.4: Configure OS Firewall Rules (Student 2 VM)
#### For Oracle Linux / RHEL (`firewalld`):
```bash
# Allow Port 3306 specifically from Student 1's VM Public IP
sudo firewall-cmd --permanent --add-rich-rule='rule family="ipv4" source address="<STUDENT_1_VM_PUBLIC_IP>" port port="3306" protocol="tcp" accept'
sudo firewall-cmd --reload
sudo firewall-cmd --list-all
```
#### For Ubuntu (`ufw`):
```bash
sudo ufw allow from <STUDENT_1_VM_PUBLIC_IP> to any port 3306 proto tcp
sudo ufw status verbose
```
#### Or raw `iptables`:
```bash
sudo iptables -I INPUT 6 -p tcp -s <STUDENT_1_VM_PUBLIC_IP> --dport 3306 -m state --state NEW,ESTABLISHED -j ACCEPT
sudo netfilter-persistent save
```

### Step 2.5: Configure OCI Console Security List Ingress Rules
1. Log in to the [Oracle Cloud Console](https://cloud.oracle.com/).
2. Navigate to **Networking** > **Virtual Cloud Networks (VCN)**.
3. Click your VCN and select **Security Lists** (e.g., `Default Security List for <vcn-name>`).
4. Click **Add Ingress Rules**:
   * **Stateless:** Unchecked
   * **Source Type:** CIDR
   * **Source CIDR:** `<STUDENT_1_VM_PUBLIC_IP>/32` (or `0.0.0.0/0` for class submission testing)
   * **IP Protocol:** `TCP`
   * **Source Port Range:** `All`
   * **Destination Port Range:** `3306`
   * **Description:** `Accept Remote JDBC traffic from Student 1 VM for KEC Evaluation System`
5. Save the rule.

---

## 3. Student 1 (Application Host) Setup Instructions

### Step 3.1: Install Java 21 LTS on OCI VM
```bash
# Ubuntu
sudo apt update && sudo apt install -y openjdk-21-jdk

# Oracle Linux / RHEL
sudo dnf install -y java-21-openjdk-devel

# Verify
java -version
javac -version
```

### Step 3.2: Verify Network Reachability to Student 2's Database
From Student 1's terminal, test network and port connectivity:
```bash
# 1. Ping Student 2's VM
ping -c 4 <STUDENT_2_VM_PUBLIC_IP>

# 2. Test TCP handshake on MySQL port 3306
nc -zv <STUDENT_2_VM_PUBLIC_IP> 3306
# Expected output: Connection to <STUDENT_2_VM_PUBLIC_IP> 3306 port [tcp/mysql] succeeded!

# Alternatively using telnet:
telnet <STUDENT_2_VM_PUBLIC_IP> 3306
```

### Step 3.3: Configure Remote Database Properties
Edit or create `db.properties` in the project directory:
```properties
db.type=MYSQL
db.host=<STUDENT_2_VM_PUBLIC_IP>
db.port=3306
db.name=kec_eval_db
db.user=kec_user
db.password=KecPassword2024!
```

### Step 3.4: Compile and Run the Java Application
```bash
# Compile all source files with the MySQL JDBC driver jar
javac -cp ".:lib/*" *.java

# Run the console evaluation application
java -cp ".:lib/*" MainApp
```

---

## 4. Verification Checklists for Final Submission

### Deliverable Checklist
- [x] **`kec_schema.sql`**: Complete DDL creating `students`, `courses`, `enrollments`, `assessment_marks` with foreign keys, checks, and seed catalog.
- [x] **`DBConnection.java`**: Remote JDBC connection manager with automatic fallback and dynamic property reload.
- [x] **`StudentDAO.java`**: Complete data access object handling students, enrollments, and phased mark records.
- [x] **`FileProcessor.java`**:
  - `BufferedReader` for bulk student import from `students.csv`.
  - Regex validation for student roll numbers (`^[0-9]{2}[A-Z]{3}[0-9]{3}$`).
  - String sanitization (`.trim()`, `.toUpperCase()`).
  - `BufferedWriter` for transcript and defaulter export.
- [x] **`BatchResultWorker.java`**:
  - Multi-threaded `Callable` for Java `ExecutorService`.
  - Category weightages (THEORY 40/60, PRACTICAL 60/40, EMBEDDED 50/50).
  - ESE thresholds (45% for Theory/Embedded, 50% for Practical).
  - 10-Point grading scale (O, A+, A, B+, B, RA, SA, AB).
  - Mathematical formulas: SGPA and Equivalent Percentage ($SGPA \times 10$).
- [x] **`MainApp.java`**: Menu-driven console UI and automated full-pipeline demonstration mode.

### Required Screenshots for Submission Document
1. **OCI VCN Ingress Rule**: Screenshot of Port 3306 Ingress rule in OCI Console.
2. **OS Firewall Status**: Output of `sudo firewall-cmd --list-all` or `sudo ufw status`.
3. **Network Handshake Proof**: Screenshot of `nc -zv <DB_VM_IP> 3306` showing succeeded connection.
4. **Execution Proof 1**: Bulk CSV import showing regex validation and student counts.
5. **Execution Proof 2**: Multi-threaded SGPA batch execution displaying worker thread names and execution times.
6. **Execution Proof 3**: Generated `attendance_defaulters.txt` audit report.
7. **Execution Proof 4**: Generated `24ITR001_marksheet.txt` transcript.

