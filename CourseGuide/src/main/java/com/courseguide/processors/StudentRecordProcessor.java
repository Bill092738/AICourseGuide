package com.courseguide.processors;

import com.courseguide.utils.CSVUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StudentRecordProcessor {
    public record StudentRecord(String firstName, String lastName, String major, double gpa) {}

    public List<StudentRecord> loadFromCSV(Path path) throws IOException {
        List<StudentRecord> records = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] cols = CSVUtils.split(line);
            if (cols.length < 4) continue;
            double gpa = 0.0;
            try { gpa = Double.parseDouble(cols[3].trim()); } catch (Exception ignore) {}
            records.add(new StudentRecord(cols[0].trim(), cols[1].trim(), cols[2].trim(), gpa));
        }
        return records;
    }
}
