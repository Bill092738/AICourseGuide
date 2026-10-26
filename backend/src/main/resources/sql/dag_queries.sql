-- SQL Queries for DAG Edge List Generation
-- These queries implement the dependency flag logic using MySQL

-- Query 1: Get all DAG edges in pipe-separated format
SELECT 
    CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS dag_edge
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
ORDER BY dc.course_name, p.dependency_flag, pc.course_name;

-- Query 2: Get DAG edges grouped by dependency type
SELECT 
    p.dependency_flag,
    COUNT(*) as edge_count,
    GROUP_CONCAT(
        CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) 
        ORDER BY dc.course_name, pc.course_name 
        SEPARATOR '\n'
    ) as edges
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
GROUP BY p.dependency_flag
ORDER BY p.dependency_flag;

-- Query 3: Get DAG edges for a specific course
SELECT 
    CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS dag_edge
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
WHERE dc.course_name = 'Math 3589'  -- Replace with desired course
ORDER BY p.dependency_flag, pc.course_name;

-- Query 4: Analyze prerequisite patterns
SELECT 
    dc.course_name as dependent_course,
    COUNT(*) as total_prerequisites,
    SUM(CASE WHEN p.dependency_flag = 0 THEN 1 ELSE 0 END) as and_requirements,
    SUM(CASE WHEN p.dependency_flag = 1 THEN 1 ELSE 0 END) as or_requirements,
    GROUP_CONCAT(
        CASE 
            WHEN p.dependency_flag = 0 THEN CONCAT('AND:', pc.course_name)
            WHEN p.dependency_flag = 1 THEN CONCAT('OR:', pc.course_name)
        END
        ORDER BY p.dependency_flag, pc.course_name
        SEPARATOR ', '
    ) as prerequisite_summary
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
GROUP BY dc.course_name
ORDER BY dc.course_name;

-- Query 5: Find courses with mixed requirements (both AND and OR)
SELECT 
    dc.course_name as dependent_course,
    COUNT(*) as total_prerequisites,
    SUM(CASE WHEN p.dependency_flag = 0 THEN 1 ELSE 0 END) as and_count,
    SUM(CASE WHEN p.dependency_flag = 1 THEN 1 ELSE 0 END) as or_count
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
GROUP BY dc.course_name
HAVING and_count > 0 AND or_count > 0
ORDER BY dc.course_name;

-- Query 6: Generate complete DAG report
SELECT 
    'DAG Edge List Report' as report_title,
    '' as separator,
    CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS dag_edge,
    CASE 
        WHEN p.dependency_flag = 0 THEN 'AND requirement'
        WHEN p.dependency_flag = 1 THEN 'OR group requirement'
    END as requirement_type
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
ORDER BY dc.course_name, p.dependency_flag, pc.course_name;
