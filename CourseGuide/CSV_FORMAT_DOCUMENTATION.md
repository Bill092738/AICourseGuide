# CSV Format Documentation for DAG Generation

This document explains the exact CSV input and output formats for the MySQL DAG generation solution.

## 📥 **Input CSV Format**

### **Format Specification**
```
CourseName,PreqCourseName,CreditHours,Category,Description
```

### **Field Descriptions**
- **CourseName**: The dependent course name (e.g., "Math1151")
- **PreqCourseName**: The prerequisite course name (e.g., "Math1150")
- **CreditHours**: Number of credit hours for the dependent course (e.g., 5)
- **Category**: Course category (M1/M2/GE/m for Major1/Major2/GenedEdu/Minor)
- **Description**: Course description (e.g., "Calculus II")

### **Example CSV Content**
```csv
CourseName,PreqCourseName,CreditHours,Category,Description
Math1151,Math1150,5,M1,Calculus II
Math1151,Math1148,5,M1,Calculus II
CS201,CS101,3,M1,Data Structures
CS201,Math1151,3,M1,Data Structures
CS301,CS201,4,M1,Advanced Algorithms
CS301,Math2000,4,M1,Advanced Algorithms
ENG400,ENG200,3,M1,Advanced Writing
ENG400,ENG300,3,M1,Advanced Writing
PHYS201,MATH100,4,M1,Physics I
PHYS201,PHYS101,4,M1,Physics I
```

## 📤 **Output Format**

### **Format Specification**
```
DependentClass|PrerequisiteClass|Flag
```

### **Field Descriptions**
- **DependentClass**: The course that has prerequisites
- **PrerequisiteClass**: The prerequisite course
- **Flag**: Dependency flag (0 or 1)
  - **0**: AND requirement (must satisfy ALL prerequisites with flag 0)
  - **1**: OR group requirement (satisfy ONE prerequisite from the group)

### **Example Output**
```
CS201|CS101|0
CS201|Math1151|0
CS301|CS201|0
CS301|Math2000|0
ENG400|ENG200|1
ENG400|ENG300|1
Math1151|Math1150|1
Math1151|Math1148|1
PHYS201|MATH100|1
PHYS201|PHYS101|1
```

## 🔧 **Usage Instructions**

### **1. Setup Database**
```sql
-- Run the complete setup
SOURCE src/main/resources/sql/setup_complete_dag.sql;
```

### **2. Load Your CSV Data**
```sql
-- Method 1: Load from file
CALL load_simple_csv_from_file('/path/to/your/courses.csv');

-- Method 2: Insert data directly
INSERT INTO temp_simple_csv (course_name, prereq_course_name, credit_hours, category, description) VALUES
('Math1151', 'Math1150', 5, 'M1', 'Calculus II'),
('Math1151', 'Math1148', 5, 'M1', 'Calculus II'),
('CS201', 'CS101', 3, 'M1', 'Data Structures'),
('CS201', 'Math1151', 3, 'M1', 'Data Structures');

-- Process the data
CALL process_simple_csv_data();
```

### **3. Get DAG Results**
```sql
-- Get results in pipe-separated format
CALL get_dag_pipe_format();

-- Export to CSV file
CALL export_dag_to_csv('/path/to/output/dag_edges.csv');
```

## 📊 **Dependency Flag Logic**

The system automatically determines dependency flags based on these rules:

### **Flag 0 (AND Requirements)**
- Single prerequisite for a course
- Multiple prerequisites from different departments
- Individual prerequisites that must ALL be satisfied

### **Flag 1 (OR Group Requirements)**
- Multiple prerequisites from the same department
- Prerequisites that form an OR group (satisfy ONE from the group)

## 🎯 **Examples by Scenario**

### **Example 1: Single Prerequisite (Flag 0)**
```csv
Input: CS201,CS101,3,M1,Data Structures
Output: CS201|CS101|0
```

### **Example 2: Multiple Prerequisites from Same Department (Flag 1)**
```csv
Input: 
Math1151,Math1150,5,M1,Calculus II
Math1151,Math1148,5,M1,Calculus II

Output:
Math1151|Math1150|1
Math1151|Math1148|1
```

### **Example 3: Multiple Prerequisites from Different Departments (Flag 0)**
```csv
Input:
CS201,CS101,3,M1,Data Structures
CS201,Math1151,3,M1,Data Structures

Output:
CS201|CS101|0
CS201|Math1151|0
```

## 📁 **File Structure**

```
src/main/resources/sql/
├── setup_complete_dag.sql      # Complete setup with CSV processing
├── simple_csv_processor.sql    # Simple CSV processor
├── csv_format_example.sql     # CSV format examples
└── schema.sql                  # Database schema
```

## 🚀 **Quick Start**

### **1. Prepare Your CSV File**
Create a CSV file with the format:
```csv
CourseName,PreqCourseName,CreditHours,Category,Description
Math1151,Math1150,5,M1,Calculus II
Math1151,Math1148,5,M1,Calculus II
CS201,CS101,3,M1,Data Structures
```

### **2. Run the Solution**
```sql
-- Setup database
SOURCE setup_complete_dag.sql;

-- Load your CSV
CALL load_simple_csv_from_file('/path/to/your/courses.csv');

-- Get results
CALL get_dag_pipe_format();
```

### **3. Export Results**
```sql
-- Export to CSV file
CALL export_dag_to_csv('/path/to/output/dag_edges.csv');
```

## 🔍 **Validation**

### **Input Validation**
- Course names cannot be empty
- Prerequisite names cannot be empty
- Credit hours must be positive integers
- Category must be one of: M1, M2, GE, m

### **Output Validation**
- All edges are in pipe-separated format
- Flags are either 0 or 1
- No duplicate edges
- Proper ordering by course name and flag

## 📈 **Performance**

- **Indexed tables** for fast processing
- **Batch processing** for large CSV files
- **Memory efficient** cursor-based processing
- **Optimized queries** for dependency analysis

This solution handles the exact CSV format you specified and produces the pipe-separated output format as requested.
