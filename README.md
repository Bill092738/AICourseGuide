# CourseGuide

A minimal Java web app for course and career recommendations.

## Structure

```
CourseGuide/
  src/main/java/com/courseguide/
    App.java
    ApiController.java
    processors/
    utils/
  src/main/resources/public/
    index.html
    app.js
  pom.xml
```

## How to Compile

```sh
cd CourseGuide
mvn clean package
```

## How to Run

```sh
java -jar target/courseguide-0.1.0-SNAPSHOT.jar
```

Then open in your browser:  
[http://localhost:8080/public/index.html](http://localhost:8080/public/index.html)