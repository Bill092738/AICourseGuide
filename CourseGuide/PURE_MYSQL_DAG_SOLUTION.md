# Pure MySQL Solution for Course Prerequisites DAG Generation

This is a complete MySQL-only solution for generating Directed Dependency Graph (DAG) edge lists from course prerequisite data, implementing the revised dependency flag logic.

## 🎯 **Overview**

The solution processes CSV data containing course prerequisites and generates DAG edge lists with dependency flags that indicate AND/OR relationships between courses, using **only MySQL** - no Java code required.

## 📋 **Dependency Flag Logic**

- **Flag 0 (AND Requirement)**: Individual mandatory requirements that must ALL be satisfied
- **Flag 1 (OR Group Requirement)**: Only ONE prerequisite from the group needs to be satisfied

## 🗄️ **Database Schema**

### **Tables:**
- **`courses`**: Stores course information (name, credits, category, description)
- **`prerequisites`**: Stores prerequisite relationships with dependency flags
- **`dag_edges`**: View for easy querying of DAG relationships

### **Functions & Procedures:**
- **`get_dag_edge_list()`**: MySQL function that returns formatted DAG edge list
- **`analyze_dependency_flags()`**: Determines AND/OR relationships
- **`clear_all_data()`**: Clears existing data
- **`generate_dag_report()`**: Generates comprehensive DAG report

## 🚀 **Quick Start**

### **1. Setup Database**
```sql
-- Run the complete setup
SOURCE src/main/resources/sql/setup_complete_dag.sql;
```

### **2. Generate DAG Edge List**
```sql
-- Get formatted DAG edge list
SELECT get_dag_edge_list();

-- Or query directly
SELECT CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS dag_edge
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
ORDER BY dc.course_name, p.dependency_flag, pc.course_name;
```

## 📊 **Expected Output**

The system generates the exact format specified in your requirements:

```
Math 2255|Math 2153|0        // Single AND requirement
Math 3607|Math 2255|0        // Requires 2255 AND 2568 (Both Flag 0)
Math 3607|Math 2568|0
Stat 4202|Math 4530|1        // Requires 4530 OR 4201 (Both Flag 1)
Stat 4202|Stat 4201|1
BusFin 3220|ACCTMIS 2000|0   // Requires 2000 AND 2200 AND 2300 (All Flag 0)
BusFin 3220|ACCTMIS 2200|0
BusFin 3220|ACCTMIS 2300|0
Math 3589|Math 3345|0        // AND Part: Must satisfy 3345 (Flag 0)
Math 3589|Math 4530|1        // OR Part: Must satisfy (4530 OR 4201) (Flag 1 Group)
Math 3589|Stat 4201|1
```

## 📁 **File Structure**

```
src/main/resources/sql/
├── setup_complete_dag.sql      # Complete setup with sample data
├── schema.sql                  # Database schema only
├── stored_procedures.sql       # All stored procedures
├── csv_import.sql             # CSV processing procedures
├── test_dag.sql               # Comprehensive test suite
└── sample_data.sql            # Sample data insertion
```

## 🔧 **Key Features**

✅ **Pure MySQL Solution**: No Java dependencies required
✅ **Correct Dependency Logic**: Implements the revised AND/OR relationship logic
✅ **Expected Output Format**: Three pipe-separated fields per line
✅ **Comprehensive Testing**: Full test suite with verification
✅ **CSV Processing**: Handles CSV data import and processing
✅ **Sample Data**: Predefined test data matching expected output
✅ **Stored Procedures**: Modular, reusable database logic
✅ **Functions**: MySQL functions for formatted output

## 📝 **Usage Examples**

### **Basic DAG Generation**
```sql
-- Run complete setup
SOURCE setup_complete_dag.sql;

-- Get DAG edge list
SELECT get_dag_edge_list();
```

### **Custom Data Processing**
```sql
-- Clear existing data
CALL clear_all_data();

-- Insert your courses
INSERT INTO courses (course_name, credit_hours, category) VALUES
('Your Course', 3, 'Major1');

-- Insert prerequisites
CALL insert_prerequisite('Your Course', 'Prerequisite Course', 0);

-- Analyze dependency flags
CALL analyze_dependency_flags('Your Course');

-- Get results
SELECT CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS dag_edge
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id;
```

### **Testing**
```sql
-- Run comprehensive tests
SOURCE test_dag.sql;
```

## 🔍 **SQL Queries for Analysis**

### **Get All DAG Edges**
```sql
SELECT 
    CONCAT(dc.course_name, '|', pc.course_name, '|', p.dependency_flag) AS dag_edge
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
JOIN courses pc ON p.prerequisite_course_id = pc.id
ORDER BY dc.course_name, p.dependency_flag, pc.course_name;
```

### **Analyze Prerequisite Patterns**
```sql
SELECT 
    dc.course_name as dependent_course,
    COUNT(*) as total_prerequisites,
    SUM(CASE WHEN p.dependency_flag = 0 THEN 1 ELSE 0 END) as and_requirements,
    SUM(CASE WHEN p.dependency_flag = 1 THEN 1 ELSE 0 END) as or_requirements
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
GROUP BY dc.course_name
ORDER BY dc.course_name;
```

### **Find Mixed Requirements**
```sql
SELECT 
    dc.course_name as dependent_course,
    SUM(CASE WHEN p.dependency_flag = 0 THEN 1 ELSE 0 END) as and_count,
    SUM(CASE WHEN p.dependency_flag = 1 THEN 1 ELSE 0 END) as or_count
FROM prerequisites p
JOIN courses dc ON p.dependent_course_id = dc.id
GROUP BY dc.course_name
HAVING and_count > 0 AND or_count > 0;
```

## 🧪 **Testing**

The solution includes comprehensive tests that verify:
- ✅ Correct dependency flag assignment
- ✅ Expected output format
- ✅ All example cases (Math 3607, Stat 4202, BusFin 3220, Math 3589)
- ✅ Edge count verification
- ✅ Flag distribution analysis

## 📈 **Performance**

- **Indexed tables** for fast queries
- **Optimized procedures** for large datasets
- **Efficient views** for common operations
- **Batch processing** for CSV imports

## 🎯 **Advantages of Pure MySQL Solution**

1. **No Dependencies**: Runs on any MySQL server
2. **Performance**: Database-optimized operations
3. **Scalability**: Handles large datasets efficiently
4. **Maintainability**: All logic in SQL procedures
5. **Portability**: Easy to deploy and migrate
6. **Testing**: Comprehensive test suite included

## 🚀 **Deployment**

1. **Setup MySQL database**
2. **Run setup script**: `SOURCE setup_complete_dag.sql;`
3. **Test the solution**: `SOURCE test_dag.sql;`
4. **Use the functions**: `SELECT get_dag_edge_list();`

This pure MySQL solution provides all the functionality of the original Java implementation while being completely database-driven and dependency-free.
