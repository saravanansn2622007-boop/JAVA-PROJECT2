import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * FileProcessor.java
 * ============================================================================
 * Handles String sanitization, Regex validation, and File I/O operations.
 * - Imports student rosters from students.csv using BufferedReader.
 * - Validates Roll Numbers against standard KEC format ^[0-9]{2}[A-Z]{3}[0-9]{3}$
 * - Exports student transcripts / mark sheets using BufferedWriter.
 * - Exports attendance defaulters list to .txt files.
 * ============================================================================
 */
public class FileProcessor {

    // Regex: 2 digits (year 24) + 3 letters (ITR, CSR, ECR, etc.) + 3 digits (001-999)
    private static final String ROLL_NO_REGEX = "^[0-9]{2}[A-Z]{3}[0-9]{3}$";
    private static final Pattern ROLL_NO_PATTERN = Pattern.compile(ROLL_NO_REGEX);

    private final StudentDAO studentDAO;

    public FileProcessor(StudentDAO dao) {
        this.studentDAO = dao;
    }

    /**
     * Validates whether a roll number matches the KEC R2024 format using Regex.
     */
    public static boolean validateRollNo(String rollNo) {
        if (rollNo == null) return false;
        return rollNo.matches(ROLL_NO_REGEX);
    }

    /**
     * Sanitizes input strings using .trim() and .toUpperCase().
     */
    public static String sanitizeString(String input) {
        if (input == null) return "";
        return input.trim().toUpperCase();
    }

