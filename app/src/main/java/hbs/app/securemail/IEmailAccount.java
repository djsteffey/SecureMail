package hbs.app.securemail;

public interface IEmailAccount {
    interface Callback{
        void onConnectComplete(boolean success, String message);
        void onSendComplete(boolean success, String message);
        void onNewEmailMessage(EmailMessage message);
    }

    void runAsync();
    void sendMessageAsync(EmailMessage message, Callback callback);
}
