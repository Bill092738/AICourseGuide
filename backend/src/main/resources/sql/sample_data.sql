-- Sample data for testing DAG generation
-- This data matches the examples from the requirements

-- Insert sample courses
INSERT INTO courses (course_name, credit_hours, category, description) VALUES
('Math 2153', 3, 'Major1', 'Single AND requirement'),
('Math 2255', 3, 'Major1', 'Advanced Calculus'),
('Math 2568', 3, 'Major1', 'Linear Algebra'),
('Math 3345', 3, 'Major1', 'Mixed requirements - AND part'),
('Math 3607', 3, 'Major1', 'Advanced Calculus'),
('Math 4530', 3, 'Major1', 'Statistics'),
('Math 3589', 3, 'Major1', 'Mixed requirements course'),
('Stat 4201', 3, 'Major1', 'Intro Statistics'),
('Stat 4202', 3, 'Major1', 'Statistics'),
('ACCTMIS 2000', 3, 'Major1', 'Financial Management'),
('ACCTMIS 2200', 3, 'Major1', 'Accounting Principles'),
('ACCTMIS 2300', 3, 'Major1', 'Managerial Accounting'),
('BusFin 3220', 3, 'Major1', 'Financial Management');

-- Insert prerequisite relationships with dependency flags
-- Based on the expected output examples

-- Math 2255 requires Math 2153 (Single AND requirement)
INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag) 
SELECT dc.id, pc.id, 0
FROM courses dc, courses pc 
WHERE dc.course_name = 'Math 2255' AND pc.course_name = 'Math 2153';

-- Math 3607 requires both Math 2255 AND Math 2568 (Both Flag 0)
INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag) 
SELECT dc.id, pc.id, 0
FROM courses dc, courses pc 
WHERE dc.course_name = 'Math 3607' AND pc.course_name = 'Math 2255';

INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag) 
SELECT dc.id, pc.id, 0
FROM courses dc, courses pc 
WHERE dc.course_name = 'Math 3607' AND pc.course_name = 'Math 2568';

-- Stat 4202 requires Math 4530 OR Stat 4201 (Both Flag 1)
INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag) 
SELECT dc.id, pc.id, 1
FROM courses dc, courses pc 
WHERE dc.course_name = 'Stat 4202' AND pc.course_name = 'Math 4530';

INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag) 
SELECT dc.id, pc.id, 1
FROM courses dc, courses pc 
WHERE dc.course_name = 'Stat 4202' AND pc.course_name = 'Stat 4201';

-- BusFin 3220 requires ACCTMIS 2000 AND 2200 AND 2300 (All Flag 0)
INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag) 
SELECT dc.id, pc.id, 0
FROM courses dc, courses pc 
WHERE dc.course_name = 'BusFin 3220' AND pc.course_name = 'ACCTMIS 2000';

INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag) 
SELECT dc.id, pc.id, 0
FROM courses dc, courses pc 
WHERE dc.course_name = 'BusFin 3220' AND pc.course_name = 'ACCTMIS 2200';

INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag) 
SELECT dc.id, pc.id, 0
FROM courses dc, courses pc 
WHERE dc.course_name = 'BusFin 3220' AND pc.course_name = 'ACCTMIS 2300';

-- Math 3589 mixed case: Math 3345 (AND) + (Math 4530 OR Stat 4201)
INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag) 
SELECT dc.id, pc.id, 0
FROM courses dc, courses pc 
WHERE dc.course_name = 'Math 3589' AND pc.course_name = 'Math 3345';

INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag) 
SELECT dc.id, pc.id, 1
FROM courses dc, courses pc 
WHERE dc.course_name = 'Math 3589' AND pc.course_name = 'Math 4530';

INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag) 
SELECT dc.id, pc.id, 1
FROM courses dc, courses pc 
WHERE dc.course_name = 'Math 3589' AND pc.course_name = 'Stat 4201';
