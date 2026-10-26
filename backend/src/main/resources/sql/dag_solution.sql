-- MySQL DAG Solution for Course Prerequisites
-- Input: CSV format - CourseName,PreqCourseName,CreditHours,Category,Description
-- Output: Pipe-separated format - DependentClass|PrerequisiteClass|Flag

-- Create database
CREATE DATABASE IF NOT EXISTS courseguide_dag;
USE courseguide_dag;

-- Drop existing objects
DROP FUNCTION IF EXISTS get_dag_edge_list;
DROP PROCEDURE IF EXISTS clear_all_data;
DROP PROCEDURE IF EXISTS process_csv_data;
DROP PROCEDURE IF EXISTS analyze_dependency_flags;
DROP PROCEDURE IF EXISTS get_dag_output;
DROP PROCEDURE IF EXISTS load_csv_from_file;
DROP PROCEDURE IF EXISTS export_dag_to_file;
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

-- Create view for DAG edges
CREATE VIEW dag_edges AS
SELECT 
    dc.course_name AS dependent_class,
    pc.course_name AS prerequisite_class,
    p.dependency_flag
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
ORDER BY dc.course_name, p.dependency_flag, pc.course_name;

-- Create function to get DAG edge list
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

-- Procedure to clear all data
DELIMITER //
CREATE PROCEDURE clear_all_data()
BEGIN
    DELETE FROM prerequisites;
    DELETE FROM courses;
    ALTER TABLE courses AUTO_INCREMENT = 1;
    ALTER TABLE prerequisites AUTO_INCREMENT = 1;
END//
DELIMITER ;

-- Procedure to analyze dependency flags (generic logic)
DELIMITER //
CREATE PROCEDURE analyze_dependency_flags(
    IN p_dependent_course VARCHAR(100)
)
BEGIN
    DECLARE prereq_count INT DEFAULT 0;
    DECLARE dept_count INT DEFAULT 0;
    DECLARE done INT DEFAULT FALSE;
    DECLARE prereq_name VARCHAR(100);
    DECLARE prereq_dept VARCHAR(50);
    DECLARE dept_prereq_count INT DEFAULT 0;
    
    -- Cursor to get all prerequisites for the dependent course
    DECLARE prereq_cursor CURSOR FOR
        SELECT DISTINCT pc.course_name, 
               SUBSTRING_INDEX(pc.course_name, ' ', 1) as dept
        FROM prerequisites p
        JOIN courses dc ON p.dependent_course_id = dc.id
        JOIN courses pc ON p.prerequisite_course_id = pc.id
        WHERE dc.course_name = p_dependent_course;
    
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    
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
    
    -- Apply generic dependency flag logic
    IF prereq_count = 1 THEN
        -- Single prerequisite - always AND (flag = 0)
        UPDATE prerequisites p
        JOIN courses dc ON p.dependent_course_id = dc.id
        SET p.dependency_flag = 0
        WHERE dc.course_name = p_dependent_course;
        
    ELSEIF dept_count = 1 THEN
        -- All prerequisites from same department - OR group (flag = 1)
        UPDATE prerequisites p
        JOIN courses dc ON p.dependent_course_id = dc.id
        SET p.dependency_flag = 1
        WHERE dc.course_name = p_dependent_course;
        
    ELSE
        -- Multiple departments - analyze each department group
        OPEN prereq_cursor;
        
        prereq_loop: LOOP
            FETCH prereq_cursor INTO prereq_name, prereq_dept;
            IF done THEN
                LEAVE prereq_loop;
            END IF;
            
            -- Count prerequisites in this department
            SELECT COUNT(*) INTO dept_prereq_count
            FROM prerequisites p
            JOIN courses dc ON p.dependent_course_id = dc.id
            JOIN courses pc ON p.prerequisite_course_id = pc.id
            WHERE dc.course_name = p_dependent_course 
            AND SUBSTRING_INDEX(pc.course_name, ' ', 1) = prereq_dept;
            
            -- Set flag based on department count
            IF dept_prereq_count = 1 THEN
                -- Single course in this department - AND requirement (flag = 0)
                UPDATE prerequisites p
                JOIN courses dc ON p.dependent_course_id = dc.id
                JOIN courses pc ON p.prerequisite_course_id = pc.id
                SET p.dependency_flag = 0
                WHERE dc.course_name = p_dependent_course 
                AND SUBSTRING_INDEX(pc.course_name, ' ', 1) = prereq_dept;
            ELSE
                -- Multiple courses in same department - OR group (flag = 1)
                UPDATE prerequisites p
                JOIN courses dc ON p.dependent_course_id = dc.id
                JOIN courses pc ON p.prerequisite_course_id = pc.id
                SET p.dependency_flag = 1
                WHERE dc.course_name = p_dependent_course 
                AND SUBSTRING_INDEX(pc.course_name, ' ', 1) = prereq_dept;
            END IF;
        END LOOP;
        
        CLOSE prereq_cursor;
    END IF;
END//
DELIMITER ;

