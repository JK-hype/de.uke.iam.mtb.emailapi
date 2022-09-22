package de.uke.iam.mtb.emailapi.service;

import static de.uke.iam.lib.restclient.RESTUtilHelper.get;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.JobKey.jobKey;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.TriggerKey.triggerKey;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.ws.rs.core.Response;

import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.reflect.TypeToken;

import de.uke.iam.lib.json.GsonHelper;
import de.uke.iam.mtb.emailapi.util.EmailQuartzJob;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeBodyPart;

@Service
public class EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    private final String emailHost;
    private final String emailPort;
    private final String userName;
    private final String password;
    private final String miracumUrl;
    private final int intervalTime;

    private final String jobKey = "emailJob";
    private final String triggerKey = "emailTrigger";

    private Store emailStore = null;
    private Folder inboxFolder = null;
    @Autowired
    private Scheduler scheduler;

    public EmailService(@Value("${emailHost}") String emailHost, @Value("${emailPort}") String emailPort,
            @Value("${emailUserName}") String userName, @Value("${emailPassword}") String password,
            @Value("${miracumUrl}") String miracumUrl, @Value("${intervalTime}") int intervalTime) {
        this.emailHost = emailHost;
        this.emailPort = emailPort;
        this.userName = userName;
        this.password = password;
        this.miracumUrl = miracumUrl;
        this.intervalTime = intervalTime;
    }

    @PostConstruct
    private void scheduleJob() {
        try {
            scheduler.start();
            JobDetail job = newJob(EmailQuartzJob.class).withIdentity(jobKey(jobKey))
                    .build();
            SimpleTrigger trigger = newTrigger().withIdentity(triggerKey(triggerKey))
                    .withSchedule(simpleSchedule().withIntervalInSeconds(intervalTime).repeatForever()).build();
            scheduler.scheduleJob(job, trigger);

            LOGGER.info("Started job to check emails");
            LOGGER.info("Checks every " + intervalTime + " minutes");
        } catch (SchedulerException e) {
            LOGGER.warn("Could not start scheduler");
            e.printStackTrace();
        }
    }

    /*
     * After downloading the attachments, the emails are deleted
     */
    public void checkInboxForNewEmails() {

        LOGGER.info("Check inbox for new messages");

        connectToInbox();

        try {
            Message[] messages = inboxFolder.getMessages();
            if (messages.length > 0) {
                LOGGER.info("Found " + messages.length + " new emails");
                for (Message message : messages) {
                    if (message.isSet(Flags.Flag.SEEN)) {
                        continue;
                    }
                    String id = downloadFastqFiles(message);
                    message.setFlag(Flags.Flag.DELETED, true);
                    if (!id.isEmpty()) {
                        try {
                            get(new URL(miracumUrl + "run/" + id));
                            LOGGER.info("Started MIRACUM Pipeline for patient with id " + id);
                        } catch (MalformedURLException e) {
                            LOGGER.error("Could not start MIRACUM Pipeline");
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                LOGGER.info("Did not found any new email");
            }
        } catch (MessagingException e) {
            e.printStackTrace();
        }

        disconnectFromInbox();
    }

    /*
     * Saves the fastQ files with name send in the subject of the email
     * The name must be in the form "firstName lastName"
     */
    private String downloadFastqFiles(Message message) {

        String id = "";

        try {
            String contentType = message.getContentType();

            if (contentType.contains("multipart")) {
                Multipart multipart = (Multipart) message.getContent();
                int numberOfFiles = multipart.getCount();

                id = message.getSubject();
                Response fastQDirectoryResponse = get(new URL(miracumUrl + "fastq/directory/" + id));
                Response fastQNameResponse = get(
                        new URL(miracumUrl + "fastq/names/" + id));
                /*
                 * 400 is the HTTP status code for "Bad Request"
                 * Returned by the miracum-api, if the patient with
                 * this id is not registered yet
                 */

                if (fastQDirectoryResponse.getStatus() == 400 || fastQNameResponse.getStatus() == 400) {
                    LOGGER.error("Patient with id " + id + " is not registered in the miracum-api");
                    return id;
                }

                String fastQDirectory = fastQDirectoryResponse.readEntity(String.class);
                List<String> fastQNames = GsonHelper.get().getNewGson().fromJson(
                        fastQNameResponse.readEntity(String.class),
                        new TypeToken<ArrayList<String>>() {
                        }.getType());

                int numberOfNoAttachments = 0;
                for (int partCount = 0; partCount < numberOfFiles; partCount++) {
                    MimeBodyPart mimePart = (MimeBodyPart) multipart.getBodyPart(partCount);
                    // Some body parts are no attachments
                    if (Part.ATTACHMENT.equalsIgnoreCase(mimePart.getDisposition())) {
                        int fastQCount = partCount - numberOfNoAttachments;
                        mimePart.saveFile(new File(
                                fastQDirectory + File.separator + fastQNames.get(fastQCount)));
                        LOGGER.info("Succesfully saved " + fastQNames.get(fastQCount) + " in " + fastQDirectory);
                    } else {
                        numberOfNoAttachments++;
                    }
                }
            }
        } catch (MessagingException e) {
            LOGGER.error("Could not retrieve attachments");
            e.printStackTrace();
        } catch (IOException e) {
            LOGGER.error("Could not write fastQ files");
            e.printStackTrace();
        }

        return id;
    }

    private void connectToInbox() {

        Properties properties = new Properties();

        properties.put("mail.store.protocol", "imap");
        properties.put("mail.imap.host", emailHost);
        properties.put("mail.imap.port", emailPort);
        properties.put("mail.imap.ssl.enable", "true");

        Session emailSession = Session.getInstance(properties);

        try {
            emailStore = emailSession.getStore();
            emailStore.connect(userName, password);

            inboxFolder = emailStore.getFolder("INBOX");
            inboxFolder.open(Folder.READ_WRITE);
        } catch (MessagingException e) {
            LOGGER.error("Failed to connect to email server");
            e.printStackTrace();
        }
    }

    private void disconnectFromInbox() {

        try {
            if (inboxFolder != null && inboxFolder.isOpen()) {
                inboxFolder.close(true);
            }
            if (emailStore != null && emailStore.isConnected()) {
                emailStore.close();
            }
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}