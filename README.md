# Salesforce Validation Rule Switch

##  Project Description
This project allows users to:
- Fetch Salesforce Validation Rules
- Toggle (ON/OFF) validation rules
- Deploy changes dynamically using Salesforce APIs

##  Features
- OAuth2 Login with Salesforce
- Fetch Account data
- Fetch Validation Rules
- Enable / Disable Validation Rules
- Deploy changes
- Rollback functionality

##  Tech Stack
- Java
- Spring Boot
- REST APIs
- Thymeleaf
- Salesforce Tooling API

##  Setup Instructions

1. Clone the repository:
   git clone https://github.com/your-username/salesforce-validation-rule-switch

2. Open in Eclipse / IntelliJ

3. Update credentials in AuthController:
   - CLIENT_ID
   - CLIENT_SECRET

4. Run the application:
   mvn spring-boot:run

5. Open browser:
   http://localhost:8080/

6. Click Login to connect with Salesforce

##  Note
Salesforce credentials are not included for security reasons.

##  Author
Sheik Iliyaz
GitHub: https://github.com/iliyazsheik
