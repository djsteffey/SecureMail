package hbs.app.securemail;

import java.util.ArrayList;
import java.util.List;

public class EmailMessage {
    // variables
    private String m_from;
    private List<String> m_to;
    private String m_subject;
    private String m_time;
    private String m_body;

    // methods
    public EmailMessage(){
        this("", new ArrayList<String>(), "", "", "");
    }

    public EmailMessage(String from, List<String> to, String subject, String time, String body){
        this.m_from = from;
        this.m_to = to;
        this.m_subject = subject;
        this.m_time = time;
        this.m_body = body;
    }

    public String getFrom(){
        return this.m_from;
    }

    public List<String> getTo(){
        return this.m_to;
    }

    public String getSubject(){
        return this.m_subject;
    }

    public String getTime(){
        return this.m_time;
    }

    public String getBody(){
        return this.m_body;
    }
}
