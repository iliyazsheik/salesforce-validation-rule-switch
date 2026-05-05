package com.cloudvandana.salesforce_task.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.client.RestTemplate;
import org.springframework.ui.Model;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.util.Map;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.security.MessageDigest;

@Controller
public class AuthController {
	
	
	Map<String, Boolean> originalStatus = new HashMap<>();

    private final String CLIENT_ID = "YOUR_CLIENT_ID";
    private final String CLIENT_SECRET = "YOUR_CLIENT_SECRET";
    private final String REDIRECT_URI = "http://localhost:8080/callback";
    private String accessToken;
    private String instanceUrl;
    Map<String, Map<String, Object>> metadataMap = new HashMap<>();
  

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws Exception {

        String verifier = generateCodeVerifier();
        savedVerifier = verifier;

        String challenge = generateCodeChallenge(verifier);

        String authUrl = "https://login.salesforce.com/services/oauth2/authorize"
                + "?response_type=code"
                + "&client_id=" + CLIENT_ID
                + "&redirect_uri=" + REDIRECT_URI
                + "&code_challenge=" + challenge
                + "&code_challenge_method=S256";

        response.sendRedirect(authUrl);
    }
    private String savedVerifier;

    private String generateCodeVerifier() {
        byte[] code = new byte[32];
        new SecureRandom().nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }

