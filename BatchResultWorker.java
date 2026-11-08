import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * BatchResultWorker.java
 * ============================================================================
 * Implements Callable<BatchResultWorker.Result> for parallel multi-threaded
 * batch grade and SGPA computation using Java's ExecutorService.
 * Ensures non-blocking execution while processing large student cohorts.
 * ============================================================================
 */
public class BatchResultWorker implements Callable<BatchResultWorker.StudentEvaluationResult> {

    private final String rollNo;
    private final StudentDAO studentDAO;

    public static class StudentEvaluationResult {
        public final String rollNo;
        public final String studentName;
        public final int totalCredits;
        public final double totalWeightedPoints;
        public final double sgpa;
        public final double equivalentPercentage;
        public final String threadName;
        public final long executionTimeNanos;
        public final List<StudentDAO.EnrollmentRecord> evaluatedRecords;

        public StudentEvaluationResult(String rollNo, String studentName, int totalCredits,
                                       double totalWeightedPoints, double sgpa,
                                       double equivalentPercentage, String threadName,
                                       long executionTimeNanos, List<StudentDAO.EnrollmentRecord> records) {
            this.rollNo = rollNo;
            this.studentName = studentName;
            this.totalCredits = totalCredits;
            this.totalWeightedPoints = totalWeightedPoints;
            this.sgpa = sgpa;
            this.equivalentPercentage = equivalentPercentage;
            this.threadName = threadName;
            this.executionTimeNanos = executionTimeNanos;
            this.evaluatedRecords = records;
        }
    }

    public BatchResultWorker(String rollNo, StudentDAO studentDAO) {
        this.rollNo = rollNo;
        this.studentDAO = studentDAO;
    }

    @Override
    public StudentEvaluationResult call() throws Exception {
        long startTime = System.nanoTime();
        String currentThreadName = Thread.currentThread().getName();

        StudentDAO.Student student = studentDAO.getStudent(rollNo);
        if (student == null) {
            throw new IllegalArgumentException("Student not found: " + rollNo);
        }

        List<StudentDAO.EnrollmentRecord> records = studentDAO.getEnrollmentsForStudent(rollNo);

        int totalCredits = 0;
        double sumWeightedGradePoints = 0.0;

        for (StudentDAO.EnrollmentRecord rec : records) {
            totalCredits += rec.credits;

            // 1. Calculate Continuous Internal Assessment (CIA)
            double t1 = (rec.ciaTest1 != null) ? rec.ciaTest1 : 0.0;
            double t2 = (rec.ciaTest2 != null) ? rec.ciaTest2 : 0.0;
            double p2 = (rec.phase2Eval != null) ? rec.phase2Eval : 0.0;

            double testAvg = (t1 + t2) / 2.0;
            double ciaRaw100 = (testAvg * 0.60) + (p2 * 0.40); // 60% Tests, 40% Assignments/Quiz
            double ciaCalculated = (ciaRaw100 * (rec.ciaWeightage / 100.0));

            // 2. Calculate End Semester Examination (ESE)
            double eseRaw100 = (rec.eseMarks != null) ? rec.eseMarks : 0.0;
            double eseCalculated = (eseRaw100 * (rec.eseWeightage / 100.0));

            double totalMarks = ciaCalculated + eseCalculated;

            // 3. Determine Grade & Grade Points according to KEC R2024 regulations
            String grade;
            int gradePoint;

            // Rule A: Attendance check
            if (rec.attendancePercentage < 65.0) {
                grade = "SA"; // Shortage of attendance, barred from ESE
                gradePoint = 0;
            } else if (rec.attendancePercentage < 75.0 && !rec.medicalCondonation) {
                grade = "SA"; // Condonation not approved
                gradePoint = 0;
            } else if (rec.isAbsent) {
                // Rule B: Absence in ESE
                grade = "AB";
                gradePoint = 0;
            } else if (eseCalculated < rec.eseMinThreshold) {
                // Rule C: ESE minimum threshold failure
                // THEORY: < 27/60, PRACTICAL: < 20/40, EMBEDDED: < 22.5/50
                grade = "RA";
                gradePoint = 0;
            } else if (totalMarks < 50.0) {
                // Rule D: Overall pass minimum threshold failure (< 50 marks)
                grade = "RA";
                gradePoint = 0;
            } else {
                // Rule E: 10-Point Grading Scale
                if (totalMarks >= 91.0) {
                    grade = "O";
                    gradePoint = 10;
                } else if (totalMarks >= 81.0) {
                    grade = "A+";
                    gradePoint = 9;
                } else if (totalMarks >= 71.0) {
                    grade = "A";
                    gradePoint = 8;
                } else if (totalMarks >= 61.0) {
                    grade = "B+";
                    gradePoint = 7;
                } else {
                    grade = "B";
                    gradePoint = 6;
                }
            }

            // Update record in database
            studentDAO.updateFinalEvaluation(rec.enrollmentId, ciaCalculated, eseCalculated, totalMarks, grade, gradePoint);

            rec.ciaCalculated = ciaCalculated;
            rec.eseCalculated = eseCalculated;
            rec.totalMarks = totalMarks;
            rec.grade = grade;
            rec.gradePoint = gradePoint;

            sumWeightedGradePoints += (rec.credits * gradePoint);
        }

        // 4. Mathematical Formulas:
        // SGPA = Sum(CourseCredits * GradePointsEarned) / Sum(TotalEnrolledCredits)
        double sgpa = (totalCredits > 0) ? (sumWeightedGradePoints / totalCredits) : 0.0;

        // Equivalent Percentage = SGPA * 10
        double equivalentPercentage = sgpa * 10.0;

        long duration = System.nanoTime() - startTime;

        return new StudentEvaluationResult(
                rollNo,
                student.name,
                totalCredits,
                sumWeightedGradePoints,
                sgpa,
                equivalentPercentage,
                currentThreadName,
                duration,
                records
        );
    }
}

