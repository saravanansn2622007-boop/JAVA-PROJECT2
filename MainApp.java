import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * MainApp.java
 * ============================================================================
 * Project Assignment Set 1: KEC (R2024) Student Academic & Continuous Evaluation System
 * ----------------------------------------------------------------------------
 * Deployed & Executed by Student 1 (Application Host) on OCI Linux VM.
 * Connects to Student 2's Database Host VM (MySQL / Port 3306).
 * Demonstrates:
 * 1. String processing & Regex roll number validation
 * 2. Bulk File I/O (BufferedReader / BufferedWriter)
 * 3. 3-Phase Incremental Continuous Evaluation
 * 4. Parallel batch SGPA calculation using Java ExecutorService
 * 5. Tabular reporting formatted with String.format()
 * ============================================================================
 */
public class MainApp {

    private static final Scanner scanner = new Scanner(System.in);
    private static final StudentDAO studentDAO = new StudentDAO();
    private static final FileProcessor fileProcessor = new FileProcessor(studentDAO);

    public static void main(String[] args) {
        printBanner();
        testInitialConnection();

        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Enter your choice (1-8): ");
            String input = scanner.nextLine().trim();

            switch (input) {
                case "1":
                    handleBulkImport();
                    break;
                case "2":
                    handlePhasedEntry();
                    break;
                case "3":
                    handleParallelBatchCalculation();
                    break;
                case "4":
                    handleExportTranscript();
                    break;
                case "5":
                    handleExportDefaulters();
                    break;
                case "6":
                    handleDisplayClassSummary();
                    break;
                case "7":
                    handleDatabaseConfig();
                    break;
                case "8":
                    System.out.println("\n[System] Exiting KEC Evaluation System. Good luck with your OCI project deployment!");
                    running = false;
                    break;
                case "demo":
                case "auto":
                    runAutomatedDemonstration();
                    break;
                default:
                    System.out.println("[Error] Invalid choice. Please select an option between 1 and 8 (or type 'demo').");
            }
        }
    }

    private static void printBanner() {
        System.out.println("=================================================================================================");
        System.out.println("          KONGU ENGINEERING COLLEGE (AUTONOMOUS) - REGULATION 2024 (R2024)");
        System.out.println("                   STUDENT ACADEMIC & CONTINUOUS EVALUATION SYSTEM");
        System.out.println("                     Cloud Architecture: OCI Linux Distributed VMs");
        System.out.println("=================================================================================================");
    }

    private static void testInitialConnection() {
        System.out.println("[DB Check] Testing database connection...");
        try (Connection conn = DBConnection.getConnection()) {
            System.out.printf("[DB Check] Connected successfully! Active Mode: %s (%s)\n",
                    DBConnection.getActiveMode(), conn.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            System.err.println("[DB Check] Warning: " + e.getMessage());
        }
    }

    private static void printMenu() {
        System.out.println("\n-------------------------------------------------------------------------------------------------");
        System.out.println("                                          MAIN MENU");
        System.out.println("-------------------------------------------------------------------------------------------------");
        System.out.println(" 1. Import Student Roster from CSV (BufferedReader + Regex Validation)");
        System.out.println(" 2. Phased Incremental Assessment Entry (Phase 1 / Phase 2 / Phase 3)");
        System.out.println(" 3. Compute Batch SGPAs Concurrently (Java ExecutorService Multithreading)");
        System.out.println(" 4. Export Individual Student Transcript to File (BufferedWriter + String.format)");
        System.out.println(" 5. Export Attendance Defaulter Audit List (< 75% Attendance, Condonation / SA)");
        System.out.println(" 6. View On-Screen Class Roster & Evaluation Summary");
        System.out.println(" 7. Database Connection & Remote OCI Host Settings");
        System.out.println(" 8. Exit");
        System.out.println(" [Tip: Type 'demo' to run a full end-to-end automated evaluation pipeline]");
        System.out.println("-------------------------------------------------------------------------------------------------");
    }

    // ========================================================================
    // 1. Bulk CSV Import
    // ========================================================================
    private static void handleBulkImport() {
        System.out.println("\n--- [1] Bulk CSV Student Import ---");
        System.out.print("Enter CSV file path [Default: students.csv]: ");
        String path = scanner.nextLine().trim();
        if (path.isEmpty()) path = "students.csv";

        try {
            int count = fileProcessor.importStudentsFromCSV(path);
            System.out.printf("[Success] Successfully imported %d students into database.\n", count);
        } catch (Exception e) {
            System.err.println("[Error] Import failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // 2. Phased Incremental Assessment Data Entry
    // ========================================================================
    private static void handlePhasedEntry() {
        System.out.println("\n--- [2] Phased Incremental Assessment Entry ---");
        System.out.println("Select Evaluation Phase to Record:");
        System.out.println(" 1. Phase 1 (Mid-Sem): CIA Test 1 (0-100) & CIA Test 2 (0-100)");
        System.out.println(" 2. Phase 2 (End of Sem CIA): Assignment / Quiz / Lab Evaluation (0-100)");
        System.out.println(" 3. Phase 3 (Post-Sem ESE): End Semester Exam Marks (0-100) or Absence (AB)");
        System.out.print("Enter Phase (1-3): ");
        String phaseChoice = scanner.nextLine().trim();

        System.out.print("Enter Student Roll Number (e.g., 24ITR001): ");
        String rollNo = FileProcessor.sanitizeString(scanner.nextLine());

        if (!FileProcessor.validateRollNo(rollNo)) {
            System.err.printf("[Regex Error] Roll Number '%s' is invalid. Must match ^\\d{2}[A-Z]{3}\\d{3}$\n", rollNo);
            return;
        }

        try {
            StudentDAO.Student s = studentDAO.getStudent(rollNo);
            if (s == null) {
                System.err.println("[Error] Student not found in database. Please import or add student first.");
                return;
            }

            List<StudentDAO.EnrollmentRecord> enrollments = studentDAO.getEnrollmentsForStudent(rollNo);
            if (enrollments.isEmpty()) {
                System.err.println("[Error] No enrolled courses found for " + rollNo);
                return;
            }

            System.out.printf("\nRecording for: %s - %s (%s)\n", s.rollNo, s.name, s.department);
            for (StudentDAO.EnrollmentRecord rec : enrollments) {
                System.out.printf("\nCourse: %s - %s [%s | Credits: %d]\n",
                        rec.courseCode, rec.courseName, rec.category, rec.credits);

                if ("1".equals(phaseChoice)) {
                    System.out.print("  Enter CIA Test 1 (0-100): ");
                    double t1 = Double.parseDouble(scanner.nextLine().trim());
                    System.out.print("  Enter CIA Test 2 (0-100): ");
                    double t2 = Double.parseDouble(scanner.nextLine().trim());
                    studentDAO.recordPhase1Marks(rollNo, rec.courseCode, t1, t2);
                    System.out.println("  -> Phase 1 Recorded.");
                } else if ("2".equals(phaseChoice)) {
                    System.out.print("  Enter Assignment/Quiz/Lab Score (0-100): ");
                    double p2 = Double.parseDouble(scanner.nextLine().trim());
                    studentDAO.recordPhase2Marks(rollNo, rec.courseCode, p2);
                    System.out.println("  -> Phase 2 Recorded.");
                } else if ("3".equals(phaseChoice)) {
                    System.out.print("  Was student Absent in ESE? (y/n): ");
                    String ab = scanner.nextLine().trim().toLowerCase();
                    if ("y".equals(ab) || "yes".equals(ab)) {
                        studentDAO.recordPhase3Marks(rollNo, rec.courseCode, null, true);
                        System.out.println("  -> Marked AB (Absent in ESE).");
                    } else {
                        System.out.print("  Enter ESE Marks (0-100): ");
                        double ese = Double.parseDouble(scanner.nextLine().trim());
                        studentDAO.recordPhase3Marks(rollNo, rec.courseCode, ese, false);
                        System.out.println("  -> Phase 3 Recorded.");
                    }
                }
            }
            System.out.println("[Success] Phase updates completed.");
        } catch (Exception e) {
            System.err.println("[Error] Phased entry failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // 3. Multi-Threaded Batch Calculation (ExecutorService)
    // ========================================================================
    private static void handleParallelBatchCalculation() {
        System.out.println("\n--- [3] Parallel Batch SGPA Calculation (Multithreading) ---");
        try {
            List<StudentDAO.Student> students = studentDAO.getAllStudents();
            if (students.isEmpty()) {
                System.out.println("[Warning] No students found. Please import students first.");
                return;
            }

            int poolSize = Math.min(Runtime.getRuntime().availableProcessors(), 8);
            System.out.printf("[Multithreading] Initializing Java ExecutorService with fixed pool of %d worker threads...\n", poolSize);
            ExecutorService executor = Executors.newFixedThreadPool(poolSize);

            List<Future<BatchResultWorker.StudentEvaluationResult>> futures = new ArrayList<>();
            long batchStart = System.currentTimeMillis();

            for (StudentDAO.Student s : students) {
                BatchResultWorker worker = new BatchResultWorker(s.rollNo, studentDAO);
                futures.add(executor.submit(worker));
            }

            System.out.println("-------------------------------------------------------------------------------------------------");
            System.out.printf("%-12s | %-24s | %-15s | %-8s | %-12s | %-14s\n",
                    "ROLL NO", "STUDENT NAME", "WORKER THREAD", "SGPA", "EQUIV %", "EXEC TIME (ms)");
            System.out.println("-------------------------------------------------------------------------------------------------");

            for (Future<BatchResultWorker.StudentEvaluationResult> f : futures) {
                BatchResultWorker.StudentEvaluationResult res = f.get();
                double execMs = res.executionTimeNanos / 1_000_000.0;
                System.out.printf("%-12s | %-24.24s | %-15s | %8.2f | %11.2f%% | %12.3f ms\n",
                        res.rollNo, res.studentName, res.threadName, res.sgpa, res.equivalentPercentage, execMs);
            }

            executor.shutdown();
            long totalTime = System.currentTimeMillis() - batchStart;
            System.out.println("-------------------------------------------------------------------------------------------------");
            System.out.printf("[Multithreading Complete] Processed %d students in %d ms using parallel threads.\n",
                    students.size(), totalTime);
        } catch (Exception e) {
            System.err.println("[Error] Parallel processing failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // 4. Export Individual Student Marksheet
    // ========================================================================
    private static void handleExportTranscript() {
        System.out.println("\n--- [4] Export Individual Student Marksheet ---");
        System.out.print("Enter Student Roll Number (e.g., 24ITR001): ");
        String rollNo = FileProcessor.sanitizeString(scanner.nextLine());

        try {
            StudentDAO.Student s = studentDAO.getStudent(rollNo);
            if (s == null) {
                System.err.println("[Error] Student not found: " + rollNo);
                return;
            }

            // Run evaluation worker for accurate SGPA
            BatchResultWorker worker = new BatchResultWorker(rollNo, studentDAO);
            BatchResultWorker.StudentEvaluationResult res = worker.call();

            String defaultOut = rollNo + "_marksheet.txt";
            System.out.printf("Enter output filename [Default: %s]: ", defaultOut);
            String outPath = scanner.nextLine().trim();
            if (outPath.isEmpty()) outPath = defaultOut;

            String fullPath = fileProcessor.exportStudentMarksheet(rollNo, res.sgpa, res.equivalentPercentage, outPath);
            System.out.println("[Success] Marksheet exported to: " + fullPath);
        } catch (Exception e) {
            System.err.println("[Error] Export failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // 5. Export Attendance Defaulters List
    // ========================================================================
    private static void handleExportDefaulters() {
        System.out.println("\n--- [5] Export Attendance Defaulter Audit List ---");
        String defaultOut = "attendance_defaulters.txt";
        System.out.printf("Enter output filename [Default: %s]: ", defaultOut);
        String outPath = scanner.nextLine().trim();
        if (outPath.isEmpty()) outPath = defaultOut;

        try {
            String fullPath = fileProcessor.exportAttendanceDefaulters(outPath);
            System.out.println("[Success] Defaulter audit list exported to: " + fullPath);
        } catch (Exception e) {
            System.err.println("[Error] Export failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // 6. View On-Screen Class Summary
    // ========================================================================
    private static void handleDisplayClassSummary() {
        System.out.println("\n--- [6] Class Roster & Course Enrollment Summary ---");
        try {
            List<StudentDAO.Student> students = studentDAO.getAllStudents();
            if (students.isEmpty()) {
                System.out.println("[Notice] No students in database. Run option 1 (Import CSV) first.");
                return;
            }

            System.out.println("-------------------------------------------------------------------------------------------------");
            System.out.printf("%-12s | %-26s | %-20s | %-4s | %-11s | %-12s\n",
                    "ROLL NO", "STUDENT NAME", "DEPARTMENT", "SEM", "ATTENDANCE", "CONDONATION");
            System.out.println("-------------------------------------------------------------------------------------------------");

            for (StudentDAO.Student s : students) {
                System.out.printf("%-12s | %-26.26s | %-20.20s | %4d | %10.2f%% | %-12s\n",
                        s.rollNo, s.name, s.department, s.semester, s.attendance,
                        s.medicalCondonation ? "APPROVED" : "NONE");
            }
            System.out.println("-------------------------------------------------------------------------------------------------");
        } catch (SQLException e) {
            System.err.println("[Error] Database query failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // 7. Database Connection Settings (Remote OCI Host)
    // ========================================================================
    private static void handleDatabaseConfig() {
        System.out.println("\n--- [7] Database Connection & Remote OCI Host Settings ---");
        System.out.println("Current active mode: " + DBConnection.getActiveMode());
        System.out.println("Current config: " + DBConnection.getConfig());
        System.out.print("\nDo you want to reconfigure connection to Student 2's VM? (y/n): ");
        String ans = scanner.nextLine().trim().toLowerCase();
        if ("y".equals(ans) || "yes".equals(ans)) {
            System.out.print("Database Type [MYSQL/SQLITE]: ");
            String type = scanner.nextLine().trim();
            System.out.print("Host (Student 2's OCI Public IP or 127.0.0.1): ");
            String host = scanner.nextLine().trim();
            System.out.print("Port [3306]: ");
            String port = scanner.nextLine().trim();
            if (port.isEmpty()) port = "3306";
            System.out.print("Database Name [kec_eval_db]: ");
            String dbName = scanner.nextLine().trim();
            if (dbName.isEmpty()) dbName = "kec_eval_db";
            System.out.print("User [kec_user]: ");
            String user = scanner.nextLine().trim();
            System.out.print("Password: ");
            String pass = scanner.nextLine().trim();

            DBConnection.setConfig(type, host, port, dbName, user, pass);
            testInitialConnection();
        }
    }

    // ========================================================================
    // Automated Full-Cycle Demonstration Pipeline
    // ========================================================================
    private static void runAutomatedDemonstration() {
        System.out.println("\n=================================================================================================");
        System.out.println("                 RUNNING AUTOMATED END-TO-END DEMONSTRATION OF ALL MODULES");
        System.out.println("=================================================================================================");

        try {
            // Step 1: Bulk CSV Import
            System.out.println("\n[Step 1/5] Bulk Importing students.csv with String Sanitization & Regex Check...");
            int imported = fileProcessor.importStudentsFromCSV("students.csv");
            System.out.printf("-> %d students imported and enrolled into courses.\n", imported);

            // Step 2: Seed realistic Phased marks across cohorts
            System.out.println("\n[Step 2/5] Simulating Phased Incremental Data Entry for all students...");
            List<StudentDAO.EnrollmentRecord> allRecs = studentDAO.getAllEnrollmentRecords();

            int count = 0;
            for (StudentDAO.EnrollmentRecord r : allRecs) {
                // Generate realistic marks based on student number
                int seed = Math.abs(r.rollNo.hashCode() + r.courseCode.hashCode()) % 40;
                double cia1 = 60.0 + seed; // 60-100
                double cia2 = 62.0 + (seed % 35);
                double p2 = 65.0 + (seed % 30);
                double ese = 55.0 + (seed % 42);
                boolean absent = (r.rollNo.endsWith("008") && r.courseCode.endsWith("31")); // Sample absence

                studentDAO.recordPhase1Marks(r.rollNo, r.courseCode, cia1, cia2);
                studentDAO.recordPhase2Marks(r.rollNo, r.courseCode, p2);
                studentDAO.recordPhase3Marks(r.rollNo, r.courseCode, ese, absent);
                count++;
            }
            System.out.printf("-> Recorded Phase 1, Phase 2, and Phase 3 marks across %d course enrollments.\n", count);

            // Step 3: Multi-threaded batch execution
            System.out.println("\n[Step 3/5] Executing Parallel Batch SGPA Calculation using ExecutorService...");
            handleParallelBatchCalculation();

            // Step 4: Export attendance defaulters
            System.out.println("\n[Step 4/5] Generating Attendance Defaulter Audit List...");
            String defPath = fileProcessor.exportAttendanceDefaulters("attendance_defaulters.txt");
            System.out.println("-> Defaulters exported: " + defPath);

            // Step 5: Export sample marksheet
            System.out.println("\n[Step 5/5] Generating Sample Marksheet for 24ITR001...");
            BatchResultWorker worker = new BatchResultWorker("24ITR001", studentDAO);
            BatchResultWorker.StudentEvaluationResult res = worker.call();
            String markPath = fileProcessor.exportStudentMarksheet("24ITR001", res.sgpa, res.equivalentPercentage, "24ITR001_marksheet.txt");
            System.out.println("-> Marksheet exported: " + markPath);

            System.out.println("\n=================================================================================================");
            System.out.println("                   AUTOMATED DEMO COMPLETE - ALL REQUIREMENTS SATISFIED!");
            System.out.println("=================================================================================================");
        } catch (Exception e) {
            System.err.println("[Demo Error] " + e.getMessage());
            e.printStackTrace();
        }
    }
}

