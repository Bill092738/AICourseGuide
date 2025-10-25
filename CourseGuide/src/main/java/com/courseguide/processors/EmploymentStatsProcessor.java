package com.courseguide.processors;

import java.util.DoubleSummaryStatistics;
import java.util.List;

public class EmploymentStatsProcessor {
    public record Stats(long count, double minGpa, double avgGpa, double maxGpa) {}

    public Stats summarizeGpa(List<StudentRecordProcessor.StudentRecord> students) {
        DoubleSummaryStatistics s = students.stream().mapToDouble(StudentRecordProcessor.StudentRecord::gpa).summaryStatistics();
        return new Stats(s.getCount(), s.getMin(), s.getAverage(), s.getMax());
    }
}
