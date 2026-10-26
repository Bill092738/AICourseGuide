package com.courseguide.processors;

import com.courseguide.processors.StudentRecordProcessor.StudentRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class StudentRecordProcessorTest {

    @TempDir
    Path tmp;

    private final StudentRecordProcessor processor = new StudentRecordProcessor();

    @Test
    void loadsStudentsAndDefaultsMissingValues() throws Exception {
        Path xml = Files.writeString(tmp.resolve("students.xml"), """
                <students>
                  <student><firstName> Ada </firstName><lastName>Lovelace</lastName><major>CS</major><gpa>3.9</gpa></student>
                  <student><firstName>Alan</firstName><gpa>not-a-number</gpa></student>
                  <student></student>
                </students>
                """);

        List<StudentRecord> records = processor.loadFromXML(xml);

        assertThat(records).containsExactly(
                new StudentRecord("Ada", "Lovelace", "CS", 3.9),
                new StudentRecord("Alan", "", "", 0.0),
                new StudentRecord("", "", "", 0.0));
    }

    @Test
    void wrapsParseErrorsInIOException() throws Exception {
        Path bad = Files.writeString(tmp.resolve("bad.xml"), "<students><student>");

        assertThatThrownBy(() -> processor.loadFromXML(bad)).isInstanceOf(IOException.class);
    }

    @Test
    void summarizesGpa() {
        EmploymentStatsProcessor.Stats stats = new EmploymentStatsProcessor().summarizeGpa(List.of(
                new StudentRecord("a", "", "", 2.0),
                new StudentRecord("b", "", "", 3.0),
                new StudentRecord("c", "", "", 4.0)));

        assertThat(stats.count()).isEqualTo(3);
        assertThat(stats.minGpa()).isEqualTo(2.0);
        assertThat(stats.avgGpa()).isCloseTo(3.0, within(1e-9));
        assertThat(stats.maxGpa()).isEqualTo(4.0);
    }
}
