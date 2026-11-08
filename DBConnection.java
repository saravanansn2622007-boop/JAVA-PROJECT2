import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * DBConnection.java
 * ============================================================================
 * Manages JDBC connection pooling and remote database connectivity.
 * Student 1 (Application Host) connects across the network to Student 2's
 * OCI VM hosting MySQL on Port 3306 (or local SQLite mode for offline testing).
 * ============================================================================
 */
public class DBConnection {

    private static final String CONFIG_FILE = "db.properties";
    private static Properties config = new Properties();
    private static String activeMode = "MYSQL"; // "MYSQL" or "SQLITE"

    static {
        loadConfig();
    }

    private static void loadConfig() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                config.load(fis);
            } catch (IOException e) {
                System.err.println("[DBConnection] Warning: Could not read " + CONFIG_FILE + ": " + e.getMessage());
            }
        } else {
            // Default configuration for OCI Remote Connection
            config.setProperty("db.type", "MYSQL"); // or SQLITE
            config.setProperty("db.host", "127.0.0.1"); // Set to Student 2's OCI Public IP
            config.setProperty("db.port", "3306");
            config.setProperty("db.name", "kec_eval_db");
            config.setProperty("db.user", "kec_user");
            config.setProperty("db.password", "KecPassword2024!");
            config.setProperty("db.sqlite.path", "kec_eval.db");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                config.store(fos, "KEC Evaluation System Database Configuration");
            } catch (IOException ignored) {}
        }
        activeMode = config.getProperty("db.type", "MYSQL").trim().toUpperCase();
    }

    public static void setConfig(String type, String host, String port, String dbName, String user, String pass) {
        config.setProperty("db.type", type);
        config.setProperty("db.host", host);
        config.setProperty("db.port", port);
        config.setProperty("db.name", dbName);
        config.setProperty("db.user", user);
        config.setProperty("db.password", pass);
        activeMode = type.trim().toUpperCase();
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            config.store(fos, "Updated Database Configuration");
        } catch (IOException ignored) {}
    }

    public static Connection getConnection() throws SQLException {
        if ("SQLITE".equals(activeMode)) {
            return getSQLiteConnection();
        } else {
            try {
                return getMySQLConnection();
            } catch (SQLException ex) {
                // If MySQL is unreachable, fallback gracefully to SQLite so the application remains operable
                System.out.println("[DBConnection] Remote MySQL unreachable (" + ex.getMessage() + ")");
                System.out.println("[DBConnection] Auto-switching to embedded SQLite mode for local execution...");
                activeMode = "SQLITE";
                return getSQLiteConnection();
            }
        }
    }

    private static Connection getMySQLConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found on classpath: " + e.getMessage());
        }

        String host = config.getProperty("db.host", "127.0.0.1");
        String port = config.getProperty("db.port", "3306");
        String name = config.getProperty("db.name", "kec_eval_db");
        String user = config.getProperty("db.user", "root");
        String pass = config.getProperty("db.password", "");

        String url = String.format("jdbc:mysql://%s:%s/%s?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC&connectTimeout=4000",
                host, port, name);

        return DriverManager.getConnection(url, user, pass);
    }

    private static Connection getSQLiteConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC Driver not found: " + e.getMessage());
        }
        String path = config.getProperty("db.sqlite.path", "kec_eval.db");
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
        initSQLiteSchema(conn);
        return conn;
    }

    private static void initSQLiteSchema(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS students (" +
                    "roll_no TEXT PRIMARY KEY," +
                    "student_name TEXT NOT NULL," +
                    "department TEXT NOT NULL," +
                    "semester INTEGER NOT NULL DEFAULT 1," +
                    "overall_attendance REAL NOT NULL DEFAULT 100.0," +
                    "medical_condonation INTEGER NOT NULL DEFAULT 0," +
                    "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS courses (" +
                    "course_code TEXT PRIMARY KEY," +
                    "course_name TEXT NOT NULL," +
                    "category TEXT NOT NULL CHECK(category IN ('THEORY', 'PRACTICAL', 'EMBEDDED'))," +
                    "credits INTEGER NOT NULL," +
                    "cia_weightage REAL NOT NULL," +
                    "ese_weightage REAL NOT NULL," +
                    "ese_min_threshold REAL NOT NULL" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS enrollments (" +
                    "enrollment_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "roll_no TEXT NOT NULL," +
                    "course_code TEXT NOT NULL," +
                    "attendance_percentage REAL NOT NULL DEFAULT 100.0," +
                    "attendance_status TEXT NOT NULL DEFAULT 'ELIGIBLE'," +
                    "FOREIGN KEY (roll_no) REFERENCES students(roll_no) ON DELETE CASCADE," +
                    "FOREIGN KEY (course_code) REFERENCES courses(course_code) ON DELETE CASCADE," +
                    "UNIQUE (roll_no, course_code)" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS assessment_marks (" +
                    "mark_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "enrollment_id INTEGER NOT NULL UNIQUE," +
                    "cia_test1 REAL," +
                    "cia_test2 REAL," +
                    "phase2_eval REAL," +
                    "ese_marks REAL," +
                    "is_absent INTEGER NOT NULL DEFAULT 0," +
                    "cia_calculated REAL," +
                    "ese_calculated REAL," +
                    "total_marks REAL," +
                    "grade TEXT," +
                    "grade_point INTEGER," +
                    "evaluation_phase TEXT NOT NULL DEFAULT 'PHASE_1'," +
                    "last_updated TEXT DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (enrollment_id) REFERENCES enrollments(enrollment_id) ON DELETE CASCADE" +
                    ");");

            // Seed courses if table is empty
            stmt.execute("INSERT OR IGNORE INTO courses (course_code, course_name, category, credits, cia_weightage, ese_weightage, ese_min_threshold) VALUES " +
                    "('24ITT31', 'Advanced Java Programming', 'THEORY', 3, 40.0, 60.0, 27.0)," +
                    "('24ITT32', 'Database Management Systems', 'THEORY', 3, 40.0, 60.0, 27.0)," +
                    "('24ITT33', 'Computer Networks', 'THEORY', 3, 40.0, 60.0, 27.0)," +
                    "('24ITP31', 'Java & Cloud Computing Laboratory', 'PRACTICAL', 2, 60.0, 40.0, 20.0)," +
                    "('24ITP32', 'DBMS & SQL Laboratory', 'PRACTICAL', 2, 60.0, 40.0, 20.0)," +
                    "('24ITE31', 'Full Stack Web Development', 'EMBEDDED', 4, 50.0, 50.0, 22.5);");
        } catch (SQLException e) {
            System.err.println("[DBConnection] SQLite Schema Init Error: " + e.getMessage());
        }
    }

    public static String getActiveMode() {
        return activeMode;
    }

    public static Properties getConfig() {
        return config;
    }
}

