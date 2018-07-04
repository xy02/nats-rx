package com.github.xy02.nats;

public class MSG {
    private String subject;
    private long sid;
    private String replyTo;
    private byte[] body;

    public MSG(String subject, byte[] body) {
        this(subject, "", body);
    }

    public MSG(String subject, String replyTo, byte[] body) {
        this(subject, 0, replyTo, body);
    }


    MSG(String subject, long sid, String replyTo, byte[] body) {
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

    public long getSid() {
        return sid;
    }

    public void setSid(long sid) {
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
