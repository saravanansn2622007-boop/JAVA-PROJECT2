# KEC (R2024) Student Academic & Continuous Evaluation System

Distributed Cloud Architecture & Continuous Evaluation System for Kongu Engineering College (Autonomous) Regulation 2024 (R2024).

---

## 📌 Project Overview
This project implements **Project Assignment Set 1**: a multi-tier, distributed academic evaluation and Continuous Internal Assessment (CIA) system deployed across Oracle Cloud Infrastructure (OCI) Linux Virtual Machines.

### Team Roles & Architecture
* **Student 1 (Application Host):** Deploys and executes the Java application on an OCI Linux VM. Implements Multithreading (`ExecutorService`), String processing & Regex validation, and File I/O (`BufferedReader` / `BufferedWriter`).
* **Student 2 (Database Host):** Deploys MySQL on an independent OCI Linux VM. Configures OCI Security List Ingress Rules (Port `3306`) and OS firewall rules (`firewalld` / `iptables`) to accept remote JDBC connections from Student 1’s VM Public IP. Manages `kec_schema.sql`.

---

## 🎯 Core Evaluation & Grading Rules

### 1. Course Categories & Weights
| Category | CIA Weightage | ESE Weightage | ESE Minimum Threshold | Overall Pass Minimum |
| :--- | :---: | :---: | :---: | :---: |
| **THEORY** | 40% | 60% | 45% (27/60 marks) | 50 Marks |
| **PRACTICAL** | 60% | 40% | 50% (20/40 marks) | 50 Marks |
| **EMBEDDED** | 50% | 50% | 45% (22.5/50 marks) | 50 Marks |

### 2. Phased Incremental Data Entry
* **Phase 1 (Mid-Semester):** Record CIA Test 1 (0–100) and CIA Test 2 (0–100).
* **Phase 2 (End of Sem CIA):** Record Assignment / Quiz / Lab Evaluation (0–100).
* **Phase 3 (Post-Sem ESE):** Record End Semester Exam Marks (0–100) or mark Absent (`AB`).

### 3. Attendance Filtering & 10-Point Grading
* Attendance $\ge 75\%$: Eligible for ESE.
* $65\% \text{ to } 74.9\%$: Conditionally eligible (*Requires Medical Condonation approval*).
* $< 65\%$: Award **SA** (*Shortage of Attendance*, Grade Point = 0, Barred from ESE).

| Grade | Marks Range | Grade Point |
| :---: | :---: | :---: |
| **O** | 91 – 100 | 10 |
| **A+** | 81 – 90 | 9 |
| **A** | 71 – 80 | 8 |
| **B+** | 61 – 70 | 7 |
| **B** | 50 – 60 | 6 |
| **RA** | < 50 or failed ESE threshold | 0 |
| **SA** | Shortage of Attendance (<65%) | 0 |
| **AB** | Absent in ESE | 0 |

### 4. Mathematical Formulas
* **Semester Grade Point Average (SGPA):**
  $$SGPA = \frac{\sum (\text{CourseCredits} \times \text{GradePointsEarned})}{\sum \text{TotalEnrolledCredits}}$$
* **Equivalent Percentage:**
  $$\text{EquivalentPercentage} = SGPA \times 10$$

---

## 📁 Repository Structure
```
├── ARCHITECTURE_AND_DEPLOYMENT.md  # Detailed OCI deployment & verification guide
├── BatchResultWorker.java          # Parallel ExecutorService worker threads
├── DBConnection.java               # Remote JDBC manager with offline fallback
├── FileProcessor.java              # Regex validation & BufferedReader/BufferedWriter I/O
├── MainApp.java                    # Menu-driven console UI and automated demo pipeline
├── StudentDAO.java                 # Data access layer for students, courses, enrollments
├── kec_schema.sql                  # MySQL/PostgreSQL database DDL & seed data
├── students.csv                    # Sample bulk student roster
├── 24ITR001_marksheet.txt          # Sample generated student marksheet transcript
├── attendance_defaulters.txt       # Sample generated attendance audit list
├── lib/                            # Bundled JDBC connectors (MySQL, SQLite, SLF4J)
├── run.bat                         # Quick-run script for Windows
└── run.sh                          # Quick-run script for Linux / OCI VM
```

---

## 🚀 Getting Started

### 1. Compile
```bash
# Windows
javac -cp ".;lib/*" *.java

# Linux / OCI VM
javac -cp ".:lib/*" *.java
```

### 2. Run
```bash
# Windows
java -cp ".;lib/*" MainApp

# Linux / OCI VM
java -cp ".:lib/*" MainApp
```
*(Or simply execute `./run.sh` or `run.bat`)*

### 3. Automated Demonstration Mode
In the Main Menu, type `demo` to automatically execute all 5 modules end-to-end (bulk import, phased entry, multithreaded SGPA computation, and transcript export).
