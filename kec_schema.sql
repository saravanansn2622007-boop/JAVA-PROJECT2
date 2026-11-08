-- ============================================================================
-- KEC (R2024) Student Academic & Continuous Evaluation System
-- Database Schema: kec_schema.sql
-- Managed by: Student 2 (Database Host) on OCI Linux VM (Port 3306)
-- Target DBMS: MySQL 8.0+ / PostgreSQL 14+ / SQLite Compatible
-- ============================================================================

DROP DATABASE IF EXISTS kec_eval_db;
CREATE DATABASE kec_eval_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE kec_eval_db;

-- ----------------------------------------------------------------------------
-- Table 1: students
-- Stores master student roster imported via FileProcessor from students.csv
-- ----------------------------------------------------------------------------
CREATE TABLE students (
    roll_no VARCHAR(10) PRIMARY KEY,                    -- Format: 24ITR001 (Regex validated)
    student_name VARCHAR(100) NOT NULL,
    department VARCHAR(50) NOT NULL,                     -- e.g., INFORMATION TECHNOLOGY
    semester INT NOT NULL DEFAULT 1,
    overall_attendance DECIMAL(5, 2) NOT NULL DEFAULT 100.00,
    medical_condonation BOOLEAN NOT NULL DEFAULT FALSE, -- Approved if 65% <= attendance < 75%
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------------------------------------------------------
-- Table 2: courses
-- Catalog of curriculum courses under R2024 Regulations
-- ----------------------------------------------------------------------------
CREATE TABLE courses (
    course_code VARCHAR(10) PRIMARY KEY,                -- e.g., 24ITT31, 24ITP31
    course_name VARCHAR(100) NOT NULL,
    category ENUM('THEORY', 'PRACTICAL', 'EMBEDDED') NOT NULL,
    credits INT NOT NULL CHECK (credits > 0),
    cia_weightage DECIMAL(5, 2) NOT NULL,              -- e.g., 40.00, 60.00, 50.00
    ese_weightage DECIMAL(5, 2) NOT NULL,              -- e.g., 60.00, 40.00, 50.00
    ese_min_threshold DECIMAL(5, 2) NOT NULL           -- e.g., 27.00/60, 20.00/40, 22.50/50
);

-- ----------------------------------------------------------------------------
-- Table 3: enrollments
-- Maps students to enrolled semester courses and course-specific attendance
-- ----------------------------------------------------------------------------
CREATE TABLE enrollments (
    enrollment_id INT AUTO_INCREMENT PRIMARY KEY,
    roll_no VARCHAR(10) NOT NULL,
    course_code VARCHAR(10) NOT NULL,
    attendance_percentage DECIMAL(5, 2) NOT NULL DEFAULT 100.00,
    attendance_status ENUM('ELIGIBLE', 'CONDONATION_REQUIRED', 'SA') NOT NULL DEFAULT 'ELIGIBLE',
    FOREIGN KEY (roll_no) REFERENCES students(roll_no) ON DELETE CASCADE,
    FOREIGN KEY (course_code) REFERENCES courses(course_code) ON DELETE CASCADE,
    CONSTRAINT uq_student_course UNIQUE (roll_no, course_code)
);

-- ----------------------------------------------------------------------------
-- Table 4: assessment_marks
-- Supports phased incremental assessment data entry and continuous evaluation
-- ----------------------------------------------------------------------------
CREATE TABLE assessment_marks (
    mark_id INT AUTO_INCREMENT PRIMARY KEY,
    enrollment_id INT NOT NULL UNIQUE,
    -- Phase 1 (Mid-Sem): CIA Test 1 & CIA Test 2 (0-100 scale)
    cia_test1 DECIMAL(5, 2) DEFAULT NULL,
    cia_test2 DECIMAL(5, 2) DEFAULT NULL,
    -- Phase 2 (End of Sem CIA): Assignment / Quiz / Lab evaluation (0-100 scale)
    phase2_eval DECIMAL(5, 2) DEFAULT NULL,
    -- Phase 3 (Post-Sem ESE): ESE Marks (0-100 scale) or marked absent
    ese_marks DECIMAL(5, 2) DEFAULT NULL,
    is_absent BOOLEAN NOT NULL DEFAULT FALSE,
    -- Computed scores & final grades
    cia_calculated DECIMAL(5, 2) DEFAULT NULL,         -- Scaled CIA based on course category
    ese_calculated DECIMAL(5, 2) DEFAULT NULL,         -- Scaled ESE based on course category
    total_marks DECIMAL(5, 2) DEFAULT NULL,            -- Total out of 100
    grade VARCHAR(5) DEFAULT NULL,                     -- O, A+, A, B+, B, RA, SA, AB
    grade_point INT DEFAULT NULL,                      -- 10, 9, 8, 7, 6, 0
    evaluation_phase ENUM('PHASE_1', 'PHASE_2', 'PHASE_3', 'FINALIZED') NOT NULL DEFAULT 'PHASE_1',
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (enrollment_id) REFERENCES enrollments(enrollment_id) ON DELETE CASCADE
);

-- ----------------------------------------------------------------------------
-- Seed Data: KEC R2024 Course Catalog
-- ----------------------------------------------------------------------------
INSERT INTO courses (course_code, course_name, category, credits, cia_weightage, ese_weightage, ese_min_threshold) VALUES
('24ITT31', 'Advanced Java Programming',           'THEORY',    3, 40.00, 60.00, 27.00),
('24ITT32', 'Database Management Systems',         'THEORY',    3, 40.00, 60.00, 27.00),
('24ITT33', 'Computer Networks',                   'THEORY',    3, 40.00, 60.00, 27.00),
('24ITP31', 'Java & Cloud Computing Laboratory',   'PRACTICAL', 2, 60.00, 40.00, 20.00),
('24ITP32', 'DBMS & SQL Laboratory',               'PRACTICAL', 2, 60.00, 40.00, 20.00),
('24ITE31', 'Full Stack Web Development',          'EMBEDDED',  4, 50.00, 50.00, 22.50);

-- ----------------------------------------------------------------------------
-- Verification Indexes
-- ----------------------------------------------------------------------------
CREATE INDEX idx_student_dept ON students(department);
CREATE INDEX idx_enrollment_roll ON enrollments(roll_no);
CREATE INDEX idx_enrollment_course ON enrollments(course_code);

