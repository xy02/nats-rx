package com.github.xy02.nats;

public class Msg {
    private String subject;
    private int sid;
    private String replyTo;
    private byte[] body;

    protected Msg(String subject, int sid, String replyTo, byte[] body) {
        this.subject = subject;
        this.sid = sid;
        this.replyTo = replyTo;
        this.body = body;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public int getSid() {
        return sid;
    }

    public void setSid(int sid) {
        this.sid = sid;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }
}
