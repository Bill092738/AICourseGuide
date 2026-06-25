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
    
    /**
     * Converts the course to XML format.
     *
     * @return XML string representation of the course
     */
    public String toXml() {
        StringBuilder xml = new StringBuilder();
        xml.append("  <course>\n");
        xml.append("    <name>").append(escapeXml(courseName)).append("</name>\n");
        xml.append("    <prerequisites>").append(escapeXml(preqCourseName)).append("</prerequisites>\n");
        xml.append("    <creditHours>").append(creditHours).append("</creditHours>\n");
        xml.append("    <category>").append(escapeXml(category)).append("</category>\n");
        xml.append("    <description>").append(escapeXml(description)).append("</description>\n");
        xml.append("  </course>");
        return xml.toString();
    }
    
    /**
     * Escapes special XML characters.
     *
     * @param value the value to escape
     * @return the escaped value
     */
    private static String escapeXml(String value) {
        if (value == null) return "";
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
    
    @Override
    public String toString() {
        return String.format("%s,%s,%d,%s,%s", courseName, preqCourseName, creditHours, category, description);
    }
}
