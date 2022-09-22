package de.uke.iam.mtb.emailapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import de.uke.iam.mtb.emailapi.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@Controller
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @Operation(summary = "Check emails", description = "Check inbox folder for new emails")

    @ApiResponse(responseCode = "200", description = "Checked inbox for new emails")
    @GetMapping("/email/check_emails")
    public ResponseEntity<String> checkEmails() {
        emailService.checkInboxForNewEmails();
        return new ResponseEntity<String>("Checked inbox for new emails", HttpStatus.OK);
    }

}
