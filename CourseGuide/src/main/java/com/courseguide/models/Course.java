package com.courseguide.models;

public class Course {
    private String courseName;
    private String preqCourseName;
    private int creditHours;
    private String category;
    private String description;
    
    public Course(String courseName, String preqCourseName, int creditHours, String category, String description) {
        this.courseName = courseName;
        this.preqCourseName = preqCourseName;
        this.creditHours = creditHours;
        this.category = category;
        this.description = description;
    }
    
    // Getters
    public String getCourseName() { return courseName; }
    public String getPreqCourseName() { return preqCourseName; }
    public int getCreditHours() { return creditHours; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    
    // Setters
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public void setPreqCourseName(String preqCourseName) { this.preqCourseName = preqCourseName; }
    public void setCreditHours(int creditHours) { this.creditHours = creditHours; }
    public void setCategory(String category) { this.category = category; }
    public void setDescription(String description) { this.description = description; }
    
    @Override
    public String toString() {
        return String.format("%s,%s,%d,%s,%s", courseName, preqCourseName, creditHours, category, description);
    }
}
