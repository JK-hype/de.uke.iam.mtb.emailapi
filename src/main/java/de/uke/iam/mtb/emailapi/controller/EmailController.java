package de.uke.iam.mtb.emailapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import de.uke.iam.mtb.emailapi.service.EmailService;

@Controller
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/email/download/fastQ")
    public ResponseEntity<String> downloadFastQFiles() {
        emailService.downloadFastqFiles();
        return new ResponseEntity<String>("Downloaded fastQ files", HttpStatus.OK);
    }

}
