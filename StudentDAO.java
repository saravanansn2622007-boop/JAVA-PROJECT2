import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * StudentDAO.java
 * ============================================================================
 * Data Access Object (DAO) for KEC Continuous Evaluation System.
 * Handles database operations for students, courses, enrollments, and marks.
 * ============================================================================
 */
public class StudentDAO {

    // ------------------------------------------------------------------------
    // Student Model Class
    // ------------------------------------------------------------------------
    public static class Student {
        public String rollNo;
        public String name;
        public String department;
        public int semester;
        public double attendance;
        public boolean medicalCondonation;

        public Student(String rollNo, String name, String department, int semester, double attendance, boolean condonation) {
            this.rollNo = rollNo;
            this.name = name;
            this.department = department;
            this.semester = semester;
            this.attendance = attendance;
            this.medicalCondonation = condonation;
        }
    }

    // ------------------------------------------------------------------------
    // Course Model Class
    // ------------------------------------------------------------------------
    public static class Course {
        public String courseCode;
        public String courseName;
        public String category; // THEORY, PRACTICAL, EMBEDDED
        public int credits;
        public double ciaWeightage;
        public double eseWeightage;
        public double eseMinThreshold;

        public Course(String code, String name, String cat, int cred, double ciaW, double eseW, double eseMin) {
            this.courseCode = code;
            this.courseName = name;
            this.category = cat;
            this.credits = cred;
            this.ciaWeightage = ciaW;
            this.eseWeightage = eseW;
            this.eseMinThreshold = eseMin;
        }
    }

    // ------------------------------------------------------------------------
    // Enrollment / Assessment Record Model
    // ------------------------------------------------------------------------
    public static class EnrollmentRecord {
        public int enrollmentId;
        public String rollNo;
        public String studentName;
        public String courseCode;
        public String courseName;
        public String category;
        public int credits;
        public double ciaWeightage;
        public double eseWeightage;
        public double eseMinThreshold;
        public double attendancePercentage;
        public String attendanceStatus;
        public boolean medicalCondonation;

        // Marks
        public Double ciaTest1;
        public Double ciaTest2;
        public Double phase2Eval;
        public Double eseMarks;
        public boolean isAbsent;
        public Double ciaCalculated;
        public Double eseCalculated;
        public Double totalMarks;
        public String grade;
        public Integer gradePoint;
        public String evaluationPhase;
    }

