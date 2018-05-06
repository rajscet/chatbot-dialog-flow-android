package com.raj.chatbot;

/**
 * Class that represent the message.
 */
public class Chat {
    public String message;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int type;

    public Chat() {
    }

    public Chat(String message, int type) {
        this.message = message;
        this.type=type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
