-- MySQL Script for CSV Import and DAG Generation
-- Pure MySQL solution for processing CSV data and generating DAG edge lists

-- Create temporary table for CSV data
CREATE TEMPORARY TABLE IF NOT EXISTS temp_csv_data (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_name VARCHAR(100),
    prereq_course_name VARCHAR(100),
    credit_hours INT,
    category VARCHAR(20),
    description TEXT
);

-- Procedure to load CSV data from file (requires FILE privilege)
DELIMITER //
CREATE PROCEDURE load_csv_from_file(IN file_path VARCHAR(500))
BEGIN
    -- Clear temporary table
    DELETE FROM temp_csv_data;
    
    -- Load CSV data (adjust path and format as needed)
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
END//
DELIMITER ;

-- Procedure to process CSV data and generate DAG
DELIMITER //
CREATE PROCEDURE process_csv_to_dag()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_course_name VARCHAR(100);
    DECLARE v_prereq_name VARCHAR(100);
    DECLARE v_credit_hours INT;
    DECLARE v_category VARCHAR(20);
    DECLARE v_description TEXT;
    DECLARE v_dependency_flag TINYINT;
    
    -- Cursor for CSV data
    DECLARE csv_cursor CURSOR FOR
        SELECT course_name, prereq_course_name, credit_hours, category, description
        FROM temp_csv_data
        WHERE prereq_course_name IS NOT NULL AND prereq_course_name != '';
    
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    
    -- Clear existing data
    CALL clear_all_data();
    
    -- Insert all unique courses first
    INSERT IGNORE INTO courses (course_name, credit_hours, category, description)
    SELECT DISTINCT course_name, credit_hours, category, description
    FROM temp_csv_data
    WHERE course_name IS NOT NULL;
    
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

-- Procedure to generate complete DAG solution
DELIMITER //
CREATE PROCEDURE generate_complete_dag()
BEGIN
    -- Process sample data
    CALL process_csv_to_dag();
    
    -- Display results
    SELECT 'DAG Edge List Generated Successfully' as status;
    
    -- Show DAG edges
    SELECT 
        CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS dag_edge,
        CASE 
            WHEN p.dependency_flag = 0 THEN 'AND requirement'
            WHEN p.dependency_flag = 1 THEN 'OR group requirement'
        END as requirement_type
    FROM prerequisites p
    JOIN courses dc ON p.dependent_course_id = dc.id
    JOIN courses pc ON p.prerequisite_course_id = pc.id
    ORDER BY dc.course_name, p.dependency_flag, pc.course_name;
    
    -- Show statistics
    CALL get_dag_statistics();
END//
DELIMITER ;

-- Sample data insertion for testing
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

-- Execute the complete DAG generation
CALL generate_complete_dag();