    // ========================================================================
    // Student Operations
    // ========================================================================
    public boolean saveOrUpdateStudent(Student s) throws SQLException {
        String sql;
        boolean isMySQL = !"SQLITE".equalsIgnoreCase(DBConnection.getActiveMode());
        if (isMySQL) {
            sql = "INSERT INTO students (roll_no, student_name, department, semester, overall_attendance, medical_condonation) " +
                  "VALUES (?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE student_name=?, department=?, semester=?, overall_attendance=?, medical_condonation=?";
        } else {
            sql = "INSERT OR REPLACE INTO students (roll_no, student_name, department, semester, overall_attendance, medical_condonation) " +
                  "VALUES (?, ?, ?, ?, ?, ?)";
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.rollNo);
            ps.setString(2, s.name);
            ps.setString(3, s.department);
            ps.setInt(4, s.semester);
            ps.setDouble(5, s.attendance);
            ps.setBoolean(6, s.medicalCondonation);

            if (isMySQL) {
                ps.setString(7, s.name);
                ps.setString(8, s.department);
                ps.setInt(9, s.semester);
                ps.setDouble(10, s.attendance);
                ps.setBoolean(11, s.medicalCondonation);
            }
            return ps.executeUpdate() > 0;
        }
    }

    public List<Student> getAllStudents() throws SQLException {
        List<Student> list = new ArrayList<>();
        String sql = "SELECT roll_no, student_name, department, semester, overall_attendance, medical_condonation FROM students ORDER BY roll_no";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Student(
                    rs.getString("roll_no"),
                    rs.getString("student_name"),
                    rs.getString("department"),
                    rs.getInt("semester"),
                    rs.getDouble("overall_attendance"),
                    rs.getBoolean("medical_condonation")
                ));
            }
        }
        return list;
    }

    public Student getStudent(String rollNo) throws SQLException {
        String sql = "SELECT roll_no, student_name, department, semester, overall_attendance, medical_condonation FROM students WHERE roll_no = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Student(
                        rs.getString("roll_no"),
                        rs.getString("student_name"),
                        rs.getString("department"),
                        rs.getInt("semester"),
                        rs.getDouble("overall_attendance"),
                        rs.getBoolean("medical_condonation")
                    );
                }
            }
        }
        return null;
    }

    // ========================================================================
    // Course & Enrollment Operations
    // ========================================================================
    public List<Course> getAllCourses() throws SQLException {
        List<Course> list = new ArrayList<>();
        String sql = "SELECT course_code, course_name, category, credits, cia_weightage, ese_weightage, ese_min_threshold FROM courses ORDER BY course_code";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Course(
                    rs.getString("course_code"),
                    rs.getString("course_name"),
                    rs.getString("category"),
                    rs.getInt("credits"),
                    rs.getDouble("cia_weightage"),
                    rs.getDouble("ese_weightage"),
                    rs.getDouble("ese_min_threshold")
                ));
            }
        }
        return list;
    }

    public void enrollStudentInAllCourses(String rollNo, double defaultAttendance) throws SQLException {
        List<Course> courses = getAllCourses();
        String attStatus = computeAttendanceStatus(defaultAttendance, false);

        for (Course c : courses) {
            String checkSql = "SELECT enrollment_id FROM enrollments WHERE roll_no = ? AND course_code = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setString(1, rollNo);
                checkPs.setString(2, c.courseCode);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (!rs.next()) {
                        String insertEnroll = "INSERT INTO enrollments (roll_no, course_code, attendance_percentage, attendance_status) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement insPs = conn.prepareStatement(insertEnroll, Statement.RETURN_GENERATED_KEYS)) {
                            insPs.setString(1, rollNo);
                            insPs.setString(2, c.courseCode);
                            insPs.setDouble(3, defaultAttendance);
                            insPs.setString(4, attStatus);
                            insPs.executeUpdate();

                            int enrollId = 0;
                            try (ResultSet genKeys = insPs.getGeneratedKeys()) {
                                if (genKeys.next()) enrollId = genKeys.getInt(1);
                            }

                            if (enrollId > 0) {
                                String insertMarks = "INSERT INTO assessment_marks (enrollment_id, evaluation_phase) VALUES (?, 'PHASE_1')";
                                try (PreparedStatement mPs = conn.prepareStatement(insertMarks)) {
                                    mPs.setInt(1, enrollId);
                                    mPs.executeUpdate();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public String computeAttendanceStatus(double attendance, boolean isCondoned) {
        if (attendance >= 75.0) {
            return "ELIGIBLE";
        } else if (attendance >= 65.0) {
            return isCondoned ? "ELIGIBLE" : "CONDONATION_REQUIRED";
        } else {
            return "SA"; // Shortage of attendance, barred
        }
    }

    // ========================================================================
    // Phased Incremental Assessment Operations
    // ========================================================================
    public boolean recordPhase1Marks(String rollNo, String courseCode, double cia1, double cia2) throws SQLException {
        String sql = "UPDATE assessment_marks SET cia_test1 = ?, cia_test2 = ?, evaluation_phase = 'PHASE_1' " +
                     "WHERE enrollment_id = (SELECT enrollment_id FROM enrollments WHERE roll_no = ? AND course_code = ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, cia1);
            ps.setDouble(2, cia2);
            ps.setString(3, rollNo);
            ps.setString(4, courseCode);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean recordPhase2Marks(String rollNo, String courseCode, double phase2Eval) throws SQLException {
        String sql = "UPDATE assessment_marks SET phase2_eval = ?, evaluation_phase = 'PHASE_2' " +
                     "WHERE enrollment_id = (SELECT enrollment_id FROM enrollments WHERE roll_no = ? AND course_code = ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, phase2Eval);
            ps.setString(2, rollNo);
            ps.setString(3, courseCode);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean recordPhase3Marks(String rollNo, String courseCode, Double eseMarks, boolean isAbsent) throws SQLException {
        String sql = "UPDATE assessment_marks SET ese_marks = ?, is_absent = ?, evaluation_phase = 'PHASE_3' " +
                     "WHERE enrollment_id = (SELECT enrollment_id FROM enrollments WHERE roll_no = ? AND course_code = ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (eseMarks != null && !isAbsent) {
                ps.setDouble(1, eseMarks);
            } else {
                ps.setNull(1, java.sql.Types.DECIMAL);
            }
            ps.setBoolean(2, isAbsent);
            ps.setString(3, rollNo);
            ps.setString(4, courseCode);
            return ps.executeUpdate() > 0;
        }
    }

    public List<EnrollmentRecord> getEnrollmentsForStudent(String rollNo) throws SQLException {
        String sql = "SELECT e.enrollment_id, e.roll_no, s.student_name, s.medical_condonation, " +
                     "c.course_code, c.course_name, c.category, c.credits, c.cia_weightage, c.ese_weightage, c.ese_min_threshold, " +
                     "e.attendance_percentage, e.attendance_status, " +
                     "m.cia_test1, m.cia_test2, m.phase2_eval, m.ese_marks, m.is_absent, " +
                     "m.cia_calculated, m.ese_calculated, m.total_marks, m.grade, m.grade_point, m.evaluation_phase " +
                     "FROM enrollments e " +
                     "JOIN students s ON e.roll_no = s.roll_no " +
                     "JOIN courses c ON e.course_code = c.course_code " +
                     "LEFT JOIN assessment_marks m ON e.enrollment_id = m.enrollment_id " +
                     "WHERE e.roll_no = ? ORDER BY c.course_code";

        List<EnrollmentRecord> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapEnrollmentRecord(rs));
                }
            }
        }
        return list;
    }

    public List<EnrollmentRecord> getAllEnrollmentRecords() throws SQLException {
        String sql = "SELECT e.enrollment_id, e.roll_no, s.student_name, s.medical_condonation, " +
                     "c.course_code, c.course_name, c.category, c.credits, c.cia_weightage, c.ese_weightage, c.ese_min_threshold, " +
                     "e.attendance_percentage, e.attendance_status, " +
                     "m.cia_test1, m.cia_test2, m.phase2_eval, m.ese_marks, m.is_absent, " +
                     "m.cia_calculated, m.ese_calculated, m.total_marks, m.grade, m.grade_point, m.evaluation_phase " +
                     "FROM enrollments e " +
                     "JOIN students s ON e.roll_no = s.roll_no " +
                     "JOIN courses c ON e.course_code = c.course_code " +
                     "LEFT JOIN assessment_marks m ON e.enrollment_id = m.enrollment_id " +
                     "ORDER BY e.roll_no, c.course_code";

        List<EnrollmentRecord> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapEnrollmentRecord(rs));
            }
        }
        return list;
    }

    public void updateFinalEvaluation(int enrollmentId, double ciaCalc, double eseCalc, double totalMarks, String grade, int gradePoint) throws SQLException {
        String sql = "UPDATE assessment_marks SET cia_calculated = ?, ese_calculated = ?, total_marks = ?, " +
                     "grade = ?, grade_point = ?, evaluation_phase = 'FINALIZED' WHERE enrollment_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, ciaCalc);
            ps.setDouble(2, eseCalc);
            ps.setDouble(3, totalMarks);
            ps.setString(4, grade);
            ps.setInt(5, gradePoint);
            ps.setInt(6, enrollmentId);
            ps.executeUpdate();
        }
    }

    private EnrollmentRecord mapEnrollmentRecord(ResultSet rs) throws SQLException {
        EnrollmentRecord r = new EnrollmentRecord();
        r.enrollmentId = rs.getInt("enrollment_id");
        r.rollNo = rs.getString("roll_no");
        r.studentName = rs.getString("student_name");
        r.medicalCondonation = rs.getBoolean("medical_condonation");
        r.courseCode = rs.getString("course_code");
        r.courseName = rs.getString("course_name");
        r.category = rs.getString("category");
        r.credits = rs.getInt("credits");
        r.ciaWeightage = rs.getDouble("cia_weightage");
        r.eseWeightage = rs.getDouble("ese_weightage");
        r.eseMinThreshold = rs.getDouble("ese_min_threshold");
        r.attendancePercentage = rs.getDouble("attendance_percentage");
        r.attendanceStatus = rs.getString("attendance_status");

        double t1 = rs.getDouble("cia_test1");
        r.ciaTest1 = rs.wasNull() ? null : t1;
        double t2 = rs.getDouble("cia_test2");
        r.ciaTest2 = rs.wasNull() ? null : t2;
        double p2 = rs.getDouble("phase2_eval");
        r.phase2Eval = rs.wasNull() ? null : p2;
        double ese = rs.getDouble("ese_marks");
        r.eseMarks = rs.wasNull() ? null : ese;

        r.isAbsent = rs.getBoolean("is_absent");

        double cCalc = rs.getDouble("cia_calculated");
        r.ciaCalculated = rs.wasNull() ? null : cCalc;
        double eCalc = rs.getDouble("ese_calculated");
        r.eseCalculated = rs.wasNull() ? null : eCalc;
        double tot = rs.getDouble("total_marks");
        r.totalMarks = rs.wasNull() ? null : tot;

        r.grade = rs.getString("grade");
        int gp = rs.getInt("grade_point");
        r.gradePoint = rs.wasNull() ? null : gp;
        r.evaluationPhase = rs.getString("evaluation_phase");
        return r;
    }
}

