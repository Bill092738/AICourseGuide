# MySQL-based DAG Generation for Course Prerequisites

This implementation provides a MySQL-based solution for generating Directed Dependency Graph (DAG) edge lists from course prerequisite data.

## Overview

The system processes CSV data containing course prerequisites and generates DAG edge lists with dependency flags that indicate AND/OR relationships between courses.

## Database Schema

### Tables

1. **courses** - Stores course information
   - `id` (Primary Key)
   - `course_name` (VARCHAR)
   - `credit_hours` (INT)
   - `category` (ENUM: Major1, Major2, GenedEdu, Minor)
   - `description` (TEXT)

2. **prerequisites** - Stores prerequisite relationships
   - `id` (Primary Key)
   - `dependent_course_id` (Foreign Key to courses)
   - `prerequisite_course_id` (Foreign Key to courses)
   - `dependency_flag` (TINYINT: 0=AND, 1=OR)

### Views and Functions

- **dag_edges** - View for easy querying of DAG relationships
- **get_dag_edge_list()** - MySQL function that returns formatted DAG edge list

## Dependency Flag Logic

- **Flag 0 (AND Requirement)**: Individual mandatory requirements that must ALL be satisfied
- **Flag 1 (OR Group Requirement)**: Only ONE prerequisite from the group needs to be satisfied

## API Endpoints

### MySQL-based DAG Generation

1. **POST /api/dag/mysql/generate**
   - Generates DAG from CSV data using MySQL
   - Input: `csvContent` or `filePath`
   - Output: DAG edge list with dependency flags

2. **GET /api/dag/mysql/sample**
   - Returns sample DAG using predefined test data
   - Demonstrates the expected output format

3. **GET /api/dag/mysql/function**
   - Uses MySQL function to generate DAG edge list
   - Returns formatted string output

## Setup Instructions

### 1. Database Setup

```sql
-- Create database
CREATE DATABASE courseguide_dag;
USE courseguide_dag;

-- Run schema.sql to create tables and functions
SOURCE src/main/resources/sql/schema.sql;

-- Load sample data (optional)
SOURCE src/main/resources/sql/sample_data.sql;
```

### 2. Configuration

Update `application-mysql.properties` with your MySQL connection details:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/courseguide_dag?useSSL=false&serverTimezone=UTC
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### 3. Dependencies

The following dependencies are included in `pom.xml`:

```xml
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

## Example Usage

### Sample CSV Data

```csv
CourseName,PreqCourseName,CreditHours,Category,Description
Math 2255,Math 2153,3,Major1,Single AND requirement
Math 3607,Math 2255,3,Major1,Advanced Calculus
Math 3607,Math 2568,3,Major1,Linear Algebra
Stat 4202,Math 4530,3,Major1,Statistics
Stat 4202,Stat 4201,3,Major1,Intro Statistics
```

### Expected Output

```
Math 2255|Math 2153|0
Math 3607|Math 2255|0
Math 3607|Math 2568|0
Stat 4202|Math 4530|1
Stat 4202|Stat 4201|1
```

## SQL Queries

### Basic DAG Query

```sql
SELECT 
    CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS dag_edge
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
ORDER BY dc.course_name, p.dependency_flag, pc.course_name;
```

### Analysis Queries

```sql
-- Find courses with mixed requirements
SELECT 
    dc.course_name as dependent_course,
    SUM(CASE WHEN p.dependency_flag = 0 THEN 1 ELSE 0 END) as and_count,
    SUM(CASE WHEN p.dependency_flag = 1 THEN 1 ELSE 0 END) as or_count
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
GROUP BY dc.course_name
HAVING and_count > 0 AND or_count > 0;
```

## Key Features

✅ **MySQL Integration**: Full database support for course and prerequisite management
✅ **Dependency Flag Logic**: Correct implementation of AND/OR relationship logic
✅ **SQL Functions**: Custom MySQL functions for DAG generation
✅ **API Integration**: RESTful endpoints for MySQL-based DAG generation
✅ **CSV Processing**: Handles CSV data import and processing
✅ **Sample Data**: Predefined test data matching expected output

## File Structure

```
src/main/java/com/courseguide/
├── processors/
│   ├── DAGProcessor.java          # Original Java-based processor
│   └── MySQLDAGProcessor.java    # MySQL-based processor
├── models/
│   ├── Course.java               # Course model
│   └── DAGEdge.java             # DAG edge model
└── ApiController.java            # Updated with MySQL endpoints

src/main/resources/
├── sql/
│   ├── schema.sql               # Database schema
│   ├── sample_data.sql         # Sample data
│   └── dag_queries.sql         # SQL queries
└── application-mysql.properties # MySQL configuration
```

## Testing

Use the sample endpoint to verify the implementation:

```bash
curl http://localhost:8080/api/dag/mysql/sample
```

This will return the expected DAG edge list format with proper dependency flags.
