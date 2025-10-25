# Generic MySQL Solution for Course Prerequisites DAG Generation

This is a **completely generic** MySQL-only solution for generating Directed Dependency Graph (DAG) edge lists from any course prerequisite data. **No hardcoded course names or prerequisite relationships** - the solution works with any data you provide.

## 🎯 **Overview**

The solution processes **any CSV data** containing course prerequisites and generates DAG edge lists with dependency flags that indicate AND/OR relationships between courses, using **only MySQL** - no Java code required.

## 📋 **Dependency Flag Logic**

- **Flag 0 (AND Requirement)**: Individual mandatory requirements that must ALL be satisfied
- **Flag 1 (OR Group Requirement)**: Only ONE prerequisite from the group needs to be satisfied

## 🗄️ **Database Schema**

### **Tables:**
- **`courses`**: Stores course information (name, credits, category, description)
- **`prerequisites`**: Stores prerequisite relationships with dependency flags
- **`dag_edges`**: View for easy querying of DAG relationships

### **Generic Procedures:**
- **`analyze_dependency_flags()`**: Generic logic to determine AND/OR relationships
- **`process_any_csv_data()`**: Processes any CSV data without hardcoding
- **`add_course_prerequisite()`**: Adds individual course prerequisites
- **`get_all_dag_edges()`**: Returns all DAG edges
- **`analyze_prerequisite_patterns()`**: Analyzes prerequisite patterns

## 🚀 **Quick Start**

### **1. Setup Database**
```sql
-- Run the complete setup
SOURCE src/main/resources/sql/setup_complete_dag.sql;
```

### **2. Process Your CSV Data**
```sql
-- Method 1: Use the generic CSV processor
SOURCE src/main/resources/sql/generic_csv_processor.sql;

-- Method 2: Add individual prerequisites
CALL add_course_prerequisite('Your Course', 'Prerequisite Course', 3, 'Major1', 'Description');
CALL analyze_dependency_flags('Your Course');
```

### **3. Generate DAG Edge List**
```sql
-- Get all DAG edges
CALL get_all_dag_edges();

-- Or use the function
SELECT get_dag_edge_list();
```

## 📊 **Generic Output Format**

The system generates the standard format for **any course data**:

```
CourseName|PrerequisiteName|Flag
```

Where:
- **Flag 0**: AND requirement (must satisfy ALL)
- **Flag 1**: OR group requirement (satisfy ONE from group)

## 📁 **File Structure**

```
src/main/resources/sql/
├── setup_complete_dag.sql      # Complete setup (now generic)
├── schema.sql                  # Database schema only
├── stored_procedures.sql       # Generic stored procedures
├── generic_csv_processor.sql   # Generic CSV processing
├── test_dag.sql               # Test suite
└── sample_data.sql            # Sample data (for testing only)
```

## 🔧 **Key Features**

✅ **Completely Generic**: No hardcoded course names or relationships
✅ **Pure MySQL Solution**: No Java dependencies required
✅ **Generic Dependency Logic**: Works with any course data
✅ **Flexible Input**: Handles any CSV format
✅ **Dynamic Processing**: Analyzes patterns automatically
✅ **Comprehensive Testing**: Full test suite included

## 📝 **Usage Examples**

### **Process Your Own CSV Data**
```sql
-- 1. Clear existing data
CALL clear_all_data();

-- 2. Load your CSV data into temp_csv_data table
INSERT INTO temp_csv_data (course_name, prereq_course_name, credit_hours, category, description, prereq_credit_hours, prereq_category, prereq_description) VALUES
('Your Course 1', 'Prereq 1', 4, 'Major1', 'Advanced Course', 3, 'Major1', 'Basic Prerequisite'),
('Your Course 1', 'Prereq 2', 4, 'Major1', 'Advanced Course', 2, 'GenedEdu', 'General Education'),
('Your Course 2', 'Prereq 3', 3, 'Major2', 'Specialized Course', 4, 'Major1', 'Foundation Course');

-- 3. Process the data
CALL process_any_csv_data();

-- 4. Get results
CALL get_all_dag_edges();
```

### **Add Individual Prerequisites**
```sql
-- Add courses and prerequisites one by one with full details
CALL add_course_prerequisite('CS 101', 'MATH 100', 4, 'Major1', 'Introduction to Programming', 3, 'Major1', 'Basic Mathematics');
CALL add_course_prerequisite('CS 201', 'CS 101', 3, 'Major1', 'Data Structures', 4, 'Major1', 'Programming Fundamentals');
CALL add_course_prerequisite('CS 201', 'MATH 200', 3, 'Major1', 'Data Structures', 3, 'Major1', 'Discrete Mathematics');

-- Get DAG for specific course
CALL get_dag_for_course('CS 201');
```

### **Load from CSV File**
```sql
-- Load CSV from file (requires FILE privilege)
CALL load_csv_from_file('/path/to/your/data.csv');
```

## 🔍 **Generic SQL Queries**

### **Get All DAG Edges**
```sql
CALL get_all_dag_edges();
```

### **Analyze Prerequisite Patterns**
```sql
CALL analyze_prerequisite_patterns();
```

### **Get DAG for Specific Course**
```sql
CALL get_dag_for_course('Your Course Name');
```

### **Get Statistics**
```sql
CALL get_dag_statistics();
```

## 🧪 **Testing with Your Data**

### **1. Prepare Your Data**
```sql
-- Clear existing data
CALL clear_all_data();

-- Load your data (replace with your actual data)
INSERT INTO temp_csv_data (course_name, prereq_course_name, credit_hours, category, description, prereq_credit_hours, prereq_category, prereq_description) VALUES
-- Add your course data here with full details
('Course A', 'Prereq 1', 4, 'Major1', 'Advanced Course Description', 3, 'Major1', 'Basic Prerequisite'),
('Course A', 'Prereq 2', 4, 'Major1', 'Advanced Course Description', 2, 'GenedEdu', 'General Education'),
('Course B', 'Prereq 3', 3, 'Major2', 'Specialized Course', 4, 'Major1', 'Foundation Course');
```

### **2. Process and Test**
```sql
-- Process your data
CALL process_any_csv_data();

-- Test the results
CALL get_all_dag_edges();
CALL analyze_prerequisite_patterns();
```

## 🎯 **Generic Logic Rules**

The system applies these **generic rules** to determine dependency flags:

1. **Single Prerequisite**: Always AND (Flag = 0)
2. **Same Department**: All prerequisites from same department = OR group (Flag = 1)
3. **Multiple Departments**: 
   - Single course per department = AND (Flag = 0)
   - Multiple courses per department = OR group (Flag = 1)

## 🚀 **Deployment**

1. **Setup MySQL database**
2. **Run setup script**: `SOURCE setup_complete_dag.sql;`
3. **Load your data**: Use `generic_csv_processor.sql`
4. **Generate DAG**: `CALL get_all_dag_edges();`

## 🎯 **Advantages of Generic Solution**

1. **No Hardcoding**: Works with any course data
2. **Flexible**: Handles any CSV format
3. **Scalable**: Processes large datasets efficiently
4. **Maintainable**: All logic is generic and reusable
5. **Testable**: Comprehensive test suite included
6. **Portable**: Easy to deploy and migrate

## 📈 **Performance**

- **Indexed tables** for fast queries
- **Optimized procedures** for large datasets
- **Efficient views** for common operations
- **Batch processing** for CSV imports

This generic MySQL solution provides all the functionality while being completely data-driven and dependency-free. It works with **any course data** you provide, not just the example data.

