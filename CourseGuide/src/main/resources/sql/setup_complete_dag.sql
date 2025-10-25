-- Complete MySQL Setup for DAG Generation
-- Pure MySQL solution for course prerequisite DAG edge list generation

-- Create database
CREATE DATABASE IF NOT EXISTS courseguide_dag;
USE courseguide_dag;

-- Drop existing objects if they exist
DROP FUNCTION IF EXISTS get_dag_edge_list;
DROP PROCEDURE IF EXISTS clear_all_data;
DROP PROCEDURE IF EXISTS insert_course_if_not_exists;
DROP PROCEDURE IF EXISTS insert_prerequisite;
DROP PROCEDURE IF EXISTS analyze_dependency_flags;
DROP PROCEDURE IF EXISTS process_csv_data;
DROP PROCEDURE IF EXISTS process_csv_to_dag;
DROP PROCEDURE IF EXISTS generate_complete_dag;
DROP PROCEDURE IF EXISTS generate_dag_report;
DROP PROCEDURE IF EXISTS get_dag_statistics;
DROP PROCEDURE IF EXISTS load_csv_from_file;
DROP VIEW IF EXISTS dag_edges;
DROP TABLE IF EXISTS prerequisites;
DROP TABLE IF EXISTS courses;

-- Create tables
CREATE TABLE courses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_name VARCHAR(100) NOT NULL UNIQUE,
    credit_hours INT NOT NULL DEFAULT 3,
    category ENUM('Major1', 'Major2', 'GenedEdu', 'Minor') NOT NULL DEFAULT 'Major1',
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_course_name (course_name),
    INDEX idx_category (category)
);

CREATE TABLE prerequisites (
    id INT AUTO_INCREMENT PRIMARY KEY,
    dependent_course_id INT NOT NULL,
    prerequisite_course_id INT NOT NULL,
    dependency_flag TINYINT NOT NULL DEFAULT 0 COMMENT '0=AND requirement, 1=OR group requirement',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dependent_course_id) REFERENCES courses(id) ON DELETE CASCADE,
    FOREIGN KEY (prerequisite_course_id) REFERENCES courses(id) ON DELETE CASCADE,
    INDEX idx_dependent (dependent_course_id),
    INDEX idx_prerequisite (prerequisite_course_id),
    INDEX idx_dependency_flag (dependency_flag),
    UNIQUE KEY unique_prerequisite (dependent_course_id, prerequisite_course_id)
);

-- Create view
CREATE VIEW dag_edges AS
SELECT 
    dc.course_name AS dependent_class,
    pc.course_name AS prerequisite_class,
    p.dependency_flag
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
ORDER BY dc.course_name, p.dependency_flag, pc.course_name;

-- Create function
DELIMITER //
CREATE FUNCTION get_dag_edge_list() 
RETURNS TEXT
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE result TEXT DEFAULT '';
    DECLARE done INT DEFAULT FALSE;
    DECLARE dependent_name VARCHAR(100);
    DECLARE prerequisite_name VARCHAR(100);
    DECLARE flag TINYINT;
    
    DECLARE edge_cursor CURSOR FOR
        SELECT dependent_class, prerequisite_class, dependency_flag
        FROM dag_edges
        ORDER BY dependent_class, dependency_flag, prerequisite_class;
    
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    
    OPEN edge_cursor;
    
    read_loop: LOOP
        FETCH edge_cursor INTO dependent_name, prerequisite_name, flag;
        IF done THEN
            LEAVE read_loop;
        END IF;
        
        IF result != '' THEN
            SET result = CONCAT(result, '\n');
        END IF;
        
        SET result = CONCAT(result, dependent_name, '|', prerequisite_name, '|', flag);
    END LOOP;
    
    CLOSE edge_cursor;
    RETURN result;
END//
DELIMITER ;

-- Create procedures
DELIMITER //
CREATE PROCEDURE clear_all_data()
BEGIN
    DELETE FROM prerequisites;
    DELETE FROM courses;
    ALTER TABLE courses AUTO_INCREMENT = 1;
    ALTER TABLE prerequisites AUTO_INCREMENT = 1;
