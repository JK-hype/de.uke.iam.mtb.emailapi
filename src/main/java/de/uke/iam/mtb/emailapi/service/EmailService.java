package de.uke.iam.mtb.emailapi.service;

import static de.uke.iam.lib.restclient.RESTUtilHelper.get;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.reflect.TypeToken;

import de.uke.iam.lib.json.GsonHelper;
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

    private Store emailStore = null;
    private Folder inboxFolder = null;

    public EmailService(@Value("${emailHost}") String emailHost, @Value("${emailPort}") String emailPort,
            @Value("${emailUserName}") String userName, @Value("${emailPassword}") String password,
            @Value("${miracumUrl}") String miracumUrl) {
        this.emailHost = emailHost;
        this.emailPort = emailPort;
        this.userName = userName;
        this.password = password;
        this.miracumUrl = miracumUrl;
    }

    /*
     * Gets the last received email
     * Saves the fastQ files with name send in the subject of the email
     * The name must be in the form "firstName lastName"
     */
    public void downloadFastqFiles() {

        connectToInbox();

        try {
            Message message = inboxFolder.getMessage(inboxFolder.getMessageCount());
            String contentType = message.getContentType();

            if (contentType.contains("multipart")) {
                Multipart multipart = (Multipart) message.getContent();
                int numberOfFiles = multipart.getCount();

                String[] name = message.getSubject().split(" ");
                String patientDirectory = get(
                        new URL(miracumUrl + "fastq/directory/" + name[0] + "/" + name[1])).readEntity(String.class);
                // Expects the number of file pairs
                String nameEntity = get(
                        new URL(miracumUrl + "fastq/names/" + numberOfFiles / 2 + "/" + name[0] + "/" + name[1]))
                        .readEntity(String.class);
                List<String> fastQNames = GsonHelper.get().getNewGson().fromJson(nameEntity,
                        new TypeToken<ArrayList<String>>() {
                        }.getType());
                int numberOfNoAttachments = 0;
                for (int partCount = 0; partCount < numberOfFiles; partCount++) {
                    MimeBodyPart mimePart = (MimeBodyPart) multipart.getBodyPart(partCount);
                    if (Part.ATTACHMENT.equalsIgnoreCase(mimePart.getDisposition())) {
                        int fastQCount = partCount - numberOfNoAttachments;
                        mimePart.saveFile(new File(
                                patientDirectory + File.separator + fastQNames.get(fastQCount)));
                        LOGGER.info("Succesfully saved " + fastQNames.get(fastQCount) + " in " + patientDirectory);
                    } else {
                        numberOfNoAttachments++;
                    }
                }
            }
        } catch (MessagingException e) {
            LOGGER.error("Could not retrieve attachments");
            e.printStackTrace();
        } catch (IOException e) {
            LOGGER.error("Could write fastQ files");
            e.printStackTrace();
        }
        /*
         * TODO
         * Send run request to miracum-api
         * Need the fully filled MiracumInputDetail Object
         */
        disconnectFromInbox();
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
            inboxFolder.open(Folder.READ_ONLY);
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