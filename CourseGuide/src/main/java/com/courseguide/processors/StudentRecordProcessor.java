package com.courseguide.processors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

public class StudentRecordProcessor {
    public record StudentRecord(String firstName, String lastName, String major, double gpa) {}

    public List<StudentRecord> loadFromXML(Path path) throws IOException {
        List<StudentRecord> records = new ArrayList<>();
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(path.toFile());
            doc.getDocumentElement().normalize();

            NodeList studentNodes = doc.getElementsByTagName("student");
            for (int i = 0; i < studentNodes.getLength(); i++) {
                Element studentElement = (Element) studentNodes.item(i);
                
                String firstName = getElementValue(studentElement, "firstName");
                String lastName = getElementValue(studentElement, "lastName");
                String major = getElementValue(studentElement, "major");
                String gpaStr = getElementValue(studentElement, "gpa");
                
                if (firstName == null) firstName = "";
                if (lastName == null) lastName = "";
                if (major == null) major = "";
                
                double gpa = 0.0;
                if (gpaStr != null && !gpaStr.isBlank()) {
                    try {
                        gpa = Double.parseDouble(gpaStr.trim());
                    } catch (NumberFormatException e) {
                        // Keep default
                    }
                }
                
                records.add(new StudentRecord(firstName.trim(), lastName.trim(), major.trim(), gpa));
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse XML file: " + e.getMessage(), e);
        }
        return records;
    }

    private String getElementValue(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() == 0) {
            return null;
        }
        Element element = (Element) nodeList.item(0);
        if (element == null) {
            return null;
        }
        Node node = element.getFirstChild();
        return node != null ? node.getNodeValue() : null;
    }
}