-- Procedure to process CSV data
DELIMITER //
CREATE PROCEDURE process_csv_data()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_course_name VARCHAR(100);
    DECLARE v_prereq_name VARCHAR(100);
    DECLARE v_credit_hours INT;
    DECLARE v_category VARCHAR(20);
    DECLARE v_description TEXT;
    
    -- Cursor for processing CSV data
    DECLARE csv_cursor CURSOR FOR
        SELECT DISTINCT course_name, prereq_course_name, credit_hours, category, description
        FROM temp_csv_data
        WHERE course_name IS NOT NULL AND prereq_course_name IS NOT NULL AND prereq_course_name != '';
    
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    
    -- Clear existing data
    CALL clear_all_data();
    
    -- Insert all unique courses first
    INSERT IGNORE INTO courses (course_name, credit_hours, category, description)
    SELECT DISTINCT course_name, credit_hours, category, description
    FROM temp_csv_data
    WHERE course_name IS NOT NULL;
    
    -- Insert prerequisite courses (use default values for missing data)
    INSERT IGNORE INTO courses (course_name, credit_hours, category, description)
    SELECT DISTINCT prereq_course_name, 3, 'Major1', 'Prerequisite course'
    FROM temp_csv_data
    WHERE prereq_course_name IS NOT NULL AND prereq_course_name != '';
    
    -- Process prerequisites
    OPEN csv_cursor;
    
    csv_loop: LOOP
        FETCH csv_cursor INTO v_course_name, v_prereq_name, v_credit_hours, v_category, v_description;
        IF done THEN
            LEAVE csv_loop;
        END IF;
        
        -- Insert prerequisite with default flag (will be updated later)
        INSERT INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag)
        SELECT dc.id, pc.id, 0
        FROM courses dc, courses pc 
        WHERE dc.course_name = v_course_name AND pc.course_name = v_prereq_name;
    END LOOP;
    
    CLOSE csv_cursor;
    
    -- Analyze dependency flags for each dependent course
    DECLARE course_cursor CURSOR FOR
        SELECT DISTINCT course_name
        FROM temp_csv_data
        WHERE course_name IS NOT NULL;
    
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = FALSE;
    
    OPEN course_cursor;
    
    course_loop: LOOP
        FETCH course_cursor INTO v_course_name;
        IF done THEN
            LEAVE course_loop;
        END IF;
        
        CALL analyze_dependency_flags(v_course_name);
    END LOOP;
    
    CLOSE course_cursor;
    
END//
DELIMITER ;

-- Procedure to get DAG output in pipe-separated format
DELIMITER //
CREATE PROCEDURE get_dag_output()
BEGIN
    SELECT 
        CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS dag_edge
    FROM prerequisites p
    JOIN courses dc ON p.dependent_course_id = dc.id
    JOIN courses pc ON p.prerequisite_course_id = pc.id
    ORDER BY dc.course_name, p.dependency_flag, pc.course_name;
END//
DELIMITER ;

-- Procedure to load CSV from file
DELIMITER //
CREATE PROCEDURE load_csv_from_file(IN file_path VARCHAR(500))
BEGIN
    -- Clear temporary table
    DELETE FROM temp_csv_data;
    
    -- Load CSV data
    SET @sql = CONCAT('
        LOAD DATA INFILE ''', file_path, '''
        INTO TABLE temp_csv_data
        FIELDS TERMINATED BY '',''
        LINES TERMINATED BY ''\n''
        IGNORE 1 ROWS
        (course_name, prereq_course_name, credit_hours, category, description)
    ');
    
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
    
    -- Process the loaded data
    CALL process_csv_data();
END//
DELIMITER ;

-- Procedure to export DAG to file
DELIMITER //
CREATE PROCEDURE export_dag_to_file(IN output_file VARCHAR(500))
BEGIN
    SET @sql = CONCAT('
        SELECT CONCAT(dc.course_name, ''|'', pc.course_name, ''|'', p.dependency_flag) AS dag_edge
        FROM prerequisites p
        JOIN courses dc ON p.dependent_course_id = dc.id
        JOIN courses pc ON p.prerequisite_course_id = pc.id
        ORDER BY dc.course_name, p.dependency_flag, pc.course_name
        INTO OUTFILE ''', output_file, '''
        FIELDS TERMINATED BY ''''
        LINES TERMINATED BY ''\n''
    ');
    
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END//
DELIMITER ;

-- Create temporary table for CSV data
CREATE TEMPORARY TABLE IF NOT EXISTS temp_csv_data (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_name VARCHAR(100),
    prereq_course_name VARCHAR(100),
    credit_hours INT,
    category VARCHAR(20),
    description TEXT
);

-- Load sample data and generate DAG
DELETE FROM temp_csv_data;

-- Insert sample data in the format: CourseName,PreqCourseName,CreditHours,Category,Description
INSERT INTO temp_csv_data (course_name, prereq_course_name, credit_hours, category, description) VALUES
('Math 2255', 'Math 2153', 3, 'Major1', 'Single AND requirement'),
('Math 3607', 'Math 2255', 3, 'Major1', 'Advanced Calculus'),
('Math 3607', 'Math 2568', 3, 'Major1', 'Linear Algebra'),
('Stat 4202', 'Math 4530', 3, 'Major1', 'Statistics'),
('Stat 4202', 'Stat 4201', 3, 'Major1', 'Intro Statistics'),
('BusFin 3220', 'ACCTMIS 2000', 3, 'Major1', 'Financial Management'),
('BusFin 3220', 'ACCTMIS 2200', 3, 'Major1', 'Accounting Principles'),
('BusFin 3220', 'ACCTMIS 2300', 3, 'Major1', 'Managerial Accounting'),
('Math 3589', 'Math 3345', 3, 'Major1', 'Mixed requirements - AND part'),
('Math 3589', 'Math 4530', 3, 'Major1', 'Mixed requirements - OR part'),
('Math 3589', 'Stat 4201', 3, 'Major1', 'Mixed requirements - OR part');

-- Process the data
CALL process_csv_data();

-- Display final results in pipe-separated format
SELECT '=== DAG EDGE LIST (PIPE-SEPARATED FORMAT) ===' as title;
CALL get_dag_output();
