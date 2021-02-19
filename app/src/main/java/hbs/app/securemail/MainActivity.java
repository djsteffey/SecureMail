package hbs.app.securemail;


import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;

import java.io.*;
import java.security.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    // const
    private static final String HEADER = "===SECUREMAIL===";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final List<String> SCOPES = new ArrayList<String>(){{
        add(GmailScopes.GMAIL_LABELS);
        add(GmailScopes.GMAIL_READONLY);
    }};

    // variables
    private byte[] m_publickey;
    private byte[] m_privatekey;
    private GoogleAccountCredential m_google_account_credential;
    private RecyclerViewAdapterMessagesRow m_adapater;

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
        this.findViewById(R.id.activitymain_button_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.onClickButtonSend();
            }
        });
        this.findViewById(R.id.activitymain_button_read).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.onClickButtonRead();
            }
        });

        // lets get started
        this.m_google_account_credential = GoogleAccountCredential.usingOAuth2(
                this.getApplicationContext(),
                SCOPES)
                .setBackOff(new ExponentialBackOff());
        this.startActivityForResult(this.m_google_account_credential.newChooseAccountIntent(), 12345);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case 12345:{
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null){
                    String name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    this.m_google_account_credential.setSelectedAccountName(name);
                }
            } break;

        }
    }

    private void onClickButtonSend(){
        // send a test message
        this.sendTestMessage();
    }

    private void onClickButtonRead(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MainActivity.this.readMessages();
                } catch (UserRecoverableAuthIOException ae){
                    MainActivity.this.startActivityForResult(ae.getIntent(), 23456);
                } catch (Exception e){
                    log(e.toString());
                    e.printStackTrace();
                }

            }
        }).start();

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

    }

    private void readMessages() throws Exception{
        HttpTransport http_transport = AndroidHttp.newCompatibleTransport(); //GoogleNetHttpTransport.newTrustedTransport();
        Gmail service = new Gmail.Builder(
                http_transport,
                JSON_FACTORY,
                this.m_google_account_credential)
                .setApplicationName(this.getResources().getString(R.string.app_name))
                .build();

        String user = "me";
        ListMessagesResponse messages_response = service.users().messages().list(user).execute();

/*
        ListLabelsResponse list_response = service.users().labels().list(user).execute();
        List<Label> lables = list_response.getLabels();
        if (lables.isEmpty()){
            log("Empty labels");
        }
        else{
            for (Label label : lables){
                log("label: " + label);
            }
        }*/
        List<String> subjects = new ArrayList<>();
        List<Message> messages = messages_response.getMessages();
        for (Message message : messages){
            GetMessageResponse get_response = service.users().messages().get(user, message.getId()).execute();

            /*
            MessagePart mp = message.getPayload();
            List<MessagePartHeader> mphs = mp.getHeaders();
            for (MessagePartHeader mph : mphs) {
                subjects.add(mph.toString());
            }*/
        }

        RecyclerView rv = this.findViewById(R.id.activitymain_recyclerview_messages);
        rv.setLayoutManager(new LinearLayoutManager(this));
        this.m_adapater = new RecyclerViewAdapterMessagesRow(this, subjects);
        this.m_adapater.setClickListener(new RecyclerViewAdapterMessagesRow.ItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {

            }
        });
        rv.setAdapter(this.m_adapater);
    }



    private void log(String msg){
        Log.d("SecureMail", msg);
    }

}
