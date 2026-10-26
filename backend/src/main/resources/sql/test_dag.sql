-- MySQL Test Script for DAG Generation
-- Tests the complete MySQL solution for generating DAG edge lists

-- Test 1: Basic DAG Generation
SELECT '=== Test 1: Basic DAG Generation ===' as test_name;

-- Clear and setup test data
CALL clear_all_data();

-- Insert test courses
INSERT INTO courses (course_name, credit_hours, category, description) VALUES
('Math 2153', 3, 'Major1', 'Single AND requirement'),
('Math 2255', 3, 'Major1', 'Advanced Calculus'),
('Math 2568', 3, 'Major1', 'Linear Algebra'),
('Math 3607', 3, 'Major1', 'Advanced Calculus'),
('Stat 4201', 3, 'Major1', 'Intro Statistics'),
('Stat 4202', 3, 'Major1', 'Statistics'),
('Math 4530', 3, 'Major1', 'Statistics'),
('ACCTMIS 2000', 3, 'Major1', 'Financial Management'),
('ACCTMIS 2200', 3, 'Major1', 'Accounting Principles'),
('ACCTMIS 2300', 3, 'Major1', 'Managerial Accounting'),
('BusFin 3220', 3, 'Major1', 'Financial Management'),
('Math 3345', 3, 'Major1', 'Mixed requirements - AND part'),
('Math 3589', 3, 'Major1', 'Mixed requirements course');

-- Insert prerequisites with initial flags
INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag) VALUES
((SELECT id FROM courses WHERE course_name = 'Math 2255'), (SELECT id FROM courses WHERE course_name = 'Math 2153'), 0),
((SELECT id FROM courses WHERE course_name = 'Math 3607'), (SELECT id FROM courses WHERE course_name = 'Math 2255'), 0),
((SELECT id FROM courses WHERE course_name = 'Math 3607'), (SELECT id FROM courses WHERE course_name = 'Math 2568'), 0),
((SELECT id FROM courses WHERE course_name = 'Stat 4202'), (SELECT id FROM courses WHERE course_name = 'Math 4530'), 0),
((SELECT id FROM courses WHERE course_name = 'Stat 4202'), (SELECT id FROM courses WHERE course_name = 'Stat 4201'), 0),
((SELECT id FROM courses WHERE course_name = 'BusFin 3220'), (SELECT id FROM courses WHERE course_name = 'ACCTMIS 2000'), 0),
((SELECT id FROM courses WHERE course_name = 'BusFin 3220'), (SELECT id FROM courses WHERE course_name = 'ACCTMIS 2200'), 0),
((SELECT id FROM courses WHERE course_name = 'BusFin 3220'), (SELECT id FROM courses WHERE course_name = 'ACCTMIS 2300'), 0),
((SELECT id FROM courses WHERE course_name = 'Math 3589'), (SELECT id FROM courses WHERE course_name = 'Math 3345'), 0),
((SELECT id FROM courses WHERE course_name = 'Math 3589'), (SELECT id FROM courses WHERE course_name = 'Math 4530'), 0),
((SELECT id FROM courses WHERE course_name = 'Math 3589'), (SELECT id FROM courses WHERE course_name = 'Stat 4201'), 0);

-- Apply dependency flag logic
CALL analyze_dependency_flags('Math 2255');
CALL analyze_dependency_flags('Math 3607');
CALL analyze_dependency_flags('Stat 4202');
CALL analyze_dependency_flags('BusFin 3220');
CALL analyze_dependency_flags('Math 3589');

-- Display results
SELECT 'DAG Edge List:' as result_type;
SELECT 
    CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS dag_edge
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
ORDER BY dc.course_name, p.dependency_flag, pc.course_name;

-- Test 2: Verify Expected Output
SELECT '=== Test 2: Verify Expected Output ===' as test_name;

-- Check Math 2255 (should be 0)
SELECT 'Math 2255 test:' as test, 
       CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS result,
       CASE WHEN p.dependency_flag = 0 THEN 'PASS' ELSE 'FAIL' END as status
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
WHERE dc.course_name = 'Math 2255';

-- Check Math 3607 (both should be 0)
SELECT 'Math 3607 test:' as test,
       CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS result,
       CASE WHEN p.dependency_flag = 0 THEN 'PASS' ELSE 'FAIL' END as status
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
WHERE dc.course_name = 'Math 3607';

-- Check Stat 4202 (both should be 1)
SELECT 'Stat 4202 test:' as test,
       CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS result,
       CASE WHEN p.dependency_flag = 1 THEN 'PASS' ELSE 'FAIL' END as status
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
WHERE dc.course_name = 'Stat 4202';

-- Check BusFin 3220 (all should be 0)
SELECT 'BusFin 3220 test:' as test,
       CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS result,
       CASE WHEN p.dependency_flag = 0 THEN 'PASS' ELSE 'FAIL' END as status
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
WHERE dc.course_name = 'BusFin 3220';

-- Check Math 3589 (mixed: 3345=0, others=1)
SELECT 'Math 3589 test:' as test,
       CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS result,
       CASE 
           WHEN pc.course_name = 'Math 3345' AND p.dependency_flag = 0 THEN 'PASS'
           WHEN pc.course_name != 'Math 3345' AND p.dependency_flag = 1 THEN 'PASS'
           ELSE 'FAIL'
       END as status
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
WHERE dc.course_name = 'Math 3589'
ORDER BY pc.course_name;

-- Test 3: Function Test
SELECT '=== Test 3: MySQL Function Test ===' as test_name;
SELECT get_dag_edge_list() as dag_function_result;

-- Test 4: Statistics
SELECT '=== Test 4: DAG Statistics ===' as test_name;
CALL get_dag_statistics();

-- Test 5: Report Generation
SELECT '=== Test 5: DAG Report ===' as test_name;
CALL generate_dag_report();

-- Test 6: Edge Count Verification
SELECT '=== Test 6: Edge Count Verification ===' as test_name;
SELECT 
    'Expected: 11 edges' as expected,
    COUNT(*) as actual,
    CASE WHEN COUNT(*) = 11 THEN 'PASS' ELSE 'FAIL' END as status
FROM prerequisites;

-- Test 7: Flag Distribution
SELECT '=== Test 7: Flag Distribution ===' as test_name;
SELECT 
    dependency_flag,
    COUNT(*) as count,
    CASE 
        WHEN dependency_flag = 0 THEN 'AND requirements'
        WHEN dependency_flag = 1 THEN 'OR group requirements'
    END as requirement_type
FROM prerequisites
GROUP BY dependency_flag
ORDER BY dependency_flag;
