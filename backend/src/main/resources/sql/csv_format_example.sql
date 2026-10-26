-- CSV Format Example for DAG Generation
-- Input format: CourseName,PreqCourseName,CreditHours,Category,Description
-- Output format: DependentClass|PrerequisiteClass|Flag

-- Example 1: Sample CSV Data
-- =========================

-- Clear existing data
CALL clear_all_data();

-- Create temporary table for CSV data
DROP TEMPORARY TABLE IF EXISTS temp_simple_csv;
CREATE TEMPORARY TABLE temp_simple_csv (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_name VARCHAR(100),
    prereq_course_name VARCHAR(100),
    credit_hours INT,
    category VARCHAR(20),
    description TEXT
);

-- Insert sample data in the exact format you specified
-- Format: CourseName,PreqCourseName,CreditHours,Category,Description
INSERT INTO temp_simple_csv (course_name, prereq_course_name, credit_hours, category, description) VALUES
-- Example data matching your format
('Math1151', 'Math1150', 5, 'M1', 'Calculus II'),
('Math1151', 'Math1148', 5, 'M1', 'Calculus II'),
('CS201', 'CS101', 3, 'M1', 'Data Structures'),
('CS201', 'Math1151', 3, 'M1', 'Data Structures'),
('CS301', 'CS201', 4, 'M1', 'Advanced Algorithms'),
('CS301', 'Math2000', 4, 'M1', 'Advanced Algorithms'),
('ENG400', 'ENG200', 3, 'M1', 'Advanced Writing'),
('ENG400', 'ENG300', 3, 'M1', 'Advanced Writing'),
('PHYS201', 'MATH100', 4, 'M1', 'Physics I'),
('PHYS201', 'PHYS101', 4, 'M1', 'Physics I');

-- Process the data
CALL process_simple_csv_data();

-- Display results in pipe-separated format
SELECT '=== DAG EDGE LIST (PIPE-SEPARATED FORMAT) ===' as title;
CALL get_dag_pipe_format();

-- Show analysis
SELECT '=== PREREQUISITE PATTERN ANALYSIS ===' as title;
CALL analyze_prerequisite_patterns();

-- Show statistics
SELECT '=== DAG STATISTICS ===' as title;
CALL get_dag_statistics();

-- Example 2: Different Categories
-- =============================

-- Clear data for category example
CALL clear_all_data();
DELETE FROM temp_simple_csv;

-- Insert data with different categories (M1/M2/GE/m)
INSERT INTO temp_simple_csv (course_name, prereq_course_name, credit_hours, category, description) VALUES
('MATH2000', 'MATH1000', 3, 'M1', 'Linear Algebra'),
('MATH2000', 'MATH1500', 3, 'M1', 'Linear Algebra'),
('CS101', 'MATH1000', 4, 'M1', 'Programming I'),
('CS101', 'ENG1000', 4, 'M1', 'Programming I'),
('ENG200', 'ENG1000', 3, 'M2', 'Technical Writing'),
('ENG200', 'ENG1500', 3, 'M2', 'Technical Writing'),
('PHYS101', 'MATH1000', 2, 'GE', 'Introduction to Physics'),
('PHYS101', 'CHEM1000', 2, 'GE', 'Introduction to Physics'),
('ART200', 'ART100', 2, 'm', 'Intermediate Art'),
('ART200', 'ART150', 2, 'm', 'Intermediate Art');

-- Process the data
CALL process_simple_csv_data();

-- Display results
SELECT '=== DIFFERENT CATEGORIES EXAMPLE ===' as title;
CALL get_dag_pipe_format();

-- Example 3: CSV File Format Documentation
-- =======================================

-- Your CSV file should have this exact format:
-- CourseName,PreqCourseName,CreditHours,Category,Description
-- 
-- Example CSV content:
-- Math1151,Math1150,5,M1,Calculus II
-- Math1151,Math1148,5,M1,Calculus II
-- CS201,CS101,3,M1,Data Structures
-- CS201,Math1151,3,M1,Data Structures
-- CS301,CS201,4,M1,Advanced Algorithms
-- CS301,Math2000,4,M1,Advanced Algorithms
-- ENG400,ENG200,3,M1,Advanced Writing
-- ENG400,ENG300,3,M1,Advanced Writing
-- PHYS201,MATH100,4,M1,Physics I
-- PHYS201,PHYS101,4,M1,Physics I

-- To load from file:
-- CALL load_simple_csv_from_file('/path/to/your/courses.csv');

-- Example 4: Expected Output Format
-- =================================

-- The output will be in pipe-separated format:
-- DependentClass|PrerequisiteClass|Flag
-- 
-- Example output:
-- CS201|CS101|0
-- CS201|Math1151|0
-- CS301|CS201|0
-- CS301|Math2000|0
-- ENG400|ENG200|1
-- ENG400|ENG300|1
-- Math1151|Math1150|1
-- Math1151|Math1148|1
-- PHYS201|MATH100|1
-- PHYS201|PHYS101|1

-- Where:
-- Flag 0 = AND requirement (must satisfy ALL)
-- Flag 1 = OR group requirement (satisfy ONE from group)

-- Example 5: Export to CSV File
-- ============================

-- To export the DAG results to a CSV file:
-- CALL export_dag_to_csv('/path/to/output/dag_edges.csv');

-- This will create a file with pipe-separated values:
-- CS201|CS101|0
-- CS201|Math1151|0
-- CS301|CS201|0
-- etc.
