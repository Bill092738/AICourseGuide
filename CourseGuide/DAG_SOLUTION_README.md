# MySQL DAG Solution for Course Prerequisites

A clean MySQL solution that converts CSV course prerequisite data into a Directed Acyclic Graph (DAG) with dependency flags.

## 📥 **Input Format**

```csv
CourseName,PreqCourseName,CreditHours,Category,Description
Math1151,Math1150,5,Major1,Calculus II
Math1151,Math1148,5,Major1,Calculus II
CS201,CS101,3,Major1,Data Structures
```

## 📤 **Output Format**

```
DependentClass|PrerequisiteClass|Flag
Math1151|Math1150|1
Math1151|Math1148|1
CS201|CS101|0
```

## 🔧 **Dependency Flag Logic**

- **Flag 0 (AND)**: Individual AND requirement - must satisfy ALL prerequisites with flag 0
- **Flag 1 (OR)**: OR group requirement - satisfy ONE prerequisite from the group

## 🚀 **Quick Start**

### **1. Setup Database**
```sql
SOURCE src/main/resources/sql/dag_solution.sql;
```

### **2. Load Your Data**
```sql
-- Method 1: From CSV file
CALL load_csv_from_file('/path/to/your/courses.csv');

-- Method 2: Insert directly
INSERT INTO temp_csv_data (course_name, prereq_course_name, credit_hours, category, description) VALUES
('Math1151', 'Math1150', 5, 'Major1', 'Calculus II'),
('Math1151', 'Math1148', 5, 'Major1', 'Calculus II');

CALL process_csv_data();
```

### **3. Get Results**
```sql
-- Display results
CALL get_dag_output();

-- Export to file
CALL export_dag_to_file('/path/to/output/dag_edges.csv');
```

## 📊 **Example Output**

Based on the sample data, you'll get:

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

## 🎯 **Key Features**

✅ **Generic Logic** - No hardcoded course names or values
✅ **CSV Input** - Standard CSV format with 5 fields
✅ **Pipe-Separated Output** - Clean, parseable format
✅ **File Processing** - Load from CSV files
✅ **Export Capability** - Save results to files
✅ **Dependency Analysis** - Automatic flag determination

## 📁 **Files**

- `dag_solution.sql` - Complete setup and sample data
- `usage_example.sql` - How to use with your own data

## 🔍 **Usage Examples**

### **Load from CSV File:**
```sql
CALL load_csv_from_file('/path/to/courses.csv');
CALL get_dag_output();
```

### **Insert Data Directly:**
```sql
INSERT INTO temp_csv_data (course_name, prereq_course_name, credit_hours, category, description) VALUES
('Math1151', 'Math1150', 5, 'Major1', 'Calculus II'),
('Math1151', 'Math1148', 5, 'Major1', 'Calculus II');

CALL process_csv_data();
CALL get_dag_output();
```

### **Export Results:**
```sql
CALL export_dag_to_file('/tmp/dag_output.csv');
```

This solution is completely generic and handles any course data without hardcoded values!
