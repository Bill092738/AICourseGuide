-- Usage Example for DAG Solution
-- This file shows how to use the DAG solution with your own data

-- 1. Setup the database and load sample data
SOURCE dag_solution.sql;

-- 2. View the results (already processed)
SELECT '=== CURRENT DAG RESULTS ===' as title;
CALL get_dag_output();

-- 3. Load your own CSV data
-- Method 1: Load from CSV file
-- CALL load_csv_from_file('/path/to/your/courses.csv');

-- Method 2: Insert your data directly
-- Clear existing data
DELETE FROM temp_csv_data;

-- Insert your CSV data in the format: CourseName,PreqCourseName,CreditHours,Category,Description
-- Example:
INSERT INTO temp_csv_data (course_name, prereq_course_name, credit_hours, category, description) VALUES
('Math1151', 'Math1150', 5, 'Major1', 'Calculus II'),
('Math1151', 'Math1148', 5, 'Major1', 'Calculus II'),
('CS201', 'CS101', 3, 'Major1', 'Data Structures'),
('CS201', 'Math1151', 3, 'Major1', 'Data Structures'),
('CS301', 'CS201', 4, 'Major1', 'Advanced Algorithms'),
('CS301', 'Math2000', 4, 'Major1', 'Advanced Algorithms');

-- Process your data
CALL process_csv_data();

-- View your results
SELECT '=== YOUR DAG RESULTS ===' as title;
CALL get_dag_output();

-- 4. Export results to file
-- CALL export_dag_to_file('/path/to/output/dag_edges.csv');

-- 5. Get DAG statistics
SELECT 
    'DAG Statistics' as title,
    COUNT(*) as total_edges,
    SUM(CASE WHEN p.dependency_flag = 0 THEN 1 ELSE 0 END) as and_requirements,
    SUM(CASE WHEN p.dependency_flag = 1 THEN 1 ELSE 0 END) as or_requirements,
    COUNT(DISTINCT dc.course_name) as dependent_courses,
    COUNT(DISTINCT pc.course_name) as prerequisite_courses
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id;
