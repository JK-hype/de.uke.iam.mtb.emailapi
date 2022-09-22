package de.uke.iam.mtb.emailapi.util;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.uke.iam.mtb.emailapi.service.EmailService;

@Component
public class EmailQuartzJob implements Job {

    @Autowired
    private EmailService emailService;

    public EmailQuartzJob() {
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        emailService.checkInboxForNewEmails();
    }
}