    /**
     * Bulk reads students from CSV file using BufferedReader and persists to Database.
     */
    public int importStudentsFromCSV(String filePath) throws IOException, SQLException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + file.getAbsolutePath());
        }

        int successCount = 0;
        int lineNumber = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                // Skip empty lines or header
                if (line.isEmpty() || line.toLowerCase().startsWith("roll_no") || line.toLowerCase().startsWith("rollno")) {
                    continue;
                }

                String[] tokens = line.split(",");
                if (tokens.length < 5) {
                    System.err.printf("[FileProcessor] Line %d: Insufficient columns. Skipped.\n", lineNumber);
                    continue;
                }

                // Sanitize string inputs using .trim() and .toUpperCase()
                String rawRoll = sanitizeString(tokens[0]);
                String rawName = tokens[1].trim().toUpperCase();
                String rawDept = tokens[2].trim().toUpperCase();
                int semester = 1;
                double attendance = 100.0;
                boolean condonation = false;

                try {
                    semester = Integer.parseInt(tokens[3].trim());
                    attendance = Double.parseDouble(tokens[4].trim());
                    if (tokens.length >= 6) {
                        condonation = Boolean.parseBoolean(tokens[5].trim());
                    }
                } catch (NumberFormatException e) {
                    System.err.printf("[FileProcessor] Line %d: Invalid numeric value. Skipped.\n", lineNumber);
                    continue;
                }

                // String Processing: Validate Roll Number via Regex matches()
                if (!validateRollNo(rawRoll)) {
                    System.err.printf("[FileProcessor] Line %d: Roll Number '%s' failed Regex validation (^\\d{2}[A-Z]{3}\\d{3}$). Skipped.\n",
                            lineNumber, rawRoll);
                    continue;
                }

                StudentDAO.Student s = new StudentDAO.Student(rawRoll, rawName, rawDept, semester, attendance, condonation);
                studentDAO.saveOrUpdateStudent(s);
                studentDAO.enrollStudentInAllCourses(rawRoll, attendance);
                successCount++;
            }
        }
        return successCount;
    }

    /**
     * Exports attendance defaulters list to a text file using BufferedWriter.
     * Flags:
     * - Attendance < 65%: Shortage of Attendance (SA - Barred from ESE)
     * - 65% <= Attendance < 74.9%: Conditionally Eligible (Requires Medical Condonation)
     */
    public String exportAttendanceDefaulters(String outputPath) throws IOException, SQLException {
        List<StudentDAO.Student> students = studentDAO.getAllStudents();
        List<StudentDAO.Student> defaulters = new ArrayList<>();

        for (StudentDAO.Student s : students) {
            if (s.attendance < 75.0) {
                defaulters.add(s);
            }
        }

        File outFile = new File(outputPath);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
            bw.write("===================================================================================================\n");
            bw.write("                        KONGU ENGINEERING COLLEGE (AUTONOMOUS) - ERODE\n");
            bw.write("                          ATTENDANCE DEFAULTER & CONDONATION AUDIT LIST\n");
            bw.write("                               REGULATION 2024 (R2024) - B.TECH IT\n");
            bw.write("===================================================================================================\n");
            bw.write(String.format("%-12s | %-28s | %-12s | %-12s | %-25s\n",
                    "ROLL NO", "STUDENT NAME", "ATTENDANCE", "CONDONATION", "ESE ELIGIBILITY STATUS"));
            bw.write("---------------------------------------------------------------------------------------------------\n");

            for (StudentDAO.Student s : defaulters) {
                String status;
                if (s.attendance < 65.0) {
                    status = "SA (BARRED FROM ESE)";
                } else {
                    status = s.medicalCondonation ? "CONDONED (ELIGIBLE)" : "ACTION REQ: PENDING CONDONATION";
                }

                bw.write(String.format("%-12s | %-28s | %10.2f%% | %-12s | %-25s\n",
                        s.rollNo,
                        s.name,
                        s.attendance,
                        s.medicalCondonation ? "APPROVED" : "NONE",
                        status));
            }
            bw.write("===================================================================================================\n");
            bw.write(String.format("Total Defaulters Identified: %d (Generated via BufferedWriter File I/O)\n", defaulters.size()));
        }
        return outFile.getAbsolutePath();
    }

    /**
     * Exports a comprehensive individual student grade transcript to a text file.
     */
    public String exportStudentMarksheet(String rollNo, double sgpa, double equivalentPercentage, String outputPath)
            throws IOException, SQLException {
        StudentDAO.Student student = studentDAO.getStudent(rollNo);
        if (student == null) {
            throw new IllegalArgumentException("Student not found for Roll No: " + rollNo);
        }

        List<StudentDAO.EnrollmentRecord> records = studentDAO.getEnrollmentsForStudent(rollNo);
        File outFile = new File(outputPath);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
            bw.write("====================================================================================================\n");
            bw.write("                             KONGU ENGINEERING COLLEGE (AUTONOMOUS)\n");
            bw.write("                                AFFILIATED TO ANNA UNIVERSITY\n");
            bw.write("                           ACADEMIC & CONTINUOUS EVALUATION GRADE SHEET\n");
            bw.write("====================================================================================================\n");
            bw.write(String.format(" Roll Number   : %-20s  Semester           : %d\n", student.rollNo, student.semester));
            bw.write(String.format(" Student Name  : %-20s  Department         : %s\n", student.name, student.department));
            bw.write(String.format(" Attendance    : %6.2f%%               Condonation Status : %s\n",
                    student.attendance, student.medicalCondonation ? "APPROVED" : "NOT APPLICABLE"));
            bw.write("----------------------------------------------------------------------------------------------------\n");
            bw.write(String.format("%-9s | %-26s | %-9s | %-4s | %-5s | %-5s | %-5s | %-5s | %-4s | %-3s\n",
                    "COURSE", "COURSE TITLE", "CATEGORY", "CRED", "CIA1", "CIA2", "ASSGN", "ESE", "TOT", "GRD"));
            bw.write("----------------------------------------------------------------------------------------------------\n");

            int totalCredits = 0;
            for (StudentDAO.EnrollmentRecord r : records) {
                totalCredits += r.credits;
                String c1 = r.ciaTest1 != null ? String.format("%5.1f", r.ciaTest1) : "  -  ";
                String c2 = r.ciaTest2 != null ? String.format("%5.1f", r.ciaTest2) : "  -  ";
                String ass = r.phase2Eval != null ? String.format("%5.1f", r.phase2Eval) : "  -  ";
                String ese = r.isAbsent ? "  AB " : (r.eseMarks != null ? String.format("%5.1f", r.eseMarks) : "  -  ");
                String tot = r.totalMarks != null ? String.format("%5.1f", r.totalMarks) : "  -  ";
                String grd = r.grade != null ? String.format("%-4s", r.grade) : " -  ";

                bw.write(String.format("%-9s | %-26.26s | %-9s | %4d | %s | %s | %s | %s | %s | %s\n",
                        r.courseCode, r.courseName, r.category, r.credits, c1, c2, ass, ese, tot, grd));
            }

            bw.write("----------------------------------------------------------------------------------------------------\n");
            bw.write(String.format(" Total Enrolled Credits       : %d\n", totalCredits));
            bw.write(String.format(" Semester GPA (SGPA)          : %.2f / 10.00\n", sgpa));
            bw.write(String.format(" Equivalent Percentage        : %.2f%%\n", equivalentPercentage));
            bw.write("====================================================================================================\n");
            bw.write(" Evaluation Weights:\n");
            bw.write("  * THEORY    : 40% CIA + 60% ESE (ESE Min: 27/60 | Pass Min: 50)\n");
            bw.write("  * PRACTICAL : 60% CIA + 40% ESE (ESE Min: 20/40 | Pass Min: 50)\n");
            bw.write("  * EMBEDDED  : 50% CIA + 50% ESE (ESE Min: 22.5/50 | Pass Min: 50)\n");
            bw.write("  * Grading   : O (91-100), A+ (81-90), A (71-80), B+ (61-70), B (50-60), RA (<50 or ESE fail)\n");
            bw.write("====================================================================================================\n");
        }
        return outFile.getAbsolutePath();
    }
}