END//
DELIMITER ;

DELIMITER //
CREATE PROCEDURE insert_course_if_not_exists(
    IN p_course_name VARCHAR(100),
    IN p_credit_hours INT,
    IN p_category VARCHAR(20),
    IN p_description TEXT
)
BEGIN
    INSERT IGNORE INTO courses (course_name, credit_hours, category, description)
    VALUES (p_course_name, p_credit_hours, p_category, p_description);
END//
DELIMITER ;

DELIMITER //
CREATE PROCEDURE insert_prerequisite(
    IN p_dependent_course VARCHAR(100),
    IN p_prerequisite_course VARCHAR(100),
    IN p_dependency_flag TINYINT
)
BEGIN
    INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag)
    SELECT dc.id, pc.id, p_dependency_flag
    FROM courses dc, courses pc 
    WHERE dc.course_name = p_dependent_course AND pc.course_name = p_prerequisite_course;
END//
DELIMITER ;

DELIMITER //
CREATE PROCEDURE analyze_dependency_flags(
    IN p_dependent_course VARCHAR(100)
)
BEGIN
    DECLARE prereq_count INT DEFAULT 0;
    DECLARE dept_count INT DEFAULT 0;
    
    -- Count total prerequisites
    SELECT COUNT(*) INTO prereq_count
    FROM prerequisites p
    JOIN courses dc ON p.dependent_course_id = dc.id
    WHERE dc.course_name = p_dependent_course;
    
    -- Count unique departments
    SELECT COUNT(DISTINCT SUBSTRING_INDEX(pc.course_name, ' ', 1)) INTO dept_count
    FROM prerequisites p
    JOIN courses dc ON p.dependent_course_id = dc.id
    JOIN courses pc ON p.prerequisite_course_id = pc.id
    WHERE dc.course_name = p_dependent_course;
    
    -- Apply dependency flag logic based on the revised requirements
    IF prereq_count = 1 THEN
        -- Single prerequisite - always AND (flag = 0)
        UPDATE prerequisites p
        JOIN courses dc ON p.dependent_course_id = dc.id
        SET p.dependency_flag = 0
        WHERE dc.course_name = p_dependent_course;
        
    ELSEIF p_dependent_course = 'Math 3607' THEN
        -- Math 3607 requires both Math 2255 AND Math 2568 (Both Flag 0)
        UPDATE prerequisites p
        JOIN courses dc ON p.dependent_course_id = dc.id
        SET p.dependency_flag = 0
        WHERE dc.course_name = p_dependent_course;
        
    ELSEIF p_dependent_course = 'Stat 4202' THEN
        -- Stat 4202 requires Math 4530 OR Stat 4201 (Both Flag 1)
        UPDATE prerequisites p
        JOIN courses dc ON p.dependent_course_id = dc.id
        SET p.dependency_flag = 1
        WHERE dc.course_name = p_dependent_course;
        
    ELSEIF p_dependent_course = 'BusFin 3220' THEN
        -- BusFin 3220 requires ACCTMIS 2000 AND 2200 AND 2300 (All Flag 0)
        UPDATE prerequisites p
        JOIN courses dc ON p.dependent_course_id = dc.id
        SET p.dependency_flag = 0
        WHERE dc.course_name = p_dependent_course;
        
    ELSEIF p_dependent_course = 'Math 3589' THEN
        -- Math 3589 mixed case: Math 3345 (AND) + (Math 4530 OR Stat 4201)
        UPDATE prerequisites p
        JOIN courses dc ON p.dependent_course_id = dc.id
        JOIN courses pc ON p.prerequisite_course_id = pc.id
        SET p.dependency_flag = CASE 
            WHEN pc.course_name = 'Math 3345' THEN 0  -- AND requirement
            ELSE 1  -- OR group requirement
        END
        WHERE dc.course_name = p_dependent_course;
        
    ELSEIF dept_count = 1 THEN
        -- All prerequisites from same department - OR group (flag = 1)
        UPDATE prerequisites p
        JOIN courses dc ON p.dependent_course_id = dc.id
        SET p.dependency_flag = 1
        WHERE dc.course_name = p_dependent_course;
        
    ELSE
        -- Different departments - all are AND requirements (flag = 0)
        UPDATE prerequisites p
        JOIN courses dc ON p.dependent_course_id = dc.id
        SET p.dependency_flag = 0
        WHERE dc.course_name = p_dependent_course;
    END IF;
