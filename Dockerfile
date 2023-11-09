
FROM openjdk:11
COPY  target/ControllerHPS-1.0-SNAPSHOT.jar /
ENTRYPOINT ["java","-jar","/ControllerHPS-1.0-SNAPSHOT.jar"]



