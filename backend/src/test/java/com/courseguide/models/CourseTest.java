package com.courseguide.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourseTest {

    @Test
    void toXmlEscapesSpecialCharacters() {
        Course c = new Course("R&D <1>", "A \"B\"", 3, "Major1", "it's");

        String xml = c.toXml();

        assertThat(xml).contains("<name>R&amp;D &lt;1&gt;</name>")
                .contains("<prerequisites>A &quot;B&quot;</prerequisites>")
                .contains("<creditHours>3</creditHours>")
                .contains("<description>it&apos;s</description>");
    }

    @Test
    void toXmlWritesNullsAsEmptyElements() {
        assertThat(new Course("X", null, 1, null, null).toXml())
                .contains("<prerequisites></prerequisites>")
                .contains("<category></category>");
    }

    @Test
    void settersAndToString() {
        Course c = new Course("A", "", 3, "Major1", "d");
        c.setCourseName("B");
        c.setPreqCourseName("A");
        c.setCreditHours(4);
        c.setCategory("Minor");
        c.setDescription("desc");

        assertThat(c.toString()).isEqualTo("B,A,4,Minor,desc");
    }
}