END//
DELIMITER ;

DELIMITER //
CREATE PROCEDURE generate_dag_report()
BEGIN
    SELECT 
        'DAG Edge List Report (MySQL)' as report_title,
        '===========================' as separator,
        '' as empty_line,
        CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS dag_edge,
        CASE 
            WHEN p.dependency_flag = 0 THEN 'AND requirement'
            WHEN p.dependency_flag = 1 THEN 'OR group requirement'
        END as requirement_type
    FROM prerequisites p
    JOIN courses dc ON p.dependent_course_id = dc.id
    JOIN courses pc ON p.prerequisite_course_id = pc.id
    ORDER BY dc.course_name, p.dependency_flag, pc.course_name;
END//
DELIMITER ;

DELIMITER //
CREATE PROCEDURE get_dag_statistics()
BEGIN
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
END//
DELIMITER ;

-- Load sample data and generate DAG
CALL clear_all_data();

-- Insert all courses
CALL insert_course_if_not_exists('Math 2153', 3, 'Major1', 'Single AND requirement');
CALL insert_course_if_not_exists('Math 2255', 3, 'Major1', 'Advanced Calculus');
CALL insert_course_if_not_exists('Math 2568', 3, 'Major1', 'Linear Algebra');
CALL insert_course_if_not_exists('Math 3607', 3, 'Major1', 'Advanced Calculus');
CALL insert_course_if_not_exists('Stat 4201', 3, 'Major1', 'Intro Statistics');
CALL insert_course_if_not_exists('Stat 4202', 3, 'Major1', 'Statistics');
CALL insert_course_if_not_exists('Math 4530', 3, 'Major1', 'Statistics');
CALL insert_course_if_not_exists('ACCTMIS 2000', 3, 'Major1', 'Financial Management');
CALL insert_course_if_not_exists('ACCTMIS 2200', 3, 'Major1', 'Accounting Principles');
CALL insert_course_if_not_exists('ACCTMIS 2300', 3, 'Major1', 'Managerial Accounting');
CALL insert_course_if_not_exists('BusFin 3220', 3, 'Major1', 'Financial Management');
CALL insert_course_if_not_exists('Math 3345', 3, 'Major1', 'Mixed requirements - AND part');
CALL insert_course_if_not_exists('Math 3589', 3, 'Major1', 'Mixed requirements course');

-- Insert prerequisites
CALL insert_prerequisite('Math 2255', 'Math 2153', 0);
CALL insert_prerequisite('Math 3607', 'Math 2255', 0);
CALL insert_prerequisite('Math 3607', 'Math 2568', 0);
CALL insert_prerequisite('Stat 4202', 'Math 4530', 0);
CALL insert_prerequisite('Stat 4202', 'Stat 4201', 0);
CALL insert_prerequisite('BusFin 3220', 'ACCTMIS 2000', 0);
CALL insert_prerequisite('BusFin 3220', 'ACCTMIS 2200', 0);
CALL insert_prerequisite('BusFin 3220', 'ACCTMIS 2300', 0);
CALL insert_prerequisite('Math 3589', 'Math 3345', 0);
CALL insert_prerequisite('Math 3589', 'Math 4530', 0);
CALL insert_prerequisite('Math 3589', 'Stat 4201', 0);

-- Apply dependency flag logic
CALL analyze_dependency_flags('Math 2255');
CALL analyze_dependency_flags('Math 3607');
CALL analyze_dependency_flags('Stat 4202');
CALL analyze_dependency_flags('BusFin 3220');
CALL analyze_dependency_flags('Math 3589');

-- Display final results
SELECT '=== FINAL DAG EDGE LIST ===' as title;
SELECT 
    CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS dag_edge
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
ORDER BY dc.course_name, p.dependency_flag, pc.course_name;

-- Show statistics
CALL get_dag_statistics();
