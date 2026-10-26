-- MySQL Database Schema for Course Prerequisites DAG
-- Pure MySQL solution for generating Directed Dependency Graph edge lists

-- Create database (uncomment if needed)
-- CREATE DATABASE courseguide_dag;
-- USE courseguide_dag;

-- Courses table to store course information
CREATE TABLE IF NOT EXISTS courses (
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

-- Prerequisites table to store prerequisite relationships
CREATE TABLE IF NOT EXISTS prerequisites (
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

-- DAG edges view for easy querying
CREATE OR REPLACE VIEW dag_edges AS
SELECT 
    dc.course_name AS dependent_class,
    pc.course_name AS prerequisite_class,
    p.dependency_flag
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
ORDER BY dc.course_name, p.dependency_flag, pc.course_name;

-- Function to get DAG edge list in pipe-separated format
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
