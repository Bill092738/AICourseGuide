-- Simple CSV Processor for DAG Generation
-- Handles CSV format: CourseName,PreqCourseName,CreditHours,Category,Description
-- Output format: DependentClass|PrerequisiteClass|Flag

-- Create temporary table for simple CSV data
CREATE TEMPORARY TABLE IF NOT EXISTS temp_simple_csv (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_name VARCHAR(100),
    prereq_course_name VARCHAR(100),
    credit_hours INT,
    category VARCHAR(20),
    description TEXT
);

-- Procedure to process simple CSV data
DELIMITER //
CREATE PROCEDURE process_simple_csv_data()
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
        FROM temp_simple_csv
        WHERE course_name IS NOT NULL AND prereq_course_name IS NOT NULL AND prereq_course_name != '';
    
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    
    -- Clear existing data
    CALL clear_all_data();
    
    -- Insert all unique courses first
    INSERT IGNORE INTO courses (course_name, credit_hours, category, description)
    SELECT DISTINCT course_name, credit_hours, category, description
    FROM temp_simple_csv
    WHERE course_name IS NOT NULL;
    
    -- Insert prerequisite courses (use default values for missing data)
    INSERT IGNORE INTO courses (course_name, credit_hours, category, description)
    SELECT DISTINCT prereq_course_name, 3, 'Major1', 'Prerequisite course'
    FROM temp_simple_csv
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
        FROM temp_simple_csv
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

-- Procedure to load CSV from file
DELIMITER //
CREATE PROCEDURE load_simple_csv_from_file(IN file_path VARCHAR(500))
BEGIN
    -- Clear temporary table
    DELETE FROM temp_simple_csv;
    
    -- Load CSV data
    SET @sql = CONCAT('
        LOAD DATA INFILE ''', file_path, '''
        INTO TABLE temp_simple_csv
        FIELDS TERMINATED BY '',''
        LINES TERMINATED BY ''\n''
        IGNORE 1 ROWS
        (course_name, prereq_course_name, credit_hours, category, description)
    ');
    
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
    
    -- Process the loaded data
    CALL process_simple_csv_data();
END//
DELIMITER ;

-- Procedure to get DAG output in pipe-separated format
DELIMITER //
CREATE PROCEDURE get_dag_pipe_format()
BEGIN
    SELECT 
        CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS dag_edge
    FROM prerequisites p
    JOIN courses dc ON p.dependent_course_id = dc.id
    JOIN courses pc ON p.prerequisite_course_id = pc.id
    ORDER BY dc.course_name, p.dependency_flag, pc.course_name;
END//
DELIMITER ;

-- Procedure to export DAG to CSV file
DELIMITER //
CREATE PROCEDURE export_dag_to_csv(IN output_file VARCHAR(500))
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

-- Example usage with sample data
-- Clear and load sample data
DELETE FROM temp_simple_csv;

-- Insert sample data in the format: CourseName,PreqCourseName,CreditHours,Category,Description
INSERT INTO temp_simple_csv (course_name, prereq_course_name, credit_hours, category, description) VALUES
('Math1151', 'Math1150', 5, 'M1', 'Calculus II'),
('Math1151', 'Math1148', 5, 'M1', 'Calculus II'),
('CS201', 'CS101', 3, 'M1', 'Data Structures'),
('CS201', 'Math1151', 3, 'M1', 'Data Structures'),
('CS301', 'CS201', 4, 'M1', 'Advanced Algorithms'),
('CS301', 'Math2000', 4, 'M1', 'Advanced Algorithms'),
('ENG400', 'ENG200', 3, 'M1', 'Advanced Writing'),
('ENG400', 'ENG300', 3, 'M1', 'Advanced Writing');

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
