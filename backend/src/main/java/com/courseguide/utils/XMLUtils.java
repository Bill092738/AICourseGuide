package com.courseguide.utils;

import com.courseguide.models.Course;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * Utility class for XML operations related to course data.
 * Provides parsing and generation of XML course plans.
 */
public class XMLUtils {

    /**
     * Parses courses from an XML file.
     * Expected XML format:
     * <coursePlan>
     *   <course>
     *     <name>Course Name</name>
     *     <prerequisites>Prereq1;Prereq2</prerequisites>
     *     <creditHours>3</creditHours>
     *     <category>Major1</category>
     *     <description>Course description</description>
     *   </course>
     * </coursePlan>
     *
     * @param filePath the path to the XML file
     * @return list of parsed Course objects
     * @throws IOException if file cannot be read
     */
    public static List<Course> parseCoursesFromXML(String filePath) throws IOException {
        List<Course> courses = new ArrayList<>();
        try {
            File xmlFile = new File(filePath);
            if (!xmlFile.exists()) {
                throw new FileNotFoundException("XML file not found: " + filePath);
            }

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList courseNodes = doc.getElementsByTagName("course");
            for (int i = 0; i < courseNodes.getLength(); i++) {
                Element courseElement = (Element) courseNodes.item(i);
                Course course = parseCourseElement(courseElement);
                if (course != null) {
                    courses.add(course);
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse XML file: " + e.getMessage(), e);
        }
        return courses;
    }

    /**
     * Parses courses from XML content string.
     *
     * @param xmlContent the XML content as a string
     * @return list of parsed Course objects
     */
    public static List<Course> parseCoursesFromXMLContent(String xmlContent) {
        List<Course> courses = new ArrayList<>();
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new InputSource(new StringReader(xmlContent)));
            doc.getDocumentElement().normalize();

            NodeList courseNodes = doc.getElementsByTagName("course");
            for (int i = 0; i < courseNodes.getLength(); i++) {
                Element courseElement = (Element) courseNodes.item(i);
                Course course = parseCourseElement(courseElement);
                if (course != null) {
                    courses.add(course);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse XML content: " + e.getMessage());
        }
        return courses;
    }

    /**
     * Parses a single course element from XML.
     *
     * @param courseElement the XML element representing a course
     * @return the parsed Course object, or null if parsing fails
     */
    private static Course parseCourseElement(Element courseElement) {
        try {
            String courseName = getElementValue(courseElement, "name");
            String prereqs = getElementValue(courseElement, "prerequisites");
            String creditStr = getElementValue(courseElement, "creditHours");
            String category = getElementValue(courseElement, "category");
            String description = getElementValue(courseElement, "description");

            if (courseName == null || courseName.isBlank()) {
                return null;
            }

            int creditHours = 3;
            if (creditStr != null && !creditStr.isBlank()) {
                try {
                    creditHours = Integer.parseInt(creditStr.trim());
                } catch (NumberFormatException e) {
                    System.err.println("Invalid credit hours: " + creditStr + ", defaulting to 3");
                }
            }

            if (category == null || category.isBlank()) {
                category = "Major1";
            }

            if (description == null) {
                description = "";
            }

            if (prereqs == null) {
                prereqs = "";
            }

            return new Course(courseName.trim(), prereqs.trim(), creditHours, category.trim(), description.trim());
        } catch (Exception e) {
            System.err.println("Failed to parse course element: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the text content of a child element.
     *
     * @param parent the parent element
     * @param tagName the tag name of the child element
     * @return the text content, or null if element not found
     */
    private static String getElementValue(Element parent, String tagName) {
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

    /**
     * Generates XML content from a list of courses.
     *
     * @param courses the list of courses to convert to XML
     * @return the XML content as a string
     */
    public static String generateXML(List<Course> courses) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();

            Element rootElement = doc.createElement("coursePlan");
            doc.appendChild(rootElement);

            for (Course course : courses) {
                Element courseElement = doc.createElement("course");
                rootElement.appendChild(courseElement);

                appendChildElement(doc, courseElement, "name", course.getCourseName());
                appendChildElement(doc, courseElement, "prerequisites", course.getPreqCourseName());
                appendChildElement(doc, courseElement, "creditHours", String.valueOf(course.getCreditHours()));
                appendChildElement(doc, courseElement, "category", course.getCategory());
                appendChildElement(doc, courseElement, "description", course.getDescription());
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            DOMSource source = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);

            return writer.toString();
        } catch (Exception e) {
            System.err.println("Failed to generate XML: " + e.getMessage());
            return "";
        }
    }

    /**
     * Appends a child element with text content to a parent element.
     *
     * @param doc the document
     * @param parent the parent element
     * @param tagName the tag name
     * @param value the text value
     */
    private static void appendChildElement(Document doc, Element parent, String tagName, String value) {
        Element element = doc.createElement(tagName);
        element.appendChild(doc.createTextNode(value != null ? value : ""));
        parent.appendChild(element);
    }
}