    private String generateCodeChallenge(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
    
    
    @GetMapping("/callback")
    public String callback(@RequestParam("code") String code, Model model) {

        String tokenUrl = "https://login.salesforce.com/services/oauth2/token";

        RestTemplate restTemplate = new RestTemplate();

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", CLIENT_ID);
        body.add("client_secret", CLIENT_SECRET);
        body.add("redirect_uri", REDIRECT_URI);
        body.add("code", code);
        body.add("code_verifier", savedVerifier);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(tokenUrl, request, Map.class);

        accessToken = (String) response.getBody().get("access_token");
        instanceUrl = (String) response.getBody().get("instance_url");

        //  USER INFO
        String userInfoUrl = instanceUrl + "/services/oauth2/userinfo";

        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.set("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(userHeaders);

        ResponseEntity<Map> userResponse =
                restTemplate.exchange(userInfoUrl, HttpMethod.GET, entity, Map.class);

        String username = (String) userResponse.getBody().get("preferred_username");
        String orgName = (String) userResponse.getBody().get("organization_id");

        model.addAttribute("username", username);
        model.addAttribute("org", orgName);

        return "home";
    }
    
    
    @GetMapping("/metadata")
    public String getMetadata(Model model) {

    	HttpComponentsClientHttpRequestFactory requestFactory =
    	        new HttpComponentsClientHttpRequestFactory();

    	RestTemplate restTemplate = new RestTemplate(requestFactory);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Accounts
        String accUrl = instanceUrl +
                "/services/data/v57.0/query?q=SELECT+Id,Name+FROM+Account";

        ResponseEntity<Map> accResponse =
                restTemplate.exchange(accUrl, HttpMethod.GET, entity, Map.class);

        List<Map<String, Object>> accounts =
                (List<Map<String, Object>>) accResponse.getBody().get("records");


        // Validation Rules
        String vrUrl = instanceUrl +
        		"/services/data/v57.0/tooling/query?q=SELECT Id, ValidationName, ErrorMessage, Active FROM ValidationRule";
    
        ResponseEntity<Map> vrResponse =
                restTemplate.exchange(vrUrl, HttpMethod.GET, entity, Map.class);

        List<Map<String, Object>> rules =
                (List<Map<String, Object>>) vrResponse.getBody().get("records");
        
        for (Map<String, Object> rule : rules) {

            String ruleId = (String) rule.get("Id");
            Boolean isActive = (Boolean) rule.get("Active");

            originalStatus.put(ruleId, isActive);
            
            String metaUrl = instanceUrl +
                    "/services/data/v57.0/tooling/query?q=SELECT Metadata FROM ValidationRule WHERE Id='" + ruleId + "'";

                ResponseEntity<Map> metaResponse =
                    restTemplate.exchange(metaUrl, HttpMethod.GET, entity, Map.class);

                List<Map<String, Object>> metaRecords =
                    (List<Map<String, Object>>) metaResponse.getBody().get("records");

                Map<String, Object> metadata =
                    (Map<String, Object>) metaRecords.get(0).get("Metadata");

                //  STORE metadata
                metadataMap.put(ruleId, metadata);
        }
        

        model.addAttribute("accounts", accounts);
        model.addAttribute("rules", rules);

        return "switch";
    }
    
    @PostMapping("/deploy")
    public String deployChanges(
            @RequestParam("ruleId") List<String> ruleIds,
            @RequestParam("status") List<String> statuses,
            Model model) {

        List<Map<String, String>> result = new ArrayList<>();
        
        System.out.println("Rule IDs: " + ruleIds);
        System.out.println("Statuses: " + statuses);
        for (int i = 0; i < ruleIds.size(); i++) {

            String id = ruleIds.get(i);
            String stat = statuses.get(i);

            //  CONVERT TO BOOLEAN
            boolean isActive = "ON".equalsIgnoreCase(stat);

            //  CALL SALESFORCE API
            updateValidationRule(id, isActive);

            // for UI display
            Map<String, String> map = new HashMap<>();
            map.put("ruleId", id);
            map.put("status", stat);
            result.add(map);
        }

        model.addAttribute("updatedRules", result);
        model.addAttribute("message", "All changes have been successfully deployed");

        return "result";
    }

    @PostMapping("/rollback")
    public String rollback(Model model) {

        model.addAttribute("message", "Rollback successful");

        return "result";
    }
    
    
    @GetMapping("/accounts")
    public List<Map<String, String>> getAccounts() {

        String url = instanceUrl + "/services/data/v57.0/query?q=SELECT+Id,Name+FROM+Account";

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        List<Map<String, Object>> records =
                (List<Map<String, Object>>) response.getBody().get("records");

        List<Map<String, String>> result = new ArrayList<>();

        for (Map<String, Object> record : records) {
            Map<String, String> acc = new HashMap<>();
            acc.put("Id", (String) record.get("Id"));
            acc.put("Name", (String) record.get("Name"));
            result.add(acc);
        }

        return result;
    }
    
    @GetMapping("/validation-rules")
    public List<Map<String, String>> getValidationRules() {

    	String url = instanceUrl + "/services/data/v57.0/tooling/query?q=SELECT+Id,ValidationName,ErrorMessage,Active+FROM+ValidationRule";

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        List<Map<String, Object>> records =
                (List<Map<String, Object>>) response.getBody().get("records");

        List<Map<String, String>> filtered = new ArrayList<>();

        for (Map<String, Object> record : records) {

            String name = (String) record.get("ValidationName");

            if (name.equalsIgnoreCase("Name_Not_Empty") ||
                name.equalsIgnoreCase("Phone_10_Digits") ||
                name.equalsIgnoreCase("Revenue_Positive") ||
                name.equalsIgnoreCase("Industry_Not_Empty")) {

                Map<String, String> rule = new HashMap<>();
                rule.put("ValidationName", name);
                rule.put("ErrorMessage", (String) record.get("ErrorMessage"));

                filtered.add(rule);
            }
        }

        return filtered;
    }
    
    
    public void updateValidationRule(String ruleId, boolean isActive) {

        String url = instanceUrl + "/services/data/v57.0/tooling/sobjects/ValidationRule/" + ruleId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        //  Get stored metadata
        Map<String, Object> existingMetadata = metadataMap.get(ruleId);

        if (existingMetadata == null) {
            System.out.println("Metadata not found for: " + ruleId);
            return;
        }

        //  ONLY change active flag
        existingMetadata.put("active", isActive);

        Map<String, Object> body = new HashMap<>();
        body.put("Metadata", existingMetadata);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory();

        RestTemplate restTemplate = new RestTemplate(factory);

        //  CALL PATCH + DEBUG RESPONSE
        ResponseEntity<String> response =
                restTemplate.exchange(url, HttpMethod.PATCH, entity, String.class);

        System.out.println("Updated Rule: " + ruleId + " -> " + isActive);
        System.out.println("Salesforce Response: " + response.getBody());
    }
    
    
    @GetMapping("/")
    public String home() {
        return "login";
    }
    
    
    @GetMapping("/logout")
    public String logout() {
        accessToken = null;
        instanceUrl = null;
        return "login";
    }
}