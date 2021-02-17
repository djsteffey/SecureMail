package hbs.app.securemail;


import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;

import java.io.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import java.nio.charset.*;
import java.util.Arrays;
import java.util.Properties;

public class MainActivity extends AppCompatActivity {
    // const
    private static final String HEADER = "===SECUREMAIL===";

    // variables
    private byte[] m_publickey;
    private byte[] m_privatekey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // setup the view
        this.setContentView(R.layout.activity_main);

        // generate keys
        if (this.generateKeys() == false){
            this.log("Unable to generate encryption keys");
            return;
        }

        // listen for button events
        this.findViewById(R.id.button_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.onClickButtonSend();
            }
        });
        this.findViewById(R.id.button_read).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.onClickButtonRead();
            }
        });

        // lets get started

    }

    private void onClickButtonSend(){
        // send a test message
        this.sendTestMessage();
    }

    private void onClickButtonRead(){
        this.readUnreadMessages();
    }

    private boolean generateKeys(){
        // first see if we can read them from a file
        try{
            FileInputStream fis = this.openFileInput("user.keys");
            ObjectInputStream ois = new ObjectInputStream(fis);
            this.m_publickey = (byte[])ois.readObject();
            this.m_privatekey = (byte[])ois.readObject();
            ois.close();
            fis.close();
            log("Keys loaded from file");
            return true;
        } catch (Exception e){
            try{
                // couldnt load from file so try to generate them new
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(512);

                KeyPair pair = gen.generateKeyPair();
                this.m_publickey = pair.getPublic().getEncoded();
                this.m_privatekey = pair.getPrivate().getEncoded();

                FileOutputStream fos = this.openFileOutput("user.keys", Context.MODE_PRIVATE);
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(this.m_publickey);
                oos.writeObject(this.m_privatekey);
                oos.close();
                fos.close();

                this.log("New keys generated");

                return true;
            } catch (Exception e1){
                e1.printStackTrace();
                return false;
            }
        }
    }


    private void sendTestMessage(){
        final String[] SCOPES = {
                GmailScopes.GMAIL_LABELS,
                GmailScopes.GMAIL_COMPOSE,
                GmailScopes.GMAIL_INSERT,
                GmailScopes.GMAIL_MODIFY,
                GmailScopes.GMAIL_READONLY,
                GmailScopes.MAIL_GOOGLE_COM
        };
        GoogleAccountCredential cred = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        new MakeRequestTask(cred).execute();
    }

    private void readUnreadMessages() {

    }



    String encodeMessageBody(String content) throws Exception{
        return HEADER + "\n" + this.encryptText(content);
    }

    String decodeMessageBody(String content) throws Exception{
        // make sure has header
        if (content.substring(0, HEADER.length()).equals(HEADER)){
            return this.decryptText(content.substring(HEADER.length()));
        }
        return "Not encrypted with SECUREMAIL";//: " + content;
    }

    String encryptText(String text) throws Exception{
        byte[] data = encrypt(this.m_publickey, text.getBytes("UTF-8"));
        String encoded = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
        return encoded;
    }

    String decryptText(String text) throws Exception{
        byte[] decoded = android.util.Base64.decode(text, android.util.Base64.NO_WRAP);
        byte[] data = decrypt(this.m_privatekey, decoded);
        String s = new String(data, StandardCharsets.UTF_8);
        return s;
    }

    private static byte[] encrypt(byte[] public_key, byte[] input_data) throws Exception{
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(public_key));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(input_data);
    }

    private static byte[] decrypt(byte[] private_key, byte[] input_data) throws Exception {
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(private_key));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(input_data);
    }

    private void log(final String message){
        if (Looper.getMainLooper().isCurrentThread()) {
            TextView tv = this.findViewById(R.id.textview);
            String s = tv.getText() + "\n" + message;
            tv.setText(s);
        }
        else{
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    log(message);
                }
            });
        }
    }



    private class MakeRequestTask extends AsyncTask {
        private com.google.api.services.gmail.Gmail mService = null;
        private Exception mLastError = null;
        private GoogleAccountCredential credential;
        public MakeRequestTask(GoogleAccountCredential credential) {
            this.credential = credential;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();
        }



        private String getDataFromApi() throws IOException {
            // getting Values for to Address, from Address, Subject and Body
            String user = "me";
            String to = "djsteffey@gmail.com";
            String from = credential.getSelectedAccountName();
            String subject = "test";
            String body = "test from android";
            MimeMessage mimeMessage;
            String response = "";
            try {
                mimeMessage = createEmail(to, from, subject, body);
                response = sendMessage(mService, user, mimeMessage);
            } catch (MessagingException e) {
                e.printStackTrace();
            }
            return response;
        }

        // Method to send email
        private String sendMessage(Gmail service,
                                   String userId,
                                   MimeMessage email)
                throws MessagingException, IOException {
            Message message = createMessageWithEmail(email);
            // GMail's official method to send email with oauth2.0
            message = service.users().messages().send(userId, message).execute();

            System.out.println("Message id: " + message.getId());
            System.out.println(message.toPrettyString());
            return message.getId();
        }

        // Method to create email Params
        private MimeMessage createEmail(String to,
                                        String from,
                                        String subject,
                                        String bodyText) throws MessagingException {
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props, null);

            MimeMessage email = new MimeMessage(session);
            InternetAddress tAddress = new InternetAddress(to);
            InternetAddress fAddress = new InternetAddress(from);

            email.setFrom(fAddress);
            email.addRecipient(javax.mail.Message.RecipientType.TO, tAddress);
            email.setSubject(subject);

            // Create Multipart object and add MimeBodyPart objects to this object
            Multipart multipart = new MimeMultipart();

            // Changed for adding attachment and text
            // This line is used for sending only text messages through mail
            // email.setText(bodyText);

            BodyPart textBody = new MimeBodyPart();
            textBody.setText(bodyText);
            multipart.addBodyPart(textBody);

            // Set the multipart object to the message object
            email.setContent(multipart);
            return email;
        }

        private Message createMessageWithEmail(MimeMessage email)
                throws MessagingException, IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            email.writeTo(bytes);
            String encodedEmail = Base64.encodeBase64URLSafeString(bytes.toByteArray());
            Message message = new Message();
            message.setRaw(encodedEmail);
            return message;
        }

        @Override
        protected Object doInBackground(Object[] objects) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected void onCancelled() {

        }
    }
}
